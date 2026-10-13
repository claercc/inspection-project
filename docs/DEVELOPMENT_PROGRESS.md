# 开发进度与接续上下文

更新时间：2026-10-11（Asia/Singapore）

## 当前协作方式：一次一个学习单元

- 新版 `codex-inspection-project-prompt.md` 替代上一轮自动推进阶段的要求。
- 当前只完成学习单元 1：检查已有实现、梳理模块/分层/学习路线、记录缺口；讲解后停止，等待用户反馈。
- 本次仅更新本文件、ARCHITECTURE.md 和新增 LEARNING_PROGRESS.md。没有新增功能、重构、升级依赖、提交或推送。
- 本次只读 GET `/api/state` 返回 `robot-001 / ONLINE / simulated=true`；未发送控制命令。以下构建和测试结果是上一轮已有验证，不是本轮重跑。
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
- `powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`：12 项实际端到端检查全部 PASS，含真实 Chrome 按钮、SSE、重复投递、离线/恢复、结果未知与 Broker 重连。
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

阶段 A 本地固定端口：1883 / 1502 / 8080 / 8088。测试固定端口：18883 / 11502 / 18080 / 18088。`.run/` 存进程身份和日志，`.tools/` 存工具/依赖缓存；两者忽略提交。npm 锁文件已生成并保留。交接提示词保持原样；工作区仍没有初始化 Git。

下一次先读新版提示词、LEARNING_PROGRESS.md、本文件和 ARCHITECTURE.md。不要自动进入 B 或重做 A。建议下一单元只提取在线/过期领域规则；是否进入下一单元由用户阅读后的反馈决定。

## 临时实现与分层待办（本轮未改代码）

| 当前实现 | 缺口 | 处理时机 |
| --- | --- | --- |
| integration/ConsoleService 同时处理 HTTP 异常、规则、MQTT、状态与内存记录 | 应用/领域/基础设施边界混合 | 先提取在线规则，后续逐用例拆分 |
| DeviceController 管理 SSE 集合与广播调度 | 接口展示与推送生命周期混在一起 | SSE 学习单元再提取 |
| App.vue 包含请求、订阅、派生状态和整页模板 | 缺 api / composables / components 边界 | 先封装一个请求或订阅，不批量拆页面 |
| EdgeApplication 包含装配、解码、去重和控制执行 | 新任务/驱动不宜继续堆入口 | MQTT/Modbus 学习后按职责小步提取 |
| common 含消息契约和具体通信实现 | server/edge/robot 都引入 Paho/JSON；HTTP 复用 MQTT record | 在 DTO / 驱动单元按需要收窄依赖 |
| command/runState 使用字符串和散落的状态集合 | 没有独立领域状态机，亦没有巡检任务 | 状态规则单元再类型化与测试 |
| 内存记录、直接覆盖最新 telemetry | 重启丢失；迟到数据更新顺序缺专项校验 | 持久化/故障单元设计并验证，不宣称已保证 |
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
