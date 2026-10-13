package demo.inspection.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** MQTT 3.1.1 / QoS 1. Clean sessions: no buffered control commands after reconnect. */
public final class MqttLink implements AutoCloseable {
    private final MqttClient client;
    private final String[] subscriptions;
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean ready;

    public MqttLink(String url, String clientId, String[] subscriptions,
                    BiConsumer<String, String> onMessage) throws MqttException {
        this.subscriptions = subscriptions;
        client = new MqttClient(url, clientId, new MemoryPersistence());
        client.setTimeToWait(3000);
        client.setCallback(new MqttCallback() {
            public void connectionLost(Throwable cause) { ready = false; System.err.println(clientId + " MQTT lost: " + cause); }
            public void deliveryComplete(IMqttDeliveryToken token) {}
            public void messageArrived(String topic, MqttMessage message) {
                // Retained controls must never trigger movement.
                if (topic.endsWith("/commands") && message.isRetained()) return;
                try { onMessage.accept(topic, new String(message.getPayload(), StandardCharsets.UTF_8)); }
                catch (Exception e) { System.err.println("Invalid MQTT message on " + topic + ": " + e); }
            }
        });
    }
    public void start() { connector.scheduleWithFixedDelay(this::connect, 0, 2, TimeUnit.SECONDS); }
    private void connect() {
        if (isConnected()) return;
        try {
            if (!client.isConnected()) {
                var options = new MqttConnectOptions();
                options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                options.setCleanSession(true);
                options.setConnectionTimeout(2);
                options.setKeepAliveInterval(5);
                options.setAutomaticReconnect(false);
                client.connect(options);
            }
            int[] qos = new int[subscriptions.length]; java.util.Arrays.fill(qos, 1);
            client.subscribe(subscriptions, qos);
            ready = true;
            System.out.println(client.getClientId() + " MQTT subscribed " + java.util.Arrays.toString(subscriptions));
        } catch (MqttException e) { ready = false; System.err.println("MQTT connecting: " + e.getMessage()); }
    }
    public boolean isConnected() { return ready && client.isConnected(); }
    public void publish(String topic, Object payload) throws Exception {
        if (!isConnected()) throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        var message = new MqttMessage(demo.inspection.protocol.Messages.JSON.writeValueAsBytes(payload));
        message.setQos(1); message.setRetained(false);
        client.publish(topic, message);
    }
    public void close() {
        connector.shutdownNow(); ready = false;
        try { if (client.isConnected()) client.disconnect(1000); client.close(); }
        catch (MqttException ignored) {}
    }
}
