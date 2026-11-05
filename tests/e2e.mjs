import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, existsSync, createWriteStream, readFileSync, writeFileSync } from 'node:fs';
import { createConnection } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import mqtt from 'mqtt';
import { chromium } from 'playwright';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const run = path.join(root, '.run', 'verification');
mkdirSync(run, { recursive: true });
const artifacts = path.join(root, 'docs', 'verification');
mkdirSync(artifacts, { recursive: true });
const artifactPrefix = process.env.VERIFICATION_PREFIX || 'stage-a';
const base = 'http://127.0.0.1:18088',
  deviceId = 'robot-e2e';
/**
 * 构造隔离测试设备的 MQTT 主题，避免测试命令进入标准演示设备的主题。
 * @param {'commands'|'telemetry'|'results'} kind 消息类别。
 * @returns {string} 当前测试设备的完整主题。
 */
const topic = (kind) => `inspection/${deviceId}/${kind}`;
const java = process.env.JAVA_BIN || 'java';
const env = {
  ...process.env,
  DEVICE_ID: deviceId,
  MQTT_PORT: '18883',
  MQTT_URL: 'tcp://127.0.0.1:18883',
  MODBUS_HOST: '127.0.0.1',
  MODBUS_BIND: '127.0.0.1',
  MODBUS_PORT: '11502',
  MODBUS_UNIT: '1',
  SERVER_BIND: '127.0.0.1',
  SERVER_PORT: '18080',
  API_URL: 'http://127.0.0.1:18080',
};
const processes = new Map(),
  logStreams = [],
  checks = [],
  replies = new Map();
let browser, observer;
/**
 * 按预定义角色启动隔离测试进程并接管日志；可用 start('edge') 启动 edge。
 * @param {'broker'|'robot'|'edge'|'server'|'web'|'sourceObserver'} role 进程角色。
 * @returns {import('node:child_process').ChildProcess} 已发起启动的子进程；不表示服务已就绪。
 */
function start(role) {
  const launches = {
    broker: [process.execPath, ['scripts/broker.mjs']],
    robot: [
      java,
      ['-Dfile.encoding=UTF-8', '-jar', 'simulator/robot/target/robot-simulator-0.1.0.jar'],
    ],
    edge: [java, ['-Dfile.encoding=UTF-8', '-jar', 'edge/target/edge-0.1.0.jar']],
    server: [java, ['-Dfile.encoding=UTF-8', '-jar', 'server/target/server-0.1.0.jar']],
    web: [
      process.execPath,
      [
        'web/node_modules/vite/bin/vite.js',
        'preview',
        'web',
        '--host',
        '127.0.0.1',
        '--port',
        '18088',
        '--strictPort',
      ],
    ],
    sourceObserver: [
      process.execPath,
      [
        'scripts/observe-telemetry.mjs',
        '--broker',
        'mqtt://127.0.0.1:18883',
        '--device',
        deviceId,
        '--seconds',
        '120',
        '--output',
        path.join(run, 'source-observer.jsonl'),
      ],
    ],
  };
  const [exe, args] = launches[role];
  const child = spawn(exe, args, {
    cwd: root,
    env,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const log = createWriteStream(path.join(run, `${role}.log`), { flags: 'a' });
  logStreams.push(log);
  child.stdout.pipe(log, { end: false });
  child.stderr.pipe(log, { end: false });
  child.on('error', (e) => log.write(String(e)));
  processes.set(role, child);
  return child;
}
/**
 * 请求关闭本测试启动的指定角色，并等待退出事件或最多四秒。
 * @param {string} role 已登记的进程角色；未登记或已退出时直接结束。
 * @returns {Promise<void>} 清理等待结束后兑现；达到等待上限不代表已确认进程退出。
 */
async function stop(role) {
  const child = processes.get(role);
  if (!child) return;
  processes.delete(role);
  if (child.exitCode !== null) return;
  const done = new Promise((resolve) => child.once('exit', resolve));
  child.kill();
  await Promise.race([done, delay(4000)]);
}
/**
 * 轮询一个条件，适用于等待服务就绪、SSE 更新和设备反馈；期间的异常允许下轮重试。
 * @template T
 * @param {() => T|Promise<T>} fn 每轮执行的检查；返回假值表示继续等待。
 * @param {string} label 超时时显示的场景说明。
 * @param {number} [timeout=20000] 最大等待时间，单位毫秒。
 * @returns {Promise<T>} 首个为真值的检查结果。
 * @throws {Error} 超过截止时间仍未得到真值时，Promise 拒绝。
 * @example
 * await until(async () => (await state()).online, '设备上线');
 */
async function until(fn, label, timeout = 20000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try {
      const result = await fn();
      if (result) return result;
    } catch {
      // The isolated stack can still be starting or reconnecting; retry until the deadline.
    }
    await delay(150);
  }
  throw new Error(`等待超时：${label}（请查看 .run/verification 日志）`);
}
/**
 * 查询隔离栈 /api/state 并断言 HTTP 200，无参数。
 * @returns {Promise<import('../web/src/features/device/api.js').DeviceState>} 当前设备与命令历史快照。
 * @throws {Error} 网络异常、HTTP 状态断言失败或响应解析失败时，Promise 拒绝。
 */
async function state() {
  const r = await fetch(`${base}/api/state`);
  assert.equal(r.status, 200);
  return r.json();
}
/**
 * 向隔离栈提交控制请求，供正常和拒绝场景断言使用。
 * @param {string} type 控制类型；负向测试也可传非法值。
 * @returns {Promise<Response>} 原始 HTTP 响应，不主动把非 2xx 状态转换成异常。
 */
async function post(type) {
  return fetch(`${base}/api/commands`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type }),
  });
}
/**
 * 将已完成的场景加入报告并输出通过信息，断言应在调用前完成。
 * @param {string} name 场景名称。
 * @param {string} evidence 描述实际观察结果的文字。
 * @returns {void} 更新 checks 并写控制台。
 */
