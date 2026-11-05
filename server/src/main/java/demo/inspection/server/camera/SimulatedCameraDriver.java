package demo.inspection.server.camera;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** 生成带时间戳和移动标记的测试画面；不代表海康实物画质或 SDK 已接通。 */
@Component
public class SimulatedCameraDriver implements CameraDriver {

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public byte[] capture() throws IOException {
        BufferedImage image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(16, 29, 43));
            g.fillRect(0, 0, 960, 540);
            g.setColor(new Color(34, 55, 71));
            for (int x = 0; x < 960; x += 60) {
                g.drawLine(x, 0, x, 540);
            }
            for (int y = 0; y < 540; y += 60) {
                g.drawLine(0, y, 960, y);
            }
            for (int i = 0; i < 4; i++) {
                int x = 100 + i * 195;
                g.setColor(new Color(48, 70, 85));
                g.fillRoundRect(x, 150, 150, 260, 10, 10);
                g.setColor(new Color(90, 219, 181));
                g.fillOval(x + 20, 175, 12, 12);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
                g.drawString("CABINET " + (i + 1), x + 16, 230);
                for (int row = 0; row < 5; row++) {
                    g.drawLine(x + 20, 275 + row * 20, x + 130, 275 + row * 20);
                }
            }
            g.setColor(new Color(90, 219, 181));
            int marker = (int) ((System.currentTimeMillis() / 20) % 850);
            g.fillRoundRect(40 + marker, 450, 30, 8, 4, 4);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
            g.drawString("SIMULATED CAMERA / NOT LIVE HARDWARE", 35, 55);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
            g.drawString(Instant.now().toString(), 35, 505);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", output)) {
            throw new IOException("无法编码摄像头图像");
        }
        return output.toByteArray();
    }
}
