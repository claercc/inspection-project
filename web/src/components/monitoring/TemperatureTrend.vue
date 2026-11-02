<script setup>
import { computed } from 'vue';
import { formatMetric, formatTime } from '../../utils/format';
const props = defineProps({
  fresh: Boolean,
  telemetry: { type: Object, default: null },
  samples: { type: Array, default: () => [] },
});
const temperature = computed(() => formatMetric(props.telemetry, props.fresh, 'temperature'));
// SVG 的 y 坐标向下增加，后续计算需将温度比例反向映射到绘图区。
const PLOT = { left: 36, right: 546, top: 30, bottom: 142, baseline: 160 };
const chart = computed(() => {
  const values = props.samples.map((sample) => sample.temperature);
  const min = values.length ? Math.floor(Math.min(...values) - 1) : 0;
  const max = values.length ? Math.ceil(Math.max(...values) + 1) : 1;
  // 按接收顺序等距绘点；首尾时间只是范围标签，不代表数据库历史或严格的时间比例轴。
  const points = values.map((value, index) => {
    const x = PLOT.left + (index / Math.max(1, values.length - 1)) * (PLOT.right - PLOT.left);
    const y = PLOT.bottom - ((value - min) / (max - min)) * (PLOT.bottom - PLOT.top);
    return `${x},${y}`;
  });
  const area =
    points.length > 1
      ? `${PLOT.left},${PLOT.baseline} ${points.join(' ')} ${PLOT.right},${PLOT.baseline}`
      : '';
  return { points: points.join(' '), area, min, max };
});
</script>

<template>
  <section
    class="panel trend-panel"
    aria-labelledby="trend-title"
  >
    <div class="panel-header">
      <div>
        <span class="panel-index">03</span>
        <h2 id="trend-title">温度趋势</h2>
      </div>
      <span class="subtle-label">本次会话 · 最多 60 个采样</span>
    </div>
    <div class="trend-summary">
      <strong class="mono">
        {{ temperature }}
        <em>°C</em>
      </strong>
      <span>
        <i
          class="small-dot"
          :class="{ lit: fresh }"
        ></i>
        {{ fresh ? '设备实时采集' : '实时数据不可用' }}
      </span>
    </div>
    <div
      class="trend-chart"
      :class="{ muted: !fresh }"
    >
      <svg
        v-if="samples.length > 1"
        viewBox="0 0 580 180"
        role="img"
        aria-label="本次会话温度采集趋势"
      >
        <defs>
          <linearGradient
            id="temperature-fill"
            x1="0"
            y1="0"
            x2="0"
            y2="1"
          >
            <stop
              stop-color="#5cdfb6"
              stop-opacity=".16"
            />
            <stop
              offset="1"
              stop-color="#5cdfb6"
              stop-opacity="0"
            />
          </linearGradient>
        </defs>
        <path
          d="M36 30H546 M36 86H546 M36 142H546"
          stroke="#253545"
          stroke-dasharray="3 5"
        />
        <text
          x="2"
          y="34"
          fill="#657d91"
          font-size="10"
        >
          {{ chart.max }}
        </text>
        <text
          x="2"
          y="146"
          fill="#657d91"
          font-size="10"
        >
          {{ chart.min }}
        </text>
        <polygon
          :points="chart.area"
          fill="url(#temperature-fill)"
        />
        <polyline
          :points="chart.points"
          fill="none"
          stroke="#5cdbb5"
          stroke-width="2"
          stroke-linejoin="round"
        />
        <text
          x="36"
          y="176"
          fill="#657d91"
          font-size="10"
        >
          {{ formatTime(samples[0]?.at) }}
        </text>
        <text
          x="546"
          y="176"
          text-anchor="end"
          fill="#657d91"
          font-size="10"
        >
          {{ formatTime(samples.at(-1)?.at) }}
        </text>
      </svg>
      <div
        v-else
        class="chart-empty"
      >
        <span class="empty-chart-icon">⌁</span>
        <p>等待设备采样，建立本次会话曲线</p>
      </div>
    </div>
  </section>
</template>
