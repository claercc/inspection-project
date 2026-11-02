package demo.inspection.server.device.domain;

/** 表达业务拒绝原因；领域规则不依赖 HTTP，由 Controller 决定具体响应状态码。 */
public final class CommandRejectedException extends RuntimeException {

    public enum Reason {
        INVALID_REQUEST,
        UNAVAILABLE,
        CONFLICT,
    }

    private final Reason reason;

    public CommandRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
