/** 独立订阅 MQTT 以核对原始采样，不依赖页面或 server API，也不发送设备控制。 */
import mqtt from 'mqtt';
import { parseArgs } from 'node:util';
import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

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
  throw new Error('device must contain only letters, digits, _ or -');
if (!/^\d+$/.test(values.count) || !/^\d+$/.test(values.seconds))
  throw new Error('count and seconds must be nonnegative integers');
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
function stop() {
  client.end(true);
}
client.on('connect', () =>
  client.subscribe(topics, { qos: 1 }, (error) => {
    if (error) {
      console.error(error.message);
      process.exitCode = 1;
      stop();
    } else
      console.error(
        `Independent MQTT observer subscribed: ${topics.join(', ')}. Open the source drawer now; compare sampleId.`,
      );
  }),
);
client.on('error', (error) => console.error(`MQTT observer: ${error.message}`));
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
    console.error(`Ignored malformed telemetry: ${error.message}`);
  }
});
const deadline =
  Number(values.seconds) > 0 ? setTimeout(stop, Number(values.seconds) * 1000) : null;
client.on('end', () => {
  if (deadline) clearTimeout(deadline);
});
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
