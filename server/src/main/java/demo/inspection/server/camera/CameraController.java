package demo.inspection.server.camera;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 摄像头接口；预览按帧获取，抓拍返回新的 JPEG，不提供录像或持久化。 */
@RestController
@RequestMapping("/api/camera")
public class CameraController {

    private final CameraService camera;

    /** @param camera 摄像头业务服务。 */
    public CameraController(CameraService camera) {
        this.camera = camera;
    }

    /** @return 能力和模拟标识；不将目标型号宣称为已连接设备。 */
    @GetMapping
    public Map<String, Object> info() {
        return Map.of("simulated", camera.simulated(), "previewMode", "SNAPSHOT", "capture", true);
    }

    /** @return 当前预览帧。 @throws IOException 图像读取失败。 */
    @GetMapping(value = "/preview", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> preview() throws IOException {
        return picture();
    }

    /** @return 本次抓拍图像。 @throws IOException 图像读取失败。 */
    @PostMapping(value = "/captures", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> capture() throws IOException {
        return picture();
    }

    private ResponseEntity<byte[]> picture() throws IOException {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_JPEG)
            .body(camera.capture());
    }

    /** @return 可展示的中文失败原因；不泄漏驱动内部地址或凭据。 */
    @ExceptionHandler(IOException.class)
    public ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            "摄像头图像获取失败，请重试。"
        );
    }
}
