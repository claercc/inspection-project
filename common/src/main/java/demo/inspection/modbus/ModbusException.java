package demo.inspection.modbus;

import java.io.IOException;

/** 设备明确返回的 Modbus 异常码，与无响应、断线等 IOException 区分。 */
public class ModbusException extends IOException {

    private final int exceptionCode;

    public ModbusException(int code) {
        super("Modbus exception code " + code);
        exceptionCode = code;
    }

    public int exceptionCode() {
        return exceptionCode;
    }
}
