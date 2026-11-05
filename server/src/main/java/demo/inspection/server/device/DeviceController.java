package demo.inspection.server.device;

import demo.inspection.server.device.domain.CommandHistory.CommandView;
import demo.inspection.server.device.domain.CommandRejectedException;
import demo.inspection.server.device.infrastructure.DeviceEventStream;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 对外暴露状态查询、控制提交和 SSE 订阅，业务准入判断交给 DeviceService。 */
@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DeviceService console;
    private final DeviceEventStream events;

    /**
     * 注入 HTTP 入口需要的应用服务和推送适配器，由 Spring 创建。
     *
     * @param console 负责查询和控制提交的应用服务。
     * @param events 负责 SSE 订阅与广播的适配器。
     */
    public DeviceController(DeviceService console, DeviceEventStream events) {
        this.console = console;
        this.events = events;
    }

    /**
     * 处理 GET /api/state，无参数，供页面和诊断工具查询。
     *
     * @return 当前设备与命令历史的完整状态快照。
     */
    @GetMapping("/state")
    public DeviceService.State state() {
        return console.state();
    }

    // 只表示 HTTP 服务存活；设备和 MQTT 是否就绪应查看 /state 中的连接状态。
    /**
     * 处理 GET /api/health，无参数；仅说明 HTTP 服务存活，不表示设备在线。
     *
     * @return 包含 status=UP、mode=SIMULATION 和 stage=A 的映射。
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "mode", "SIMULATION", "stage", "A");
    }

    public record CommandRequest(String type) {}

    /**
     * 处理 POST /api/commands，返回操作记录；最终执行结果继续通过 SSE 观察。
     *
     * @param request 请求体，type 必须为 START 或 STOP。
     * @return 本次提交对应的服务端命令记录。
     * @throws CommandRejectedException 业务准入拒绝时抛出，并由本控制器转换为问题详情响应。
     */
    @PostMapping("/commands")
    public CommandView command(@RequestBody CommandRequest request) {
        // HTTP 成功仅代表返回了操作记录，执行结果应通过相同 commandId 的后续状态确认。
        return console.issue(request.type());
    }

    /**
     * 处理 GET /api/events，无参数；建立订阅后立即推送一次快照。
     *
     * @return 由 Spring 持有的 SSE 连接，后续广播 state 事件。
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.subscribe();
    }

    /**
     * 将业务拒绝转成标准 HTTP 问题详情，由 Spring 异常处理流程调用。
     *
     * @param exception 准入规则抛出的业务异常。
     * @return 带 400、409 或 503 状态码及原因说明的 ProblemDetail。
     */
    @ExceptionHandler(CommandRejectedException.class)
    public ProblemDetail rejectedCommand(CommandRejectedException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle("控制请求未被接受");
        return problem;
    }

    /**
     * 将无法解析的控制请求体转换为中文提示，由 Spring 异常处理流程调用，无参数。
     * @return 状态码为 400 的问题详情。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail invalidRequestBody() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "请求内容无法解析，请提供有效的 JSON，并使用 type 字段指定启动或停止。"
        );
        problem.setTitle("请求内容格式错误");
        return problem;
    }
}
