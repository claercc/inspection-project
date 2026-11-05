/**
 * 将脚本捕获的常见网络错误转换为中文，保留可用于排查的错误码。
 * @param {Error & {code?: string}} error 原始异常。
 * @returns {string} 中文说明；工程主动抛出的中文提示直接保留。
 */
export function describeError(error) {
  const message = error?.message;
  if (typeof message === 'string' && /[\u4e00-\u9fff]/u.test(message)) {
    return message;
  }
  const networkErrors = {
    ECONNREFUSED: '连接被拒绝，请确认目标服务已启动且地址端口正确',
    ETIMEDOUT: '连接或响应超时，请检查通信链路',
    ENOTFOUND: '无法解析主机地址，请检查服务地址',
    ECONNRESET: '网络连接被重置，请检查设备或消息服务状态',
    EHOSTUNREACH: '目标主机不可达，请检查网络配置',
    EPIPE: '连接已关闭，无法继续发送数据',
  };
  if (networkErrors[error?.code]) {
    return networkErrors[error.code];
  }
  if (error instanceof SyntaxError) {
    return 'JSON 消息格式不正确，请检查消息内容';
  }
  return `操作失败（错误码或类型：${error?.code || error?.name || '未知'}）`;
}
