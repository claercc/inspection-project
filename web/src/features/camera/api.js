/**
 * 读取当前摄像头能力。
 * @returns {Promise<{simulated: boolean, previewMode: string, capture: boolean}>} 摄像头能力。
 */
export async function getCameraInfo() {
  const response = await fetch('/api/camera');
  if (!response.ok) throw new Error('摄像头信息读取失败');
  return response.json();
}

/**
 * 请求一张新图像，不在服务端保存。
 * @returns {Promise<Blob>} JPEG 图像。
 */
export async function captureCameraImage() {
  const response = await fetch('/api/camera/captures', { method: 'POST' });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail || '摄像头抓拍失败');
  }
  return response.blob();
}

/** @returns {string} 带时间参数的预览地址，避免浏览器复用旧帧。 */
export function previewUrl() {
  return `/api/camera/preview?t=${Date.now()}`;
}
