<script setup>
import { computed } from 'vue';
const props = defineProps({
  deviceId: { type: String, default: 'robot-001' },
  fresh: Boolean,
  positionX: { type: Number, default: 0 },
});
const PATH_START_X = 100;
const PIXELS_PER_METRE = 5.15;
const MAX_POSITION_X = 99.9;
// 将演示 X 坐标投影到固定横向路线；该图形不是导航地图，也不表示二维路径规划。
const robotX = computed(() => {
  const position = Math.max(0, Math.min(MAX_POSITION_X, props.positionX));
  return PATH_START_X + position * PIXELS_PER_METRE;
});
</script>

<template>
  <section
    class="panel scene-panel"
    aria-labelledby="scene-title"
  >
    <div class="panel-header">
      <div>
        <span class="panel-index">01</span>
        <h2 id="scene-title">现场态势</h2>
      </div>
      <span class="subtle-label">
        <span class="small-dot lit"></span>
        二维场景示意
      </span>
    </div>
    <div class="scene-toolbar">
      <span>配电室巡检区域</span>
      <span class="scene-scale">0 — 100 m</span>
    </div>
    <div class="floor-scene">
      <svg
        viewBox="0 0 720 340"
        role="img"
        aria-labelledby="floor-title"
      >
        <title id="floor-title">配电室二维示意，机器人位置来自 Modbus 读回，不代表导航地图</title>
        <defs>
          <pattern
            id="floor-grid"
            width="24"
            height="24"
            patternUnits="userSpaceOnUse"
          >
            <path
              d="M24 0H0V24"
              fill="none"
              stroke="#263e50"
              stroke-width=".6"
            />
          </pattern>
          <linearGradient id="route-glow">
            <stop
              stop-color="#51e0b9"
              stop-opacity=".1"
            />
            <stop
              offset="1"
              stop-color="#51e0b9"
              stop-opacity=".8"
            />
          </linearGradient>
          <radialGradient id="floor-light">
            <stop
              stop-color="#1c5148"
              stop-opacity=".3"
            />
            <stop
              offset="1"
              stop-color="#0c1926"
              stop-opacity="0"
            />
          </radialGradient>
        </defs>
        <rect
          width="720"
          height="340"
          fill="url(#floor-grid)"
        />
        <ellipse
          cx="360"
          cy="190"
          rx="300"
          ry="150"
          fill="url(#floor-light)"
        />
        <path
          d="M50 54V30H74 M646 30h24v24 M670 278v24h-24 M74 302H50v-24"
          fill="none"
          stroke="#416171"
          stroke-width="1.5"
        />
        <g
          v-for="(cabinet, index) in ['进线柜', '变压器柜', '出线柜']"
          :key="cabinet"
          :transform="`translate(${100 + index * 200},65)`"
        >
          <path
            d="m0 8 12-8h116l-12 8z"
            fill="#294353"
          />
          <rect
            y="8"
            width="116"
            height="78"
            rx="3"
            fill="#172c3a"
            stroke="#375364"
          />
          <path
            d="M12 24h92 M12 33h92 M12 64h28 M12 72h28"
            stroke="#3f5b6b"
            stroke-width="2"
          />
          <rect
            x="78"
            y="48"
            width="23"
            height="22"
            rx="2"
            fill="#101f2c"
            stroke="#385563"
          />
          <circle
            cx="91"
            cy="58"
            r="2"
            fill="#77c7b1"
          />
          <text
            x="58"
            y="110"
            text-anchor="middle"
            fill="#a4b9c7"
            font-size="12"
          >
            {{ cabinet }}
          </text>
          <text
            x="58"
            y="127"
            text-anchor="middle"
            fill="#55788b"
            font-size="9"
            letter-spacing="2"
          >
            柜体编号 0{{ index + 1 }}
          </text>
        </g>
        <path
          d="M100 239H615"
          stroke="#2b4c59"
          stroke-width="18"
          stroke-linecap="round"
          opacity=".35"
        />
        <path
          d="M100 239H615"
          stroke="#487161"
          stroke-width="1.5"
          stroke-dasharray="5 8"
        />
        <path
          :d="`M100 239H${robotX}`"
          stroke="url(#route-glow)"
          stroke-width="3"
          :opacity="fresh ? 1 : 0.2"
        />
        <g
          :transform="`translate(${robotX},239)`"
          :opacity="fresh ? 1 : 0.25"
          class="robot-marker"
        >
          <circle
            r="29"
            fill="#53dfb7"
            fill-opacity=".06"
            stroke="#53dfb7"
            stroke-opacity=".16"
          />
          <circle
            r="19"
            fill="#113b35"
            stroke="#69e5bf"
            stroke-opacity=".7"
          />
          <rect
            x="-11"
            y="-8"
            width="22"
            height="16"
            rx="4"
            fill="#6ee3bf"
          />
          <path
            d="M-5-12H5 M-5 12H5"
            stroke="#a6f2d7"
            stroke-width="2"
          />
          <circle
            cx="4"
            cy="-2"
            r="2"
            fill="#164c43"
          />
          <path
            d="m31-4 6 4-6 4"
            stroke="#63d9b5"
            fill="none"
          />
        </g>
        <text
          x="360"
          y="290"
          text-anchor="middle"
          fill="#4e7281"
          font-size="9"
          letter-spacing="2"
        >
          模拟场景 · 非导航地图
        </text>
      </svg>
    </div>
    <div class="scene-footer">
      <div>
        <span class="robot-legend"></span>
        <strong>{{ deviceId }}</strong>
        <span
          class="badge"
          :class="fresh ? 'positive' : 'neutral'"
        >
          {{ fresh ? '实时读回' : '上次位置' }}
        </span>
      </div>
      <span class="scene-source">来源：Modbus TCP 模拟器</span>
    </div>
  </section>
</template>
