export function formatTime(value, includeDate = false) {
  if (!value) {
    return '—';
  }
  const options = includeDate
    ? { hour12: false }
    : { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false };
  return new Date(value).toLocaleString('zh-CN', options);
}

export function formatMetric(telemetry, fresh, field, digits = 1) {
  const value = telemetry?.[field];
  return fresh && Number.isFinite(value) ? value.toFixed(digits) : '—';
}
