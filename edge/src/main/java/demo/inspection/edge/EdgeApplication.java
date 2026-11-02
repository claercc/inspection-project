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

    public void start() {
        mqtt.start();
        worker.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

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

    private void publishResult(Result result) {
        try {
            mqtt.publish(Messages.topic(deviceId, "results"), result);
        } catch (Exception exception) {
            System.err.println(
                "Result delivery failed (cached, no automatic command retry): " + exception
            );
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
        robot.close();
        mqtt.close();
    }

    public static void main(String[] args) throws Exception {
        EdgeApplication application = new EdgeApplication();
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
        application.start();
        new CountDownLatch(1).await();
    }
}
