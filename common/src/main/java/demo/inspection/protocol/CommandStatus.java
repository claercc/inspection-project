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

    /**
     * 判断本状态是否仍在等待发布或设备反馈，无参数。
     *
     * @return PENDING 或 SENT 返回 true，其余状态返回 false。
     */
    public boolean isAwaitingFeedback() {
        return this == PENDING || this == SENT;
    }

    // 结果未知仍可被迟到的确切反馈更新；成功/失败是受保护的已确认终态。
    /**
     * 判断本状态是否为受保护的已确认终态，无参数。
     *
     * @return SUCCEEDED 或 FAILED 返回 true；RESULT_UNKNOWN 仍可接受迟到确认，返回 false。
     */
    public boolean isConfirmed() {
        return this == SUCCEEDED || this == FAILED;
    }

    /**
     * 判断本状态能否作为 edge 上报的执行结果，无参数。
     *
     * @return 成功、失败或结果未知返回 true；仅服务端使用的 PENDING/SENT 返回 false。
     */
    public boolean isDeviceResult() {
        return isConfirmed() || this == RESULT_UNKNOWN;
    }
}
