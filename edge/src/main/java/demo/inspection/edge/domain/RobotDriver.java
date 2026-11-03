package demo.inspection.edge.domain;

import demo.inspection.protocol.CommandType;
import demo.inspection.protocol.Messages.Telemetry;
import java.io.IOException;

/** 命令用例所需的设备操作接口；具体通信和点表转换由驱动实现。 */
public interface RobotDriver extends AutoCloseable {
    /**
     * 读取一份设备状态，供轮询和控制后的反馈核对使用，无参数。
     *
     * @return 本次成功读取的遥测状态；调用方负责处理读取失败。
     * @throws IOException 设备不可达、协议响应异常或读取失败时抛出。
     */
    Telemetry readState() throws IOException;

    /**
     * 写入一次控制意图，无返回值；成功返回后仍需 readState 读回确认。
     *
     * @param type 待执行的 START 或 STOP。
     * @throws IOException 通信失败或设备拒绝写入时抛出，调用方需区分明确拒绝和结果未知。
     */
    void writeControl(CommandType type) throws IOException;

    /**
     * 释放驱动占用的设备连接，无参数、无返回值；由 edge 关闭流程调用。
     */
    @Override
    void close();
}
