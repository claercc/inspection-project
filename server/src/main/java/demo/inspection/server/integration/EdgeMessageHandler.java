package demo.inspection.server.integration;

import demo.inspection.protocol.Messages;
import demo.inspection.protocol.Messages.Result;
import demo.inspection.protocol.Messages.Telemetry;
import demo.inspection.server.device.DeviceService;
import demo.inspection.support.ErrorMessages;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** 将 MQTT JSON 转成业务数据；主题和序列化细节止于此处。 */
@Component
public class EdgeMessageHandler {

    private final EdgeGateway gateway;
    private final DeviceService console;

    /**
     * @param gateway 接入端通信连接。
     * @param console 接收已解析的数据并更新设备和命令状态。
     */
    public EdgeMessageHandler(EdgeGateway gateway, DeviceService console) {
        this.gateway = gateway;
        this.console = console;
    }

    /** 所有依赖装配完成后启动消息接收，避免构造业务对象时触发网络回调。 */
    @PostConstruct
    public void start() {
        gateway.start(this::receive);
    }

    /**
     * @param topic 实际接收主题，随采样保留用于来源核对。
     * @param json UTF-8 消息载荷；无法解析时记录中文诊断。
     */
    void receive(String topic, String json) {
        try {
            if (topic.equals(Messages.topic(gateway.deviceId(), "telemetry"))) {
                console.receiveTelemetry(topic, Messages.JSON.readValue(json, Telemetry.class));
            } else if (topic.equals(Messages.topic(gateway.deviceId(), "results"))) {
                console.receiveResult(topic, Messages.JSON.readValue(json, Result.class));
            }
        } catch (Exception exception) {
            console.recordMessageError(ErrorMessages.describe(exception));
        }
    }
}
