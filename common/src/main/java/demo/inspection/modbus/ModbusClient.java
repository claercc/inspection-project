package demo.inspection.modbus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

/**
 * 最小 Modbus TCP 客户端：同一连接上的读写串行执行，按 MBAP 长度字段拆分报文。
 * 本层检查协议响应，不自动重试控制写入；执行是否成功由上层读取设备状态确认。
 */
public final class ModbusClient implements AutoCloseable {

    private static final int READ_HOLDING_REGISTERS = 3;
    private static final int WRITE_SINGLE_REGISTER = 6;
    private static final int MAX_REGISTER_QUANTITY = 125;
    // 本项目的 FC03/FC06 请求：Unit ID 1 字节 + 功能码 1 字节 + 地址 2 字节 + 数量/值 2 字节。
    private static final int REQUEST_LENGTH = 6;

    private final String host;
    private final int port;
    private final int unit;
    private final int timeout;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private int transactionId;

    /**
     * 配置一个串行 Modbus TCP 客户端；构造时不联网，首次读写时建立连接。
     *
     * @param host 设备主机名或 IP 地址。
     * @param port Modbus TCP 监听端口；本地模拟器默认 1502。
     * @param unit 请求中的 Unit ID，应与设备配置一致。
     * @param timeout 连接与套接字读取超时，单位毫秒。
     *
     * <p>用法示例：
     * <pre>{@code
     * new ModbusClient("127.0.0.1", 1502, 1, 1000);
     * }</pre>
     */
    public ModbusClient(String host, int port, int unit, int timeout) {
        this.host = host;
        this.port = port;
        this.unit = unit;
        this.timeout = timeout;
    }

