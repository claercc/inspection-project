package demo.inspection.server.device.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import demo.inspection.protocol.Messages.Telemetry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeviceTelemetryTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final DeviceTelemetry state = new DeviceTelemetry("robot-test");

    @Test
    void aLateResultCannotOverwriteNewerTelemetry() {
        Telemetry stopped = sample("robot-test", NOW, "STOPPED");
        assertTrue(state.accept(stopped, "telemetry", NOW));
        assertFalse(
            state.accept(sample("robot-test", NOW.minusSeconds(1), "RUNNING"), "results", NOW)
        );
        assertEquals(stopped, state.latest());
        assertEquals("telemetry", state.topic());
    }

    @Test
    void replayingASampleDoesNotExtendItsLiveness() {
        Telemetry sample = sample("robot-test", NOW, "STOPPED");
        state.accept(sample, "telemetry", NOW);
        assertFalse(state.accept(sample, "results", NOW.plusSeconds(4)));
        assertEquals(NOW, state.receivedAt());
        assertTrue(state.isStale(NOW.plusSeconds(4)));
        assertFalse(state.isOnline(true, NOW.plusSeconds(4)));
    }

    @Test
    void invalidAndForeignSamplesAreIgnored() {
        assertFalse(state.accept(null, "telemetry", NOW));
        assertFalse(state.accept(sample("another-robot", NOW, "RUNNING"), "telemetry", NOW));
        assertFalse(state.accept(sample("robot-test", null, "RUNNING"), "telemetry", NOW));
        assertFalse(state.accept(sample("robot-test", NOW, null), "telemetry", NOW));
        assertTrue(state.isStale(NOW));
    }

    @Test
    void brokerLossMakesAFreshSampleUnavailable() {
        state.accept(sample("robot-test", NOW, "RUNNING"), "telemetry", NOW);
        assertTrue(state.isOnline(true, NOW));
        assertFalse(state.isOnline(false, NOW));
    }

    private Telemetry sample(String deviceId, Instant capturedAt, String runState) {
        return new Telemetry(
            deviceId,
            "TEST",
            capturedAt,
            true,
            runState,
            0,
            0,
            100,
            26,
            0,
            0,
            null,
            null
        );
    }
}
