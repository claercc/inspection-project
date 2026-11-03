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

    /**
     * 读取设备编号和 Broker 环境配置，装配 MQTT 并启动后台连接，无参数，由 Spring 创建。
     *
     * @throws Exception MQTT 客户端装配失败时抛出。
     */
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

    /**
     * 处理 MQTT 入口消息并串行访问设备状态，无返回值；非法载荷会写入诊断。
     *
     * @param topic MQTT 回调提供的完整实际主题。
     * @param json 主题对应的 UTF-8 JSON 字符串。
     */
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

    /**
     * 接收新遥测并在在线/运行状态变化时记录诊断，无返回值；由 receive 在状态锁内调用。
     *
     * @param topic 当前遥测的实际来源主题。
     * @param next 从 JSON 解析出的候选采样。
     */
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

    /**
     * 登记命令结果并尝试接收其附带采样，无返回值；需在状态锁内调用。
     *
     * @param topic 结果消息的实际主题。
     * @param result 已解析的设备回执；匹配规则由 CommandHistory 处理。
     */
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

    /**
     * 构造供 HTTP/SSE 展示的状态快照，无参数；以同一时刻计算过期和在线状态。
     *
     * @return 包含设备采样、连接判断以及新建的命令/诊断历史列表的 State。
     *
     * <p>用法示例：
     * <pre>{@code
     * ConsoleService.State snapshot = console.state();
     * }</pre>
     */
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

    /**
     * 按 Broker、edge 数据新鲜度和设备采集结果的优先级生成链路状态码。
     *
     * @param connected MQTT 是否就绪。
     * @param stale 采样是否已过期。
     * @param online 综合计算后的设备在线状态。
     * @return BROKER_OFFLINE、EDGE_STALE、ROBOT_OFFLINE 或 ONLINE。
     */
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

    /**
     * 由 HTTP 控制入口提交一次命令，串行完成准入和发布，但不等待设备最终执行结果。
     *
     * @param type 大小写敏感的 START 或 STOP。
     * @return 本次命令的当前记录，可能为等待反馈、已确认结果或结果未知。
     * @throws CommandRejectedException 类型非法、Broker 不可用或操作与当前状态冲突时抛出。
     *
     * <p>用法示例：
     * <pre>{@code
     * CommandView record = console.issue("STOP");
     * }</pre>
     */
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

    /**
     * 在状态锁内执行准入检查并创建 PENDING 记录，供按序发布流程调用。
     *
     * @param commandType 已解析的控制意图。
     * @return 准入成功后创建的原始 Command。
     * @throws CommandRejectedException 当前依赖或设备状态不允许该操作时抛出。
     */
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

    /**
     * 在不占用状态锁时等待 MQTT 发布确认，无返回值；确认成功或失败后更新命令记录。
     *
     * @param command 已经通过准入并建立记录的命令。
     */
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

    /**
     * 由 Spring 定时扫描到期命令并记录诊断，无参数、无返回值；不会重新发送命令。
     */
    @Scheduled(fixedDelay = 500)
    public synchronized void expire() {
        for (String commandId : commandHistory.expire(Instant.now())) {
            log("SERVER", "timeout", commandId, "Device result missing; no automatic resend");
        }
    }

    /**
     * 向进程内有界诊断队列加入事件，无返回值；调用方应持有服务的状态锁。
     *
     * @param direction 事件方向或来源，例如 SERVER → EDGE。
     * @param protocol 协议或处理阶段说明。
     * @param commandId 可关联的命令编号；非命令事件可传 null。
     * @param summary 供诊断面板显示的事件摘要。
     */
    private void log(String direction, String protocol, String commandId, String summary) {
        diagnostics.addFirst(
            new Diagnostic(Instant.now(), direction, protocol, commandId, summary)
        );
        while (diagnostics.size() > DIAGNOSTIC_LIMIT) {
            diagnostics.removeLast();
        }
    }

    /**
     * 由 Spring 销毁回调关闭 MQTT 客户端，无参数、无返回值。
     */
    @PreDestroy
    public void close() {
        mqtt.close();
    }
}
