package demo.inspection.edge.infrastructure.modbus;

import demo.inspection.edge.domain.RobotDriver;
import demo.inspection.modbus.ModbusClient;
import demo.inspection.modbus.RobotRegisters;
import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.SamplingEvidence;
import demo.inspection.protocol.Messages.Telemetry;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 将模拟器寄存器转换为测量值，同时保留本次读取的原始数据供独立核对。 */
public final class ModbusRobotDriver implements RobotDriver {

    private static final int SOCKET_TIMEOUT_MS = 1000;

    private final String deviceId;
    private final String host;
    private final int port;
    private final int unit;
    private final ModbusClient client;

    /**
     * 装配模拟器点表对应的 Modbus 驱动；构造时只配置连接，首次读取才联网。
     *
     * @param deviceId 遥测中使用的业务设备编号。
     * @param host Modbus 设备主机名或 IP。
     * @param port 设备端口。
     * @param unit Modbus Unit ID。
     */
    public ModbusRobotDriver(String deviceId, String host, int port, int unit) {
        this.deviceId = deviceId;
        this.host = host;
        this.port = port;
        this.unit = unit;
        client = new ModbusClient(host, port, unit, SOCKET_TIMEOUT_MS);
    }

    /**
     * 一次 FC03 连续读取整个演示点表，无参数；轮询和控制反馈共用此方法。
     *
     * @return 由同一原始响应换算的设备状态，附带 sampleId、读取端点和原始寄存器证据。
     * @throws IOException 读取失败或响应不符合 Modbus 协议时抛出。
     */
    @Override
    public Telemetry readState() throws IOException {
        int[] registers = client.readHolding(
            RobotRegisters.RUN_STATE,
            RobotRegisters.REGISTER_COUNT
        );
        // 换算值与来源证据共用本次 FC03 响应，不能根据显示温度反推原始寄存器。
        SamplingEvidence evidence = new SamplingEvidence(
            UUID.randomUUID().toString(),
            "inspection-edge-" + deviceId,
            "MODBUS_TCP",
            host,
            port,
            unit,
            3,
            RobotRegisters.RUN_STATE,
            Arrays.stream(registers).boxed().toList()
        );

        // capturedAt 记录 edge 读完数据的时间，不代表物理传感器内部的采样时钟。
        return new Telemetry(
            deviceId,
            "MODBUS_TCP_SIMULATOR",
            Instant.now(),
            true,
            registers[RobotRegisters.RUN_STATE] == RobotRegisters.RUNNING ? "RUNNING" : "STOPPED",
            scaled(registers, RobotRegisters.POSITION_X),
            scaled(registers, RobotRegisters.POSITION_Y),
            scaled(registers, RobotRegisters.BATTERY),
            scaled(registers, RobotRegisters.TEMPERATURE),
            registers[RobotRegisters.CONTROL],
            registers[RobotRegisters.WRITE_COUNT],
            null,
            evidence
        );
    }

    /**
     * 按演示点表的统一比例将原始寄存器值换成业务测量值。
     *
     * @param registers 从地址 0 开始读取的完整原始寄存器数组。
     * @param address 当前测量项的零基地址，在本数组中也对应下标。
     * @return 原始整数值除以 MEASUREMENT_SCALE 后的数值。
     */
    private double scaled(int[] registers, int address) {
        return registers[address] / RobotRegisters.MEASUREMENT_SCALE;
    }

    /**
     * 将命令类型映射为模拟器控制值并执行 FC06，无返回值。
     *
     * @param type START 写启动值，STOP 写停止值。
     * @throws IOException 控制写入或回显校验失败时抛出；执行确认由上层读回完成。
     */
    @Override
    public void writeControl(CommandType type) throws IOException {
        int control =
            type == CommandType.START ? RobotRegisters.START_CONTROL : RobotRegisters.STOP_CONTROL;
        client.writeSingle(RobotRegisters.CONTROL, control);
    }

    /**
     * 关闭内部 Modbus 客户端，无参数、无返回值。
     */
    @Override
    public void close() {
        client.close();
    }
}
