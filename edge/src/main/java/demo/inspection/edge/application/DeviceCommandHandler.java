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

    /**
     * 装配单设备命令处理器，生产环境传系统时钟，测试可传固定时钟。
     *
     * @param deviceId 本处理器允许控制的业务设备编号。
     * @param robot 提供读状态和写控制能力的驱动。
     * @param clock 用于有效期判断、结果时间和缓存清理的时钟。
     */
    public DeviceCommandHandler(String deviceId, RobotDriver robot, Clock clock) {
        this.deviceId = deviceId;
        this.robot = robot;
        this.clock = clock;
    }

    /**
     * 由 edge 单线程 worker 调用：检查设备和编号，去重后执行或回放结果。
     *
     * @param command 从 MQTT 解析出的完整控制命令；重复投递需保留原始编号和全部内容。
     * @return 待发布的处理结果；发给其他设备或缺少有效编号时返回 null。
     *
     * <p>用法示例：
     * <pre>{@code
     * Result result = handler.handle(command);
     * }</pre>
     */
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

    /**
     * 按校验/预读、写入、读回三个阶段处理命令，区分明确失败和结果未知。
     *
     * @param command 尚未执行的新命令。
     * @param now 开始处理时的当前时间，用于有效期校验。
     * @return SUCCEEDED、FAILED 或 RESULT_UNKNOWN 结果；设备通信异常在内部转换为结果。
     */
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
    /**
     * 检查指令有效期和允许的时钟偏差，无返回值，写入设备前调用。
     *
     * @param command 包含创建时间和截止时间的控制命令。
     * @param now edge 当前时间。
     * @throws IllegalArgumentException 时间缺失、已过期、有效期异常或创建时间过于超前时抛出。
     */
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
    /**
     * 核对设备最近控制值与当前运行状态是否同时符合意图，无返回值。
     *
     * @param type 原始 START 或 STOP 意图。
     * @param feedback 控制写入后的本次设备读回。
     * @throws IOException 任一反馈项不匹配时抛出，上层将执行结果保持为未知。
     */
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

    /**
     * 按统一结构创建执行结果，供成功、失败和未知分支复用。
     *
     * @param command 用于关联编号和控制类型的原始请求。
     * @param status 本次处理结果状态。
     * @param message 结果说明或异常提示。
     * @param telemetry 可用的读回状态；没有确切反馈时传 null。
     * @return 带当前处理器设备编号和时钟时间的新 Result。
     */
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
