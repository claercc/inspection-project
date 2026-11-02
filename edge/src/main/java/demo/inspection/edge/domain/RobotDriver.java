package demo.inspection.edge.domain;

import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.Telemetry;
import java.io.IOException;

/** 命令用例所需的设备操作接口；具体通信和点表转换由驱动实现。 */
public interface RobotDriver extends AutoCloseable {
    Telemetry readState() throws IOException;

    /** 完成一次控制写入；上层仍须 readState 读回，才能确认设备状态与意图一致。 */
    void writeControl(CommandType type) throws IOException;

    @Override
    void close();
}
