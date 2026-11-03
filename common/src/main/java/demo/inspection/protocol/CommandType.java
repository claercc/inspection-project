package demo.inspection.protocol;

/** 业务判断使用枚举，HTTP/MQTT 中仍以 START、STOP 字符串传输。 */
public enum CommandType {
    START,
    STOP;

    /**
     * 把外部请求中的控制类型转换为枚举，供 server 和 edge 校验使用。
     *
     * @param value 大小写敏感的 START 或 STOP；不接受 null。
     * @return 对应的命令类型枚举。
     * @throws IllegalArgumentException value 为空或不是受支持的控制类型时抛出。
     *
     * <p>用法示例：
     * <pre>{@code
     * CommandType.parse("STOP");
     * }</pre>
     */
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
