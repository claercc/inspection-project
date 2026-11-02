# 学习进度

更新时间：2026-10-16（Asia/Singapore）。按用户最新指示，改为“前端视觉与按钮优先，再逐步接后端”。每次完成一个小单元后停止；没有用户反馈不能标注已掌握。

## 当前单元 3：从温度按钮核对数据来源

状态：已实现、构建和运行验证，等待用户阅读与反馈。用户已确认理解的内容：暂无。

- 目标：回答页面温度从哪里来，能否绕过页面独立核对；只处理温度来源，不展开巡检任务或批量后端重构。
- 实现：温度卡片「查看来源」打开锁定采样抽屉；展示 FC03 实际读取的原始寄存器、换算、采集编号、连接地址、时间与 server 实际收到的 MQTT 主题。采集端生成 sampleId，页面不反推原值。
- 范围：6 个业务文件（共享契约、edge、server、监控视图、来源组件和样式），另加独立 MQTT 订阅脚本、必要回归与文档。没有新增依赖。
- 验证：Java clean verify、5 个真实 TCP 测试、Vue 生产构建通过；20 项端到端检查通过。独立 Modbus Socket 读取与独立 MQTT 订阅均与页面样本核对；设备离线不展示伪造的零寄存器，过期样本保留快照并提示当前不可用。
- 限制：数据来自软件模拟器；来源元数据不是硬件身份认证。没有数据库历史、摄像机或公网验收。

本次阅读顺序（先跟按钮，再看跨进程数据）：

1. `web/src/views/monitoring/MonitoringView.vue`：找到 `temperature-source` 按钮，跟到组件的 `open()`。
2. `web/src/components/monitoring/TemperatureSourceDrawer.vue`：先读 `captureLatest`，理解为什么打开后锁定单次采样、为何实时可用状态仍持续更新；再看 `rawTemperature` 与公式。
3. `edge/src/main/java/demo/inspection/edge/EdgeApplication.java`：`readState` 一次读取 0..6，保存真实数组并生成 sampleId；地址 4 的值除以 10 得到温度。
4. `common/src/main/java/demo/inspection/protocol/Messages.java`：看 `SamplingEvidence` 如何成为跨进程消息的一部分。
5. `server/src/main/java/demo/inspection/server/integration/ConsoleService.java`：`receive` 同时保存 telemetry、接收时间和实际 topic；`state` 再交给现有 SSE。
6. `scripts/observe-telemetry.mjs`：独立订阅 Broker，不读取 server API。按照 [数据来源核对练习](DATA_PROVENANCE.md) 找到同一个 sampleId。

可选练习：解释“模拟数值”和“前端随机数”的区别；说明 sampleId 为什么不能单独证明物理硬件真实性。未收到反馈前不替用户填写答案或标记掌握。

本单元到此停止。下一小步先根据用户反馈决定是否完善场景化模拟说明，或回到新建巡检任务表单；均未自动开始。以下保留旧单元记录，旧的默认下一步不再生效。

## 单元 2：目录骨架与前端监控工作台

状态：已实现视觉与前端职责拆分，等待用户查看与反馈。用户已确认理解的内容：暂无。

- 目标：先看见完整的工业控制台展示，认识页面、状态逻辑、通信 API 的边界；后端未来功能只建文件夹。
- 本次脚手架例外：约 8 个主要前端文件，加目录脚本、必要回归测试和文档；没有批量迁移后端。
- 目录与按钮清单：见 [FRONTEND_FIRST_PLAN.md](FRONTEND_FIRST_PLAN.md)。62 个规划目录可通过 prepare-structure.ps1 恢复。
- 当前按钮：启动/停止接已有后端；重连实时订阅、指令详情、功能路线已实现前端交互；任务等未实现入口禁用并标注状态。
- 本次验证：Vue 生产构建通过；15 项端到端检查覆盖原有链路与新增前端弹窗/订阅生命周期，报告独立保存在 docs/verification/frontend-workspace-report.json。
- 未验证：MySQL/Redis、任务/摄像机/告警等空目录能力、容器运行、公网发布；本单元不声称完成后端分层迁移。

阅读顺序（先读四个小文件，约 130 行，再按需读监控模板）：

1. `web/src/App.vue`：只组合布局和页面。
2. `web/src/api/device.js`：实际 HTTP 请求与 SSE 解析。
3. `web/src/composables/useDeviceConsole.js`：状态、按钮条件、订阅创建和关闭；没有设备执行规则。
4. `web/src/config/workspace.js`：区分已接通与规划能力。
5. `web/src/views/monitoring/MonitoringView.vue`：从按钮 @click 找到 control/reconnect/showCommand；布局见 layouts/ConsoleLayout.vue，样式见 assets/styles/console.css。

亲手观察：刷新 http://127.0.0.1:8088 ，打开「功能路线」与「指令详情」，或点击「重连实时订阅」。在 DevTools Network 确认重连走 GET /api/events，没有 POST /api/commands。

可选练习：在 config/workspace.js 找到一个规划模块，解释它为何禁用、后端应落在哪个业务目录；不要仅把 implemented 改为 true 来假装功能已完成。

下一小步建议（尚未执行）：只做「新建巡检任务」前端表单、校验和配置预览，保持后端未接入标识；之后另一个单元接创建任务 API。此前“先提取在线领域规则”的建议不再是默认下一步。

以下保留单元 1 的历史盘点；其中旧 App.vue 阅读位置和下一步建议已由本单元替代。

## 单元 1：现有代码、边界与分层

