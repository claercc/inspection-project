/** 封装 HTTP 请求和 SSE 报文解析；页面组件只处理业务数据和交互。 */
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
