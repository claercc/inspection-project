# 后端整理：第二步

本轮将网络通信从业务服务中取出，接口地址和消息格式保持兼容。

## 从哪里读

1. `device/DeviceController`：HTTP 请求入口。
2. `device/DeviceService`：查看状态、检查控制条件、创建和更新命令记录。
3. `integration/EdgeGateway`：连接 MQTT、订阅主题、发送命令、退出时关闭连接。
4. `integration/EdgeMessageHandler`：解析接入端消息，调用设备服务登记采样或结果。

发送：`Controller → DeviceService.issue → EdgeGateway.sendCommand → MQTT`。

接收：`MQTT → EdgeMessageHandler → DeviceService.receiveTelemetry / receiveResult → SSE`。

`application.yml` 统一声明设备编号和 MQTT 地址，继续读取原来的 DEVICE_ID、MQTT_URL 环境变量。Gateway 构造时只装配客户端，消息处理器准备好后才启动连接，避免构造业务对象期间发生回调。

## 修改范围

- 换 MQTT 地址：修改环境变量或应用配置。
- 新增接入消息：在消息处理器解析，在业务服务中实现处理；业务服务不解析 JSON。
- 新增控制：扩展业务规则和消息契约，通信出口继续复用。
- 调整页面接口：改 Controller，不改 MQTT 连接管理。

本轮没有再拆命令服务和设备状态服务，它们仍共享同一状态锁，避免为了分层新增锁顺序和跨服务状态协调。等持久化落地时再按需要拆分。

数据库、摄像头与新业务功能尚未实现。命令和诊断记录仍保存在内存中。
