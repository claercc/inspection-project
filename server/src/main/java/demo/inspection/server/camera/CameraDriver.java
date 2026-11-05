package demo.inspection.server.camera;

import java.io.IOException;

/** 单台摄像头的图像入口；实物接入时替换实现，页面和抓拍接口继续复用。 */
public interface CameraDriver {
    /** @return 是否为软件模拟画面。 */
    boolean simulated();

    /**
     * 获取当前 JPEG 帧，不保存到磁盘。
     * @return 可直接预览或下载的 JPEG 字节。
     * @throws IOException 读取或编码失败。
     */
    byte[] capture() throws IOException;
}
