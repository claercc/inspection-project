package demo.inspection.server.device.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import demo.inspection.protocol.CommandStatus;
import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.Command;
import demo.inspection.protocol.Messages.Result;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandHistoryTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final CommandHistory history = new CommandHistory();

    @Test
    void aResultReceivedBeforePubackStaysConfirmed() {
        Command command = history.create("robot-test", CommandType.START, NOW);
        assertTrue(history.accept(result(command, "SUCCEEDED"), NOW));
        history.markPublished(command.commandId(), NOW.plusMillis(1));
        history.markPublicationUnknown(command.commandId(), "timeout", NOW.plusMillis(2));
        assertEquals(CommandStatus.SUCCEEDED, history.get(command.commandId()).status());
    }

    @Test
    void anExpiredCommandCanStillReceiveItsLateDeviceResult() {
        Command command = history.create("robot-test", CommandType.STOP, NOW);
        assertEquals(List.of(command.commandId()), history.expire(NOW.plusSeconds(8)));
        assertEquals(CommandStatus.RESULT_UNKNOWN, history.get(command.commandId()).status());
        assertTrue(history.accept(result(command, "SUCCEEDED"), NOW.plusSeconds(9)));
        assertFalse(history.accept(result(command, "FAILED"), NOW.plusSeconds(10)));
        assertEquals(CommandStatus.SUCCEEDED, history.get(command.commandId()).status());
    }

    @Test
    void feedbackMustMatchTheDeviceAndCommandType() {
        Command command = history.create("robot-test", CommandType.START, NOW);
        assertFalse(
            history.accept(
                new Result(command.commandId(), "other", "START", "SUCCEEDED", "", NOW, null),
                NOW
            )
        );
        assertFalse(
            history.accept(
                new Result(command.commandId(), "robot-test", "STOP", "SUCCEEDED", "", NOW, null),
                NOW
            )
        );
        assertFalse(history.accept(result(command, "PENDING"), NOW));
        assertFalse(history.accept(result(command, "INVALID"), NOW));
        assertEquals(CommandStatus.PENDING, history.get(command.commandId()).status());
    }

    @Test
    void historyDistinguishesAPendingStopFromAPendingStart() {
        history.create("robot-test", CommandType.START, NOW);
        assertTrue(history.hasPendingCommand());
        assertFalse(history.hasPendingStop());
        history.create("robot-test", CommandType.STOP, NOW);
        assertTrue(history.hasPendingStop());
    }

    private Result result(Command command, String status) {
        return new Result(
            command.commandId(),
            command.deviceId(),
            command.type(),
            status,
            "feedback",
            NOW,
            null
        );
    }
}
