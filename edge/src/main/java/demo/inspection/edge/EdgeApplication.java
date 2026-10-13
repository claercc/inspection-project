package demo.inspection.edge;

import demo.inspection.modbus.*;
import demo.inspection.mqtt.MqttLink;
import demo.inspection.protocol.Messages;
import demo.inspection.protocol.Messages.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** A single worker serializes polling and commands so read/write feedback cannot interleave. */
public final class EdgeApplication implements AutoCloseable {
    private final String deviceId = System.getenv().getOrDefault("DEVICE_ID", "robot-001");
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final ModbusClient modbus;
    private final MqttLink mqtt;
    // Expiring process-local ledger. Restart is NOT a guarantee of exactly-once physical execution.
    private final Map<String, Cached> results = new HashMap<>();
    private record Cached(Command command, Result result, Instant cachedAt) {}

    public EdgeApplication() throws Exception {
        modbus = new ModbusClient(System.getenv().getOrDefault("MODBUS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("MODBUS_PORT", "1502")),
                Integer.parseInt(System.getenv().getOrDefault("MODBUS_UNIT", "1")), 1000);
        mqtt = new MqttLink(System.getenv().getOrDefault("MQTT_URL", "tcp://127.0.0.1:1883"),
                "inspection-edge-" + deviceId, new String[]{Messages.topic(deviceId, "commands")},
                (topic, json) -> worker.submit(() -> receive(json)));
    }
    public void start() {
        mqtt.start(); worker.scheduleWithFixedDelay(this::poll, 0, 500, TimeUnit.MILLISECONDS);
    }
    private Telemetry readState() throws IOException {
        int[] r = modbus.readHolding(0, 7);
        return new Telemetry(deviceId, "MODBUS_TCP_SIMULATOR", Instant.now(), true,
                r[0] == 1 ? "RUNNING" : "STOPPED", r[1] / 10.0, r[2] / 10.0,
                r[3] / 10.0, r[4] / 10.0, r[5], r[6], null);
    }
    private void poll() {
        if (!mqtt.isConnected()) return;
        Telemetry telemetry;
        try { telemetry = readState(); }
        catch (IOException e) { telemetry = new Telemetry(deviceId, "MODBUS_TCP_SIMULATOR", Instant.now(), false,
                "UNKNOWN", 0, 0, 0, 0, 0, 0, e.getMessage()); }
        try { mqtt.publish(Messages.topic(deviceId, "telemetry"), telemetry); }
        catch (Exception e) { System.err.println("Telemetry publish: " + e.getMessage()); }
    }
    private void receive(String json) {
        try {
            Command c = Messages.JSON.readValue(json, Command.class);
            if (!deviceId.equals(c.deviceId()) || c.commandId() == null || c.commandId().isBlank()) return;
            Instant now = Instant.now();
            results.entrySet().removeIf(e -> e.getValue().cachedAt().isBefore(now.minusSeconds(120)));
            Cached cached = results.get(c.commandId());
            if (cached != null) {
                if (!cached.command().equals(c)) {
                    publish(new Result(c.commandId(), deviceId, c.type(), "FAILED", "commandId reused with different payload", now, null));
                } else {
                    System.out.println("Duplicate " + c.commandId() + ": replaying result, no FC06 write"); publish(cached.result());
                }
                return;
            }
            Result result;
            boolean writeAttempted = false;
            try {
                if (c.createdAt() == null || c.expiresAt() == null || !c.expiresAt().isAfter(now)
                        || c.createdAt().isAfter(now.plusSeconds(5))
                        || c.expiresAt().isAfter(c.createdAt().plusSeconds(30)))
                    throw new IllegalArgumentException("Command expired or invalid timestamps");
                int control = switch (c.type() == null ? "" : c.type()) {
                    case "START" -> 1; case "STOP" -> 2;
                    default -> throw new IllegalArgumentException("Unsupported command type");
                };
                readState(); // A failure before writing is a definite execution failure.
                writeAttempted = true;
                modbus.writeSingle(5, control);
                Telemetry feedback = readState();
                if (feedback.lastControl() != control || !feedback.runState().equals(control == 1 ? "RUNNING" : "STOPPED"))
                    throw new IOException("Device readback did not confirm control");
                result = new Result(c.commandId(), deviceId, c.type(), "SUCCEEDED",
                        "FC06 write + FC03 device feedback confirmed", Instant.now(), feedback);
            } catch (Exception e) {
                String status = writeAttempted && !(e instanceof ModbusException) ? "RESULT_UNKNOWN" : "FAILED";
                result = new Result(c.commandId(), deviceId, c.type(), status, e.getMessage(), Instant.now(), null);
            }
            results.put(c.commandId(), new Cached(c, result, Instant.now()));
            publish(result); poll();
        } catch (Exception e) { System.err.println("Invalid command: " + e); }
    }
    private void publish(Result result) {
        try { mqtt.publish(Messages.topic(deviceId, "results"), result); }
        catch (Exception e) { System.err.println("Result delivery failed (cached, no automatic command retry): " + e); }
    }
    public void close() { worker.shutdownNow(); modbus.close(); mqtt.close(); }
    public static void main(String[] args) throws Exception {
        var app = new EdgeApplication(); Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.start(); new CountDownLatch(1).await();
    }
}
