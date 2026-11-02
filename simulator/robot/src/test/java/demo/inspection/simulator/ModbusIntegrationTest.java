package demo.inspection.simulator;

import static org.junit.jupiter.api.Assertions.*;

import demo.inspection.modbus.ModbusClient;
import demo.inspection.modbus.ModbusException;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ModbusIntegrationTest {

    @Test
    void realTcpReadWriteAndExceptions() throws Exception {
        try (var robot = new RobotSimulator("127.0.0.1", 0, 1)) {
            robot.start();
            try (var client = new ModbusClient("127.0.0.1", robot.port(), 1, 1000)) {
                assertEquals(0, client.readHolding(0, 7)[0]);
                client.writeSingle(5, 1);
                assertEquals(1, client.readHolding(0, 7)[0]);
                Thread.sleep(300);
                assertTrue(client.readHolding(0, 7)[1] > 0);
                client.writeSingle(5, 2);
                assertEquals(0, client.readHolding(0, 7)[0]);
                assertEquals(2, client.readHolding(0, 7)[6]);
                assertEquals(
                    2,
                    assertThrows(ModbusException.class, () ->
                        client.readHolding(7, 1)
                    ).exceptionCode()
                );
                assertEquals(
                    3,
                    assertThrows(ModbusException.class, () ->
                        client.writeSingle(5, 9)
                    ).exceptionCode()
                );
                assertEquals(
                    2,
                    assertThrows(ModbusException.class, () ->
                        client.writeSingle(0, 1)
                    ).exceptionCode()
                );
            }
        }
    }

    @Test
    void simulatorAcceptsFragmentedAndCoalescedRequests() throws Exception {
        try (var robot = new RobotSimulator("127.0.0.1", 0, 1)) {
            robot.start();
            try (var socket = new Socket("127.0.0.1", robot.port())) {
                socket.setSoTimeout(1000);
                byte[] frame = { 0, 42, 0, 0, 0, 6, 1, 3, 0, 0, 0, 1 };
                var out = socket.getOutputStream();
                var in = new DataInputStream(socket.getInputStream());
                for (byte b : frame) {
                    out.write(b);
                    out.flush();
                }
                byte[] response = new byte[11];
                in.readFully(response);
                assertEquals(42, response[1]);
                out.write(frame);
                out.write(frame);
                out.flush();
                in.readFully(response);
                assertEquals(42, response[1]);
                in.readFully(response);
                assertEquals(42, response[1]);
            }
        }
    }

    @Test
    void clientReadsSplitResponse() throws Exception {
        try (var listener = new ServerSocket(0)) {
            CompletableFuture<Void> response = CompletableFuture.runAsync(() -> {
                try (var socket = listener.accept()) {
                    new DataInputStream(socket.getInputStream()).readFully(new byte[12]);
                    byte[] reply = { 0, 1, 0, 0, 0, 5, 1, 3, 2, 0, 123 };
                    for (byte b : reply) {
                        socket.getOutputStream().write(b);
                        socket.getOutputStream().flush();
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
            try (var client = new ModbusClient("127.0.0.1", listener.getLocalPort(), 1, 1000)) {
                assertEquals(123, client.readHolding(0, 1)[0]);
            }
            response.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void clientRejectsInvalidFrames() throws Exception {
        byte[][] replies = {
            { 0, 9, 0, 0, 0, 5, 1, 3, 2, 0, 1 }, // wrong transaction
            { 0, 1, 0, 1, 0, 5, 1, 3, 2, 0, 1 }, // nonzero protocol
            { 0, 1, 0, 0, 1, 0, 1 }, // oversized length
            { 0, 1, 0, 0, 0, 5, 2, 3, 2, 0, 1 }, // wrong unit
            { 0, 1, 0, 0, 0, 5, 1, 6, 2, 0, 1 }, // wrong function
            { 0, 1, 0, 0, 0, 5, 1, 3, 4, 0, 1 }, // wrong byte count
            { 0, 1, 0, 0, 0, 5, 1, 3, 2 }, // truncated PDU
        };
        for (byte[] reply : replies) {
            try (var listener = new ServerSocket(0)) {
                var task = CompletableFuture.runAsync(() -> {
                    try (var socket = listener.accept()) {
                        new DataInputStream(socket.getInputStream()).readFully(new byte[12]);
                        socket.getOutputStream().write(reply);
                        socket.getOutputStream().flush();
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
                try (var client = new ModbusClient("127.0.0.1", listener.getLocalPort(), 1, 500)) {
                    assertThrows(IOException.class, () -> client.readHolding(0, 1));
                }
                task.get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void clientRejectsWriteEchoAndReadTimeout() throws Exception {
        try (var listener = new ServerSocket(0)) {
            var task = CompletableFuture.runAsync(() -> {
                try (var socket = listener.accept()) {
                    new DataInputStream(socket.getInputStream()).readFully(new byte[12]);
                    socket
                        .getOutputStream()
                        .write(new byte[] { 0, 1, 0, 0, 0, 6, 1, 6, 0, 5, 0, 2 });
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
            try (var client = new ModbusClient("127.0.0.1", listener.getLocalPort(), 1, 500)) {
                assertThrows(IOException.class, () -> client.writeSingle(5, 1));
            }
            task.get(2, TimeUnit.SECONDS);
        }
        try (
            var listener = new ServerSocket(0);
            var client = new ModbusClient("127.0.0.1", listener.getLocalPort(), 1, 100)
        ) {
            assertThrows(SocketTimeoutException.class, () -> client.readHolding(0, 1));
        }
    }
}
