package demo.inspection.protocol;

/** 业务判断使用枚举，HTTP/MQTT 中仍以 START、STOP 字符串传输。 */
public enum CommandType {
    START,
    STOP;

    /**
     * 获取控制类型的中文显示文字，无参数；协议仍使用枚举原名传输。
     * @return 启动或停止。
     */
    public String displayName() {
        return this == START ? "启动" : "停止";
    }

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
            throw new IllegalArgumentException("控制类型必须为 START（启动）或 STOP（停止）");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("控制类型必须为 START（启动）或 STOP（停止）");
        }
    }
}
