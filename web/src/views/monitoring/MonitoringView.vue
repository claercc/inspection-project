<script setup>
import { computed, ref } from 'vue';
import { useDeviceConsole } from '../../composables/useDeviceConsole';
import { formatTime } from '../../utils/format';
import MonitoringMetrics from '../../components/monitoring/MonitoringMetrics.vue';
import RobotScenePanel from '../../components/monitoring/RobotScenePanel.vue';
import DeviceControlPanel from '../../components/monitoring/DeviceControlPanel.vue';
import TemperatureTrend from '../../components/monitoring/TemperatureTrend.vue';
import ConnectionPanel from '../../components/monitoring/ConnectionPanel.vue';
import CommandHistoryPanel from '../../components/monitoring/CommandHistoryPanel.vue';
import DiagnosticsPanel from '../../components/monitoring/DiagnosticsPanel.vue';
import TemperatureSourceDrawer from '../../components/monitoring/TemperatureSourceDrawer.vue';

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
const deviceId = computed(() => state.value?.deviceId || 'robot-001');
const latestCommand = computed(() => commands.value[0]);
</script>

<template>
  <div class="page-heading">
    <div>
      <div class="section-kicker">
        <span class="heading-line"></span>
        LIVE OPERATIONS / 实时运行
      </div>
      <h1>
        配电室巡检工作台
        <span class="heading-period">.</span>
      </h1>
      <p>让每一条指令有反馈，让每一次运行可观察。</p>
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
    <span class="notice-scope">配电室 · {{ state?.deviceId || 'robot-001' }}</span>
  </div>
  <MonitoringMetrics
    :fresh="fresh"
    :running="running"
    :telemetry="telemetry"
    @inspect-temperature="temperatureSource.open()"
  />
  <div class="operations-grid">
    <RobotScenePanel
      :device-id="deviceId"
      :fresh="fresh"
      :position-x="telemetry?.x || 0"
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
  <div class="insight-grid">
    <TemperatureTrend
      :fresh="fresh"
      :telemetry="telemetry"
      :samples="samples"
    />
    <ConnectionPanel
      :state="state"
      :stream="stream"
      :fresh="fresh"
      @reconnect="reconnect"
    />
  </div>
  <CommandHistoryPanel
    :commands="commands"
    :diagnostics="diagnostics"
  />
  <DiagnosticsPanel :diagnostics="diagnostics" />
  <footer class="page-footer">
    <span>
      <i class="small-dot lit"></i>
      模拟设备 · 真实协议通信
    </span>
    <span>任务、摄像机、告警与历史模块按按钮逐步接入</span>
  </footer>
  <TemperatureSourceDrawer
    ref="temperatureSource"
    :console-state="state"
    :stream-connected="stream"
  />
</template>
