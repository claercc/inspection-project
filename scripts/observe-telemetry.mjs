/** 独立订阅 MQTT 以核对原始采样，不依赖页面或 server API，也不发送设备控制。 */
import mqtt from 'mqtt';
import { parseArgs } from 'node:util';
import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { describeError } from './error-message.mjs';

const { values } = parseArgs({
  options: {
    broker: { type: 'string', default: process.env.MQTT_URL || 'mqtt://127.0.0.1:1883' },
    device: { type: 'string', default: process.env.DEVICE_ID || 'robot-001' },
    output: { type: 'string' },
    count: { type: 'string', default: '0' },
    seconds: { type: 'string', default: '0' },
  },
});
if (!/^[A-Za-z0-9_-]+$/.test(values.device))
  throw new Error('设备编号只能包含字母、数字、下划线或连字符');
if (!/^\d+$/.test(values.count) || !/^\d+$/.test(values.seconds))
  throw new Error('count 和 seconds 参数必须为非负整数');
const output = values.output && path.resolve(values.output);
if (output) {
  mkdirSync(path.dirname(output), { recursive: true });
  writeFileSync(output, '');
}
const topics = ['telemetry', 'results'].map((kind) => `inspection/${values.device}/${kind}`);
const client = mqtt.connect(values.broker.replace(/^tcp:/, 'mqtt:'), {
  clientId: `inspection-source-observer-${process.pid}`,
  clean: true,
  protocolVersion: 4,
  connectTimeout: 5000,
  reconnectPeriod: 1000,
});
let observed = 0;
/**
 * 结束独立 MQTT 观察会话，无参数；由数量限制、截止计时器或退出信号调用。
 * @returns {void} 发起客户端关闭；end 事件回调随后负责清理截止计时器。
 */
function stop() {
  client.end(true);
}
client.on('connect', () =>
  client.subscribe(topics, { qos: 1 }, (error) => {
    if (error) {
      console.error(`MQTT 主题订阅失败：${describeError(error)}`);
      process.exitCode = 1;
      stop();
    } else
      console.error(
        `独立 MQTT 观察器已订阅：${topics.join(', ')}。现在可打开来源抽屉，核对采样编号 sampleId。`,
      );
  }),
);
client.on('error', (error) => console.error(`MQTT 观察器连接异常：${describeError(error)}`));
client.on('message', (topic, payload, packet) => {
  try {
    const message = JSON.parse(payload.toString());
    const telemetry = topic.endsWith('/results') ? message.telemetry : message;
    if (!telemetry || telemetry.deviceId !== values.device) return;
    const record = {
      observedAt: new Date().toISOString(),
      topic,
      retained: Boolean(packet.retain),
      telemetry,
    };
    const line = `${JSON.stringify(record)}\n`;
    process.stdout.write(line);
    if (output) appendFileSync(output, line);
    if (Number(values.count) > 0 && ++observed >= Number(values.count)) stop();
  } catch (error) {
    console.error(`已忽略格式无效的遥测消息：${describeError(error)}`);
  }
});
const deadline =
  Number(values.seconds) > 0 ? setTimeout(stop, Number(values.seconds) * 1000) : null;
client.on('end', () => {
  if (deadline) clearTimeout(deadline);
});
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
