package demo.inspection.server.device.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import demo.inspection.protocol.CommandType;
import demo.inspection.server.device.domain.CommandRejectedException.Reason;
import org.junit.jupiter.api.Test;

class CommandPolicyTest {

    @Test
    void stopRemainsAvailableWhileStartAwaitsFeedback() {
        assertDoesNotThrow(() ->
            CommandPolicy.validate(CommandType.STOP, true, false, true, true, false)
        );
    }

    @Test
    void aSecondPendingStopIsRejected() {
        CommandRejectedException exception = assertThrows(CommandRejectedException.class, () ->
            CommandPolicy.validate(CommandType.STOP, true, true, true, true, true)
        );
        assertEquals(Reason.CONFLICT, exception.reason());
    }

    @Test
    void startRequiresFreshStateAndNoPendingCommand() {
        assertThrows(CommandRejectedException.class, () ->
            CommandPolicy.validate(CommandType.START, true, false, false, false, false)
        );
        assertThrows(CommandRejectedException.class, () ->
            CommandPolicy.validate(CommandType.START, true, true, false, true, false)
        );
        assertThrows(CommandRejectedException.class, () ->
            CommandPolicy.validate(CommandType.START, true, true, true, false, false)
        );
        assertDoesNotThrow(() ->
            CommandPolicy.validate(CommandType.START, true, true, false, false, false)
        );
    }

    @Test
    void neitherCommandCanBePublishedWithoutBroker() {
        for (CommandType type : CommandType.values()) {
            CommandRejectedException exception = assertThrows(CommandRejectedException.class, () ->
                CommandPolicy.validate(type, false, false, false, false, false)
            );
            assertEquals(Reason.UNAVAILABLE, exception.reason());
        }
    }
}
