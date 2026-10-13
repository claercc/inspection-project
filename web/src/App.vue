<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
const state = ref(null), stream = ref(false), error = ref(''), sending = ref(false);
const telemetry = computed(() => state.value?.telemetry);
const fresh = computed(() => stream.value && state.value?.online);
const running = computed(() => fresh.value && telemetry.value?.runState === 'RUNNING');
const pending = computed(() => state.value?.commands.some(c => ['PENDING', 'SENT'].includes(c.status)));
const labels = { ONLINE: '链路在线', BROKER_OFFLINE: 'Broker 离线', EDGE_STALE: '边缘数据已过期', ROBOT_OFFLINE: '机器人离线', SUCCEEDED: '设备反馈已确认', SENT: '命令已发送', PENDING: '准备发送', FAILED: '执行失败', RESULT_UNKNOWN: '结果未知' };
const statusText = computed(() => !stream.value ? '实时连接中断' : labels[state.value?.connectionStatus] || '等待设备连接');
const time = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
const metric = (field, unit) => fresh.value ? `${telemetry.value?.[field]?.toFixed(1) ?? '—'}${unit}` : '—';
const robotX = computed(() => 60 + (telemetry.value?.x || 0) * 4.6);
let events;
onMounted(() => {
  events = new EventSource('/api/events');
  events.addEventListener('state', e => { state.value = JSON.parse(e.data); stream.value = true; });
  events.onerror = () => { stream.value = false; };
});
onUnmounted(() => events?.close());
async function control(type) {
  sending.value = true; error.value = '';
  try {
    const response = await fetch('/api/commands', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type }) });
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || data.message || `操作未接受（HTTP ${response.status}）`);
  } catch (e) { error.value = e.message; }
  finally { sending.value = false; }
}
</script>

