# 学习进度

更新时间：2026-10-11（Asia/Singapore）。每次只处理一个单元，讲解与相应验证后停止；用户明确说“继续”才进入下一单元。新版提示词替代此前自动推进阶段的要求。

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

## 下一次最小单元建议（未执行）

**只提取“设备在线/过期判定”这一条领域规则。** 从 ConsoleService.state 中提取不依赖 HTTP/MQTT 的判断，使用传入的时间测试在线、数据过期和 Broker 离线。预计 3–4 个主要文件：领域规则、现有 service 接入、规则测试，必要时 server/pom.xml 加测试依赖。保持 HTTP/SSE 字段与控制行为；不同时加数据库或拆前端。

尚未解决：用户对各职责/依赖方向的理解等待反馈；迟到遥测的更新顺序、完整状态转换与持久化一致性尚未专项验证；Compose 容器运行仍未验收。