    /**
     * 在尚无可用套接字时建立连接，供 request 调用，无参数、无返回值。
     *
     * @throws IOException 连接设备或建立输入输出流失败时抛出。
     */
    private void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }
        Socket nextSocket = new Socket();
        try {
            nextSocket.connect(new InetSocketAddress(host, port), timeout);
            nextSocket.setSoTimeout(timeout);
            nextSocket.setTcpNoDelay(true);
            socket = nextSocket;
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
        } catch (IOException exception) {
            nextSocket.close();
            throw exception;
        }
    }

    /**
     * 执行一次 FC03 保持寄存器读取；同一客户端上的读写由对象锁串行化。
     *
     * @param address 零基起始地址，按目标设备点表提供。
     * @param quantity 连续读取的寄存器数量，必须为 1～125。
     * @return 按地址递增排列的原始无符号 16 位寄存器值数组，未进行单位换算。
     * @throws IllegalArgumentException quantity 超出允许范围时抛出。
     * @throws IOException 连接、读取、帧校验失败或设备返回协议异常时抛出。
     *
     * <p>用法示例：
     * <pre>{@code
     * int[] registers = client.readHolding(0, 7);
     * }</pre>
     */
    public synchronized int[] readHolding(int address, int quantity) throws IOException {
        if (quantity < 1 || quantity > MAX_REGISTER_QUANTITY) {
            throw new IllegalArgumentException("寄存器读取数量必须在 1～125 之间");
        }
        byte[] response = request(READ_HOLDING_REGISTERS, address, quantity);
        int expectedByteCount = quantity * 2;
        if (
            response.length != 2 + expectedByteCount ||
            Byte.toUnsignedInt(response[1]) != expectedByteCount
        ) {
            close();
            throw new IOException("FC03 响应字节数与请求数量不一致");
        }
        int[] registers = new int[quantity];
        for (int index = 0; index < quantity; index++) {
            int offset = 2 + index * 2;
            registers[index] =
                (Byte.toUnsignedInt(response[offset]) << 8) |
                Byte.toUnsignedInt(response[offset + 1]);
        }
        return registers;
    }

    /**
     * 执行一次 FC06 写单寄存器并核对回显，无返回值；业务层仍需读回状态确认执行。
     *
     * @param address 零基目标地址，由调用方按点表指定。
     * @param value 要写入的 16 位原始值，由调用方保证符合点表约定。
     * @throws IOException 通信失败、回显不匹配或设备明确拒绝写入时抛出；本层不自动重试。
     *
     * <p>用法示例：
     * <pre>{@code
     * client.writeSingle(RobotRegisters.CONTROL, RobotRegisters.STOP_CONTROL);
     * }</pre>
     */
    public synchronized void writeSingle(int address, int value) throws IOException {
        byte[] response = request(WRITE_SINGLE_REGISTER, address, value);
        // FC06 必须回显原地址和原值；回显匹配后仍需业务层读取设备状态。
        byte[] expectedEcho = {
            (byte) WRITE_SINGLE_REGISTER,
            (byte) (address >> 8),
            (byte) address,
            (byte) (value >> 8),
            (byte) value,
        };
        if (!Arrays.equals(response, expectedEcho)) {
            close();
            throw new IOException("FC06 写入回显与请求不一致");
        }
    }

    /**
     * 完成一次连接、发送和响应读取，供 FC03/FC06 方法调用。
     *
     * @param function Modbus 功能码，本客户端使用 3 或 6。
     * @param address 请求中的零基寄存器地址。
     * @param value FC03 时为数量，FC06 时为写入值。
     * @return 不含 MBAP 头和 Unit ID 的响应 PDU。
     * @throws IOException 任何通信或协议校验失败时抛出，并清除连接供下次请求重建。
     */
    private byte[] request(int function, int address, int value) throws IOException {
        try {
            connect();
            // 事务编号为 16 位，用于识别当前请求对应的响应；达到上限后回绕。
            transactionId = (transactionId + 1) & 0xffff;
            writeRequest(function, address, value);
            return readResponse(function);
        } catch (IOException exception) {
            close();
            throw exception;
        }
    }

    /**
     * 按当前事务编号写出完整请求帧并刷新输出流，无返回值。
     *
     * @param function 当前功能码。
     * @param address 零基寄存器地址。
     * @param value 数量或写入值，具体含义由功能码决定。
     * @throws IOException 写入套接字失败时抛出。
     */
    private void writeRequest(int function, int address, int value) throws IOException {
        output.writeShort(transactionId);
        output.writeShort(0);
        output.writeShort(REQUEST_LENGTH);
        output.writeByte(unit);
        output.writeByte(function);
        output.writeShort(address);
        output.writeShort(value);
        output.flush();
    }

    /**
     * 按 MBAP 长度读取响应，并校验事务编号、协议、设备单元和功能码。
     *
     * @param function 本次请求使用的功能码，用于匹配正常或异常响应。
     * @return 完整响应 PDU；具体字节数或 FC06 回显由调用方继续核对。
     * @throws IOException 超时、截断或响应格式不匹配时抛出。
     * @throws ModbusException 设备返回格式正确的协议异常响应时抛出。
     */
    private byte[] readResponse(int function) throws IOException {
        // TCP 没有消息边界，一帧可能分多次到达；不能把一次 read 当成完整响应。
        int responseTransactionId = input.readUnsignedShort();
        int protocolId = input.readUnsignedShort();
        int length = input.readUnsignedShort();
        int responseUnit = input.readUnsignedByte();
        if (
            responseTransactionId != transactionId ||
            protocolId != 0 ||
            responseUnit != unit ||
            length < 2 ||
            length > 254
        ) {
            throw new IOException(
                String.format(
                    "MBAP 响应头无效：事务编号=%d，协议编号=%d，长度=%d，设备单元=%d",
                    responseTransactionId,
                    protocolId,
                    length,
                    responseUnit
                )
            );
        }

        // 长度字段包含 Unit ID；它已经读过，剩余 length - 1 字节才是 PDU。
        byte[] response = new byte[length - 1];
        input.readFully(response);
        int actualFunction = Byte.toUnsignedInt(response[0]);
        // 功能码最高位置 1 表示设备返回协议异常，与网络断线或超时分开处理。
        if (actualFunction == (function | 0x80)) {
            if (response.length != 2) {
                throw new IOException("Modbus 异常响应格式不正确");
            }
            throw new ModbusException(Byte.toUnsignedInt(response[1]));
        }
        if (actualFunction != function) {
            throw new IOException("响应功能码与请求不一致");
        }
        return response;
    }

    /**
     * 释放当前套接字并清空连接引用，无参数、无返回值；下次请求可重新建连。
     */
    @Override
    public synchronized void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败也要清除引用，下次请求重新建立连接。
            }
        }
        socket = null;
    }
}
