# 阶段 A 验证记录

验证日期：2026-10-11（Asia/Singapore）。已实际运行本地模拟链路。

## 环境

Windows；Microsoft OpenJDK 17.0.20.1+1（便携包，官方 SHA-256 校验通过）；Maven 3.9.11（官方 SHA-512 校验通过）；Node.js 24.19.0；npm 11.17.0；本机 Chrome headless；Aedes 0.51.3 MQTT 3.1.1 TCP Broker。

## 构建与协议

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

最终干净构建执行 `mvn -B -ntp clean verify`，5 个模块全部 SUCCESS，生成 server/edge/robot 可执行 jar；Vue/Vite 生产构建通过。5 个真实 TCP 集成测试全部通过，0 failures / errors / skipped：

- FC03 状态读取、FC06 START/STOP、实际位置变化与停止、异常码 02/03。
- 模拟器接收逐字节拆分请求与一条 Socket 上的连续报文。
- 客户端接收拆分响应。
- 错误 transaction ID / protocol ID / Unit ID / MBAP length / function / byte count / 截断 PDU 被拒绝。
- FC06 回显不匹配与响应超时被拒绝。

JUnit 原始结果在 `simulator/robot/target/surefire-reports/`。初次构建的 Shade 父配置合并问题已修正，最终 clean verify 通过。

## 端到端浏览器验证

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

隔离启动 5 个实际进程：Broker、robot、edge、Spring Boot、Vue 生产预览。测试从真实浏览器按钮发起操作，MQTT 观察者检查结果消息，不替换通信端点或伪造设备反馈。

**12 项检查全部 PASS：**

| 检查 | 实际结果 |
| --- | --- |
| 浏览器 START 全链路 | HTTP → MQTT → FC06/FC03 → MQTT → SSE → Vue；位置移动，SUCCEEDED |
| 单活动运动 | 运行中再次 START 返回 409 |
| MQTT 同命令重复投递 | 第二次结果返回，寄存器写次数仍为 1 |
| 浏览器 STOP | 设备反馈确认，位置停止变化 |
| 非法/过期命令 | FAILED，无寄存器写入 |
| commandId 复用不同 payload | FAILED，无寄存器写入 |
| 停止独立 robot 进程 | ROBOT_OFFLINE；界面隐藏测量值；STOP 在写前失败 |
| robot 恢复 | FC03 重连，STOPPED，写次数 0 |
| edge 停止 | EDGE_STALE；START 拒绝；STOP 8 秒无结果 → RESULT_UNKNOWN |
| edge 重启 | 无旧命令重放；订阅时收到的 retained 控制被忽略；写次数 0 |
| Broker 停止/恢复 | 离线 503；重订阅后恢复采集；写次数不增加 |
| 浏览器界面 | 桌面/390px 手机截图、无 JS pageerror、无整页横向溢出 |

最终 clean 构建及手机表格可读性调整后再次执行端到端验证，12 项仍全部通过。

机器可读报告：[stage-a-report.json](verification/stage-a-report.json)。截图：[桌面](verification/stage-a-desktop.png)、[手机](verification/stage-a-mobile.png)。原始日志 `.run/verification/`。测试完成后关闭测试进程，报告保留。

## 日常脚本

`scripts/start.ps1 -SkipBuild` 已实际启动标准端口的演示栈，`/api/state` 返回 ONLINE；通过 Web 代理 POST START/STOP 后，两条命令均获设备读回确认。`scripts/stop.ps1` 已实际关闭 5 个受管理进程，并确认 Web 端口关闭。随后重启演示栈供查看。

## Compose 与尚未验证项

本机 Compose v5.3.1 的 `config --quiet` 通过。Docker CLI 未发现运行中的 Docker 引擎，所以 **未执行 Compose 镜像构建与容器链路验收**；本地 Aedes 通过不等于 Mosquitto 容器已测试。恢复 Docker 引擎后按 README 执行 `docker compose up --build -d` 和启动/停止/离线验收。

未执行真实硬件、摄像机 SDK、公网部署、MySQL/Redis、三点巡检、截图、告警与视频验证；这些能力没有在阶段 A 标为完成。