状态：代码盘点和资料整理完成，等待用户阅读与反馈。**用户已确认理解的内容：暂无；不能由生成代码或测试通过推定已掌握。**

- 目标：分清运行进程、代码模块、软件分层，沿一次启动与一次状态回传定位职责。
- 前置概念：DTO 是边界上传递的数据结构；用例是一次业务操作；领域规则描述业务判断；适配器把统一意图转换成外部协议调用。SSE 是 server 向浏览器持续推送事件的 HTTP 连接；Broker 是 MQTT 消息转发程序。
- 阅读量：约 30–45 分钟；5 个主要文件，只读 App.vue 的脚本部分，总计约 305 行。协议底层本轮仅定位，不要求逐字节理解。
- 本次改动：仅 ARCHITECTURE.md、LEARNING_PROGRESS.md、DEVELOPMENT_PROGRESS.md；业务代码、依赖、配置和测试未改。
- 本次验证：检查源码/配置/历史报告，并只读访问 `/api/state`，结果 ONLINE；没有重新构建、发送控制命令或重跑完整端到端测试。

## 按调用顺序阅读

| 顺序 | 文件 / 方法 | 此次只关注什么 |
| --- | --- | --- |
| 1 | web/src/App.vue：control（20）、onMounted（14） | 按钮发 POST；EventSource 接收返回状态；网页不生成遥测 |
| 2 | server/.../device/DeviceController.java：command（21）、state（18）、events（23） | HTTP 入口转给 service；SSE 是另一条响应路径 |
| 3 | server/.../integration/ConsoleService.java：issue（67） | 校验、分配 commandId、保存记录、MQTT 发送；HTTP 返回不是设备执行确认 |
| 4 | common/.../protocol/Messages.java：Command（13）、Telemetry（15）、Result（18） | 三种消息用途；commandId 关联请求与结果；record 是 Java 的数据载体 |
| 5 | edge/.../EdgeApplication.java：receive（48）、readState（33）、poll（39） | 写控制寄存器后读回；缩放测量值；定期采集和反馈 |

回程再定位：ConsoleService.receive（34）→ state（57）→ DeviceController.broadcast（28）→ App.vue 的 state 事件回调（16）。

暂缓深入：MqttLink.connect/publish 的 QoS 握手、ModbusClient.request 的 MBAP/PDU、RobotSimulator.serve 的 Socket 字节流。它们已有实现，后续结合小实验学习，不重新开发一套。

## 现阶段需要先看清的区别

1. `common` 是共享 jar，不是运行中的网络服务；共享消息类不等于共享状态。
2. `RUNNING/STOPPED` 属于机器人；`SENT/SUCCEEDED/FAILED/RESULT_UNKNOWN` 属于命令；任务状态尚未实现。
3. `@Service` 不自动带来分层。ConsoleService 当前同时依赖 HTTP、Paho 和业务规则，是需要逐步拆开的过渡实现。
4. MQTT 消息 record 当前还进入 HTTP 响应；后续需区分消息契约、业务模型和响应 DTO。数据库实体尚不存在。
5. 现有真网络通信和断线策略应保留；不为架构演示重写已通过验证的协议实现。

## 用户亲手观察：不改代码的小练习

```powershell
$s = Invoke-RestMethod http://127.0.0.1:8088/api/state
$s | Select-Object deviceId, online, connectionStatus, lastReceivedAt
$s.telemetry | Select-Object source, runState, capturedAt, lastControl, writeCount
```

目前已运行的演示预期返回 `robot-001`、ONLINE、模拟数据来源与采集时间。若服务后来已关闭，再按 README 启动；本单元不必运行完整测试栈。

可选练习：找出 JSON 中的“连接状态”和“机器人运行状态”，再在源码中各定位一个负责它们的判断。写一句自己的理解：哪些字段是设备事实，哪些是 server 根据时间推导出的状态？本轮不预先替用户完成练习。

## 后续导航：每一项仍拆成小单元

1. 盘点与架构（当前，等待反馈）。
2. 从现有在线查询提取一条纯领域规则，再分开查询用例和接口 DTO。
3. 前端 HTTP 封装与 SSE composable，分两次小改动，保持现有页面行为。
4. MQTT：主题/契约、一次收发、QoS 与业务反馈；逐项使用已有代码。
5. TCP/Modbus：点表与一次 FC03；再讲 FC06 与读回；再看分包/异常测试。
6. 业务持久化：先说明数据模型、MySQL 与 Redis 分工、迁移和 ORM 选择，再逐步接一个查询和一个写入用例。
7. 任务状态机与单设备活动任务，再逐点编排；命令状态与任务状态分开。
8. 独立 HTTP 摄像机与动态 PNG、驱动、上传、文件元数据，分单元实现。
9. 告警/历史/幂等/故障；之后才是视频、权限审计、公网部署与面试讲解。

## 单元 1 原建议（已由前端优先路线替代，未执行）

**只提取“设备在线/过期判定”这一条领域规则。** 从 ConsoleService.state 中提取不依赖 HTTP/MQTT 的判断，使用传入的时间测试在线、数据过期和 Broker 离线。预计 3–4 个主要文件：领域规则、现有 service 接入、规则测试，必要时 server/pom.xml 加测试依赖。保持 HTTP/SSE 字段与控制行为；不同时加数据库或拆前端。

尚未解决：用户对各职责/依赖方向的理解等待反馈；迟到遥测的更新顺序、完整状态转换与持久化一致性尚未专项验证；Compose 容器运行仍未验收。
