import aedes from 'aedes';
import net from 'node:net';
const broker = aedes();
const server = net.createServer(broker.handle);
const port = Number(process.env.MQTT_PORT || 1883);
server.listen(port, '127.0.0.1', () => console.log(`Local simulation MQTT 3.1.1 Broker on 127.0.0.1:${port}`));
broker.on('client', client => console.log(`MQTT connected: ${client.id}`));
broker.on('clientError', (client, error) => console.error(client?.id, error.message));
broker.on('publish', (packet, client) => {
  if (client && !packet.topic.startsWith('$SYS') && !packet.topic.endsWith('/telemetry'))
    console.log(`${client.id} ${packet.topic} QoS=${packet.qos} retain=${packet.retain} ${packet.payload}`);
});
process.on('SIGTERM', () => { broker.close(() => server.close(() => process.exit(0))); });
