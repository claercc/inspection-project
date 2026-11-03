/**
 * 将时间格式化为浏览器当前时区的中文显示，供采样时间和命令记录复用。
 *
 * @param {string|number|Date|null|undefined} value ISO 时间、时间戳或 Date；空值显示占位符。
 * @param {boolean} [includeDate=false] 是否同时显示年月日；默认只显示时分秒。
 * @returns {string} 本地化时间文字；空值返回“—”，无效日期沿用 Date 的无效日期提示。
 * @example
 * formatTime(null); // '—'
 * formatTime('2026-11-03T08:00:00Z', true); // 当前时区的日期和时间
 */
export function formatTime(value, includeDate = false) {
  if (!value) {
    return '—';
  }
  const options = includeDate
    ? { hour12: false }
    : { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false };
  return new Date(value).toLocaleString('zh-CN', options);
}

/**
 * 格式化一项实时测量值；采样失效或字段不是有限数值时显示占位符。
 *
 * @param {object|null|undefined} telemetry 含 temperature、battery、x、y 等字段的采样对象。
 * @param {boolean} fresh 当前采样是否仍可作为实时值使用。
 * @param {string} field 要读取的字段名。
 * @param {number} [digits=1] 传给 Number.toFixed 的小数位数。
 * @returns {string} 固定小数位的数值文字，或“—”。
 * @throws {RangeError} 需要格式化有效值，但 digits 超出 Number.toFixed 允许范围时抛出。
 * @example
 * formatMetric({ temperature: 26 }, true, 'temperature'); // '26.0'
 * formatMetric({ temperature: 26 }, false, 'temperature'); // '—'
 */
export function formatMetric(telemetry, fresh, field, digits = 1) {
  const value = telemetry?.[field];
  return fresh && Number.isFinite(value) ? value.toFixed(digits) : '—';
}
