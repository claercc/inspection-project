# 开发进度与接续上下文

更新时间：2026-11-02（Asia/Singapore）

## 最新交付：代码可读性与质量检查

- 展开 Java、Vue、JavaScript、CSS 和 Maven 配置；统一 Prettier/EditorConfig 与换行规则，配置编辑器保存格式化。
- 监控首页按实际面板拆分组件，状态文案与显示工具独立；HTTP/SSE 会话仍由原 API/composable 管理。
- server 提取 CommandPolicy、CommandHistory、DeviceTelemetry 与 DeviceEventStream；edge 提取 DeviceCommandHandler、RobotDriver 与 ModbusRobotDriver；模拟器点表使用具名常量。
- 修复待确认 START 阻挡 STOP、迟到遥测覆盖较新状态、写入成功后读回异常被判为确定失败的问题。
- 新增 ESLint 与 Maven Checkstyle，构建脚本执行检查；24 个 Java 测试、22 项独立进程端到端检查、Vue 生产构建均通过。
- 阅读入口与命令见 [CODE_GUIDE.md](CODE_GUIDE.md)，验证证据见 [VERIFICATION.md](VERIFICATION.md)。以下保留此前学习单元与长期规划记录；未自动进入后续业务阶段。

## 2026-10-16 交付：温度来源核对（学习单元 3）

- 用户认为画面无法说明数据真假，确认先实现温度来源抽屉与独立核对。本轮只推进这个单元。
- 温度卡片改为「模拟设备温度」；抽屉展示实际 FC03 寄存器、原值 × 0.1、sampleId、采集端、连接地址、时间、实际 MQTT 主题和转发后的遥测 JSON。
- edge 在成功读取时附加 SamplingEvidence；失败时 evidence=null。server 将实际接收 topic 与样本一同保存，可能是 telemetry 或 results 主题。前端锁定样本供核对，同时保留实时可用性提示。
- 新增独立脚本 `scripts/observe-telemetry.mjs`，直接订阅 MQTT，支持 JSONL 输出；不访问 HTTP/SSE，不发布设备控制。
- 已执行 Java clean verify（5 个 TCP 测试通过）、Vue 生产构建、20 项端到端检查。新报告为 `temperature-source-report.json`；保留旧报告。标准演示服务已用新产物重启，http://127.0.0.1:8088 为 ONLINE；独立脚本单次订阅验证通过。
- 阅读与操作见 [LEARNING_PROGRESS.md](LEARNING_PROGRESS.md)、[DATA_PROVENANCE.md](DATA_PROVENANCE.md)。完成后停止，等待用户反馈。
- 仍未实现：场景化温度测点模型、数据库历史、硬件认证、任务、摄像机和公网发布；本轮没有迁移后端分层。

## 当前协作方式：一次一个学习单元

- 新版 `codex-inspection-project-prompt.md` 替代上一轮自动推进阶段的要求。
- 2026-10-15 用户改为前端视觉/按钮优先：本轮完成目录骨架与监控首页，后续逐按钮向后端接入，未实现业务只建文件夹。
- 单元 2 前端已分开布局、视图、API、composable、配置与样式；后端预留分层目录。单元 3 仅补采样来源契约和实际 MQTT 主题，不改变控制语义。
- 目录清单与按钮顺序见 FRONTEND_FIRST_PLAN.md；prepare-structure.ps1 可恢复 62 个规划目录。
- 单元 2 端到端回归 15 项通过；单元 3 增加来源与独立核对后为 20 项。原阶段 A 和 frontend-workspace 报告与截图保留。
- 学习进度与功能进度分开：代码已存在、测试通过，不意味着用户已确认理解。

## 当前阶段 A：本地闭环已交付，容器运行待验收

- 工作区初始只有 `codex-inspection-project-prompt.md`，没有 Git 仓库、代码、AGENTS.md，也没有之前的 Modbus 教学文件。
- 已读取完整需求。当前只交付启动/停止与状态回传，不把阶段 B/C 的能力标为已完成。
- 初始环境有 Java 26、Node 24.19.0，缺 Maven；后来已补齐下述便携 Java 17/Maven。Docker 客户端可用，上一轮未发现运行中的引擎；本轮不启动或重新验收容器。
- 采用 Java 17、Spring Boot 3.5.16、Vue 3、MQTT 3.1.1、FC03/FC06 自定义演示点表。
- 工程结构：common / server / edge / simulator/robot / web / deploy / scripts / docs。
- 本机验证使用独立进程和 Aedes TCP Broker；Compose 使用 Mosquitto，需 Docker 引擎。
- 已完成零基点表、MBAP 校验、FC03/FC06、独立机器人运动模拟、MQTT 命令/遥测/结果主题、SSE 与 Vue 控制台。
- 已完成发送/设备确认/失败/结果未知区分、8 秒命令超时、进程内 commandId 去重、过期拒绝、断线数据过期和重连不重放控制。
- 已交付 portable 工具引导、干净构建、五进程启动/关闭、隔离浏览器验证脚本、Compose 与 Nginx SSE 代理。
- 本机 Java 17.0.20.1+1 与 Maven 3.9.11 已下载到 `.tools/` 并通过官方校验和校验，没有全局安装或改写系统环境。

