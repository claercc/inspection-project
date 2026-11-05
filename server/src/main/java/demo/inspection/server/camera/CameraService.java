package demo.inspection.server.camera;

import java.io.IOException;
import org.springframework.stereotype.Service;

/** 摄像头预览和抓拍共用的业务入口。 */
@Service
public class CameraService {

    private final CameraDriver driver;

    /** @param driver 当前装配的图像提供者。 */
    public CameraService(CameraDriver driver) {
        this.driver = driver;
    }

    /** @return 画面来源是否为软件模拟。 */
    public boolean simulated() {
        return driver.simulated();
    }

    /**
     * @return 当前 JPEG 图像，不写入文件或数据库。
     * @throws IOException 摄像头读取失败。
     */
    public byte[] capture() throws IOException {
        return driver.capture();
    }
}
