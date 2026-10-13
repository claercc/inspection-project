package demo.inspection.modbus;

import java.io.IOException;

public class ModbusException extends IOException {
    private final int exceptionCode;
    public ModbusException(int code) { super("Modbus exception code " + code); exceptionCode = code; }
    public int exceptionCode() { return exceptionCode; }
}
