package demo.inspection.server.integration;

import demo.inspection.mqtt.MqttLink;
import demo.inspection.protocol.Messages;
import demo.inspection.protocol.Messages.Command;
import jakarta.annotation.PreDestroy;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 接入端通信出口：管理 MQTT 连接和命令发布，不处理设备业务规则。 */
@Component
public class EdgeGateway {

    private final String deviceId;
    private final MqttLink mqtt;
    private volatile BiConsumer<String, String> receiver;

    /**
     * 装配连接；消息处理器准备好后才启动订阅。
     * @param deviceId 当前设备编号。
     * @param brokerUrl MQTT 服务地址。
     * @throws Exception MQTT 客户端初始化失败。
     */
    public EdgeGateway(
        @Value("${inspection.device-id}") String deviceId,
        @Value("${inspection.mqtt-url}") String brokerUrl
    ) throws Exception {
        this.deviceId = deviceId;
        mqtt = new MqttLink(
            brokerUrl,
            "inspection-server-" + deviceId,
            new String[] {
                Messages.topic(deviceId, "telemetry"),
                Messages.topic(deviceId, "results"),
            },
            (topic, json) -> receiver.accept(topic, json)
        );
    }

    /**
     * 由消息处理器初始化时调用一次，先登记回调，再启动连接。
     * @param receiver 已就绪的消息处理入口。
     */
    public synchronized void start(BiConsumer<String, String> receiver) {
        if (this.receiver != null) {
            throw new IllegalStateException("设备消息接收器已经启动");
        }
        this.receiver = java.util.Objects.requireNonNull(receiver);
        mqtt.start();
    }

    /** @return 当前设备编号，用于消息主题匹配。 */
    public String deviceId() {
        return deviceId;
    }

    /** @return MQTT 连接和订阅是否均已就绪。 */
    public boolean isConnected() {
        return mqtt.isConnected();
    }

    /**
     * 发布控制命令，等待消息服务确认，不等待设备执行。
     * @param command 已通过业务校验的命令。
     * @throws Exception 发布失败或确认超时，设备是否执行仍可能未知。
     */
    public void sendCommand(Command command) throws Exception {
        mqtt.publish(Messages.topic(deviceId, "commands"), command);
    }

    /** 应用退出时释放连接和重连线程。 */
    @PreDestroy
    public void close() {
        mqtt.close();
    }
}
