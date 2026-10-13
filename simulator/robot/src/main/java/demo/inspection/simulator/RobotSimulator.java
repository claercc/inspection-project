package demo.inspection.simulator;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

/** Physical behavior simulator only; no inspection task orchestration. Addresses are zero-based. */
public final class RobotSimulator implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final int unit;
    private final int[] registers = {0, 0, 0, 1000, 260, 0, 0};
    private volatile boolean running;
    public RobotSimulator(String host, int port, int unit) throws IOException {
        this.unit = unit; listener = new ServerSocket(); listener.bind(new InetSocketAddress(host, port));
    }
    public int port() { return listener.getLocalPort(); }
    public void start() {
        running = true;
        clients.submit(() -> {
            while (running) try {
                Socket socket = listener.accept(); socket.setSoTimeout(10000); socket.setTcpNoDelay(true);
                sockets.add(socket); clients.submit(() -> serve(socket));
            } catch (IOException e) { if (running) System.err.println(e); }
        });
        clock.scheduleAtFixedRate(this::tick, 250, 250, TimeUnit.MILLISECONDS);
        System.out.println("SIMULATED robot Modbus TCP listening " + listener.getLocalSocketAddress() + " unit=" + unit);
    }
    private synchronized void tick() {
        if (registers[0] == 1) {
            registers[1] = (registers[1] + 5) % 1000;
            registers[2] = (registers[1] / 10) % 100;
            registers[3] = Math.max(100, registers[3] - 1);
            registers[4] = 260 + (registers[1] / 5) % 200;
        }
    }
    private void serve(Socket socket) {
        try (socket; var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream())) {
            while (running) {
                int tx;
                try { tx = in.readUnsignedShort(); } catch (EOFException e) { break; }
                int protocol = in.readUnsignedShort(), length = in.readUnsignedShort(), requestUnit = in.readUnsignedByte();
                if (protocol != 0 || length < 2 || length > 254) break;
                byte[] pdu = new byte[length - 1]; in.readFully(pdu);
                // Ignore other unit identifiers rather than controlling this simulated device.
                if (requestUnit != unit) continue;
                byte[] response = execute(pdu);
                out.writeShort(tx); out.writeShort(0); out.writeShort(response.length + 1); out.writeByte(unit);
                out.write(response); out.flush();
            }
        } catch (IOException e) { if (running && !(e instanceof SocketTimeoutException)) System.err.println("Robot TCP: " + e.getMessage()); }
        finally { sockets.remove(socket); }
    }
    private synchronized byte[] execute(byte[] pdu) throws IOException {
        int fc = Byte.toUnsignedInt(pdu[0]);
        if (fc != 3 && fc != 6) return exception(fc, 1);
        if (pdu.length != 5) return exception(fc, 3);
        int address = (Byte.toUnsignedInt(pdu[1]) << 8) | Byte.toUnsignedInt(pdu[2]);
        int value = (Byte.toUnsignedInt(pdu[3]) << 8) | Byte.toUnsignedInt(pdu[4]);
        if (fc == 3) {
            if (value < 1 || value > 125) return exception(fc, 3);
            if (address >= registers.length || address + value > registers.length) return exception(fc, 2);
            var bytes = new ByteArrayOutputStream(); var data = new DataOutputStream(bytes);
            data.writeByte(3); data.writeByte(value * 2);
            for (int i = address; i < address + value; i++) data.writeShort(registers[i]);
            return bytes.toByteArray();
        }
        if (address != 5) return exception(fc, 2);
        if (value != 1 && value != 2) return exception(fc, 3);
        registers[5] = value; registers[0] = value == 1 ? 1 : 0; registers[6] = (registers[6] + 1) & 0xffff;
        return pdu; // FC06 echo only acknowledges a write; edge verifies the state with FC03.
    }
    private static byte[] exception(int fc, int code) { return new byte[]{(byte)(fc | 0x80), (byte)code}; }
    public void close() {
        running = false; clock.shutdownNow();
        try { listener.close(); } catch (IOException ignored) {}
        sockets.forEach(s -> { try { s.close(); } catch (IOException ignored) {} }); clients.shutdownNow();
    }
    public static void main(String[] args) throws Exception {
        var robot = new RobotSimulator(System.getenv().getOrDefault("MODBUS_BIND", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("MODBUS_PORT", "1502")),
                Integer.parseInt(System.getenv().getOrDefault("MODBUS_UNIT", "1")));
        Runtime.getRuntime().addShutdownHook(new Thread(robot::close)); robot.start();
        new CountDownLatch(1).await();
    }
}
