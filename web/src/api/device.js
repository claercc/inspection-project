/**
 * @typedef {object} DeviceCommand 一次启停控制请求。
 * @property {string} commandId 唯一命令编号，也是结果关联和重复投递去重的依据。
 * @property {string} deviceId 目标设备编号。
 * @property {'START'|'STOP'} type 控制意图。
 * @property {object} parameters 扩展参数，当前启停命令使用空对象。
 * @property {string} createdAt 创建时间，ISO-8601 字符串。
 * @property {string} expiresAt 执行有效期截止时间，ISO-8601 字符串。
 */

/**
 * @typedef {object} Telemetry edge 通过设备读取获得的一次遥测。
 * @property {string} deviceId 设备编号。
 * @property {string} source 采集来源声明。
 * @property {string} capturedAt edge 读回时间，ISO-8601 字符串。
 * @property {boolean} robotOnline 此次设备读取是否成功。
 * @property {string} runState 设备运行状态，如 RUNNING、STOPPED、UNKNOWN。
 * @property {number} x X 坐标，单位米。
 * @property {number} y Y 坐标，单位米。
 * @property {number} battery 电池电量，单位百分比。
 * @property {number} temperature 温度，单位摄氏度。
 * @property {number} lastControl 设备读回的上次控制值。
 * @property {number} writeCount 模拟器累计控制写入次数。
 * @property {string|null} error 采集失败说明，成功时为 null。
 * @property {object|null} evidence 原始寄存器和端点等采样证据，失败时为 null。
 */

/**
 * @typedef {object} CommandView 服务端返回的一条命令记录。
 * @property {DeviceCommand} command 原始命令，包含编号、控制类型和有效期。
 * @property {'PENDING'|'SENT'|'SUCCEEDED'|'FAILED'|'RESULT_UNKNOWN'} status 当前执行阶段。
 * @property {string} message 当前阶段的说明。
 * @property {string} updatedAt 此记录最后更新的 ISO-8601 时间。
 */

/**
 * @typedef {object} DeviceState 服务端通过 /state 和 SSE 提供的设备快照。
 * @property {string} deviceId 设备编号。
 * @property {boolean} simulated 是否为模拟设备。
 * @property {boolean} mqttConnected 服务端是否已连通 Broker 并完成订阅。
 * @property {boolean} online 当前设备数据是否新鲜且采集成功。
 * @property {boolean} stale 采样或接收时间是否已过期。
 * @property {string} connectionStatus 链路状态码，如 ONLINE、EDGE_STALE。
 * @property {string|null} lastReceivedAt 最新有效采样的服务端接收时间。
 * @property {string|null} telemetryTopic 最新有效采样实际来自的 MQTT 主题。
 * @property {Telemetry|null} telemetry 设备采样；尚无数据时为 null。
 * @property {CommandView[]} commands 按创建时间从新到旧排列的命令记录。
 * @property {object[]} diagnostics 按记录时间从新到旧排列的诊断事件。
 */

/**
 * 提交一次启停请求，供页面的控制动作调用；HTTP 返回后仍需观察 SSE 中的最终结果。
 *
 * @param {'START'|'STOP'} type 控制类型：启动或停止。
 * @returns {Promise<CommandView>} 本次命令的服务端记录，可能仍处于等待反馈阶段。
 * @throws {Error} 请求被拒绝、网络异常或响应无法解析时，Promise 拒绝。
 * @example
 * const record = await sendDeviceCommand('START');
 * console.log(record.command.commandId); // 用此编号关联后续 SSE 反馈
 */
export async function sendDeviceCommand(type) {
  const response = await fetch('/api/commands', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type }),
  });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.detail || data.message || `操作未接受（HTTP ${response.status}）`);
  }
  return data;
}

/**
 * EventSource 会在连接异常后自动尝试恢复；返回关闭函数供手动重连和组件卸载时释放连接。
 * 重建网页订阅不会重发任何设备控制指令。
 *
 * @param {object} callbacks 订阅期间使用的回调，三个回调均需提供。
 * @param {(state: DeviceState) => void} callbacks.onState 收到并解析一份状态快照后调用。
 * @param {() => void} callbacks.onDisconnect 连接异常或快照处理失败时调用，页面应停止显示实时状态。
 * @param {(message: string) => void} callbacks.onError 快照解析或 onState 处理失败时接收提示文字。
 * @returns {() => void} 关闭本次订阅的函数；调用后不再自动重连。
 * @example
 * const close = subscribeDeviceState({
 *   onState: (state) => console.log(state.connectionStatus),
 *   onDisconnect: () => console.log('实时订阅暂不可用'),
 *   onError: (message) => console.error(message),
 * });
 * close(); // 离开页面或手动重连前调用
 */
export function subscribeDeviceState({ onState, onDisconnect, onError }) {
  const source = new EventSource('/api/events');
  source.addEventListener('state', (event) => {
    try {
      onState(JSON.parse(event.data));
    } catch {
      onDisconnect();
      onError('状态消息格式不正确，请重新连接实时订阅。');
    }
  });
  source.onerror = onDisconnect;
  return () => source.close();
}
