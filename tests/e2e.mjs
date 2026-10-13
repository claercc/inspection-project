import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, existsSync, createWriteStream, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import mqtt from 'mqtt';
import { chromium } from 'playwright';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const run = path.join(root, '.run', 'verification'); mkdirSync(run, { recursive: true });
const artifacts = path.join(root, 'docs', 'verification'); mkdirSync(artifacts, { recursive: true });
const base = 'http://127.0.0.1:18088', deviceId = 'robot-e2e';
const topic = kind => `inspection/${deviceId}/${kind}`;
const java = process.env.JAVA_BIN || 'java';
const env = { ...process.env, DEVICE_ID: deviceId, MQTT_PORT: '18883', MQTT_URL: 'tcp://127.0.0.1:18883',
  MODBUS_HOST: '127.0.0.1', MODBUS_BIND: '127.0.0.1', MODBUS_PORT: '11502', MODBUS_UNIT: '1',
  SERVER_BIND: '127.0.0.1', SERVER_PORT: '18080', API_URL: 'http://127.0.0.1:18080' };
const processes = new Map(), logStreams = [], checks = [], replies = new Map();
let browser, observer;
function start(role) {
  const launches = {
    broker: [process.execPath, ['scripts/broker.mjs']],
    robot: [java, ['-jar', 'simulator/robot/target/robot-simulator-0.1.0.jar']],
    edge: [java, ['-jar', 'edge/target/edge-0.1.0.jar']],
    server: [java, ['-jar', 'server/target/server-0.1.0.jar']],
    web: [process.execPath, ['web/node_modules/vite/bin/vite.js', 'preview', 'web', '--host', '127.0.0.1', '--port', '18088', '--strictPort']]
  };
  const [exe,args] = launches[role];
  const child = spawn(exe, args, { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const log = createWriteStream(path.join(run, `${role}.log`), { flags: 'a' }); logStreams.push(log);
  child.stdout.pipe(log, { end: false }); child.stderr.pipe(log, { end: false });
  child.on('error', e => log.write(String(e))); processes.set(role, child); return child;
}
async function stop(role) {
  const child = processes.get(role); if (!child) return;
  processes.delete(role);
  if (child.exitCode !== null) return;
  const done = new Promise(resolve => child.once('exit', resolve)); child.kill();
  await Promise.race([done, delay(4000)]);
}
async function until(fn, label, timeout = 20000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { try { const result = await fn(); if (result) return result; } catch {} await delay(150); }
  throw new Error(`Timeout: ${label} (see .run/verification logs)`);
}
async function state() { const r = await fetch(`${base}/api/state`); assert.equal(r.status,200); return r.json(); }
async function post(type) { return fetch(`${base}/api/commands`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type }) }); }
function pass(name, evidence) { checks.push({ name, status: 'PASS', evidence }); console.log(`PASS ${name}: ${evidence}`); }
function publish(command) { return new Promise((resolve,reject) => observer.publish(topic('commands'), JSON.stringify(command), { qos: 1, retain: false }, e => e ? reject(e) : resolve())); }
async function resultFor(id, count = 1) { return until(() => replies.get(id)?.length >= count && replies.get(id).at(-1), `MQTT result ${id}`); }
async function settled(type) { return until(async () => { const s = await state(); const c = s.commands.find(c => c.command.type === type); return c?.status === 'SUCCEEDED' && c; }, `${type} readback`); }
const report = { stage: 'A', verifiedAt: new Date().toISOString(), runtime: { node: process.version, java }, checks };
try {
  start('broker'); start('robot'); start('edge'); start('server'); start('web');
  await until(async () => (await state()).online, 'stack ready', 60000);
  observer = mqtt.connect('mqtt://127.0.0.1:18883', { clientId: 'inspection-e2e-observer', protocolVersion: 4 });
  await new Promise((resolve,reject) => { observer.once('connect',resolve); observer.once('error',reject); });
  observer.on('message', (_,data) => { const r = JSON.parse(data.toString()); const list = replies.get(r.commandId) || []; list.push(r); replies.set(r.commandId,list); });
  await new Promise((resolve,reject) => observer.subscribe(topic('results'), { qos: 1 }, e => e ? reject(e) : resolve()));
  const candidates = [process.env.BROWSER_EXE, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].filter(Boolean);
  const executablePath = candidates.find(p => existsSync(p));
  browser = await chromium.launch({ headless: true, ...(executablePath ? { executablePath } : {}) });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1120 } });
  const jsErrors = []; page.on('pageerror', e => jsErrors.push(e.message));
  await page.goto(base); await page.getByTestId('start').waitFor();
  await until(async () => page.getByTestId('start').isEnabled(), 'Vue telemetry online');
  await page.evaluate(() => { window.__states=[]; window.__events=new EventSource('/api/events'); window.__events.addEventListener('state', e => window.__states.push(JSON.parse(e.data))); });
  await page.getByTestId('start').click();
  const startCommand = await settled('START');
  await until(async () => (await state()).telemetry.x > 0, 'Modbus simulated movement');
  await until(async () => (await page.getByTestId('run-state').innerText()) === '运行中', 'Vue running');
  await page.waitForFunction(() => window.__states.some(s => s.telemetry?.runState === 'RUNNING'));
  pass('Vue → REST → MQTT → edge → FC06/FC03 → SSE → Vue', `START ${startCommand.command.commandId}; moving position; device readback SUCCEEDED`);
  const conflict = await post('START'); assert.equal(conflict.status,409);
  pass('single active movement guard', 'START while RUNNING returns HTTP 409');
  const writeCount = (await state()).telemetry.writeCount;
  await resultFor(startCommand.command.commandId);
  await publish(startCommand.command); await resultFor(startCommand.command.commandId,2);
  assert.equal((await state()).telemetry.writeCount,writeCount);
  pass('duplicate MQTT command', `same commandId delivered twice, simulator FC06 writeCount stays ${writeCount}`);
  await page.screenshot({ path: path.join(artifacts,'stage-a-desktop.png'), fullPage: true });
  await page.getByTestId('stop').click(); const stopCommand = await settled('STOP');
  await until(async () => (await page.getByTestId('run-state').innerText()) === '已停止', 'Vue stopped');
  const stoppedX = (await state()).telemetry.x; await delay(1000); assert.equal((await state()).telemetry.x,stoppedX);
  pass('Vue STOP + physical feedback', `STOP ${stopCommand.command.commandId}; position stops changing`);
  const invalid = { commandId:'invalid-command',deviceId,type:'INVALID',parameters:{},createdAt:new Date().toISOString(),expiresAt:new Date(Date.now()+8000).toISOString() };
  const writesBeforeInvalid = (await state()).telemetry.writeCount;
  await publish(invalid); assert.equal((await resultFor(invalid.commandId)).status,'FAILED');
  const expired = { ...invalid, commandId:'expired-command',type:'START',createdAt:new Date(Date.now()-20000).toISOString(),expiresAt:new Date(Date.now()-10000).toISOString() };
  await publish(expired); assert.equal((await resultFor(expired.commandId)).status,'FAILED');
  assert.equal((await state()).telemetry.writeCount,writesBeforeInvalid);
  pass('invalid and expired controls', 'edge rejects both with FAILED, no device write');
  const writeBefore = (await state()).telemetry.writeCount;
  await publish({...startCommand.command,type:'STOP'}); assert.equal((await resultFor(startCommand.command.commandId,3)).status,'FAILED');
  assert.equal((await state()).telemetry.writeCount,writeBefore);
  pass('commandId payload collision', 'different payload under same commandId rejected, no FC06');
  await stop('robot');
  await until(async () => (await state()).connectionStatus === 'ROBOT_OFFLINE', 'robot offline');
  await until(async () => (await page.getByTestId('run-state').innerText()) === '状态不可用', 'Vue hides offline telemetry');
  const offlineReply = await post('STOP'); assert.equal(offlineReply.status,200); const offline = await offlineReply.json();
  await until(async () => (await state()).commands.find(c => c.command.commandId === offline.command.commandId)?.status === 'FAILED', 'definite offline failure');
  pass('robot offline + definite failure', 'stopping independent robot process displays ROBOT_OFFLINE, STOP fails before write');
  start('robot'); await until(async () => (await state()).online && (await state()).telemetry.writeCount === 0, 'robot reconnect');
  pass('Modbus reconnect', 'restart simulator → fresh FC03 state, STOPPED, zero writes');
  await stop('edge'); await until(async () => (await state()).connectionStatus === 'EDGE_STALE', 'edge stale');
  assert.equal((await post('START')).status,409);
  const unknownReply = await post('STOP'); const unknown = await unknownReply.json();
  await until(async () => (await state()).commands.find(c => c.command.commandId === unknown.command.commandId)?.status === 'RESULT_UNKNOWN', 'unconfirmed command expiry');
  pass('edge stale + result unknown', 'edge stopped → stale state; no result in 8s → RESULT_UNKNOWN; no automatic retry');
  const retained = { ...invalid, commandId:'retained-control', type:'START', createdAt:new Date().toISOString(), expiresAt:new Date(Date.now()+8000).toISOString() };
  await new Promise((resolve,reject) => observer.publish(topic('commands'),JSON.stringify(retained),{qos:1,retain:true},e=>e?reject(e):resolve()));
  start('edge'); await until(async () => (await state()).online, 'edge reconnect');
  assert.equal((await state()).telemetry.writeCount,0);
  assert.equal(replies.has(retained.commandId),false);
  await new Promise((resolve,reject) => observer.publish(topic('commands'),'',{qos:1,retain:true},e=>e?reject(e):resolve()));
  pass('no control replay after edge restart', 'clean MQTT session; retained control delivered at subscription ignored; simulator writeCount remains 0');
  await until(async () => page.getByTestId('start').isEnabled(), 'Vue recovered');
  await page.getByTestId('start').click();
  await until(async () => (await state()).telemetry.runState === 'RUNNING', 'start after recovery');
  await until(async () => page.getByTestId('stop').isEnabled(), 'STOP after recovery'); await page.getByTestId('stop').click();
  await until(async () => (await state()).telemetry.runState === 'STOPPED' && !(await state()).commands.some(c => ['SENT','PENDING'].includes(c.status)), 'stop after recovery');
  const writesBeforeBroker = (await state()).telemetry.writeCount;
  await stop('broker'); await until(async () => !(await state()).mqttConnected, 'broker offline');
  assert.equal((await post('START')).status,503);
  start('broker'); await until(async () => (await state()).online, 'broker reconnect',30000);
  assert.equal((await state()).telemetry.writeCount,writesBeforeBroker);
  pass('Broker loss + reconnect', 'HTTP 503 while MQTT offline, server/edge resubscribe and read current device state; no replay');
  await until(async () => (await page.getByTestId('connection').innerText()) === '链路在线', 'Vue Broker recovery');
  await page.setViewportSize({ width:390,height:844 });
  await page.screenshot({ path:path.join(artifacts,'stage-a-mobile.png'),fullPage:true });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),true);
  assert.deepEqual(jsErrors,[]); pass('Vue browser validation', 'desktop + mobile screenshots, no uncaught JavaScript errors, mobile has no page overflow');
  const final = await state(); report.finalState = final; report.status = 'PASS';
} catch (e) { report.status='FAIL';report.error=e.stack;console.error(e);process.exitCode=1; }
finally {
  await browser?.close(); observer?.end(true);
  for (const role of ['web','server','edge','robot','broker']) await stop(role);
  logStreams.forEach(s => s.end());
  writeFileSync(path.join(artifacts,'stage-a-report.json'),JSON.stringify(report,null,2));
}
