<script setup>
import { computed, ref } from 'vue';
import { useDeviceConsole } from './useDevice';
import { formatTime } from '../../utils/format';
import MonitoringMetrics from './components/MonitoringMetrics.vue';
import DeviceControlPanel from './components/DeviceControlPanel.vue';
import TemperatureTrend from './components/TemperatureTrend.vue';
import ConnectionPanel from './components/ConnectionPanel.vue';
import CommandHistoryPanel from './components/CommandHistoryPanel.vue';
import DiagnosticsPanel from './components/DiagnosticsPanel.vue';
import TemperatureSourceDrawer from './components/TemperatureSourceDrawer.vue';

// 页面只组合监控面板并转发交互；会话状态与订阅生命周期统一由 composable 管理。
const {
  state,
  stream,
  telemetry,
  fresh,
  running,
  commands,
  diagnostics,
  samples,
  error,
  pending,
  canStart,
  canStop,
  connectionText,
  control,
  reconnect,
} = useDeviceConsole();
const temperatureSource = ref(null);
const diagnosticsDialog = ref(null);
const deviceId = computed(() => state.value?.deviceId || 'robot-001');
const latestCommand = computed(() => commands.value[0]);
</script>

<template>
  <div class="page-heading">
    <div>
      <div class="section-kicker">
        <span class="heading-line"></span>
        实时运行
      </div>
      <h1>
        设备监控
        <span class="heading-period">.</span>
      </h1>
      <p>查看实时数据、控制设备，并核对执行结果。</p>
    </div>
    <div class="heading-status">
      <span
        class="status-pill"
        :class="{ online: fresh }"
      >
        <i></i>
        <span data-testid="connection">{{ connectionText }}</span>
      </span>
      <small>
        最后采集
        <span class="mono">{{ formatTime(telemetry?.capturedAt) }}</span>
      </small>
    </div>
  </div>
  <div class="mode-notice">
    <span class="notice-label">模拟模式</span>
    <p>设备与测量值由独立软件模拟，MQTT / Modbus TCP 使用真实网络通信。</p>
    <span class="notice-scope">演示设备 · {{ state?.deviceId || 'robot-001' }}</span>
  </div>
  <MonitoringMetrics
    :fresh="fresh"
    :running="running"
    :telemetry="telemetry"
    @inspect-temperature="temperatureSource.open()"
  />
  <div class="operations-grid">
    <TemperatureTrend
      :fresh="fresh"
      :telemetry="telemetry"
      :samples="samples"
    />
    <DeviceControlPanel
      :device-id="deviceId"
      :can-start="canStart"
      :can-stop="canStop"
      :pending="pending"
      :error="error"
      :latest-command="latestCommand"
      @control="control"
    />
  </div>
  <CommandHistoryPanel
    :commands="commands"
    :diagnostics="diagnostics"
  />
  <button
    class="button secondary"
    data-testid="open-diagnostics"
    @click="diagnosticsDialog.showModal()"
  >
    查看通信诊断
  </button>
  <dialog
    ref="diagnosticsDialog"
    class="modal"
    aria-labelledby="device-diagnostics-title"
    @click="(event) => event.target === diagnosticsDialog && diagnosticsDialog.close()"
  >
    <div class="modal-heading">
      <h2 id="device-diagnostics-title">连接与通信诊断</h2>
      <button
        class="icon-button"
        aria-label="关闭通信诊断"
        @click="diagnosticsDialog.close()"
      >
        ×
      </button>
    </div>
    <ConnectionPanel
      :state="state"
      :stream="stream"
      :fresh="fresh"
      @reconnect="reconnect"
    />
    <DiagnosticsPanel :diagnostics="diagnostics" />
  </dialog>
  <footer class="page-footer">
    <span>
      <i class="small-dot lit"></i>
      模拟设备 · 真实协议通信
    </span>
    <span>曲线仅保留本次页面会话；命令记录重启服务后清空</span>
  </footer>
  <TemperatureSourceDrawer
    ref="temperatureSource"
    :console-state="state"
    :stream-connected="stream"
  />
</template>