## 已执行验证

- `powershell -ExecutionPolicy Bypass -File scripts/build.ps1`：最终 Java 17 clean verify，全部模块 SUCCESS，5 个真实 TCP 测试全部通过；Vue 生产构建通过。
- `$env:VERIFICATION_PREFIX='temperature-source'; powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`：最新 20 项实际端到端检查全部 PASS，含真实 Chrome 按钮、来源样本独立核对、SSE、重复投递、离线/恢复、结果未知与 Broker 重连。
- `scripts/start.ps1 -SkipBuild`：标准演示栈 ONLINE，START/STOP 均经 FC03 确认。
- `scripts/stop.ps1`：5 个进程关闭，Web 端口关闭；随后再次启动演示栈。
- Compose `config --quiet`：通过；容器运行未验证，原因是本机 Docker 引擎未运行。
- 详细命令、结果、原始 JSON 与截图见 `docs/VERIFICATION.md` 和 `docs/verification/`。

## 待验证

- Docker 引擎可用后执行 Compose 镜像构建、Mosquitto 容器闭环和离线恢复验收。
- 当前控制台命令/诊断为内存数据，重启 server 会清空；MySQL/Redis 在 B 接入。
- 尚未取得公网环境与摄像机条件；继续模拟模式，不把它们作为 B 的前置条件。

## 启动与接续

```powershell
# 此工作区依赖和产物已准备好
powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -SkipBuild
# 若提示 already running，直接访问 http://127.0.0.1:8088
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
powershell -ExecutionPolicy Bypass -File scripts/stop.ps1
```

阶段 A 本地固定端口：1883 / 1502 / 8080 / 8088。测试固定端口：18883 / 11502 / 18080 / 18088。`.run/` 存进程身份和日志，`.tools/` 存工具/依赖缓存；两者忽略提交。npm 锁文件已生成并保留。交接提示词保持原样；当前已存在 Git 仓库，本轮未初始化、提交或推送。

下一次先读用户最新要求、LEARNING_PROGRESS.md 与 DATA_PROVENANCE.md。等待温度来源单元的阅读反馈，再决定完善场景模型或进入「新建巡检任务」表单；不自动进入 B。

## 临时实现与分层待办（尚未批量迁移）

| 当前实现 | 缺口 | 处理时机 |
| --- | --- | --- |
| ConsoleService 协调 MQTT、命令用例与调度 | 纯规则已提取到 device/domain；仍需按新增用例收窄协调服务 | 后续业务单元 |
| DeviceEventStream 管理 SSE 集合与广播调度 | 已从 Controller 提取；尚未设计大量客户端场景 | 有实际规模需求时处理 |
| 前端已拆为 layout / view / api / composable / config / style；温度来源有独立组件 | 其余监控面板暂在一个视图中，路由/stores 为空目录 | 后续按真实复用需求逐个提取 |
| EdgeApplication 装配并调度命令处理与设备驱动 | 去重/执行与寄存器转换已提取 | 新驱动按 RobotDriver 接入 |
| common 含消息契约和具体通信实现 | server/edge/robot 都引入 Paho/JSON；HTTP 复用 MQTT record | 在 DTO / 驱动单元按需要收窄依赖 |
| 命令判断使用 CommandType / CommandStatus | HTTP/MQTT 仍保持原字符串契约，巡检任务未实现 | 后续任务状态按实际业务定义 |
| 内存记录；最新 telemetry 拒绝迟到/重复覆盖 | 重启仍丢失；时间顺序规则已有专项回归 | 持久化单元再设计存储 |
| 环境变量、文本日志、简单 health | 缺集中配置校验、结构化关联日志与依赖就绪判定 | 单独运维单元 |
| 当前仅 loopback；无权限与持久化审计 | 不满足公网操作入口要求 | 公网前按小单元实现 |

最新状态未来由 Redis 保存可重建快照；业务事实由 MySQL 保存，迁移/事务/唯一键尚未实施。文件持久卷、厂商能力/驱动注册、真实 SDK 均未实施。已有真网络链路、反馈确认与去重应保留。

## 后续阶段

以下仅表示长期功能里程碑，不是自动连续执行的任务；每项按学习单元拆分，等待用户确认后再进入下一小步。

- B：独立 HTTP 摄像机模拟器、动态截图、三点任务、MySQL 业务历史、Redis 最新状态、告警、文件与幂等事件。
- C：独立媒体 profile、RTSP/HLS、只读公网入口、管理员鉴权与部署。
- D：仅获得硬件/授权后开展真实设备接入。

## 粗略剩余投入估计

估计用于分批安排开发、验证和讲解，不是工期承诺，每个闭环后修正：A 的本地闭环已完成，容器验收约 1–3 工时（引擎可用后）；B 的截图/存储/三点任务/告警/故障验证约 20–35 工时；视频增强约 8–16 工时；公网鉴权、只读入口、TLS 与部署约 4–8 工时（环境就绪后）；可选硬件先用 2–4 工时核对 SDK/授权/平台，再按具体设备估计接入，不纳入无硬件交付前提。

所有设备、测量值、控制点表均为演示模拟，不代表真实工业项目经验或工业安全标准。
