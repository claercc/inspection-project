<script setup>
defineProps({
  fresh: Boolean,
  stream: Boolean,
  state: { type: Object, default: null },
});
defineEmits(['reconnect']);
</script>

<template>
  <section
    class="panel connection-panel"
    aria-labelledby="link-title"
  >
    <div class="panel-header">
      <div>
        <span class="panel-index">04</span>
        <h2 id="link-title">通信链路</h2>
      </div>
      <button
        class="text-button"
        data-testid="reconnect"
        @click="$emit('reconnect')"
      >
        重连实时订阅 ↻
      </button>
    </div>
    <div class="connection-chain">
      <div>
        <span
          class="chain-node"
          :class="{ lit: stream }"
        >
          01
        </span>
        <strong>监控页面</strong>
        <small>HTTP / SSE</small>
      </div>
      <i></i>
      <div>
        <span
          class="chain-node"
          :class="{ lit: stream }"
        >
          02
        </span>
        <strong>服务端</strong>
        <small>Spring Boot</small>
      </div>
      <i></i>
      <div>
        <span
          class="chain-node"
          :class="{ lit: state?.mqttConnected }"
        >
          03
        </span>
        <strong>消息服务</strong>
        <small>MQTT</small>
      </div>
      <i></i>
      <div>
        <span
          class="chain-node"
          :class="{ lit: state && !state.stale && state.mqttConnected }"
        >
          04
        </span>
        <strong>边缘端</strong>
        <small>设备采集</small>
      </div>
      <i></i>
      <div>
        <span
          class="chain-node"
          :class="{ lit: fresh }"
        >
          05
        </span>
        <strong>演示设备</strong>
        <small>Modbus TCP</small>
      </div>
    </div>
    <div class="connection-meta">
      <span>
        更新方式
        <strong>SSE 实时推送</strong>
      </span>
      <span>
        确认方式
        <strong>FC06 写入 → FC03 读回</strong>
      </span>
    </div>
    <p class="connection-explanation">
      重连实时订阅只恢复网页事件连接，不重发设备控制。节点状态依据已有反馈显示。
    </p>
  </section>
</template>