function pass(name, evidence) {
  checks.push({ name, status: 'PASS', evidence });
  console.log(`通过 ${name}：${evidence}`);
}
/**
 * 经独立 MQTT 客户端投递控制消息，供重复投递、非法参数等通信场景调用。
 * @param {object} command 完整的原始命令对象，包含编号、设备、类型和有效期。
 * @returns {Promise<void>} MQTT 发布回调成功后兑现，不等待设备执行结果。
 * @throws {Error} 发布回调收到错误时，Promise 拒绝。
 */
function publish(command) {
  return new Promise((resolve, reject) =>
    observer.publish(topic('commands'), JSON.stringify(command), { qos: 1, retain: false }, (e) =>
      e ? reject(e) : resolve(),
    ),
  );
}
/**
 * 等待独立订阅者观察到指定命令的足够多条回执，供重复投递验证使用。
 * @param {string} id 命令 commandId。
 * @param {number} [count=1] 至少需要观测到的回执条数。
 * @returns {Promise<object>} 达到条数要求时，该命令最后一条 MQTT 回执。
 * @throws {Error} 等待超过 until 的截止时间时，Promise 拒绝。
 */
async function resultFor(id, count = 1) {
  return until(
    () => replies.get(id)?.length >= count && replies.get(id).at(-1),
    `MQTT 命令回执 ${id}`,
  );
}
/**
 * 等待服务端记录中某类型的最近一条命令变为成功，供首次启停闭环断言使用。
 * @param {'START'|'STOP'} type 要查询的命令类型；此辅助函数不按 commandId 区分同类型命令。
 * @returns {Promise<import('../web/src/features/device/api.js').CommandView>} 最近一条已确认成功的匹配记录。
 * @throws {Error} 未能在截止时间内观察到成功记录时，Promise 拒绝。
 */
async function settled(type) {
  return until(async () => {
    const s = await state();
    const c = s.commands.find((c) => c.command.type === type);
    return c?.status === 'SUCCEEDED' && c;
  }, `${type} 设备读回`);
}
/**
 * 在独立观察脚本输出的 JSONL 中查找同一次采样，供来源抽屉的数据核对使用。
 * @param {string} sampleId edge 生成的采样编号。
 * @param {string} mqttTopic 该采样实际接收的完整 MQTT 主题。
 * @returns {object|null} 同时匹配编号和主题的观察记录；文件未生成或暂未找到时返回 null。
 */
