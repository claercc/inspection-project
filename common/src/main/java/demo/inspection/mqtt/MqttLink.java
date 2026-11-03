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

    /**
     * 创建 MQTT 客户端并安装回调；构造后调用 start 才开始尝试连接。
     *
     * @param url Broker 地址，例如 tcp://127.0.0.1:1883。
     * @param clientId Broker 内唯一的客户端编号。
     * @param subscriptions 连接成功后要订阅的完整主题数组。
     * @param onMessage 收到消息时调用，参数依次为实际主题和 UTF-8 JSON；应避免长时间阻塞。
     * @throws MqttException 客户端创建或初始化失败时抛出。
     */
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
                /**
                 * 由 Paho 在连接丢失时调用，无返回值；清除订阅就绪标志。
                 *
                 * @param cause 连接丢失原因，仅用于诊断。
                 */
                @Override
                public void connectionLost(Throwable cause) {
                    ready = false;
                    System.err.println(clientId + " MQTT lost: " + cause);
                }

                /**
                 * 由 Paho 通知一次发布交付完成，无返回值；本项目通过结果消息确认设备执行。
                 *
                 * @param token 本次交付的 MQTT 令牌，此回调中不使用。
                 */
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}

                /**
                 * 由 Paho 交付一条订阅消息，无返回值；过滤 retained 控制后转交业务回调。
                 *
                 * @param topic Broker 交付消息的实际主题。
                 * @param message 原始消息，包含载荷和 retained 标志。
                 */
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

    /**
     * 启动后台连接检查任务，无参数、无返回值；每个实例应在装配完成后调用一次。
     */
    public void start() {
        connector.scheduleWithFixedDelay(this::connect, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * 由连接检查任务尝试连接和订阅，无参数、无返回值；失败只记录，留待下轮处理。
     */
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

    /**
     * 生成当前演示链路使用的连接配置，无参数。
     *
     * @return 启用 clean session、关闭 Paho 自动重连并设置连接/保活超时的新配置对象。
     */
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

    /**
     * 查询当前是否既连通 Broker 又完成订阅，供发布和在线判断使用，无参数。
     *
     * @return 两个条件同时满足时返回 true。
     */
    public boolean isConnected() {
        return ready && client.isConnected();
    }

    /**
     * 把对象序列化为 JSON，以 QoS 1、retain=false 同步发布，无返回值。
     *
     * @param topic 完整目标 MQTT 主题。
     * @param payload 可被共享 ObjectMapper 序列化的消息对象。
     * @throws Exception 未就绪、序列化失败或发布确认不可用时抛出；不等于设备一定未执行。
     *
     * <p>用法示例：
     * <pre>{@code
     * mqtt.publish(Messages.topic(deviceId, "commands"), command);
     * }</pre>
     */
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

    /**
     * 停止重连任务并尽力断开客户端，无参数、无返回值；用于进程关闭时释放资源。
     */
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
