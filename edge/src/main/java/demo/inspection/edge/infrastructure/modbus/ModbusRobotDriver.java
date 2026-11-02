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

    public ModbusRobotDriver(String deviceId, String host, int port, int unit) {
        this.deviceId = deviceId;
        this.host = host;
        this.port = port;
        this.unit = unit;
        client = new ModbusClient(host, port, unit, SOCKET_TIMEOUT_MS);
    }

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

    private double scaled(int[] registers, int address) {
        return registers[address] / RobotRegisters.MEASUREMENT_SCALE;
    }

    @Override
    public void writeControl(CommandType type) throws IOException {
        int control =
            type == CommandType.START ? RobotRegisters.START_CONTROL : RobotRegisters.STOP_CONTROL;
        client.writeSingle(RobotRegisters.CONTROL, control);
    }

    @Override
    public void close() {
        client.close();
    }
}
