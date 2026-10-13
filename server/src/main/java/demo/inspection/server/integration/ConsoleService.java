package demo.inspection.server.integration;

import demo.inspection.mqtt.MqttLink;
import demo.inspection.protocol.Messages;
import demo.inspection.protocol.Messages.*;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class ConsoleService {
    public record CommandView(Command command, String status, String message, Instant updatedAt) {}
    public record Diagnostic(Instant timestamp, String direction, String protocol, String commandId, String summary) {}
    public record State(String deviceId, boolean simulated, boolean mqttConnected, boolean online,
                        boolean stale, String connectionStatus, Instant lastReceivedAt, Telemetry telemetry,
                        List<CommandView> commands, List<Diagnostic> diagnostics) {}
    private final String deviceId = System.getenv().getOrDefault("DEVICE_ID", "robot-001");
    private final MqttLink mqtt;
    private Telemetry telemetry;
    private Instant receivedAt;
    private final LinkedHashMap<String, CommandView> commands = new LinkedHashMap<>();
    private final Deque<Diagnostic> diagnostics = new ArrayDeque<>();

    public ConsoleService() throws Exception {
        mqtt = new MqttLink(System.getenv().getOrDefault("MQTT_URL", "tcp://127.0.0.1:1883"),
                "inspection-server-" + deviceId,
                new String[]{Messages.topic(deviceId, "telemetry"), Messages.topic(deviceId, "results")}, this::receive);
        mqtt.start();
    }
    private synchronized void receive(String topic, String json) {
        try {
            if (topic.endsWith("/telemetry")) {
                Telemetry next = Messages.JSON.readValue(json, Telemetry.class);
                if (!deviceId.equals(next.deviceId()) || next.capturedAt() == null) return;
                boolean changed = telemetry == null || next.robotOnline() != telemetry.robotOnline()
                        || !next.runState().equals(telemetry.runState());
                telemetry = next; receivedAt = Instant.now();
                if (changed) log("EDGE → SERVER", "MQTT / FC03", null,
                        next.robotOnline() ? "Modbus readback: " + next.runState() : "Device offline: " + next.error());
            } else {
                Result r = Messages.JSON.readValue(json, Result.class);
                CommandView previous = commands.get(r.commandId());
                if (previous == null || !deviceId.equals(r.deviceId()) || !previous.command().type().equals(r.type())
                        || !Set.of("SUCCEEDED", "FAILED", "RESULT_UNKNOWN").contains(r.status())) return;
                // Duplicate replies cannot overwrite an established terminal result.
                if (Set.of("SUCCEEDED", "FAILED").contains(previous.status())) return;
                commands.put(r.commandId(), new CommandView(previous.command(), r.status(), r.message(), Instant.now()));
                if (r.telemetry() != null) { telemetry = r.telemetry(); receivedAt = Instant.now(); }
                log("EDGE → SERVER", "MQTT result", r.commandId(), r.status() + ": " + r.message());
            }
        } catch (Exception e) { log("IN", "MQTT", null, "Invalid payload: " + e.getMessage()); }
    }
    public synchronized State state() {
        boolean stale = receivedAt == null || receivedAt.isBefore(Instant.now().minusSeconds(3))
                || telemetry == null || telemetry.capturedAt().isBefore(Instant.now().minusSeconds(3));
        boolean connected = mqtt.isConnected();
        boolean online = connected && !stale && telemetry != null && telemetry.robotOnline();
        String status = !connected ? "BROKER_OFFLINE" : stale ? "EDGE_STALE" : !online ? "ROBOT_OFFLINE" : "ONLINE";
        var history = new ArrayList<>(commands.values()); Collections.reverse(history);
        return new State(deviceId, true, connected, online, stale, status, receivedAt, telemetry,
                history, new ArrayList<>(diagnostics));
    }
    public CommandView issue(String type) {
        Command c;
        synchronized (this) {
            if (!Set.of("START", "STOP").contains(type == null ? "" : type))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be START or STOP");
            if (!mqtt.isConnected()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT Broker offline");
            // START requires current state; STOP remains available for uncertain / offline conditions.
            if ("START".equals(type) && !state().online())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Device state is unavailable; wait for fresh Modbus telemetry");
            if (commands.values().stream().anyMatch(view -> Set.of("PENDING", "SENT").contains(view.status())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another command is awaiting feedback");
            if ("START".equals(type) && telemetry != null && "RUNNING".equals(telemetry.runState()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Robot is already running");
            Instant now = Instant.now();
            c = new Command(UUID.randomUUID().toString(), deviceId, type, Map.of(), now, now.plusSeconds(8));
            commands.put(c.commandId(), new CommandView(c, "PENDING", "Preparing MQTT publication", now));
        }
        // Publish outside the monitor: a result can arrive while QoS1 awaits PUBACK.
        try {
            mqtt.publish(Messages.topic(deviceId, "commands"), c);
            synchronized (this) {
                if ("PENDING".equals(commands.get(c.commandId()).status()))
                    commands.put(c.commandId(), new CommandView(c, "SENT", "MQTT QoS1 acknowledged; waiting for device readback", Instant.now()));
                log("SERVER → EDGE", "MQTT QoS1 retain=false", c.commandId(), type + " sent");
            }
        } catch (Exception e) {
            synchronized (this) {
                if ("PENDING".equals(commands.get(c.commandId()).status()))
                    commands.put(c.commandId(), new CommandView(c, "RESULT_UNKNOWN", "Publication acknowledgement unavailable: " + e.getMessage(), Instant.now()));
                log("SERVER → EDGE", "MQTT", c.commandId(), "RESULT_UNKNOWN: " + e.getMessage());
            }
        }
        synchronized (this) {
            while (commands.size() > 100) commands.remove(commands.keySet().iterator().next());
            return commands.get(c.commandId());
        }
    }
    @Scheduled(fixedDelay = 500)
    public synchronized void expire() {
        Instant now = Instant.now();
        commands.replaceAll((id, view) -> {
            if (Set.of("SENT", "PENDING").contains(view.status()) && !view.command().expiresAt().isAfter(now)) {
                log("SERVER", "timeout", id, "Device result missing; no automatic resend");
                return new CommandView(view.command(), "RESULT_UNKNOWN", "No device feedback before deadline; inspect state before manual recovery", now);
            }
            return view;
        });
    }
    private void log(String direction, String protocol, String id, String summary) {
        diagnostics.addFirst(new Diagnostic(Instant.now(), direction, protocol, id, summary));
        while (diagnostics.size() > 100) diagnostics.removeLast();
    }
    @PreDestroy public void close() { mqtt.close(); }
}
