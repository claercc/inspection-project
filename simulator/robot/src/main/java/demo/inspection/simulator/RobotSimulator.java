package demo.inspection.simulator;

import demo.inspection.modbus.RobotRegisters;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 提供模拟设备行为和 Modbus 寄存器；巡检任务编排仍属于 edge/server。 */
public final class RobotSimulator implements AutoCloseable {

    private static final int READ_HOLDING_REGISTERS = 3;
    private static final int WRITE_SINGLE_REGISTER = 6;
    private static final int INITIAL_BATTERY = 1000;
    private static final int INITIAL_TEMPERATURE = 260;

    private final ServerSocket listener;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final int unit;
    private final int[] registers = new int[RobotRegisters.REGISTER_COUNT];
    private volatile boolean running;

    /**
     * 创建并绑定独立模拟器的 TCP 监听器；调用 start 后才开始处理连接与模拟时钟。
     *
     * @param host 监听地址，本地演示使用 127.0.0.1。
     * @param port 监听端口，测试传 0 由系统分配空闲端口。
     * @param unit 本模拟器响应的 Modbus Unit ID。
     * @throws IOException 监听地址或端口绑定失败时抛出。
     */
    public RobotSimulator(String host, int port, int unit) throws IOException {
        this.unit = unit;
        registers[RobotRegisters.BATTERY] = INITIAL_BATTERY;
        registers[RobotRegisters.TEMPERATURE] = INITIAL_TEMPERATURE;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(host, port));
    }

    /**
     * 读取实际绑定的端口，主要供使用随机端口的 TCP 测试调用，无参数。
     *
     * @return ServerSocket 实际监听端口。
     */
    public int port() {
        return listener.getLocalPort();
    }

    /**
     * 启动连接接收和模拟时钟，无参数、无返回值；每个实例应只调用一次。
     */
    public void start() {
        running = true;
        clients.submit(this::acceptConnections);
        clock.scheduleAtFixedRate(this::tick, 250, 250, TimeUnit.MILLISECONDS);
        System.out.println(
            "SIMULATED robot Modbus TCP listening " +
                listener.getLocalSocketAddress() +
                " unit=" +
                unit
        );
    }

    /**
     * 由后台线程持续接受设备连接并分配处理任务，无参数、无返回值。
     */
    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = listener.accept();
                socket.setSoTimeout(10000);
                socket.setTcpNoDelay(true);
                sockets.add(socket);
                clients.submit(() -> serve(socket));
            } catch (IOException exception) {
                if (running) {
                    System.err.println(exception);
                }
            }
        }
    }

    // 以下位置、电量和温度变化是演示规律，用于观察控制闭环，不代表真实设备或传感器模型。
    /**
     * 由定时时钟推进运行中设备的演示位置、电量与温度，无参数、无返回值。
     */
    private synchronized void tick() {
        if (registers[RobotRegisters.RUN_STATE] != RobotRegisters.RUNNING) {
            return;
        }
        int positionX = (registers[RobotRegisters.POSITION_X] + 5) % 1000;
        registers[RobotRegisters.POSITION_X] = positionX;
        registers[RobotRegisters.POSITION_Y] = (positionX / 10) % 100;
        registers[RobotRegisters.BATTERY] = Math.max(100, registers[RobotRegisters.BATTERY] - 1);
        registers[RobotRegisters.TEMPERATURE] = INITIAL_TEMPERATURE + ((positionX / 5) % 200);
    }

    /**
     * 按 MBAP 帧边界循环处理一个客户端连接，无返回值；结束时关闭并移除连接。
     *
     * @param socket 已接受、已设置读取超时的 TCP 连接。
     */
    private void serve(Socket socket) {
        try (
            socket;
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream())
        ) {
            while (running) {
                int transactionId;
                try {
                    transactionId = input.readUnsignedShort();
                } catch (EOFException exception) {
                    break;
                }
                int protocolId = input.readUnsignedShort();
                int length = input.readUnsignedShort();
                int requestUnit = input.readUnsignedByte();
                if (protocolId != 0 || length < 2 || length > 254) {
                    break;
                }
                byte[] request = new byte[length - 1];
                input.readFully(request);
                if (requestUnit != unit) {
                    continue;
                }
                byte[] response = execute(request);
                output.writeShort(transactionId);
                output.writeShort(0);
                output.writeShort(response.length + 1);
                output.writeByte(unit);
                output.write(response);
                output.flush();
            }
        } catch (IOException exception) {
            if (running && !(exception instanceof SocketTimeoutException)) {
                System.err.println("Robot TCP: " + exception.getMessage());
            }
        } finally {
            sockets.remove(socket);
        }
    }

    /**
     * 在寄存器锁内解析并执行一个请求 PDU，供 TCP 连接处理器调用。
     *
     * @param request 已按 MBAP 长度读取、至少包含功能码的 PDU。
     * @return 正常响应或带异常功能码的响应 PDU，不含 MBAP 头。
     * @throws IOException 生成寄存器读取响应时发生 I/O 异常则抛出。
     */
    private synchronized byte[] execute(byte[] request) throws IOException {
        int function = Byte.toUnsignedInt(request[0]);
        if (function != READ_HOLDING_REGISTERS && function != WRITE_SINGLE_REGISTER) {
            return exceptionResponse(function, 1);
        }
        if (request.length != 5) {
            return exceptionResponse(function, 3);
        }
        int address = (Byte.toUnsignedInt(request[1]) << 8) | Byte.toUnsignedInt(request[2]);
        int value = (Byte.toUnsignedInt(request[3]) << 8) | Byte.toUnsignedInt(request[4]);
        if (function == READ_HOLDING_REGISTERS) {
            return readRegisters(address, value);
        }
        return writeControl(address, value, request);
    }

    /**
     * 按模拟点表读取一段寄存器，调用方应已持有寄存器锁。
     *
     * @param address 零基起始地址。
     * @param quantity 请求读取的寄存器数量。
     * @return FC03 正常响应 PDU；数量或地址非法时返回对应异常 PDU。
     * @throws IOException 组装读取响应失败时抛出。
     */
    private byte[] readRegisters(int address, int quantity) throws IOException {
        if (quantity < 1 || quantity > 125) {
            return exceptionResponse(READ_HOLDING_REGISTERS, 3);
        }
        if (address >= registers.length || address + quantity > registers.length) {
            return exceptionResponse(READ_HOLDING_REGISTERS, 2);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeByte(READ_HOLDING_REGISTERS);
        data.writeByte(quantity * 2);
        for (int index = address; index < address + quantity; index++) {
            data.writeShort(registers[index]);
        }
        return bytes.toByteArray();
    }

    /**
     * 校验控制点并更新运行状态与写入计数，调用方应已持有寄存器锁。
     *
     * @param address 目标地址，演示点表仅允许 CONTROL。
     * @param value 启动或停止的控制值。
     * @param request 原始 FC06 请求，用于成功时回显。
     * @return 成功时返回原请求 PDU；地址或控制值非法时返回异常 PDU。
     */
    private byte[] writeControl(int address, int value, byte[] request) {
        if (address != RobotRegisters.CONTROL) {
            return exceptionResponse(WRITE_SINGLE_REGISTER, 2);
        }
        if (value != RobotRegisters.START_CONTROL && value != RobotRegisters.STOP_CONTROL) {
            return exceptionResponse(WRITE_SINGLE_REGISTER, 3);
        }
        registers[RobotRegisters.CONTROL] = value;
        registers[RobotRegisters.RUN_STATE] =
            value == RobotRegisters.START_CONTROL ? RobotRegisters.RUNNING : RobotRegisters.STOPPED;
        registers[RobotRegisters.WRITE_COUNT] =
            (registers[RobotRegisters.WRITE_COUNT] + 1) & 0xffff;
        // FC06 回显只确认写入应答，edge 仍需用 FC03 读取状态后才能确认执行。
        return request;
    }

    /**
     * 组装 Modbus 异常响应，供不支持的功能或非法参数分支调用。
     *
     * @param function 原请求的功能码。
     * @param code 异常码，例如 1 为不支持功能、2 为非法地址、3 为非法值。
     * @return 由异常功能码和异常码组成的两字节 PDU。
     */
    private static byte[] exceptionResponse(int function, int code) {
        return new byte[] { (byte) (function | 0x80), (byte) code };
    }

    /**
     * 停止模拟时钟并关闭监听器和所有客户端连接，无参数、无返回值。
     */
    @Override
    public void close() {
        running = false;
        clock.shutdownNow();
        try {
            listener.close();
        } catch (IOException ignored) {
            // 监听器可能已经关闭，仍需继续释放其他连接。
        }
        sockets.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 单个连接关闭失败不应阻止其余连接释放。
            }
        });
        clients.shutdownNow();
    }

    /**
     * 读取环境配置并启动机器人模拟器独立进程，无返回值。
     *
     * @param args Java 命令行参数，当前未使用；端点配置来自环境变量。
     * @throws Exception 启动失败或主线程等待被中断时抛出。
     */
    public static void main(String[] args) throws Exception {
        RobotSimulator robot = new RobotSimulator(
            System.getenv().getOrDefault("MODBUS_BIND", "127.0.0.1"),
            Integer.parseInt(System.getenv().getOrDefault("MODBUS_PORT", "1502")),
            Integer.parseInt(System.getenv().getOrDefault("MODBUS_UNIT", "1"))
        );
        Runtime.getRuntime().addShutdownHook(new Thread(robot::close));
        robot.start();
        new CountDownLatch(1).await();
    }
}
