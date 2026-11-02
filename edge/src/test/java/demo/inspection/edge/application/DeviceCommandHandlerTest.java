package demo.inspection.edge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import demo.inspection.edge.domain.RobotDriver;
import demo.inspection.modbus.ModbusException;
import demo.inspection.modbus.RobotRegisters;
import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.Command;
import demo.inspection.protocol.Messages.Result;
import demo.inspection.protocol.Messages.Telemetry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeviceCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final FakeRobot robot = new FakeRobot();
    private final DeviceCommandHandler handler = new DeviceCommandHandler(
        "robot-test",
        robot,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void duplicateDeliveryReplaysTheResultWithoutAnotherWrite() {
        Command command = command("start-1", "START", NOW.plusSeconds(8));
        Result first = handler.handle(command);
        assertEquals("SUCCEEDED", first.status());
        assertEquals(first, handler.handle(command));
        assertEquals(1, robot.writes);
    }

    @Test
    void commandIdCollisionCannotChangeTheDeviceAction() {
        handler.handle(command("same-id", "START", NOW.plusSeconds(8)));
        assertEquals(
            "FAILED",
            handler.handle(command("same-id", "STOP", NOW.plusSeconds(8))).status()
        );
        assertEquals(1, robot.writes);
    }

    @Test
    void invalidOrExpiredCommandsNeverWrite() {
        assertEquals(
            "FAILED",
            handler.handle(command("invalid", "INVALID", NOW.plusSeconds(8))).status()
        );
        assertEquals(
            "FAILED",
            handler.handle(command("expired", "START", NOW.minusSeconds(1))).status()
        );
        assertEquals(0, robot.writes);
    }

    @Test
    void anotherDevicesCommandIsIgnored() {
        Command command = new Command(
            "foreign",
            "other-robot",
            "START",
            Map.of(),
            NOW,
            NOW.plusSeconds(8)
        );
        assertNull(handler.handle(command));
        assertEquals(0, robot.writes);
    }

    @Test
    void failureBeforeWritingIsDefinite() {
        robot.beforeWriteFailure = new IOException("Device offline");
        assertEquals(
            "FAILED",
            handler.handle(command("offline", "STOP", NOW.plusSeconds(8))).status()
        );
        assertEquals(0, robot.writes);
    }

    @Test
    void anExplicitWriteRejectionIsDefiniteButALostWriteReplyIsUnknown() {
        robot.writeFailure = new ModbusException(2);
        assertEquals(
            "FAILED",
            handler.handle(command("rejected", "START", NOW.plusSeconds(8))).status()
        );
        robot.writeFailure = new IOException("Write reply lost");
        assertEquals(
            "RESULT_UNKNOWN",
            handler.handle(command("lost", "START", NOW.plusSeconds(8))).status()
        );
    }

    @Test
    void aModbusReadExceptionAfterSuccessfulWriteLeavesResultUnknown() {
        robot.readbackFailure = new ModbusException(2);
        Result result = handler.handle(command("readback-failed", "START", NOW.plusSeconds(8)));
        assertEquals("RESULT_UNKNOWN", result.status());
        assertEquals(1, robot.writes);
    }

    private Command command(String id, String type, Instant expiresAt) {
        return new Command(id, "robot-test", type, Map.of(), NOW, expiresAt);
    }

    private static final class FakeRobot implements RobotDriver {

        private int writes;
        private CommandType lastControl = CommandType.STOP;
        private IOException beforeWriteFailure;
        private IOException writeFailure;
        private IOException readbackFailure;

        @Override
        public Telemetry readState() throws IOException {
            if (writes == 0 && beforeWriteFailure != null) {
                throw beforeWriteFailure;
            }
            if (writes > 0 && readbackFailure != null) {
                throw readbackFailure;
            }
            boolean running = lastControl == CommandType.START;
            return new Telemetry(
                "robot-test",
                "TEST",
                NOW,
                true,
                running ? "RUNNING" : "STOPPED",
                0,
                0,
                100,
                26,
                running ? RobotRegisters.START_CONTROL : RobotRegisters.STOP_CONTROL,
                writes,
                null,
                null
            );
        }

        @Override
        public void writeControl(CommandType type) throws IOException {
            if (writeFailure != null) {
                throw writeFailure;
            }
            writes++;
            lastControl = type;
        }

        @Override
        public void close() {}
    }
}
