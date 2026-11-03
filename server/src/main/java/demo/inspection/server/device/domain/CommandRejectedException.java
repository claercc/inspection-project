package demo.inspection.server.device.domain;

/** 表达业务拒绝原因；领域规则不依赖 HTTP，由 Controller 决定具体响应状态码。 */
public final class CommandRejectedException extends RuntimeException {

    public enum Reason {
        INVALID_REQUEST,
        UNAVAILABLE,
        CONFLICT,
    }

    private final Reason reason;

    /**
     * 构造一次业务拒绝，供规则层抛出并由接口层统一映射。
     *
     * @param reason 拒绝类别：非法请求、依赖不可用或状态冲突。
     * @param message 向调用方说明拒绝原因的文字。
     */
    public CommandRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * 取得拒绝类别，供 Controller 选择 HTTP 状态码，无参数。
     *
     * @return 构造异常时保存的 Reason。
     */
    public Reason reason() {
        return reason;
    }
}
