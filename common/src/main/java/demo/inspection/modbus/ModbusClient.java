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

    public ModbusClient(String host, int port, int unit, int timeout) {
        this.host = host;
        this.port = port;
        this.unit = unit;
        this.timeout = timeout;
    }

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

    public synchronized int[] readHolding(int address, int quantity) throws IOException {
        if (quantity < 1 || quantity > MAX_REGISTER_QUANTITY) {
            throw new IllegalArgumentException("quantity must be 1..125");
        }
        byte[] response = request(READ_HOLDING_REGISTERS, address, quantity);
        int expectedByteCount = quantity * 2;
        if (
            response.length != 2 + expectedByteCount ||
            Byte.toUnsignedInt(response[1]) != expectedByteCount
        ) {
            close();
            throw new IOException("FC03 byte count mismatch");
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
            throw new IOException("FC06 echo mismatch");
        }
    }

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
                    "Invalid MBAP: tx=%d, protocol=%d, length=%d, unit=%d",
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
                throw new IOException("Malformed exception response");
            }
            throw new ModbusException(Byte.toUnsignedInt(response[1]));
        }
        if (actualFunction != function) {
            throw new IOException("Function code mismatch");
        }
        return response;
    }

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
