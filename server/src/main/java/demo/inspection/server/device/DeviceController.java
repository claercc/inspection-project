package demo.inspection.server.device;

import demo.inspection.server.integration.ConsoleService;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class DeviceController {
    private final ConsoleService console;
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
    public DeviceController(ConsoleService console) { this.console = console; }
    @GetMapping("/state") public ConsoleService.State state() { return console.state(); }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP", "mode", "SIMULATION", "stage", "A"); }
    public record CommandRequest(String type) {}
    @PostMapping("/commands")
    public ConsoleService.CommandView command(@RequestBody CommandRequest request) {
        return console.issue(request.type());
    }
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        var emitter = new SseEmitter(0L); clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter)); emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter)); send(emitter, console.state()); return emitter;
    }
    @Scheduled(fixedDelay = 500) public void broadcast() {
        if (clients.isEmpty()) return;
        var state = console.state(); clients.forEach(c -> send(c, state));
    }
    private void send(SseEmitter client, ConsoleService.State state) {
        try { client.send(SseEmitter.event().name("state").data(state)); }
        catch (Exception e) { clients.remove(client); client.complete(); }
    }
}
