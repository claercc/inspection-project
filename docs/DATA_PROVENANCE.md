# 温度来源核对：从页面找到同一条 MQTT 采样

当前设备是独立 Java 软件模拟器。它产生寄存器值，edge 经 Modbus TCP 读取后通过 MQTT 上报，server 通过 SSE 推到 Vue。页面不生成随机温度；模拟器产生的数据也不应当称为真实环境测量。

## 亲手核对（约 5 分钟）

1. 打开 http://127.0.0.1:8088 。若服务未启动，在项目根目录执行：

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -SkipBuild
   ```

2. 先在项目根目录的另一终端开始独立订阅，等到出现 `subscribed` 提示：

   ```powershell
   node scripts/observe-telemetry.mjs --device robot-001 --output .run/telemetry-observer.jsonl
   ```

   脚本直接连接本机 Broker 1883，不访问 HTTP/SSE。默认持续监听，Ctrl+C 停止；每次运行会清空指定的输出文件，需保留旧记录时请换文件名。仅快速检查连接可加 `--count 1 --seconds 10`。

3. 在页面「模拟设备温度」卡片点击「查看来源」，复制采集编号。抽屉打开时锁定一个样本，避免每 500ms 更新导致终端无法逐项对照；需要下一条时点「更新到最新采样」。

4. 在第三个 PowerShell 终端中执行下列命令，将采集编号替换为刚复制的值：

   ```powershell
   $sampleId = '替换为抽屉中的采集编号'
   Get-Content -Encoding UTF8 .run/telemetry-observer.jsonl |
     ForEach-Object { $_ | ConvertFrom-Json } |
     Where-Object { $_.telemetry.evidence.sampleId -eq $sampleId } |
     ConvertTo-Json -Depth 8
   ```

5. 对照 `topic`、`telemetry.evidence.registers`、`temperature` 和 `capturedAt`。例如初始地址 4 为 260，则温度为 260 × 0.1 = 26.0 °C。实际值以对应样本为准；采样可能来自 telemetry，也可能是命令结果的 results 主题，所以脚本订阅两者。

如果找不到编号，先确认订阅已开始，再点「更新到最新采样」后重新复制。消息不 retain，Broker 不提供订阅前的采样历史。其他 Broker 使用 `--broker mqtt://地址:端口`；当前脚本未提供远程认证配置。

## 页面中几个时间和状态的含义

- edge 读回时间：采集端完成 FC03 后记录的时间，不是传感器时钟。
- server 接收时间：服务端收到所展示遥测的时间，与 MQTT 主题一同保存。
- 当前实时采集不可用：实时连接或在线条件不满足；下方仍可能保留此前锁定的有效样本，不能把它当成当前值。
- 没有可核对的寄存器样本：该次采集失败，或采集端未提供原始值；界面显示空缺，不从温度反推寄存器。

## 代码与验证证据

依次阅读 `MonitoringView.vue` 的按钮、`TemperatureSourceDrawer.vue` 的 `captureLatest`、`EdgeApplication.readState`、`Messages.SamplingEvidence`、`ConsoleService.receive`、`observe-telemetry.mjs`。具体路径见 [学习进度](LEARNING_PROGRESS.md)。

自动验证同时使用独立 TCP Socket 读取温度寄存器，并将抽屉锁定的整条遥测与独立 MQTT 连接收到的同编号、同主题遥测比较。报告见 [验证记录](VERIFICATION.md)，保存的匹配样本见 [temperature-source-matched-sample.json](verification/temperature-source-matched-sample.json)。

这证明了本地软件模拟链路的数据对应关系；sampleId、连接地址等由采集软件声明，不是物理硬件身份或传感器校准证明。当前也没有数据库历史、真实柜体测点或物理温度传感器。
