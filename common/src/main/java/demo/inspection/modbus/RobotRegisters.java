package demo.inspection.modbus;

/**
 * 软件模拟器的寄存器点表，地址从 0 开始。
 * 采集驱动与模拟器共用这些名称；接真实设备时需按厂商点表实现对应驱动。
 */
public final class RobotRegisters {

    public static final int RUN_STATE = 0;
    public static final int POSITION_X = 1;
    public static final int POSITION_Y = 2;
    public static final int BATTERY = 3;
    public static final int TEMPERATURE = 4;
    // 地址 5 既是 FC06 的控制写入点，也是 FC03 读回的上次控制值。
    public static final int CONTROL = 5;
    public static final int WRITE_COUNT = 6;
    public static final int REGISTER_COUNT = 7;

    public static final int RUNNING = 1;
    public static final int STOPPED = 0;
    public static final int START_CONTROL = 1;
    public static final int STOP_CONTROL = 2;
    // 位置、电量和温度以实际数值的 10 倍存入整数寄存器，例如温度 260 表示 26.0 °C。
    public static final double MEASUREMENT_SCALE = 10.0;

    private RobotRegisters() {}
}
