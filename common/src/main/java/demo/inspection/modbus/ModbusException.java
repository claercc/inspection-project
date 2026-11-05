package demo.inspection.modbus;

import java.io.IOException;

/** 设备明确返回的 Modbus 异常码，与无响应、断线等 IOException 区分。 */
public class ModbusException extends IOException {

    private final int exceptionCode;

    /**
     * 根据设备返回的异常码构造协议异常，供读写调用方区分设备拒绝和网络故障。
     *
     * @param code Modbus 异常响应中的无符号异常码。
     */
    public ModbusException(int code) {
        super("Modbus 设备返回异常码：" + code);
        exceptionCode = code;
    }

    /**
     * 读取设备原始异常码，供诊断和上层分类使用，无参数。
     *
     * @return 构造此异常时保存的异常码。
     */
    public int exceptionCode() {
        return exceptionCode;
    }
}
