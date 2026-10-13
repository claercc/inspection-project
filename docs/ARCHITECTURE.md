# 架构、分层与当前实现

更新：2026-10-11，学习单元 1。新版 `codex-inspection-project-prompt.md` 是当前协作依据；以下目标结构尚未全部落地。本次只梳理和更新文档，未调整业务代码。

## 模块边界：现在有谁，分别负责什么

| 目录 / 程序 | 当前职责 | 保留的边界 |
| --- | --- | --- |
| web | Vue 3 + Vite，HTTP 控制、SSE 展示 | 展示状态和发起意图，不产生设备测量值 |
| server | Spring Boot，命令记录、最新状态、REST、SSE | 未来管理任务定义、历史和权限，不直接读写 Modbus |
| edge | 普通 Java 独立进程，MQTT 收发、Modbus 采集与控制 | 未来执行逐点任务与调用摄像机，不管理整套业务数据库 |
| simulator/robot | 独立 Java Modbus TCP 服务，模拟运动与寄存器 | 仅设备行为，不承载巡检任务编排 |
| common | 消息 record、JSON、MQTT 客户端、Modbus 客户端 | 是编译时共享库，不是服务，不通过它共享运行时状态 |
| deploy / scripts | Compose/Nginx 与本地构建、运行、验证 | 本地 Broker 是 Aedes，Compose Broker 是 Mosquitto |

`simulator/camera` 尚未创建；MySQL、Redis、摄像机、视频未接入。server/edge/robot 是独立进程，Maven 多模块不等于多套微服务。保持 Java 17、Spring Boot 3、Vue 3、JavaScript、SSE，不为分层更换框架。ORM 在持久化单元说明并选择，目前未确定。

## server：当前结构与目标分层

当前 `DeviceController` 大体承担 HTTP 接口职责，但还管理 SSE 客户端集合和广播定时器。`integration/ConsoleService` 混合了接口响应 record、HTTP 异常、用例规则、内存存储、MQTT 连接/解析、在线判定和超时调度。`@Service` 只说明 Spring 管理这个对象，不意味着已经完成应用层或领域层划分。

目标采用模块化单体：逐步按 device / inspection / alarm / file 划分业务模块。每个模块按真实需要分层，不预先生成空包、空接口：

| 层 | 本项目中的例子 | 不应承担的职责 |
| --- | --- | --- |
| 接口层 api | Controller、请求/响应 DTO、校验、HTTP 异常映射 | MQTT 报文解析、任务规则 |
| 应用层 application | 查询设备状态、发送命令、启动任务等用例；权限与事务边界 | 拼 Modbus 报文、直接使用厂商 SDK |
| 领域层 domain | 在线/过期规则、命令/任务状态及允许的转换 | HTTP 状态码、Paho、数据库框架 |
| 基础设施层 infrastructure | MQTT 适配、数据库、Redis、文件与具体驱动 | 决定业务状态转换 |

**依赖方向**：接口层调用应用层，应用层使用领域规则；领域层不反向依赖 Controller 或 MQTT。需要可替换的存储/通信时，由应用层或领域层定义其所需接口，基础设施实现接口，Spring 在装配时注入。运行时仍会调用具体 MQTT/数据库代码，但业务代码不必知道具体实现。简单纯函数规则或查询 DTO 不必每个都再配接口。

例如“设备是否在线”依赖采集时间、接收时间和连接事实，不需要知道 `MqttClient` 的类名；“如何订阅 MQTT”则需要 Paho，属于基础设施。当前 `ConsoleService` 是过渡实现，不能在加入新功能时继续无限扩充。

## 前端与 edge 的调整方向

- 前端：`views` 负责页面组合，`components` 展示指标/位置/记录，`api` 封装 HTTP，`composables` 管理 SSE 生命周期与状态。当前 `App.vue` 全部混在一起；先提取一个真实职责，不一次拆完整页面。本期不引入 Pinia、路由或 TypeScript。
- edge：保持普通 Java。入口仅装配配置与组件；以后逐步分开命令执行/去重、寄存器解码和 MQTT 适配。真正接入摄像机时再建立 `CameraDriver`；先只实现模拟驱动。
- common：MQTT 消息是跨进程契约，不等于 server 的领域模型或 Web 响应 DTO。当前 `common` 还包含具体 Paho/Modbus 实现，机器人依赖它也会带入不需要的 MQTT/JSON 依赖；后续按实际使用拆边界，本轮保留。

## 状态归属与能力边界

| 状态 / 能力 | 当前归属与实现 | 后续扩展位置 |
| --- | --- | --- |
| 机器人 RUNNING/STOPPED | 模拟器寄存器为设备反馈事实，edge 读取 | 不同设备驱动将厂商点表映射为统一状态 |
| 命令 PENDING/SENT/终态 | server 记录；edge 判断执行结果 | application 编排、domain 转换规则、SQL 操作记录 |
| 接收/执行中间状态 | 没有独立 ACCEPTED/EXECUTING 反馈 | 需要时补执行事件，不把 SENT 当作设备已接收/已执行 |
| 巡检任务状态 | 尚未实现；START 只是启动运动 | server 管定义和记录，edge 管逐点执行，同设备一个活动任务 |
| 最新遥测与在线判定 | server 内存，含时间与数据来源 | Redis 保存可重建快照；迟到消息的更新顺序须单独验证 |
| 幂等 | edge 进程内 120 秒命令账本 | 业务事件的 eventId / 唯一键在持久化单元实现 |
| 多设备/不同厂商 | 有 deviceId 和主题，但只运行一台 | 设备登记、能力查询与驱动选择，不宣称已有多设备支持 |
| 历史、告警、文件 | 尚未实现 | MySQL 业务事实、迁移、事务、唯一键；文件元数据与持久卷 |
| 权限/审计 | 无鉴权，本地 loopback 演示 | 访客只读、管理员操作；公网前完成；诊断日志不等于操作审计 |
| 摄像机/视频/真实 SDK | 尚未实现 | edge HTTP 模拟 CameraDriver；媒体服务独立；硬件授权后再扩展 |
| 运维 | 环境变量、脚本、文本日志、server 存活接口 | 配置校验、关联 ID 日志、依赖就绪检查、持久化服务健康检查 |

