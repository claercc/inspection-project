package demo.inspection.server.device.infrastructure;

import demo.inspection.server.integration.ConsoleService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 管理浏览器 SSE 订阅、状态广播和断线清理；不参与设备命令执行。 */
@Component
public class DeviceEventStream {

    private final ConsoleService console;
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();

    /**
     * 注入状态查询服务，供 SSE 初始推送和周期广播使用。
     *
     * @param console 提供当前状态快照的服务。
     */
    public DeviceEventStream(ConsoleService console) {
        this.console = console;
    }

    /**
     * 建立一个浏览器订阅并注册断线清理，无参数，供 GET /api/events 调用。
     *
     * @return 应用层不设置超时的新 SSE emitter，已尝试发送当前状态。
     */
    public SseEmitter subscribe() {
        // 此处不设置应用层超时；完成、超时和错误回调均需移除订阅，避免遗留失效连接。
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(exception -> clients.remove(emitter));
        // 建立订阅后立即发送当前快照，页面无需等到下一轮广播才能显示状态。
        send(emitter, console.state());
        return emitter;
    }

    /**
     * 由 Spring 定时向当前订阅者广播同一份状态快照，无参数、无返回值。
     */
    @Scheduled(fixedDelay = 500)
    public void broadcast() {
        if (clients.isEmpty()) {
            return;
        }
        ConsoleService.State state = console.state();
        clients.forEach(client -> send(client, state));
    }

    /**
     * 向单个订阅写出名为 state 的事件，无返回值；写入失败时移除并结束该订阅。
     *
     * @param client 当前 SSE 订阅对象。
     * @param state 要序列化并发送的设备状态快照。
     */
    private void send(SseEmitter client, ConsoleService.State state) {
        try {
            client.send(SseEmitter.event().name("state").data(state));
        } catch (Exception exception) {
            clients.remove(client);
            client.complete();
        }
    }
}
