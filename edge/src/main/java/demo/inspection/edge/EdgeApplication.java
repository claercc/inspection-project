package demo.inspection.edge;

import demo.inspection.edge.application.DeviceCommandHandler;
import demo.inspection.edge.domain.RobotDriver;
import demo.inspection.edge.infrastructure.modbus.ModbusRobotDriver;
import demo.inspection.mqtt.MqttLink;
import demo.inspection.protocol.Messages;
import demo.inspection.protocol.Messages.Command;
import demo.inspection.protocol.Messages.Result;
import demo.inspection.protocol.Messages.Telemetry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * edge 进程入口，负责配置装配、MQTT 收发和采集调度。
 * 采集与控制共用单线程 worker，防止读回被其他读写插入，也让去重账本按顺序访问。
 */
public final class EdgeApplication implements AutoCloseable {

    private static final long POLL_INTERVAL_MS = 500;

    private final String deviceId = System.getenv().getOrDefault("DEVICE_ID", "robot-001");
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final RobotDriver robot;
    private final DeviceCommandHandler commands;
    private final MqttLink mqtt;

    /**
     * 从环境变量装配驱动、命令处理器与 MQTT，无参数；随后调用 start 开始工作。
     *
     * @throws Exception MQTT 初始化失败或环境变量无法解析时抛出。
     */
    public EdgeApplication() throws Exception {
        String host = System.getenv().getOrDefault("MODBUS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("MODBUS_PORT", "1502"));
        int unit = Integer.parseInt(System.getenv().getOrDefault("MODBUS_UNIT", "1"));
        robot = new ModbusRobotDriver(deviceId, host, port, unit);
        commands = new DeviceCommandHandler(deviceId, robot, Clock.systemUTC());

        String brokerUrl = System.getenv().getOrDefault("MQTT_URL", "tcp://127.0.0.1:1883");
        mqtt = new MqttLink(
            brokerUrl,
            "inspection-edge-" + deviceId,
            new String[] { Messages.topic(deviceId, "commands") },
            (topic, json) -> worker.submit(() -> receiveCommand(json))
        );
    }

    /**
     * 启动 MQTT 连接与定时轮询，无参数、无返回值；进程入口在装配后调用一次。
     */
    public void start() {
        mqtt.start();
        worker.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 由单线程定时任务或命令处理后调用，无参数、无返回值；读取并发布一次设备采样。
     */
    private void poll() {
        if (!mqtt.isConnected()) {
            return;
        }
        Telemetry telemetry;
        try {
            telemetry = robot.readState();
        } catch (IOException exception) {
            // 失败时明确上报离线和空证据；下方数值只是占位，前端不能当作有效零值展示。
            telemetry = new Telemetry(
                deviceId,
                "MODBUS_TCP_SIMULATOR",
                Instant.now(),
                false,
                "UNKNOWN",
                0,
                0,
                0,
                0,
                0,
                0,
                exception.getMessage(),
                null
            );
        }
        try {
            mqtt.publish(Messages.topic(deviceId, "telemetry"), telemetry);
        } catch (Exception exception) {
            System.err.println("Telemetry publish: " + exception.getMessage());
        }
    }

    /**
     * 由 worker 处理 MQTT 载荷，无返回值；解析、执行、发布结果后立即补一次采样。
     *
     * @param json commands 主题中的 UTF-8 JSON 载荷。
     */
    private void receiveCommand(String json) {
        try {
            Command command = Messages.JSON.readValue(json, Command.class);
            Result result = commands.handle(command);
            if (result == null) {
                return;
            }
            publishResult(result);
            // 结果消息之后再采集一次，使监控状态尽快反映命令后的设备读回。
            poll();
        } catch (Exception exception) {
            System.err.println("Invalid command: " + exception);
        }
    }

    /**
     * 发布一条命令结果，无返回值；失败仅记录，不重新执行物理控制。
     *
     * @param result 处理器已执行或从去重账本回放的结果。
     */
    private void publishResult(Result result) {
        try {
            mqtt.publish(Messages.topic(deviceId, "results"), result);
        } catch (Exception exception) {
            System.err.println(
                "Result delivery failed (cached, no automatic command retry): " + exception
            );
        }
    }

    /**
     * 停止 worker 并关闭设备、MQTT 连接，无参数、无返回值；由关闭钩子调用。
     */
    @Override
    public void close() {
        worker.shutdownNow();
        robot.close();
        mqtt.close();
    }

    /**
     * 启动 edge 独立进程并等待退出，无返回值。
     *
     * @param args Java 命令行参数，当前未使用；配置从环境变量读取。
     * @throws Exception 装配失败或主线程等待被中断时抛出。
     */
    public static void main(String[] args) throws Exception {
        EdgeApplication application = new EdgeApplication();
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
        application.start();
        new CountDownLatch(1).await();
    }
}
