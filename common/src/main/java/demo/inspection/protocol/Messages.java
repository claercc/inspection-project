package demo.inspection.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * server 与 edge 通过 MQTT 交换的消息契约。
 * 字段名会直接成为 JSON 属性；调整结构时需同时检查两端和前端的兼容性。
 */
public final class Messages {

    private Messages() {}

    // 统一使用 ISO-8601 时间字符串，方便跨 Java、Node 和浏览器核对同一条消息。
    public static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 控制请求。同一 commandId 用于关联 HTTP、MQTT 和设备反馈；重复投递必须保持内容一致。
     * createdAt / expiresAt 限定有效期，避免旧指令在通信恢复后意外执行。
     */
    public record Command(
        String commandId,
        String deviceId,
        String type,
        Map<String, Object> parameters,
        Instant createdAt,
        Instant expiresAt
    ) {}

    /**
     * 一次设备状态采样：x/y 单位为米，battery 为百分比，temperature 为摄氏度。
     * capturedAt 是 edge 完成读取的时间；采集失败时应结合 robotOnline/error 判断，不能展示占位零值。
     */
    public record Telemetry(
        String deviceId,
        String source,
        Instant capturedAt,
        boolean robotOnline,
        String runState,
        double x,
        double y,
        double battery,
        double temperature,
        int lastControl,
        int writeCount,
        String error,
        SamplingEvidence evidence
    ) {}

    /**
     * 保存 FC03 实际返回的原始寄存器，用于核对温度换算和数据来源。
     * registers 的下标相对于 startAddress；sampleId 由采集端生成，不代表硬件身份认证。
     */
    public record SamplingEvidence(
        String sampleId,
        String collectorId,
        String protocol,
        String host,
        int port,
        int unitId,
        int functionCode,
        int startAddress,
        List<Integer> registers
    ) {}

    /** 设备侧处理结果；telemetry 可为空，必须由 status 判断是否已获得执行确认。 */
    public record Result(
        String commandId,
        String deviceId,
        String type,
        String status,
        String message,
        Instant timestamp,
        Telemetry telemetry
    ) {}

    /**
     * 构造当前项目的 MQTT 主题，供发布和订阅共用。
     *
     * @param deviceId 设备编号，调用方应提供不含 MQTT 通配符或路径分隔符的业务编号。
     * @param kind 消息类别，通常为 commands、telemetry 或 results；本方法不校验类别。
     * @return 格式为 inspection/{deviceId}/{kind} 的主题字符串。
     *
     * <p>用法示例：
     * <pre>{@code
     * Messages.topic("robot-001", "telemetry");
     * }</pre>
     */
    public static String topic(String deviceId, String kind) {
        return "inspection/" + deviceId + "/" + kind;
    }
}
