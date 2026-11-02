package demo.inspection.protocol;

/** 业务判断使用枚举，HTTP/MQTT 中仍以 START、STOP 字符串传输。 */
public enum CommandType {
    START,
    STOP;

    public static CommandType parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("type must be START or STOP");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("type must be START or STOP");
        }
    }
}
