package demo.inspection.server.integration;

import demo.inspection.mqtt.MqttLink;
import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages;
import demo.inspection.protocol.Messages.Command;
import demo.inspection.protocol.Messages.Result;
import demo.inspection.protocol.Messages.Telemetry;
import demo.inspection.server.device.domain.CommandHistory;
import demo.inspection.server.device.domain.CommandHistory.CommandView;
import demo.inspection.server.device.domain.CommandPolicy;
import demo.inspection.server.device.domain.CommandRejectedException;
import demo.inspection.server.device.domain.CommandRejectedException.Reason;
import demo.inspection.server.device.domain.DeviceTelemetry;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 协调 MQTT 消息与设备规则，向 HTTP/SSE 提供状态快照。
 * 状态读写使用同一对象锁；消息发布单独排序，避免等待网络应答时阻塞结果接收。
 */
@Service
public class ConsoleService {

    private static final int DIAGNOSTIC_LIMIT = 100;

    public record Diagnostic(
        Instant timestamp,
        String direction,
        String protocol,
        String commandId,
        String summary
    ) {}

    public record State(
        String deviceId,
        boolean simulated,
        boolean mqttConnected,
        boolean online,
        boolean stale,
        String connectionStatus,
        Instant lastReceivedAt,
        String telemetryTopic,
        Telemetry telemetry,
        List<CommandView> commands,
        List<Diagnostic> diagnostics
    ) {}

    private final String deviceId = System.getenv().getOrDefault("DEVICE_ID", "robot-001");
    private final DeviceTelemetry deviceTelemetry = new DeviceTelemetry(deviceId);
    private final CommandHistory commandHistory = new CommandHistory();
    private final Deque<Diagnostic> diagnostics = new ArrayDeque<>();
    private final MqttLink mqtt;
    // 该锁只负责命令接受与发布顺序；接收回调不会获取它，避免相互等待 PUBACK。
    private final Object commandPublicationLock = new Object();

    public ConsoleService() throws Exception {
        String brokerUrl = System.getenv().getOrDefault("MQTT_URL", "tcp://127.0.0.1:1883");
        String[] subscriptions = {
            Messages.topic(deviceId, "telemetry"),
            Messages.topic(deviceId, "results"),
        };
        mqtt = new MqttLink(
            brokerUrl,
            "inspection-server-" + deviceId,
            subscriptions,
            this::receive
        );
        mqtt.start();
    }

    private synchronized void receive(String topic, String json) {
        try {
            if (topic.equals(Messages.topic(deviceId, "telemetry"))) {
                receiveTelemetry(topic, Messages.JSON.readValue(json, Telemetry.class));
            } else if (topic.equals(Messages.topic(deviceId, "results"))) {
                receiveResult(topic, Messages.JSON.readValue(json, Result.class));
            }
        } catch (Exception exception) {
            log("IN", "MQTT", null, "Invalid payload: " + exception.getMessage());
        }
    }

    private void receiveTelemetry(String topic, Telemetry next) {
        Telemetry previous = deviceTelemetry.latest();
        if (!deviceTelemetry.accept(next, topic, Instant.now())) {
            return;
        }
        boolean stateChanged =
            previous == null ||
            next.robotOnline() != previous.robotOnline() ||
            !Objects.equals(next.runState(), previous.runState());
        if (stateChanged) {
            String summary = next.robotOnline()
                ? "Modbus readback: " + next.runState()
                : "Device offline: " + next.error();
            log("EDGE → SERVER", "MQTT / FC03", null, summary);
        }
    }

    private void receiveResult(String topic, Result result) {
        if (!commandHistory.accept(result, Instant.now())) {
            return;
        }
        // 迟到的命令确认仍需记账；其中的旧遥测由 DeviceTelemetry 过滤，不能回退设备状态。
        deviceTelemetry.accept(result.telemetry(), topic, Instant.now());
        log(
            "EDGE → SERVER",
            "MQTT result",
            result.commandId(),
            result.status() + ": " + result.message()
        );
    }

    /** 以同一时刻计算过期和在线状态，返回供页面展示的快照。 */
    public synchronized State state() {
        Instant now = Instant.now();
        boolean connected = mqtt.isConnected();
        boolean stale = deviceTelemetry.isStale(now);
        boolean online = deviceTelemetry.isOnline(connected, now);
        return new State(
            deviceId,
            true,
            connected,
            online,
            stale,
            connectionStatus(connected, stale, online),
            deviceTelemetry.receivedAt(),
            deviceTelemetry.topic(),
            deviceTelemetry.latest(),
            commandHistory.newestFirst(),
            new ArrayList<>(diagnostics)
        );
    }

    private String connectionStatus(boolean connected, boolean stale, boolean online) {
        if (!connected) {
            return "BROKER_OFFLINE";
        }
        if (stale) {
            return "EDGE_STALE";
        }
        if (!online) {
            return "ROBOT_OFFLINE";
        }
        return "ONLINE";
    }

    public CommandView issue(String type) {
        CommandType commandType;
        try {
            commandType = CommandType.parse(type);
        } catch (IllegalArgumentException exception) {
            throw new CommandRejectedException(Reason.INVALID_REQUEST, exception.getMessage());
        }
        // 多个 HTTP 请求可能并发到达，将准入与发布串行化，防止后接受的 STOP 先于 START 发出。
        synchronized (commandPublicationLock) {
            Command command = prepareCommand(commandType);
            // 等待 PUBACK 时释放状态锁，让 MQTT 接收线程能够及时登记设备结果。
            publishCommand(command);
            synchronized (this) {
                return commandHistory.get(command.commandId());
            }
        }
    }

    private synchronized Command prepareCommand(CommandType commandType) {
        Instant now = Instant.now();
        boolean brokerConnected = mqtt.isConnected();
        CommandPolicy.validate(
            commandType,
            brokerConnected,
            deviceTelemetry.isOnline(brokerConnected, now),
            deviceTelemetry.isRunning(),
            commandHistory.hasPendingCommand(),
            commandHistory.hasPendingStop()
        );
        return commandHistory.create(deviceId, commandType, now);
    }

    private void publishCommand(Command command) {
        try {
            mqtt.publish(Messages.topic(deviceId, "commands"), command);
            synchronized (this) {
                commandHistory.markPublished(command.commandId(), Instant.now());
                log(
                    "SERVER → EDGE",
                    "MQTT QoS1 retain=false",
                    command.commandId(),
                    command.type() + " sent"
                );
            }
        } catch (Exception exception) {
            synchronized (this) {
                commandHistory.markPublicationUnknown(
                    command.commandId(),
                    exception.getMessage(),
                    Instant.now()
                );
                log(
                    "SERVER → EDGE",
                    "MQTT",
                    command.commandId(),
                    "RESULT_UNKNOWN: " + exception.getMessage()
                );
            }
        }
    }

    @Scheduled(fixedDelay = 500)
    public synchronized void expire() {
        for (String commandId : commandHistory.expire(Instant.now())) {
            log("SERVER", "timeout", commandId, "Device result missing; no automatic resend");
        }
    }

    private void log(String direction, String protocol, String commandId, String summary) {
        diagnostics.addFirst(
            new Diagnostic(Instant.now(), direction, protocol, commandId, summary)
        );
        while (diagnostics.size() > DIAGNOSTIC_LIMIT) {
            diagnostics.removeLast();
        }
    }

    @PreDestroy
    public void close() {
        mqtt.close();
    }
}
