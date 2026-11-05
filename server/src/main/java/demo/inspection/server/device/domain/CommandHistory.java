package demo.inspection.server.device.domain;

import demo.inspection.protocol.CommandStatus;
import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.Command;
import demo.inspection.protocol.Messages.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理进程内的命令记录与状态流转，重启服务会清空。
 * 由应用服务统一加锁，保证“检查待确认命令”和“创建新记录”处于同一临界区。
 */
public final class CommandHistory {

    private static final int HISTORY_LIMIT = 100;
    private static final Duration COMMAND_LIFETIME = Duration.ofSeconds(8);

    public record CommandView(
        Command command,
        CommandStatus status,
        String message,
        Instant updatedAt
    ) {}

    private final Map<String, CommandView> entries = new LinkedHashMap<>();

    /**
     * 创建带唯一编号和截止时间的 PENDING 记录；调用方应先在同一状态锁内通过准入校验。
     *
     * @param deviceId 目标业务设备编号。
     * @param type 已解析的控制类型。
     * @param now 创建时间，也用于计算命令截止时间。
     * @return 供 MQTT 发布的原始 Command；对应记录同时保存在进程内存中。
     */
    public Command create(String deviceId, CommandType type, Instant now) {
        Command command = new Command(
            UUID.randomUUID().toString(),
            deviceId,
            type.name(),
            Map.of(),
            now,
            now.plus(COMMAND_LIFETIME)
        );
        entries.put(
            command.commandId(),
            new CommandView(command, CommandStatus.PENDING, "正在准备发送 MQTT 命令", now)
        );
        trimCompletedEntries();
        return command;
    }

    /**
     * 查询是否存在任何等待反馈的命令，无参数，供 START 准入规则调用。
     *
     * @return 至少一条记录为 PENDING/SENT 时返回 true。
     */
    public boolean hasPendingCommand() {
        return entries
            .values()
            .stream()
            .anyMatch(entry -> entry.status().isAwaitingFeedback());
    }

    /**
     * 查询是否已有等待反馈的停止命令，无参数，供 STOP 去重准入判断使用。
     *
     * @return 至少一条 STOP 记录为 PENDING/SENT 时返回 true。
     */
    public boolean hasPendingStop() {
        return entries
            .values()
            .stream()
            .anyMatch(
                entry ->
                    entry.status().isAwaitingFeedback() &&
                    CommandType.STOP.name().equals(entry.command().type())
            );
    }

    /**
     * 取得 Broker 发布确认后，将仍处于 PENDING 的记录改为 SENT，无返回值。
     *
     * @param commandId 已发布命令的唯一编号。
     * @param now 本次状态更新时间。
     */
    public void markPublished(String commandId, Instant now) {
        updatePending(
            commandId,
            CommandStatus.SENT,
            "MQTT 消息服务已确认接收，正在等待设备读回",
            now
        );
    }

    /**
     * 发布确认不可用时，将仍处于 PENDING 的记录标为结果未知，无返回值、不重发命令。
     *
     * @param commandId 本次发布的命令编号。
     * @param error 发布或确认失败的说明。
     * @param now 本次状态更新时间。
     */
    public void markPublicationUnknown(String commandId, String error, Instant now) {
        updatePending(
            commandId,
            CommandStatus.RESULT_UNKNOWN,
            "未能确认消息发布结果：" + error,
            now
        );
    }

    /**
     * 统一更新 PENDING 记录，无返回值；编号不存在或已收到设备结果时保持原记录。
     *
     * @param commandId 待更新的命令编号。
     * @param status 发布阶段要切换到的状态。
     * @param message 新状态对应的说明。
     * @param now 新记录的更新时间。
     */
    private void updatePending(
        String commandId,
        CommandStatus status,
        String message,
        Instant now
    ) {
        CommandView previous = entries.get(commandId);
        // 设备结果可能早于 PUBACK 到达；仅更新 PENDING，避免把已确认结果改回 SENT。
        if (previous != null && previous.status() == CommandStatus.PENDING) {
            entries.put(commandId, new CommandView(previous.command(), status, message, now));
        }
    }

    /**
     * 登记一条设备回执，保护已确认终态，并允许迟到确认更新 RESULT_UNKNOWN。
     *
     * @param result 从 results 主题解析的结果，需匹配原命令编号、设备与类型。
     * @param now 服务端登记回执的时间。
     * @return 匹配并接受本次状态更新时返回 true；未知编号、冲突或非法结果返回 false。
     */
    public boolean accept(Result result, Instant now) {
        CommandView previous = entries.get(result.commandId());
        if (
            previous == null ||
            previous.status().isConfirmed() ||
            !previous.command().deviceId().equals(result.deviceId()) ||
            !previous.command().type().equals(result.type()) ||
            result.status() == null
        ) {
            return false;
        }
        CommandStatus status;
        try {
            status = CommandStatus.valueOf(result.status());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!status.isDeviceResult()) {
            return false;
        }
        entries.put(
            result.commandId(),
            new CommandView(previous.command(), status, result.message(), now)
        );
        return true;
    }

    // 超时只能说明“没收到结果”，不能据此断定设备没有执行，也不能自动重发。
    /**
     * 将已到截止时间且仍等待反馈的命令改为 RESULT_UNKNOWN，供定时扫描调用。
     *
     * @param now 扫描时的当前时间；等于截止时间也视为已到期。
     * @return 本轮刚过期的命令编号列表，供应用服务写诊断；没有新增过期项时为空列表。
     */
    public List<String> expire(Instant now) {
        List<String> expiredIds = new ArrayList<>();
        entries.replaceAll((commandId, entry) -> {
            if (!entry.status().isAwaitingFeedback() || entry.command().expiresAt().isAfter(now)) {
                return entry;
            }
            expiredIds.add(commandId);
            return new CommandView(
                entry.command(),
                CommandStatus.RESULT_UNKNOWN,
                "截止时间内未收到设备反馈，请检查设备状态后手动处理",
                now
            );
        });
        return expiredIds;
    }

    /**
     * 按编号查询当前进程保留的命令记录，供返回本次提交结果使用。
     *
     * @param commandId 命令唯一编号。
     * @return 对应的当前记录；未知或已清理的编号返回 null。
     */
    public CommandView get(String commandId) {
        return entries.get(commandId);
    }

    /**
     * 按创建顺序从新到旧生成命令历史列表，无参数。
     *
     * @return 新建的列表副本；修改列表结构不会改变内部历史。
     */
    public List<CommandView> newestFirst() {
        List<CommandView> history = new ArrayList<>(entries.values());
        Collections.reverse(history);
        return history;
    }

    // 优先清理非待确认记录；不能为满足历史条数上限而丢掉仍在等待反馈的命令。
    /**
     * 在创建记录后清理超出保留上限的非待确认记录，无参数、无返回值。
     */
    private void trimCompletedEntries() {
        var iterator = entries.values().iterator();
        while (entries.size() > HISTORY_LIMIT && iterator.hasNext()) {
            if (!iterator.next().status().isAwaitingFeedback()) {
                iterator.remove();
            }
        }
    }
}
