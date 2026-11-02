package demo.inspection.server.device.domain;

import demo.inspection.protocol.Messages.Telemetry;
import java.time.Duration;
import java.time.Instant;

/**
 * 保存当前设备最新的有效采样及接收信息。
 * 对象自身不加锁，由 ConsoleService 的状态锁统一串行访问。
 */
public final class DeviceTelemetry {

    private static final Duration STALE_AFTER = Duration.ofSeconds(3);

    private final String deviceId;
    private Telemetry latest;
    private Instant receivedAt;
    private String topic;

    public DeviceTelemetry(String deviceId) {
        this.deviceId = deviceId;
    }

    /** 返回是否接受了新采样；被拒绝的报文不更新接收时间或来源主题。 */
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

    public boolean isStale(Instant now) {
        // 接收时间和采集时间都要新鲜：刚收到一条积压旧消息，也不能认为设备仍在线。
        Instant cutoff = now.minus(STALE_AFTER);
        return (
            latest == null || receivedAt.isBefore(cutoff) || latest.capturedAt().isBefore(cutoff)
        );
    }

    public boolean isOnline(boolean brokerConnected, Instant now) {
        return brokerConnected && !isStale(now) && latest.robotOnline();
    }

    // 这里只描述最近一次读回的运动状态；是否仍然新鲜由 isOnline 单独判断。
    public boolean isRunning() {
        return latest != null && "RUNNING".equals(latest.runState());
    }

    public Telemetry latest() {
        return latest;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public String topic() {
        return topic;
    }
}
