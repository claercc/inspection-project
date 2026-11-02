package demo.inspection.mqtt;

import demo.inspection.protocol.Messages;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * 封装 MQTT 3.1.1 连接、订阅和 QoS 1 发布。
 * 连接恢复后重新订阅；控制消息不保留，避免新订阅者把历史控制当作新指令。
 */
public final class MqttLink implements AutoCloseable {

    private static final int QOS = 1;
    private static final long PUBLICATION_TIMEOUT_MS = 3000;

    private final MqttClient client;
    private final String[] subscriptions;
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor();
    // TCP 连通还不等于订阅就绪；volatile 让回调线程和业务线程看到一致的就绪标志。
    private volatile boolean ready;

    public MqttLink(
        String url,
        String clientId,
        String[] subscriptions,
        BiConsumer<String, String> onMessage
    ) throws MqttException {
        this.subscriptions = subscriptions;
        client = new MqttClient(url, clientId, new MemoryPersistence());
        client.setTimeToWait(PUBLICATION_TIMEOUT_MS);
        client.setCallback(
            new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    ready = false;
                    System.err.println(clientId + " MQTT lost: " + cause);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // 忽略订阅时收到的 retained 历史控制，避免重启 edge 触发旧动作。
                    if (topic.endsWith("/commands") && message.isRetained()) {
                        return;
                    }
                    try {
                        onMessage.accept(
                            topic,
                            new String(message.getPayload(), StandardCharsets.UTF_8)
                        );
                    } catch (Exception exception) {
                        System.err.println("Invalid MQTT message on " + topic + ": " + exception);
                    }
                }
            }
        );
    }

    public void start() {
        connector.scheduleWithFixedDelay(this::connect, 0, 2, TimeUnit.SECONDS);
    }

    private void connect() {
        if (isConnected()) {
            return;
        }
        try {
            if (!client.isConnected()) {
                client.connect(connectionOptions());
            }
            int[] subscriptionQos = new int[subscriptions.length];
            Arrays.fill(subscriptionQos, QOS);
            client.subscribe(subscriptions, subscriptionQos);
            ready = true;
            System.out.println(
                client.getClientId() + " MQTT subscribed " + Arrays.toString(subscriptions)
            );
        } catch (MqttException exception) {
            ready = false;
            System.err.println("MQTT connecting: " + exception.getMessage());
        }
    }

    private MqttConnectOptions connectionOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        // 不恢复旧会话中排队的离线控制；重连后由 connect 重新订阅当前主题。
        options.setCleanSession(true);
        options.setConnectionTimeout(2);
        options.setKeepAliveInterval(5);
        options.setAutomaticReconnect(false);
        return options;
    }

    public boolean isConnected() {
        return ready && client.isConnected();
    }

    public void publish(String topic, Object payload) throws Exception {
        if (!isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage message = new MqttMessage(Messages.JSON.writeValueAsBytes(payload));
        message.setQos(QOS);
        message.setRetained(false);
        // PUBACK 只确认 Broker 收到消息，不能替代 edge 的结果消息或设备状态读回。
        client.publish(topic, message);
    }

    @Override
    public void close() {
        connector.shutdownNow();
        ready = false;
        try {
            if (client.isConnected()) {
                client.disconnect(1000);
            }
            client.close();
        } catch (MqttException ignored) {
            // Broker 已离线时尽力释放资源，不让关闭过程因通信异常中断。
        }
    }
}
