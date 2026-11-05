# 阶段 A 验证记录

## 2026-11-05：模拟摄像头预览与抓拍

- 新增摄像头页面、模拟图像驱动、每秒预览帧、手动抓拍和 JPEG 下载；页面与图像均明确标注模拟来源。
- 不使用数据库或服务端文件归档；抓拍只保留在当前浏览器会话，刷新后清空。
- 完整构建通过：24 个 Java 测试、Checkstyle、Prettier、ESLint 和 Vue 生产构建通过。
- `VERIFICATION_PREFIX=simulated-camera-step3` 的 24 项端到端检查全部通过，包含摄像头预览、抓拍、下载和原有设备通信故障恢复检查。
- 证据：[回归报告](verification/simulated-camera-step3-report.json)、[摄像头页面](verification/simulated-camera-step3-current.png)、[桌面](verification/simulated-camera-step3-desktop.png)、[手机](verification/simulated-camera-step3-mobile.png)。海康 DS-2CD3T25-I3 尚未实物验收。

## 2026-11-04：中文提示与日志回归

- 工程自定义的接口错误、命令反馈、通信诊断、页面说明和脚本提示改为中文；命令值、状态码、JSON 字段、MQTT 主题与协议名称保持原约定。
- Java 全模块构建与 24 个测试通过；Checkstyle、Prettier、ESLint 检查通过。
- 以 `VERIFICATION_PREFIX=chinese-messages-20261104` 运行独立进程验证：23 项检查通过。新增非法命令类型与无效 JSON 的中文响应检查，并核对消息服务离线时的中文原因，以及成功反馈和诊断摘要。
- 单独验证前端断网、非 JSON 响应和脚本连接被拒绝时的中文提示。桌面与手机截图已检查，无整页横向溢出或未捕获脚本错误。
- Java 启动与 Maven 测试统一使用 UTF-8；PowerShell 脚本使用 UTF-8 BOM，兼容 Windows PowerShell 读取中文消息。未改写外部工具自带的输出或历史验证记录。
- 证据：[回归报告](verification/chinese-messages-20261104-report.json)、[桌面](verification/chinese-messages-20261104-desktop.png)、[手机](verification/chinese-messages-20261104-mobile.png)。本轮仍为本地模拟验证，未验收 Docker 容器运行或真实硬件。

## 2026-11-02：代码整理与控制边界回归

- Java 全模块 `mvn verify` 成功，Checkstyle 零违规，24 个测试通过（原 TCP 协议 5 个、edge 命令处理 7 个、server 规则 12 个）。
- 根目录 `npm run check` 通过：Prettier 格式检查与 JavaScript/Vue ESLint 检查，无警告；Vue 生产构建通过。
- 使用 `VERIFICATION_PREFIX=readability-20261102` 运行 `scripts/verify.ps1`：22 项端到端检查全部 PASS，包含已有 20 项与新增的待确认 START 后发 STOP、迟到 MQTT 采样拒绝。
- 实际浏览器验证了启停、指令详情、温度来源、SSE 生命周期、设备/edge/Broker 离线恢复；桌面和 390px 手机截图已检查，无整页横向溢出或未捕获 JavaScript 错误。
- 新增单元测试覆盖设备写入成功后 FC03 返回 Modbus 异常时保持 RESULT_UNKNOWN、重复命令不再写入、PUBACK 迟于结果、终态保护及重复采样不刷新存活时间。
- 本轮为独立本地进程验证；没有执行 Docker 容器运行或真实硬件验收。
- 证据：[回归报告](verification/readability-20261102-report.json)、[桌面](verification/readability-20261102-desktop.png)、[手机](verification/readability-20261102-mobile.png)。代码入口见 [阅读指南](CODE_GUIDE.md)。

## 2026-10-16：温度来源与独立核对

- 执行 `scripts/build.ps1`：Java 17 clean verify 全模块成功，5 个真实 TCP 测试通过，Vue/Vite 生产构建通过；未新增依赖。
- 运行命令：`$env:VERIFICATION_PREFIX='temperature-source'; powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`。
- 20 项端到端检查全部 PASS，保留原 15 项检查，新增以下 5 项：

| 检查 | 实际结果 |
| --- | --- |
| 原始寄存器来源 | 独立 Node TCP Socket 发 FC03 读地址 4，与 edge 原值、换算温度及独立 MQTT 记录一致 |
| 来源抽屉与独立消息 | 同 sampleId、同实际 topic 的完整 telemetry 深度比较相等；快照保持不变，显式刷新切换编号 |
| 模拟器离线 | evidence=null；抽屉不显示伪造零寄存器或有效温度 |
| edge 过期 | 当前实时不可用提示可见；旧样本可查，刷新按钮禁用 |
| 手机端来源抽屉 | 390px 宽度无横向溢出，可用 Escape 关闭 |

- 桌面与手机截图已检查，无未捕获 JS 错误。隔离栈使用独立端口，结束后清理；标准本地演示用新产物重启为 ONLINE，独立观察脚本 `--count 1 --seconds 10` 成功收到真实 MQTT 消息。
- 证据：[报告](verification/temperature-source-report.json)、[匹配样本](verification/temperature-source-matched-sample.json)、[桌面来源抽屉](verification/temperature-source-source-desktop.png)、[手机来源抽屉](verification/temperature-source-source-mobile.png)、[完整桌面](verification/temperature-source-desktop.png)、[完整手机](verification/temperature-source-mobile.png)。历史报告保留。
- 测试使用软件模拟设备，验证数据对应与通信行为，不表示已验证物理传感器、硬件身份、数据库历史或公网部署。

## 2026-10-15：前端优先单元回归

- Vue/Vite 生产构建通过；未改变依赖、后端 Java 或 Modbus 点表。
- `prepare-structure.ps1` 创建 62 个规划目录；未实现模块只保留空目录。
- 运行命令：`$env:VERIFICATION_PREFIX='frontend-workspace'; powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`。
- 15 项端到端检查通过：原 12 项链路/故障检查，加规划入口与路线弹窗、真实指令详情、连续三次 SSE 重连的生命周期检查。
- SSE 重连验证旧 EventSource 被关闭，活动订阅数不增长，设备写次数不增加。
- 桌面和手机截图、无未捕获 JS 错误、手机无整页横向溢出。测试结束清理隔离栈，不关闭原有本地演示服务。
- 本次独立报告：[frontend-workspace-report.json](verification/frontend-workspace-report.json)，截图：[桌面](verification/frontend-workspace-desktop.png)、[手机](verification/frontend-workspace-mobile.png)。旧 stage-a 文件保留。
- 未验收后端新业务、完整分层迁移、容器运行或公网发布。

以下保留 2026-10-11 阶段 A 的历史验收记录。

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