MySQL 与 Redis 不存两套业务事实；图片走 HTTP/文件服务而非 MQTT。文件与数据库不是一个天然原子事务，归档失败与元数据一致性需在文件单元制定策略。容器运行、业务持久化和公网接入未验收。

## 当前通信数据流

```mermaid
sequenceDiagram
    participant UI as Vue 浏览器
    participant API as Spring Boot server
    participant MQ as MQTT Broker
    participant E as Java edge
    participant R as Java 机器人模拟器
    UI->>API: POST /api/commands START 或 STOP
    API->>MQ: commandId / QoS 1 / retain=false
    MQ->>E: commands 主题
    E->>R: TCP FC03 先检查连接
    E->>R: FC06 写控制寄存器 5
    R-->>E: FC06 写入回显
    E->>R: FC03 读寄存器 0..6
    R-->>E: 实际运行状态与命令反馈
    E->>MQ: results，关联 commandId
    MQ->>API: SUCCEEDED / FAILED / RESULT_UNKNOWN
    loop 每次读取后等待 500ms
        E->>R: FC03 状态采集
        R-->>E: 位置 / 状态 / 电量 / 温度
        E->>MQ: telemetry
        MQ->>API: 最新遥测
    end
    API-->>UI: SSE state 事件，每 500ms
```

## 职责

- `web/`：Vue 3 通过 REST 发起操作，通过 EventSource 接收 SSE。位置与指标来自 edge 的网络采集；浏览器不生成机器人运动数据。
- `server/`：Spring Boot 管理操作记录、在线判定与 SSE；不直接访问机器人寄存器。控制请求互斥，运行时再次 START 返回 409。
- `edge/`：独立 Java 17 进程，单线程串行命令和采集，负责 MQTT 与 Modbus 转换。后续阶段在这里加入逐点任务执行和 CameraDriver。
- `simulator/robot/`：只提供简单运动行为和真实 Modbus TCP 服务，没有业务任务编排。每 250ms 更新模拟状态，可独立停止。
- `common/`：共享消息定义、MQTT 连接与 Modbus 客户端。共享代码不等于共享内存状态，所有业务链路都经过网络。

## 为什么写入回显还不够

MQTT QoS 1 确认 Broker 接收消息，不确认 edge 执行，更不确认设备运动。FC06 回显表明寄存器写请求被接受。edge 在写入后执行 FC03，验证运行状态与控制反馈，才返回 SUCCEEDED。

如果写入前读操作已经失败，命令显示 FAILED；写入开始后 Socket 超时/断连，无法判断设备是否已动作，显示 RESULT_UNKNOWN。收到明确 Modbus 异常响应时显示 FAILED。server 在 8 秒内没有执行反馈也显示 RESULT_UNKNOWN，不自动重发。

## TCP 字节流与边界

`DataInputStream` 的基本类型读取与 `readFully` 支持报文拆分到多次 TCP 读取。先读取 7 字节 MBAP，再按 length-1 读取 PDU。客户端核验事务 ID、protocol=0、Unit ID、长度、功能码、异常 PDU、FC03 byte count 和 FC06 回显。

模拟器逐帧处理连接上的连续请求，验证 PDU 长度、地址、数量与写值，返回功能码异常响应。单次请求 1 秒 Socket 超时；错误后关闭连接，下一次采集建立新 Socket，重新读取真实状态。无自动控制重放。

## 断线与幂等边界

edge/server 每 2 秒尝试 MQTT 连接并重新订阅。使用 clean session；命令不 retain、不在本地缓存待发送控制，不在重连时重放。模拟器恢复后 edge 重新读取状态；模拟器重启初始 STOPPED。

edge 按 commandId 保存 120 秒的进程内命令/结果账本，同一完整消息重复投递只重发已缓存结果，不执行 FC06。相同 commandId 携带不同 payload 返回 FAILED。命令有效期最多 30 秒，当前 server 发出 8 秒有效命令。超过账本保留期的旧消息也因过期被拒绝。这是运行进程内去重，不是跨 edge 崩溃的物理动作恰好一次。

server 在收到遥测超过 3 秒、设备采集时间过期或 Broker 不可用时标记数据不可用；前端 SSE 断开也立即隐藏实时指标。位置示意图保留上次位置并降低透明度，明确显示上次位置而非实时位置。正常在线与业务运行状态分开显示。

## 阶段边界与存储

阶段 A 最近 100 条指令和诊断存内存，SSE 推送状态快照。没有对尚未接入的数据库或摄像机提供虚假成功接口。阶段 B 才引入 MySQL 任务/事件/告警/文件元数据唯一键，以及 Redis 最新遥测与更新时间。阶段 C 增加管理员鉴权与公网只读入口；当前五个本地进程只监听 loopback，Compose 只暴露 loopback Web。
