<script setup>
import { formatMetric } from '../../utils/format';
const props = defineProps({
  fresh: Boolean,
  running: Boolean,
  telemetry: { type: Object, default: null },
});
defineEmits(['inspect-temperature']);
const metric = (field, digits) => formatMetric(props.telemetry, props.fresh, field, digits);
</script>

<template>
  <section
    class="metric-grid"
    aria-label="设备实时指标"
  >
    <article class="metric-card">
      <div class="metric-caption">
        设备运行状态
        <svg
          class="metric-symbol"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <circle
            cx="12"
            cy="12"
            r="8"
          />
          <path d="m8 12 3 3 5-6" />
        </svg>
      </div>
      <div
        class="metric-value state-value"
        :class="{ 'text-accent': running }"
        data-testid="run-state"
      >
        {{ fresh ? (running ? '运行中' : '已停止') : '状态不可用' }}
      </div>
      <div class="metric-note">
        <span
          class="small-dot"
          :class="{ lit: fresh }"
        ></span>
        {{ fresh ? '设备寄存器读回确认' : '等待有效设备反馈' }}
      </div>
    </article>
    <article class="metric-card">
      <div class="metric-caption">
        电池电量
        <svg
          class="metric-symbol"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <rect
            x="3"
            y="7"
            width="16"
            height="10"
            rx="2"
          />
          <path d="M21 10v4 M6 10v4 M9 10v4 M12 10v4" />
        </svg>
      </div>
      <div class="metric-value mono">
        {{ metric('battery') }}
        <em>%</em>
      </div>
      <div class="battery-track">
        <span :style="{ width: fresh ? `${telemetry.battery}%` : '0%' }"></span>
      </div>
    </article>
    <article class="metric-card temperature-card">
      <div class="metric-caption">
        模拟设备温度
        <svg
          class="metric-symbol"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path d="M9 14V5a3 3 0 0 1 6 0v9a5 5 0 1 1-6 0z M12 8v9" />
          <circle
            cx="12"
            cy="18"
            r="1"
          />
        </svg>
      </div>
      <div
        class="metric-value mono"
        data-testid="temperature-value"
      >
        {{ metric('temperature') }}
        <em>°C</em>
      </div>
      <div class="metric-note temperature-source-note">
        <span>Modbus TCP 采集</span>
        <button
          class="text-button"
          data-testid="temperature-source"
          @click="$emit('inspect-temperature')"
        >
          查看来源 ↗
        </button>
      </div>
    </article>
    <article class="metric-card">
      <div class="metric-caption">
        设备坐标
        <svg
          class="metric-symbol"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <circle
            cx="12"
            cy="12"
            r="6"
          />
          <path d="M12 2v5 M12 17v5 M2 12h5 M17 12h5" />
        </svg>
      </div>
      <div class="metric-value coordinate-value mono">
        {{ metric('x') }}
        <span class="coordinate-divider">/</span>
        {{ metric('y') }}
        <em>m</em>
      </div>
      <div class="metric-note">
        X / Y
        <span>·</span>
        演示坐标系
      </div>
    </article>
  </section>
</template>
