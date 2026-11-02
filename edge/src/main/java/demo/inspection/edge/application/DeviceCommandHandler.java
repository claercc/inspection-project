package demo.inspection.edge.application;

import demo.inspection.edge.domain.RobotDriver;
import demo.inspection.modbus.ModbusException;
import demo.inspection.modbus.RobotRegisters;
import demo.inspection.protocol.CommandStatus;
import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.Command;
import demo.inspection.protocol.Messages.Result;
import demo.inspection.protocol.Messages.Telemetry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 处理命令校验、进程内去重、设备写入和读回确认。
 * 由 edge 的单线程 worker 调用；重复投递只回放已有结果，避免再次写设备。
 */
public final class DeviceCommandHandler {

    private static final Duration RESULT_RETENTION = Duration.ofMinutes(2);
    private static final Duration MAX_COMMAND_LIFETIME = Duration.ofSeconds(30);
    private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(5);

    private record CachedResult(Command command, Result result, Instant cachedAt) {}

    private final String deviceId;
    private final RobotDriver robot;
    private final Clock clock;
    // 去重账本只保存在当前进程；重启会丢失，不能据此承诺物理动作“恰好执行一次”。
    private final Map<String, CachedResult> results = new HashMap<>();

    public DeviceCommandHandler(String deviceId, RobotDriver robot, Clock clock) {
        this.deviceId = deviceId;
        this.robot = robot;
        this.clock = clock;
    }

    /** 发给其他设备或缺少命令编号的请求会忽略并返回 null，其余请求返回可发布的处理结果。 */
    public Result handle(Command command) {
        if (
            !deviceId.equals(command.deviceId()) ||
            command.commandId() == null ||
            command.commandId().isBlank()
        ) {
            return null;
        }
        Instant now = clock.instant();
        results.values().removeIf(entry -> entry.cachedAt().isBefore(now.minus(RESULT_RETENTION)));
        // 必须先查已有结果再判断过期：原命令过期后，重复投递仍应返回首次执行结果。
        CachedResult cached = results.get(command.commandId());
        if (cached != null) {
            // 同一编号只能对应同一份请求，防止换类型或参数后绕过去重。
            if (!cached.command().equals(command)) {
                return result(
                    command,
                    CommandStatus.FAILED,
                    "commandId reused with different payload",
                    null
                );
            }
            return cached.result();
        }

        Result result = execute(command, now);
        results.put(command.commandId(), new CachedResult(command, result, clock.instant()));
        return result;
    }

    private Result execute(Command command, Instant now) {
        CommandType type;
        try {
            validateTimestamps(command, now);
            type = CommandType.parse(command.type());
            robot.readState();
        } catch (Exception exception) {
            // 写入前的校验或探测失败，尚未尝试控制设备，可以确定为 FAILED。
            return result(command, CommandStatus.FAILED, exception.getMessage(), null);
        }

        try {
            robot.writeControl(type);
        } catch (ModbusException exception) {
            // 设备明确返回写入异常码，可以确定此次写入被拒绝。
            return result(command, CommandStatus.FAILED, exception.getMessage(), null);
        } catch (IOException exception) {
            // 写入应答可能只是丢失，设备可能已经动作；保持结果未知，不能自动重发。
            return result(command, CommandStatus.RESULT_UNKNOWN, exception.getMessage(), null);
        }

        try {
            Telemetry feedback = robot.readState();
            verifyFeedback(type, feedback);
            return result(
                command,
                CommandStatus.SUCCEEDED,
                "FC06 write + FC03 device feedback confirmed",
                feedback
            );
        } catch (IOException exception) {
            // 写入已经成功，后续读回失败（包括 Modbus 异常码）只能记为 RESULT_UNKNOWN。
            return result(command, CommandStatus.RESULT_UNKNOWN, exception.getMessage(), null);
        }
    }

    // 限制指令有效期并容忍少量时钟偏差，避免积压旧指令在恢复通信后执行。
    private void validateTimestamps(Command command, Instant now) {
        if (
            command.createdAt() == null ||
            command.expiresAt() == null ||
            !command.expiresAt().isAfter(now) ||
            !command.expiresAt().isAfter(command.createdAt()) ||
            command.createdAt().isAfter(now.plus(CLOCK_TOLERANCE)) ||
            command.expiresAt().isAfter(command.createdAt().plus(MAX_COMMAND_LIFETIME))
        ) {
            throw new IllegalArgumentException("Command expired or invalid timestamps");
        }
    }

    // 同时核对“上次接受的控制”和“当前运行状态”，不能仅凭 FC06 回显宣布执行成功。
    private void verifyFeedback(CommandType type, Telemetry feedback) throws IOException {
        int expectedControl =
            type == CommandType.START ? RobotRegisters.START_CONTROL : RobotRegisters.STOP_CONTROL;
        String expectedState = type == CommandType.START ? "RUNNING" : "STOPPED";
        if (
            feedback.lastControl() != expectedControl || !expectedState.equals(feedback.runState())
        ) {
            throw new IOException("Device readback did not confirm control");
        }
    }

    private Result result(
        Command command,
        CommandStatus status,
        String message,
        Telemetry telemetry
    ) {
        return new Result(
            command.commandId(),
            deviceId,
            command.type(),
            status.name(),
            message,
            clock.instant(),
            telemetry
        );
    }
}
