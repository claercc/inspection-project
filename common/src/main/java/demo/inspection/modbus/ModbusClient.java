package demo.inspection.modbus;

import java.io.*;
import java.net.*;
import java.util.Arrays;

/** One serialized request per socket. MBAP length frames the TCP byte stream. */
public final class ModbusClient implements AutoCloseable {
    private final String host; private final int port, unit, timeout;
    private Socket socket; private DataInputStream input; private DataOutputStream output;
    private int transaction;
    public ModbusClient(String host, int port, int unit, int timeout) {
        this.host = host; this.port = port; this.unit = unit; this.timeout = timeout;
    }
    private void connect() throws IOException {
        if (socket != null && !socket.isClosed()) return;
        Socket next = new Socket();
        try {
            next.connect(new InetSocketAddress(host, port), timeout); next.setSoTimeout(timeout); next.setTcpNoDelay(true);
            socket = next; input = new DataInputStream(next.getInputStream()); output = new DataOutputStream(next.getOutputStream());
        } catch (IOException e) { next.close(); throw e; }
    }
    public synchronized int[] readHolding(int address, int quantity) throws IOException {
        if (quantity < 1 || quantity > 125) throw new IllegalArgumentException("quantity must be 1..125");
        byte[] pdu = request(3, address, quantity);
        if (pdu.length != 2 + quantity * 2 || Byte.toUnsignedInt(pdu[1]) != quantity * 2) {
            close(); throw new IOException("FC03 byte count mismatch");
        }
        int[] result = new int[quantity];
        for (int i = 0; i < quantity; i++) result[i] = (Byte.toUnsignedInt(pdu[2+i*2]) << 8) | Byte.toUnsignedInt(pdu[3+i*2]);
        return result;
    }
    public synchronized void writeSingle(int address, int value) throws IOException {
        byte[] pdu = request(6, address, value);
        byte[] expected = {(byte)6, (byte)(address >> 8), (byte)address, (byte)(value >> 8), (byte)value};
        if (!Arrays.equals(pdu, expected)) { close(); throw new IOException("FC06 echo mismatch"); }
    }
    private byte[] request(int function, int address, int value) throws IOException {
        try {
            connect(); int tx = (transaction = (transaction + 1) & 0xffff);
            output.writeShort(tx); output.writeShort(0); output.writeShort(6); output.writeByte(unit);
            output.writeByte(function); output.writeShort(address); output.writeShort(value); output.flush();
            // readFully / DataInputStream primitive reads handle fragmented responses.
            int responseTx = input.readUnsignedShort(), protocol = input.readUnsignedShort();
            int length = input.readUnsignedShort(), responseUnit = input.readUnsignedByte();
            if (responseTx != tx || protocol != 0 || responseUnit != unit || length < 2 || length > 254)
                throw new IOException("Invalid MBAP: tx=" + responseTx + ", protocol=" + protocol + ", length=" + length + ", unit=" + responseUnit);
            byte[] pdu = new byte[length - 1]; input.readFully(pdu);
            int actualFunction = Byte.toUnsignedInt(pdu[0]);
            if (actualFunction == (function | 0x80)) {
                if (pdu.length != 2) throw new IOException("Malformed exception response");
                throw new ModbusException(Byte.toUnsignedInt(pdu[1]));
            }
            if (actualFunction != function) throw new IOException("Function code mismatch");
            return pdu;
        } catch (IOException e) { close(); throw e; }
    }
    public synchronized void close() { if (socket != null) try { socket.close(); } catch (IOException ignored) {} socket = null; }
}
