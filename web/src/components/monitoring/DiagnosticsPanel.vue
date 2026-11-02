<script setup>
import { formatTime } from '../../utils/format';
defineProps({
  diagnostics: { type: Array, default: () => [] },
});
</script>

<template>
  <section
    id="diagnostics"
    class="panel diagnostic-panel"
    aria-labelledby="diagnostics-title"
  >
    <div class="panel-header">
      <div>
        <span class="panel-index">06</span>
        <h2 id="diagnostics-title">通信诊断</h2>
      </div>
      <span class="subtle-label">当前进程的命令与状态变化</span>
    </div>
    <div
      v-for="(item, index) in diagnostics.slice(0, 6)"
      :key="`${item.timestamp}-${index}`"
      class="diagnostic-row"
    >
      <span class="diagnostic-dot"></span>
      <time class="mono">{{ formatTime(item.timestamp) }}</time>
      <strong>{{ item.direction }}</strong>
      <span class="diagnostic-protocol">{{ item.protocol }}</span>
      <p>{{ item.summary }}</p>
    </div>
    <div
      v-if="!diagnostics.length"
      class="diagnostic-empty"
    >
      等待通信事件；建立连接后将显示状态变化。
    </div>
  </section>
</template>
