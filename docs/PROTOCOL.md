# 演示协议：点表与消息

本文件描述自定义模拟设备协议。地址从 **0** 开始，不使用 40001 表示法；多字节值为大端无符号 16 位。默认 TCP 端口 1502，Unit ID 1。温度/阈值/控制字不代表真实工业设备或安全标准。

| 零基地址 | 名称 | 单位 / 换算 | 权限 | 值 |
| --- | --- | --- | --- | --- |
| 0 | 运行状态 | 枚举 | FC03 读 | 0 停止，1 运行 |
| 1 | X 位置 | 原值 × 0.1 m | FC03 读 | 0–999，演示循环轨迹 |
| 2 | Y 位置 | 原值 × 0.1 m | FC03 读 | 演示派生位置 |
| 3 | 电池电量 | 原值 × 0.1 % | FC03 读 | 初始 1000，即 100% |
| 4 | 温度 | 原值 × 0.1 °C | FC03 读 | 初始 260，即 26 °C |
| 5 | 控制 / 上次接受的控制 | 枚举 | FC03 读 / FC06 写 | 0 初始，1 START，2 STOP |
| 6 | 接受写入次数 | 次，uint16 回绕 | FC03 读 | 验证重复投递没有二次写入 |

FC03 数量 1–125，超出点表返回异常 02；写只允许地址 5，非法地址 02，非法值 03；不支持的功能码返回 01。MBAP length 包含 Unit ID + PDU，最大 254；length 不合法时关闭连接。其它 Unit ID 不处理。

## MQTT 3.1.1

默认设备 `robot-001`。三个主题均 QoS 1；所有发布 retain=false；没有离线控制缓存。服务器/edge 均 clean session。

| 主题 | 方向 | 内容 |
| --- | --- | --- |
| inspection/robot-001/commands | server → edge | 控制命令 |
| inspection/robot-001/results | edge → server | 与原 commandId 关联的执行结果 |
| inspection/robot-001/telemetry | edge → server | 每次 Modbus 采集结果 / 离线反馈 |

命令示例：

```json
{
  "commandId": "e8dcaefb-8330-4083-8478-3840514e598f",
  "deviceId": "robot-001",
  "type": "START",
  "parameters": {},
  "createdAt": "2026-10-11T04:00:00Z",
  "expiresAt": "2026-10-11T04:00:08Z"
}
```

以上时间为格式示例，实际发送必须使用当前时间。类型只接受 START/STOP。

执行结果：

```json
{
  "commandId": "e8dcaefb-8330-4083-8478-3840514e598f",
  "deviceId": "robot-001",
  "type": "START",
  "status": "SUCCEEDED",
  "message": "FC06 write + FC03 device feedback confirmed",
  "timestamp": "2026-10-11T04:00:00.100Z",
  "telemetry": {
    "deviceId": "robot-001",
    "source": "MODBUS_TCP_SIMULATOR",
    "capturedAt": "2026-10-11T04:00:00.100Z",
    "robotOnline": true,
    "runState": "RUNNING",
    "x": 0.0,
    "y": 0.0,
    "battery": 100.0,
    "temperature": 26.0,
    "lastControl": 1,
    "writeCount": 1,
    "error": null,
    "evidence": {
      "sampleId": "9e14421b-1613-4ab2-aa34-8704d8b147dd",
      "collectorId": "inspection-edge-robot-001",
      "protocol": "MODBUS_TCP",
      "host": "127.0.0.1",
      "port": 1502,
      "unitId": 1,
      "functionCode": 3,
      "startAddress": 0,
      "registers": [1, 0, 0, 1000, 260, 1, 1]
    }
  }
}
```

telemetry 主题发送上述 telemetry 对象；设备不可达时 `robotOnline=false, runState=UNKNOWN` 并携带 error。离线对象的零值不能被当作有效测量，界面按 online 判定隐藏数值。

## 采样来源（2026-10-16 增补）

成功读取时，`Telemetry.evidence` 保存该次 FC03 实际返回的寄存器，不由温度等换算值反推。上面的时间、编号和寄存器是格式示例；实际编号每次成功读取重新生成。

| 字段 | 来源与含义 |
| --- | --- |
| sampleId | edge 为一次成功读取生成的 UUID；相同样本在 MQTT 与页面中保持相同编号 |
| collectorId | 采集端标识，当前为 edge 的 MQTT clientId |
| protocol / functionCode | 当前 MODBUS_TCP / 3（FC03） |
| host / port / unitId | edge 实际使用的连接配置；host 是从 edge 所在环境访问的地址 |
| startAddress / registers | 零基起始地址与实际读取数组；地址 a 对应 registers[a - startAddress] |

温度使用地址 4 的值除以 10。`capturedAt` 是 edge 完成读取的时刻，不是设备时钟。采集失败时 `evidence=null`；旧版消息未携带 evidence 时同样不能展示来源证明。

HTTP/SSE 的状态快照另外增加 `telemetryTopic`，由 server 的实际接收回调记录。它不是 telemetry 对象内部字段，可能为 `/telemetry` 或 `/results`。独立观察脚本订阅两者，从结果消息中提取 telemetry，再按 sampleId 和实际 topic 核对。

所有来源字段均为软件采集端声明，不提供签名、硬件身份认证或物理测量保证。核对方法见 [DATA_PROVENANCE.md](DATA_PROVENANCE.md)。

执行结果可为 SUCCEEDED / FAILED / RESULT_UNKNOWN。server 另外维护 PENDING / SENT 阶段。超时不是设备失败证据，RESULT_UNKNOWN 可以被迟到的确切反馈更新；已确认终态不被重复反馈改写。

## 验证边界

Paho 库处理 MQTT 报文、连接和 QoS1 握手；业务 commandId 去重、结果关联、过期与状态判定由本项目实现。Modbus TCP 未引入通信库，字节流边界、字段校验和异常处理由本项目实现，并有真实 Socket 测试。
