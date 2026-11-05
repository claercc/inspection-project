package demo.inspection.server.device.domain;

import demo.inspection.protocol.Messages.Telemetry;
import java.time.Duration;
import java.time.Instant;

/**
 * 保存当前设备最新的有效采样及接收信息。
 * 对象自身不加锁，由 DeviceService 的状态锁统一串行访问。
 */
public final class DeviceTelemetry {

    private static final Duration STALE_AFTER = Duration.ofSeconds(3);

    private final String deviceId;
    private Telemetry latest;
    private Instant receivedAt;
    private String topic;

    /**
     * 创建一个只接收指定设备采样的状态容器，初始没有有效采样。
     *
     * @param deviceId 允许接收的业务设备编号。
     */
    public DeviceTelemetry(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 尝试接收一份采样；由应用服务在状态锁内调用，拒绝时不刷新接收时间和主题。
     *
     * @param sample 来自遥测或结果消息的采样；可为 null，缺少关键字段或设备不匹配会拒绝。
     * @param sourceTopic 消息实际到达的 MQTT 主题，不由前端推算。
     * @param now 服务端本次接收时间。
     * @return 采样合法且采集时间严格晚于已保存样本时返回 true，其余情况返回 false。
     */
    public boolean accept(Telemetry sample, String sourceTopic, Instant now) {
        if (
            sample == null ||
            !deviceId.equals(sample.deviceId()) ||
            sample.capturedAt() == null ||
            sample.runState() == null
        ) {
            return false;
        }
        // 迟到或重复采样不能覆盖新状态，也不能通过重复到达让旧数据重新“在线”。
        if (latest != null && !sample.capturedAt().isAfter(latest.capturedAt())) {
            return false;
        }
        latest = sample;
        receivedAt = now;
        topic = sourceTopic;
        return true;
    }

    /**
     * 检查当前采样是否已过期，供在线判断和页面连接状态计算使用。
     *
     * @param now 执行判定时的服务端时间。
     * @return 没有采样，或采集/接收时间任一早于过期边界时返回 true。
     */
    public boolean isStale(Instant now) {
        // 接收时间和采集时间都要新鲜：刚收到一条积压旧消息，也不能认为设备仍在线。
        Instant cutoff = now.minus(STALE_AFTER);
        return (
            latest == null || receivedAt.isBefore(cutoff) || latest.capturedAt().isBefore(cutoff)
        );
    }

    /**
     * 结合 Broker 就绪状态与当前采样判断设备能否作为在线设备使用。
     *
     * @param brokerConnected 服务端是否连通 Broker 且订阅就绪。
     * @param now 统一用于本次新鲜度判定的时间。
     * @return Broker 就绪、采样未过期且 robotOnline 为 true 时返回 true。
     */
    public boolean isOnline(boolean brokerConnected, Instant now) {
        return brokerConnected && !isStale(now) && latest.robotOnline();
    }

    // 这里只描述最近一次读回的运动状态；是否仍然新鲜由 isOnline 单独判断。
    /**
     * 读取最近一次设备反馈中的运动状态，无参数；调用方需另行核对新鲜度。
     *
     * @return 已保存样本的 runState 为 RUNNING 时返回 true，否则返回 false。
     */
    public boolean isRunning() {
        return latest != null && "RUNNING".equals(latest.runState());
    }

    /**
     * 取得当前保存的最新采样，无参数，供构造页面快照使用。
     *
     * @return 已保存的 Telemetry 引用；尚未接收有效样本时为 null。
     */
    public Telemetry latest() {
        return latest;
    }

    /**
     * 取得最新有效采样的服务端接收时间，无参数。
     *
     * @return 最近一次被 accept 接受时传入的时间；尚无有效样本时为 null。
     */
    public Instant receivedAt() {
        return receivedAt;
    }

    /**
     * 取得最新有效采样的实际来源主题，无参数。
     *
     * @return telemetry 或 results 消息的完整主题；尚无有效样本时为 null。
     */
    public String topic() {
        return topic;
    }
}
