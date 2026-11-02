package demo.inspection.server.device;

import demo.inspection.server.device.domain.CommandHistory.CommandView;
import demo.inspection.server.device.domain.CommandRejectedException;
import demo.inspection.server.device.infrastructure.DeviceEventStream;
import demo.inspection.server.integration.ConsoleService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 对外暴露状态查询、控制提交和 SSE 订阅，业务准入判断交给 ConsoleService。 */
@RestController
@RequestMapping("/api")
public class DeviceController {

    private final ConsoleService console;
    private final DeviceEventStream events;

    public DeviceController(ConsoleService console, DeviceEventStream events) {
        this.console = console;
        this.events = events;
    }

    @GetMapping("/state")
    public ConsoleService.State state() {
        return console.state();
    }

    // 只表示 HTTP 服务存活；设备和 MQTT 是否就绪应查看 /state 中的连接状态。
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "mode", "SIMULATION", "stage", "A");
    }

    public record CommandRequest(String type) {}

    @PostMapping("/commands")
    public CommandView command(@RequestBody CommandRequest request) {
        // HTTP 成功仅代表返回了操作记录，执行结果应通过相同 commandId 的后续状态确认。
        return console.issue(request.type());
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.subscribe();
    }

    @ExceptionHandler(CommandRejectedException.class)
    public ProblemDetail rejectedCommand(CommandRejectedException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }
}