function independentlyObserved(sampleId, mqttTopic) {
  const file = path.join(run, 'source-observer.jsonl');
  if (!existsSync(file)) return null;
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    try {
      const record = JSON.parse(line);
      if (record.telemetry.evidence?.sampleId === sampleId && record.topic === mqttTopic)
        return record;
    } catch {
      // The observer may still be appending its last JSON line; retry on the next read.
    }
  }
  return null;
}
/**
 * 使用独立 TCP Socket 直接执行 FC03 读取温度寄存器地址 4，无参数，不经过 server/edge。
 * @returns {Promise<number>} 本次读取的无符号原始寄存器值，尚未除以 10 换算成摄氏度。
 * @throws {Error} 连接异常、两秒内未读到完整响应或协议断言失败时，Promise 拒绝。
 */
function readTemperatureDirectly() {
  return new Promise((resolve, reject) => {
    const socket = createConnection({ host: '127.0.0.1', port: 11502 });
    const deadline = setTimeout(() => socket.destroy(new Error('独立 Modbus 读取超时')), 2000);
    let bytes = Buffer.alloc(0);
    socket.on('connect', () => socket.write(Buffer.from([0, 77, 0, 0, 0, 6, 1, 3, 0, 4, 0, 1])));
    socket.on('data', (chunk) => {
      bytes = Buffer.concat([bytes, chunk]);
      if (bytes.length < 11) return;
      try {
        assert.equal(bytes.readUInt16BE(0), 77);
        assert.equal(bytes.readUInt16BE(4), 5);
        assert.equal(bytes[6], 1);
        assert.equal(bytes[7], 3);
        assert.equal(bytes[8], 2);
        resolve(bytes.readUInt16BE(9));
        socket.end();
      } catch (error) {
        reject(error);
        socket.destroy();
      }
    });
    socket.on('error', reject);
    socket.on('close', () => clearTimeout(deadline));
  });
}
const report = {
  stage: 'A',
  verifiedAt: new Date().toISOString(),
  runtime: { node: process.version, java },
  checks,
};
try {
  start('broker');
  start('robot');
  start('edge');
  start('server');
  start('web');
  await until(async () => (await state()).online, '隔离测试栈就绪', 60000);
  start('sourceObserver');
  const initialState = await until(async () => {
    const s = await state();
    return (
      s.telemetry?.evidence &&
      independentlyObserved(s.telemetry.evidence.sampleId, s.telemetryTopic) &&
      s
    );
  }, '独立 MQTT 订阅者收到来源采样');
  const directRawTemperature = await readTemperatureDirectly();
  assert.equal(initialState.telemetry.evidence.registers[4], directRawTemperature);
  assert.equal(initialState.telemetry.temperature, directRawTemperature / 10);
  assert.equal(initialState.telemetry.evidence.host, '127.0.0.1');
  assert.equal(initialState.telemetry.evidence.port, 11502);
  assert.equal(initialState.telemetry.evidence.unitId, 1);
  assert.equal(initialState.telemetry.evidence.functionCode, 3);
  assert.equal(initialState.telemetry.evidence.startAddress, 0);
  assert.equal(initialState.telemetry.evidence.registers.length, 7);
  pass('原始寄存器来源核对', '独立 FC03 读取与边缘端原始寄存器、温度换算值及独立 MQTT 记录一致');
  const invalidTypeReply = await post('INVALID');
  assert.equal(invalidTypeReply.status, 400);
  const invalidTypeProblem = await invalidTypeReply.json();
  assert.equal(invalidTypeProblem.title, '控制请求未被接受');
  assert.match(invalidTypeProblem.detail, /控制类型必须为/);
  const malformedReply = await fetch(`${base}/api/commands`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{"type":',
  });
  assert.equal(malformedReply.status, 400);
  const malformedProblem = await malformedReply.json();
  assert.equal(malformedProblem.title, '请求内容格式错误');
  assert.match(malformedProblem.detail, /请求内容无法解析/);
  pass('中文接口错误提示', '非法控制类型和格式错误的 JSON 请求均返回中文标题与原因');
  observer = mqtt.connect('mqtt://127.0.0.1:18883', {
    clientId: 'inspection-e2e-observer',
    protocolVersion: 4,
  });
  await new Promise((resolve, reject) => {
    observer.once('connect', resolve);
    observer.once('error', reject);
  });
  observer.on('message', (_, data) => {
    const r = JSON.parse(data.toString());
    const list = replies.get(r.commandId) || [];
    list.push(r);
    replies.set(r.commandId, list);
  });
  await new Promise((resolve, reject) =>
    observer.subscribe(topic('results'), { qos: 1 }, (e) => (e ? reject(e) : resolve())),
  );
  const candidates = [
    process.env.BROWSER_EXE,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  ].filter(Boolean);
  const executablePath = candidates.find((p) => existsSync(p));
  browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1120 } });
  await page.addInitScript(() => {
    const BaseEventSource = window.EventSource;
    window.__openEventSources = 0;
    window.EventSource = class extends BaseEventSource {
      constructor(...args) {
        super(...args);
        window.__openEventSources++;
      }
      close() {
        if (!this.__closed) {
          window.__openEventSources--;
          this.__closed = true;
        }
        super.close();
      }
    };
  });
  const jsErrors = [];
  page.on('pageerror', (e) => jsErrors.push(e.message));
  await page.goto(base);
  await page.getByTestId('start').waitFor();
  await until(async () => page.getByTestId('start').isEnabled(), '页面遥测已上线');
  assert.equal(await page.locator('.nav-item').count(), 3);
  assert.equal(await page.locator('.nav-item').filter({ hasText: '摄像头' }).isEnabled(), true);
  assert.equal(await page.locator('.nav-item').filter({ hasText: '采集记录' }).isDisabled(), true);
  await page.getByTestId('open-diagnostics').click();
  await page.getByRole('heading', { name: '连接与通信诊断' }).waitFor();
  await page.keyboard.press('Escape');
  assert.equal(await page.getByRole('heading', { name: '连接与通信诊断' }).isVisible(), false);
  pass('设备工作台入口', '保留三个入口，未接入功能禁用，诊断按需打开并支持 Esc 关闭');
  await page.locator('.nav-item').filter({ hasText: '摄像头' }).click();
  await page.getByRole('heading', { name: /摄像头/u }).waitFor();
  await page.waitForFunction(
    () => document.querySelector('[data-testid="camera-preview"]')?.naturalWidth > 0,
  );
  assert.equal(await page.getByText('模拟摄像头', { exact: true }).isVisible(), true);
  await page.getByTestId('capture-camera').click();
  await page.locator('.camera-capture-list article').waitFor();
  assert.equal(
    await page
      .locator('.camera-capture-list article img')
      .evaluate((image) => image.naturalWidth > 0),
    true,
  );
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载图片 ↓' }).click();
  assert.match((await downloadPromise).suggestedFilename(), /^camera-.*\.jpg$/u);
  pass('模拟摄像头预览与抓拍', '模拟标识清晰，预览、手动抓拍和 JPEG 下载均可使用');
  await page.locator('.nav-item').filter({ hasText: '设备监控' }).click();
  await page.getByTestId('start').waitFor();
  await page.getByTestId('temperature-source').click();
  await page.getByRole('heading', { name: '温度数据来源' }).waitFor();
  const drawerTelemetry = JSON.parse(await page.getByTestId('source-telemetry-json').textContent());
  const drawerTopic = await page.getByTestId('source-topic').innerText();
  const independentRecord = await until(
    () => independentlyObserved(drawerTelemetry.evidence.sampleId, drawerTopic),
    '独立 MQTT 客户端中的同一采样',
  );
  assert.deepEqual(drawerTelemetry, independentRecord.telemetry);
  assert.equal(
    Number(await page.getByTestId('source-raw-temperature').innerText()),
    directRawTemperature,
  );
  assert.equal(
    parseFloat(await page.getByTestId('source-temperature').innerText()),
    independentRecord.telemetry.temperature,
  );
  await delay(1100);
  assert.equal(
    await page.getByTestId('source-sample-id').innerText(),
    drawerTelemetry.evidence.sampleId,
  );
  await page.getByTestId('source-refresh').click();
  assert.notEqual(
    await page.getByTestId('source-sample-id').innerText(),
    drawerTelemetry.evidence.sampleId,
  );
  writeFileSync(
    path.join(artifacts, `${artifactPrefix}-matched-sample.json`),
    JSON.stringify({ drawerTelemetry, independentRecord, directRawTemperature }, null, 2),
  );
  await page
    .getByTestId('temperature-source-drawer')
    .screenshot({ path: path.join(artifacts, `${artifactPrefix}-source-desktop.png`) });
  await page.keyboard.press('Escape');
  pass(
    '温度来源抽屉与独立采样核对',
    '原始寄存器、采样编号、温度和主题与独立 MQTT 记录一致，快照仅在刷新后切换',
  );
  await page.evaluate(() => {
    window.__states = [];
    window.__events = new EventSource('/api/events');
    window.__events.addEventListener('state', (e) => window.__states.push(JSON.parse(e.data)));
  });
  await page.getByTestId('start').click();
  const startCommand = await settled('START');
  assert.match(startCommand.message, /FC06 写入.*FC03 读回/);
  assert.equal(
    (await state()).diagnostics.every((event) => /[\u4e00-\u9fff]/u.test(event.summary)),
    true,
  );
  await until(async () => (await state()).telemetry.x > 0, 'Modbus 模拟设备运动');
  await until(
    async () => (await page.getByTestId('run-state').innerText()) === '运行中',
    '页面显示运行中',
  );
  await page.waitForFunction(() =>
    window.__states.some((s) => s.telemetry?.runState === 'RUNNING'),
  );
  pass(
    '网页控制到设备读回的完整链路',
    `启动命令 ${startCommand.command.commandId} 已确认，设备位置开始变化`,
  );
  await page.getByTestId(`command-detail-${startCommand.command.commandId}`).click();
  await page.getByRole('heading', { name: '设备指令详情' }).waitFor();
  assert.equal(
    await page
      .locator('.command-modal')
      .getByText(startCommand.command.commandId, { exact: true })
      .isVisible(),
    true,
  );
  await page.keyboard.press('Escape');
  pass('页面指令详情', '弹窗显示完整命令编号和服务端已记录的关联事件');
  const subscriptionsBefore = await page.evaluate(() => window.__openEventSources);
  const writesBeforeReconnect = (await state()).telemetry.writeCount;
  await page.getByTestId('open-diagnostics').click();
  for (let count = 0; count < 3; count++) {
    await page.getByTestId('reconnect').click();
    await until(
      async () => (await page.getByTestId('connection').innerText()) === '链路在线',
      '手动重连实时订阅',
    );
  }
  assert.equal(await page.evaluate(() => window.__openEventSources), subscriptionsBefore);
  assert.equal((await state()).telemetry.writeCount, writesBeforeReconnect);
  await page.keyboard.press('Escape');
  pass('页面 SSE 订阅生命周期', '连续三次手动重连均关闭旧订阅，设备写入次数不增加');
  const conflict = await post('START');
  assert.equal(conflict.status, 409);
  pass('运行状态下的重复启动保护', '机器人运行中再次启动返回 HTTP 409');
  const writeCount = (await state()).telemetry.writeCount;
  await resultFor(startCommand.command.commandId);
  await publish(startCommand.command);
  await resultFor(startCommand.command.commandId, 2);
  assert.equal((await state()).telemetry.writeCount, writeCount);
  pass('重复 MQTT 控制命令', `同一命令重复投递，模拟器 FC06 写入次数仍为 ${writeCount}`);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await page.screenshot({
    path: path.join(artifacts, `${artifactPrefix}-desktop.png`),
    fullPage: true,
  });
  await page.getByTestId('stop').click();
  const stopCommand = await settled('STOP');
  await until(
    async () => (await page.getByTestId('run-state').innerText()) === '已停止',
    '页面显示已停止',
  );
  const stoppedX = (await state()).telemetry.x;
  await delay(1000);
  assert.equal((await state()).telemetry.x, stoppedX);
  pass(
    '页面停止操作与设备读回',
    `停止命令 ${stopCommand.command.commandId} 已确认，设备位置停止变化`,
  );
  const invalid = {
    commandId: 'invalid-command',
    deviceId,
    type: 'INVALID',
    parameters: {},
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 8000).toISOString(),
  };
  const writesBeforeInvalid = (await state()).telemetry.writeCount;
  await publish(invalid);
  assert.equal((await resultFor(invalid.commandId)).status, 'FAILED');
  const expired = {
    ...invalid,
    commandId: 'expired-command',
    type: 'START',
    createdAt: new Date(Date.now() - 20000).toISOString(),
    expiresAt: new Date(Date.now() - 10000).toISOString(),
  };
  await publish(expired);
  assert.equal((await resultFor(expired.commandId)).status, 'FAILED');
  assert.equal((await state()).telemetry.writeCount, writesBeforeInvalid);
  pass('非法类型与过期控制', '边缘端拒绝两类命令并返回执行失败，没有新增设备写入');
  const writeBefore = (await state()).telemetry.writeCount;
  await publish({ ...startCommand.command, type: 'STOP' });
  assert.equal((await resultFor(startCommand.command.commandId, 3)).status, 'FAILED');
  assert.equal((await state()).telemetry.writeCount, writeBefore);
  pass('命令编号与内容冲突', '同一命令编号携带不同内容被拒绝，没有执行 FC06 写入');
  await stop('robot');
  await until(async () => (await state()).connectionStatus === 'ROBOT_OFFLINE', '机器人离线');
  await until(
    async () => (await page.getByTestId('run-state').innerText()) === '状态不可用',
    '页面隐藏失效遥测',
  );
  assert.equal((await state()).telemetry.evidence, null);
  await page.getByTestId('temperature-source').click();
  assert.equal(await page.getByTestId('source-live-status').innerText(), '当前实时采集不可用');
  assert.equal(await page.getByTestId('source-raw-temperature').count(), 0);
  assert.equal(
    await page.getByRole('heading', { name: '没有可核对的寄存器样本' }).isVisible(),
    true,
  );
  await page.keyboard.press('Escape');
  pass('设备离线时的来源抽屉', '离线采样没有来源证据，抽屉不展示占位零寄存器或有效温度');
  const offlineReply = await post('STOP');
  assert.equal(offlineReply.status, 200);
  const offline = await offlineReply.json();
  await until(
    async () =>
      (await state()).commands.find((c) => c.command.commandId === offline.command.commandId)
        ?.status === 'FAILED',
    '写入前设备离线的明确失败',
  );
  pass('机器人离线与明确失败反馈', '停止模拟器进程后显示机器人离线，停止命令在写入前失败');
  start('robot');
  await until(
    async () => (await state()).online && (await state()).telemetry.writeCount === 0,
    '机器人重新连接',
  );
  pass('Modbus 连接恢复', '重启模拟器后收到新鲜 FC03 采样，设备已停止，写入次数为零');
  await stop('edge');
  const pendingStartReply = await post('START');
  assert.equal(pendingStartReply.status, 200);
  const pendingStart = await pendingStartReply.json();
  await until(
    async () =>
      (await state()).commands.find(
        (entry) => entry.command.commandId === pendingStart.command.commandId,
      )?.status === 'SENT',
    '边缘端反馈不可用时发布启动命令',
  );
  await until(async () => page.getByTestId('stop').isEnabled(), '启动等待反馈期间停止按钮仍可用');
  await page.getByTestId('stop').click();
  const pendingStopCommand = await until(async () => {
    const latest = (await state()).commands[0];
    return latest?.command.type === 'STOP' && latest.status === 'SENT' && latest;
  }, '启动反馈到达前通过页面发布停止命令');
  assert.equal((await post('STOP')).status, 409);
  pass(
    '启动未确认期间提交停止命令',
    '启动等待反馈时页面停止按钮可用，服务端接受停止并拒绝重复的待确认停止命令',
  );

  const latestTelemetry = (await state()).telemetry;
  const lateTelemetry = {
    ...latestTelemetry,
    capturedAt: new Date(new Date(latestTelemetry.capturedAt).getTime() - 1000).toISOString(),
    runState: 'RUNNING',
    temperature: 99,
  };
  await new Promise((resolve, reject) =>
    observer.publish(topic('telemetry'), JSON.stringify(lateTelemetry), { qos: 1 }, (error) =>
      error ? reject(error) : resolve(),
    ),
  );
  await delay(300);
  assert.deepEqual((await state()).telemetry, latestTelemetry);
  pass('忽略迟到遥测', '较旧的 MQTT 采样不能覆盖当前状态或测量值');
  await until(async () => (await state()).connectionStatus === 'EDGE_STALE', '边缘端采样已过期');
  await until(
    async () => (await page.getByTestId('connection').innerText()) === '边缘数据已过期',
    '页面显示边缘数据已过期',
  );
  await page.getByTestId('temperature-source').click();
  assert.equal(await page.getByTestId('source-live-status').innerText(), '当前实时采集不可用');
  assert.equal(await page.getByTestId('source-refresh').isDisabled(), true);
  await page.keyboard.press('Escape');
  pass('采样过期时的来源抽屉', '旧采样可作为锁定快照查看，当前不可用提示持续显示且刷新按钮禁用');
  assert.equal((await post('START')).status, 409);
  await until(
    async () =>
      (await state()).commands.find(
        (c) => c.command.commandId === pendingStopCommand.command.commandId,
      )?.status === 'RESULT_UNKNOWN',
    '未确认命令到期',
  );
  pass(
    '边缘数据过期与执行结果未知',
    '停止边缘端后数据过期，八秒内未收到结果则记为结果未知，不自动重试',
  );
  const retained = {
    ...invalid,
    commandId: 'retained-control',
    type: 'START',
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 8000).toISOString(),
  };
  await new Promise((resolve, reject) =>
    observer.publish(topic('commands'), JSON.stringify(retained), { qos: 1, retain: true }, (e) =>
      e ? reject(e) : resolve(),
    ),
  );
  start('edge');
  await until(async () => (await state()).online, '边缘端重新连接');
  assert.equal((await state()).telemetry.writeCount, 0);
  assert.equal(replies.has(retained.commandId), false);
  await new Promise((resolve, reject) =>
    observer.publish(topic('commands'), '', { qos: 1, retain: true }, (e) =>
      e ? reject(e) : resolve(),
    ),
  );
  pass(
    '边缘端重启后不回放历史控制',
    '使用全新 MQTT 会话，忽略订阅时的保留控制消息，模拟器写入次数保持为零',
  );
  await until(async () => page.getByTestId('start').isEnabled(), '页面连接恢复');
  await page.getByTestId('start').click();
  await until(async () => (await state()).telemetry.runState === 'RUNNING', '恢复后启动机器人');
  await until(async () => page.getByTestId('stop').isEnabled(), '恢复后停止按钮可用');
  await page.getByTestId('stop').click();
  await until(
    async () =>
      (await state()).telemetry.runState === 'STOPPED' &&
      !(await state()).commands.some((c) => ['SENT', 'PENDING'].includes(c.status)),
    '恢复后停止机器人',
  );
  const writesBeforeBroker = (await state()).telemetry.writeCount;
  await stop('broker');
  await until(async () => !(await state()).mqttConnected, 'MQTT 消息服务离线');
  const brokerOfflineReply = await post('START');
  assert.equal(brokerOfflineReply.status, 503);
  const brokerOfflineProblem = await brokerOfflineReply.json();
  assert.equal(brokerOfflineProblem.title, '控制请求未被接受');
  assert.equal(brokerOfflineProblem.detail, 'MQTT 消息服务未连接');
  start('broker');
  await until(async () => (await state()).online, 'MQTT 消息服务重新连接', 30000);
  assert.equal((await state()).telemetry.writeCount, writesBeforeBroker);
  pass(
    'MQTT 消息服务断开与恢复',
    '消息服务离线时返回 HTTP 503，恢复后两端重新订阅并读取当前状态，不回放控制',
  );
  await until(
    async () => (await page.getByTestId('connection').innerText()) === '链路在线',
    '页面显示消息链路恢复',
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await page.screenshot({
    path: path.join(artifacts, `${artifactPrefix}-mobile.png`),
    fullPage: true,
  });
  assert.equal(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    true,
  );
  await page.getByTestId('temperature-source').click();
  assert.equal(await page.getByRole('heading', { name: '温度数据来源' }).isVisible(), true);
  assert.equal(
    await page
      .getByTestId('temperature-source-drawer')
      .evaluate((element) => element.scrollWidth <= element.clientWidth),
    true,
  );
  await page
    .getByTestId('temperature-source-drawer')
    .screenshot({ path: path.join(artifacts, `${artifactPrefix}-source-mobile.png`) });
  await page.keyboard.press('Escape');
  pass('手机端来源核对', '390 像素宽度下来源抽屉无横向溢出，并可用 Esc 关闭');
  assert.deepEqual(jsErrors, []);
  pass('页面浏览器验证', '已保存桌面和手机截图，无未捕获脚本错误，手机页面无整页横向溢出');
  const final = await state();
  report.finalState = final;
  report.status = 'PASS';
} catch (e) {
  report.status = 'FAIL';
  report.error = e.stack;
  console.error(e);
  process.exitCode = 1;
} finally {
  await browser?.close();
  observer?.end(true);
  for (const role of ['web', 'server', 'edge', 'robot', 'sourceObserver', 'broker'])
    await stop(role);
  logStreams.forEach((s) => s.end());
  writeFileSync(
    path.join(artifacts, `${artifactPrefix}-report.json`),
    JSON.stringify(report, null, 2),
  );
}
