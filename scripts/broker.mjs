import aedes from 'aedes';
import net from 'node:net';
import { describeError } from './error-message.mjs';
const broker = aedes();
const server = net.createServer(broker.handle);
const port = Number(process.env.MQTT_PORT || 1883);
server.listen(port, '127.0.0.1', () =>
  console.log(`本地模拟 MQTT 3.1.1 消息服务正在监听 127.0.0.1:${port}`),
);
broker.on('client', (client) => console.log(`MQTT 客户端已连接：${client.id}`));
broker.on('clientError', (client, error) =>
  console.error(`MQTT 客户端连接异常：${client?.id || '未知客户端'}，${describeError(error)}`),
);
broker.on('publish', (packet, client) => {
  if (client && !packet.topic.startsWith('$SYS') && !packet.topic.endsWith('/telemetry'))
    console.log(
      `${client.id} 主题=${packet.topic} QoS=${packet.qos} 保留消息=${packet.retain ? '是' : '否'} 载荷=${packet.payload}`,
    );
});
process.on('SIGTERM', () => {
  broker.close(() => server.close(() => process.exit(0)));
});
