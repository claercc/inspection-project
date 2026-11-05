package demo.inspection.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.eclipse.paho.client.mqttv3.MqttException;

/** 为界面反馈和运行诊断提供中文异常说明，保留用于排查的协议错误码。 */
public final class ErrorMessages {

    private ErrorMessages() {}

    /**
     * 将已知通信异常转换为中文；工程主动抛出的中文说明直接保留。
     *
     * @param error 当前捕获的异常；没有异常对象时可传 null。
     * @return 中文错误说明，未知异常附带类型名，MQTT 异常附带原因码。
     */
    public static String describe(Throwable error) {
        if (error == null) {
            return "未提供错误详情";
        }
        if (error instanceof MqttException mqttError) {
            return "MQTT 通信失败（原因码：" + mqttError.getReasonCode() + "）";
        }
        if (error instanceof JsonProcessingException) {
            return "JSON 消息格式不正确，请核对字段和数据类型";
        }
        String message = error.getMessage();
        if (
            message != null &&
            message
                .codePoints()
                .anyMatch(
                    character ->
                        Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN
                )
        ) {
            return message;
        }
        if (error instanceof SocketTimeoutException) {
            return "连接或响应超时，请检查通信链路";
        }
        if (error instanceof ConnectException) {
            return "连接被拒绝，请确认目标服务已启动且地址端口正确";
        }
        if (error instanceof UnknownHostException) {
            return "无法解析主机地址，请检查设备或消息服务地址";
        }
        if (error instanceof NoRouteToHostException) {
            return "目标主机不可达，请检查网络配置";
        }
        if (error instanceof EOFException) {
            return "连接已关闭，未收到完整响应";
        }
        if (error instanceof SocketException) {
            return "网络连接中断，请检查设备与通信链路";
        }
        if (error instanceof IOException) {
            return "通信读写失败，请检查设备连接";
        }
        if (error instanceof IllegalArgumentException) {
            return "参数无效，请检查命令内容";
        }
        return "内部处理失败，请查看运行日志（" + error.getClass().getSimpleName() + "）";
    }
}