<template>
  <div class="console">
    <aside>
      <div class="brand"><span class="brand-symbol">⌘</span><div>巡检控制台<small>INSPECTION CONSOLE</small></div></div>
      <div class="nav-label">工作空间</div><div class="nav active"><span>▣</span> 实时监控 <b>01</b></div>
      <div class="phase-note"><span class="tiny-tag">阶段 A</span><p>通信闭环演示</p><small>启动 / 停止 / 状态回传</small></div>
      <div class="aside-bottom"><span class="dot"></span> 本地模拟环境<small>配电室 · 虚拟机器人</small></div>
    </aside>
    <main>
      <header><div class="breadcrumb">工作空间 <span>/</span> 实时监控</div><div class="mode">SIMULATION <span>模拟模式</span></div></header>
      <section class="page-title"><div><div class="eyebrow">DEVICE OPERATIONS</div><h1>机器人实时监控</h1><p>从控制指令到设备反馈，观察每一段真实通信。</p></div><div class="connection" :class="{good:fresh}"><i></i><span data-testid="connection">{{ statusText }}</span></div></section>
      <div class="simulation-banner"><span>ⓘ</span> 当前设备由独立软件进程模拟。MQTT 与 Modbus TCP 使用真实网络通信；测量值和点表仅用于演示。</div>
      <section class="metrics">
        <article><div class="metric-title">运行状态 <span>01</span></div><strong data-testid="run-state" :class="{accent:running}">{{ fresh ? (running ? '运行中' : '已停止') : '状态不可用' }}</strong><small>{{ fresh ? 'FC03 设备寄存器读回' : '等待有效遥测，不显示过期值' }}</small></article>
        <article><div class="metric-title">电池电量 <span>02</span></div><strong>{{ metric('battery', '%') }}</strong><div class="bar"><i :style="{width: fresh ? `${telemetry.battery}%` : '0%'}"></i></div></article>
        <article><div class="metric-title">环境温度 <span>03</span></div><strong>{{ metric('temperature', ' °C') }}</strong><small>模拟传感器 · 倍率 0.1 °C</small></article>
        <article><div class="metric-title">实时位置 <span>04</span></div><strong class="position">{{ fresh ? `${telemetry.x.toFixed(1)}, ${telemetry.y.toFixed(1)}` : '—' }} <em>m</em></strong><small>演示坐标系 X / Y</small></article>
      </section>
      <div class="workspace">
        <section class="panel map-panel"><div class="panel-heading"><h2>配电室 · 设备位置</h2><span class="tiny-tag">二维示意</span></div>
          <div class="map"><svg viewBox="0 0 580 275" role="img" aria-label="机器人位置示意图">
            <defs><pattern id="grid" width="25" height="25" patternUnits="userSpaceOnUse"><path d="M 25 0 L 0 0 0 25" fill="none" stroke="#203044" stroke-width=".5"/></pattern></defs>
            <rect width="580" height="275" fill="url(#grid)"/>
            <g fill="#162438" stroke="#2c4157"><rect x="55" y="35" width="115" height="57" rx="5"/><rect x="232" y="35" width="115" height="57" rx="5"/><rect x="408" y="35" width="115" height="57" rx="5"/></g>
            <g fill="#95a9bd" font-size="11" text-anchor="middle"><text x="113" y="68">进线柜</text><text x="289" y="68">变压器柜</text><text x="466" y="68">出线柜</text></g>
            <path d="M60 169H520" stroke="#385467" stroke-width="2" stroke-dasharray="6 6"/>
            <g :transform="`translate(${robotX},169)`" :opacity="fresh ? 1 : .3"><circle r="24" fill="#53e0bb" fill-opacity=".1"/><circle r="15" fill="#102e2d" stroke="#53e0bb"/><rect x="-8" y="-6" width="16" height="12" rx="3" fill="#53e0bb"/><circle cx="0" cy="-9" r="2" fill="#b4ffee"/></g>
            <text x="25" y="250" fill="#5c778f" font-size="10">SIMULATED FLOOR PLAN / 示意图，非导航地图</text>
          </svg><div class="map-legend"><span><i></i> {{ state?.deviceId || 'robot-001' }}</span><span>{{ fresh ? '实时 Modbus 数据' : '上次位置 · 数据不可用' }}</span></div></div>
          <div class="device-footer"><div><small>数据来源</small><b>Modbus TCP 模拟器</b></div><div><small>设备采集时间</small><b>{{ time(telemetry?.capturedAt) }}</b></div></div>
        </section>
        <section class="panel control-panel"><div class="panel-heading"><h2>设备控制</h2><span class="tiny-tag">FC06</span></div><div class="device-name"><div class="device-icon">▣</div><div><b>{{ state?.deviceId || 'robot-001' }}</b><small>虚拟巡检机器人 / Unit ID 1</small></div></div>
          <p class="control-description">启动模拟运动，或停止当前运动。执行结果以设备寄存器反馈为准。</p>
          <button data-testid="start" class="primary" :disabled="!fresh || running || sending || pending" @click="control('START')">▷ 启动机器人</button>
          <button data-testid="stop" class="secondary" :disabled="!stream || !state?.mqttConnected || sending || pending" @click="control('STOP')">□ 停止机器人</button>
          <p v-if="error" class="error" role="alert">{{ error }}</p><div class="control-tip">消息送达 → FC06 写入 → FC03 读回<br><span>反馈超时会显示「结果未知」。</span></div>
        </section>
      </div>
      <section class="panel link-panel"><div class="panel-heading"><h2>通信链路</h2><span>SSE 实时推送 · 无浏览器模拟数据</span></div><div class="link-chain"><div>Vue<small>HTTP / SSE</small></div><b>→</b><div>Spring Boot<small>REST / MQTT</small></div><b>→</b><div :class="{unavailable:!state?.mqttConnected}">MQTT Broker<small>QoS 1 · 无 retain</small></div><b>→</b><div>Edge<small>串行采集与执行</small></div><b>→</b><div :class="{unavailable:!fresh}">机器人模拟器<small>TCP / FC03 · FC06</small></div></div></section>
      <section class="panel history-panel"><div class="panel-heading"><h2>指令与执行反馈</h2><span>当前进程最近 100 条</span></div><div class="table-wrap"><table><thead><tr><th>指令 / COMMAND ID</th><th>类型</th><th>执行状态</th><th>反馈说明</th><th>更新时间</th></tr></thead><tbody><tr v-for="item in state?.commands || []" :key="item.command.commandId"><td class="mono">{{ item.command.commandId.slice(0, 8) }}</td><td>{{ item.command.type }}</td><td><span class="result" :class="item.status.toLowerCase()">{{ labels[item.status] || item.status }}</span></td><td>{{ item.message }}</td><td>{{ time(item.updatedAt) }}</td></tr><tr v-if="!state?.commands.length"><td colspan="5" class="empty">启动机器人，观察第一条指令的执行反馈。</td></tr></tbody></table></div></section>
      <section class="panel diagnostic-panel"><div class="panel-heading"><h2>通信诊断</h2><span>命令与状态变化记录</span></div><div class="diagnostic" v-for="(item,i) in (state?.diagnostics || []).slice(0,8)" :key="i"><time>{{ time(item.timestamp) }}</time><b>{{ item.direction }}</b><span>{{ item.protocol }} · {{ item.summary }}</span></div><p v-if="!state?.diagnostics.length" class="empty">等待通信事件</p></section>
      <footer>阶段 A · 模拟通信闭环 <span>摄像机、三点巡检、历史归档将在后续阶段接入</span></footer>
    </main>
  </div>
</template>
