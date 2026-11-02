# API 与阶段 A 演示

浏览器统一通过 Web `/api` 路径访问 server，避免跨域。状态经 SSE 回传，不需要浏览器轮询。

| 方法 / 路径 | 用途 | 响应 |
| --- | --- | --- |
| GET /api/health | server 存活与模式 | status=UP, mode=SIMULATION, stage=A |
| GET /api/state | 当前状态快照 | 在线/过期、最新遥测、命令、诊断 |
| GET /api/events | SSE 实时推送 | event: state，每 500ms 推送状态快照 |
| POST /api/commands | 启动 / 停止 | body: `{ "type": "START" }` 或 STOP |

POST 返回操作记录，不承诺设备已执行。必须观察后续 SSE 中同一 commandId 的结果。Broker 不可用返回 503；无新鲜状态时 START 返回 409；已有待确认命令或机器人正在运行时再次 START 返回 409；非法类型返回 400。离线时仍可发 STOP，由 edge 返回具体失败或由 server 超时显示结果未知。

2026-11-02：START 尚在等待反馈时，界面与服务端仍允许发 STOP；已有等待反馈的 STOP 时再次 STOP 返回 409。命令按接受顺序发布。

```powershell
Invoke-RestMethod http://127.0.0.1:8088/api/state
Invoke-RestMethod http://127.0.0.1:8088/api/commands -Method Post -ContentType application/json -Body '{"type":"START"}'
curl.exe --no-buffer http://127.0.0.1:8088/api/events
```

## 演示顺序

1. 启动后观察「链路在线」与「已停止」，确认来源为 Modbus TCP 模拟器。
2. 点击启动。讲解 commandId 贯穿 HTTP → MQTT → edge，edge 先读、再写控制字，最后读回确认。
3. 观察位置、电量、温度更新；它们来自独立模拟器寄存器。MQTT QoS1 不等于执行成功。
4. 点击停止，观察「设备反馈已确认」和位置停止变化。
5. Compose 中 `docker compose stop robot`。界面显示机器人离线、实时数值不可用；恢复后重新读回状态。
6. 运行 `scripts/verify.ps1`，查看重复命令/离线/超时/重连结果和浏览器截图。

本地启动脚本的进程 PID 在 `.run/processes.json`，日志在 `.run/<role>.out.log` 和 `.err.log`。正常关闭使用 `scripts/stop.ps1`。不要把阶段 A 的 START 描述为已完成「三点巡检任务」；编排、截图与归档在阶段 B 实施。
