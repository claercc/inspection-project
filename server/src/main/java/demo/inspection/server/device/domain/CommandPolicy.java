package demo.inspection.server.device.domain;

import demo.inspection.protocol.CommandType;
import demo.inspection.server.device.domain.CommandRejectedException.Reason;

/** 控制命令的准入规则；只使用状态事实，HTTP 状态码由接口层映射。 */
public final class CommandPolicy {

    private CommandPolicy() {}

    /**
     * 在创建命令记录前校验准入规则，无返回值；通过时由调用方继续创建并发布命令。
     *
     * @param type 已解析的 START 或 STOP 控制类型。
     * @param brokerConnected Broker 连接和订阅是否均已就绪。
     * @param deviceOnline 当前设备采样是否新鲜且采集成功。
     * @param robotRunning 最近一次读回是否为运行状态。
     * @param awaitingAnyCommand 是否存在 PENDING/SENT 命令，供启动准入判断。
     * @param awaitingStop 是否已有 PENDING/SENT 的停止命令，用于阻止重复 STOP。
     * @throws CommandRejectedException Broker 不可用或操作与当前状态冲突时抛出，由 Controller 映射 HTTP 状态。
     */
    public static void validate(
        CommandType type,
        boolean brokerConnected,
        boolean deviceOnline,
        boolean robotRunning,
        boolean awaitingAnyCommand,
        boolean awaitingStop
    ) {
        if (!brokerConnected) {
            throw new CommandRejectedException(Reason.UNAVAILABLE, "MQTT Broker offline");
        }

        // START 尚未确认时仍允许发送 STOP；已有待确认 STOP 时拒绝重复提交。
        // 允许提交 STOP 并不代表设备已经停下，仍需等待 edge 的执行反馈。
        if (type == CommandType.STOP) {
            if (awaitingStop) {
                throw new CommandRejectedException(Reason.CONFLICT, "A STOP is awaiting feedback");
            }
            return;
        }

        // 启动需要新鲜在线状态，并且不能与待确认命令或已有运动重复。
        if (!deviceOnline) {
            throw new CommandRejectedException(
                Reason.CONFLICT,
                "Device state is unavailable; wait for fresh Modbus telemetry"
            );
        }
        if (awaitingAnyCommand) {
            throw new CommandRejectedException(
                Reason.CONFLICT,
                "Another command is awaiting feedback"
            );
        }
        if (robotRunning) {
            throw new CommandRejectedException(Reason.CONFLICT, "Robot is already running");
        }
    }
}
