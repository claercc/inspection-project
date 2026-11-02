package demo.inspection.protocol;

/** 区分“已发送”和“设备已确认”，避免把通信成功当成执行成功。 */
public enum CommandStatus {
    /** 已建立记录，尚未取得发布确认。 */
    PENDING,
    /** Broker 已确认接收，仍需等待设备读回。 */
    SENT,
    /** 写入及后续设备读回均已确认。 */
    SUCCEEDED,
    /** 写入前失败，或设备明确拒绝写入。 */
    FAILED,
    /** 无法确定设备是否执行；不得据此自动重发控制。 */
    RESULT_UNKNOWN;

    public boolean isAwaitingFeedback() {
        return this == PENDING || this == SENT;
    }

    // 结果未知仍可被迟到的确切反馈更新；成功/失败是受保护的已确认终态。
    public boolean isConfirmed() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean isDeviceResult() {
        return isConfirmed() || this == RESULT_UNKNOWN;
    }
}
