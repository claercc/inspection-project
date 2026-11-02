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
            new CommandView(command, CommandStatus.PENDING, "Preparing MQTT publication", now)
        );
        trimCompletedEntries();
        return command;
    }

    public boolean hasPendingCommand() {
        return entries
            .values()
            .stream()
            .anyMatch(entry -> entry.status().isAwaitingFeedback());
    }

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

    public void markPublished(String commandId, Instant now) {
        updatePending(
            commandId,
            CommandStatus.SENT,
            "MQTT QoS1 acknowledged; waiting for device readback",
            now
        );
    }

    public void markPublicationUnknown(String commandId, String error, Instant now) {
        updatePending(
            commandId,
            CommandStatus.RESULT_UNKNOWN,
            "Publication acknowledgement unavailable: " + error,
            now
        );
    }

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

    /** 只接受匹配设备、类型和编号的结果；已确认终态不被重复回执覆盖。 */
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
                "No device feedback before deadline; inspect state before manual recovery",
                now
            );
        });
        return expiredIds;
    }

    public CommandView get(String commandId) {
        return entries.get(commandId);
    }

    public List<CommandView> newestFirst() {
        List<CommandView> history = new ArrayList<>(entries.values());
        Collections.reverse(history);
        return history;
    }

    // 优先清理非待确认记录；不能为满足历史条数上限而丢掉仍在等待反馈的命令。
    private void trimCompletedEntries() {
        var iterator = entries.values().iterator();
        while (entries.size() > HISTORY_LIMIT && iterator.hasNext()) {
            if (!iterator.next().status().isAwaitingFeedback()) {
                iterator.remove();
            }
        }
    }
}
