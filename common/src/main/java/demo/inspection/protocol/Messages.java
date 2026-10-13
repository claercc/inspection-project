package demo.inspection.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;

public final class Messages {
    private Messages() {}
    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public record Command(String commandId, String deviceId, String type, Map<String, Object> parameters,
                          Instant createdAt, Instant expiresAt) {}
    public record Telemetry(String deviceId, String source, Instant capturedAt, boolean robotOnline,
                            String runState, double x, double y, double battery, double temperature,
                            int lastControl, int writeCount, String error) {}
    public record Result(String commandId, String deviceId, String type, String status, String message,
                         Instant timestamp, Telemetry telemetry) {}
    public static String topic(String deviceId, String kind) { return "inspection/" + deviceId + "/" + kind; }
}
