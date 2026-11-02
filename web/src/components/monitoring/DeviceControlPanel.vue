<script setup>
import { statusLabels } from '../../config/deviceStatus';
defineProps({
  deviceId: { type: String, default: 'robot-001' },
  canStart: Boolean,
  canStop: Boolean,
  pending: Boolean,
  error: { type: String, default: '' },
  latestCommand: { type: Object, default: null },
});
defineEmits(['control']);
</script>

<template>
  <section
    class="panel control-panel"
    aria-labelledby="control-title"
  >
    <div class="panel-header">
      <div>
        <span class="panel-index">02</span>
        <h2 id="control-title">设备控制</h2>
      </div>
      <span class="badge neutral">已接通</span>
    </div>
    <div class="robot-identity">
      <span class="robot-avatar">
        <svg
          viewBox="0 0 40 40"
          fill="none"
          aria-hidden="true"
        >
          <rect
            x="8"
            y="13"
            width="24"
            height="18"
            rx="5"
            stroke="currentColor"
            stroke-width="1.5"
          />
          <path
            d="M20 7v6 M15 32v3 M25 32v3 M5 19v7 M35 19v7"
            stroke="currentColor"
            stroke-width="1.5"
          />
          <circle
            cx="15"
            cy="21"
            r="2"
            fill="currentColor"
          />
          <circle
            cx="25"
            cy="21"
            r="2"
            fill="currentColor"
          />
          <path
            d="M16 27h8"
            stroke="currentColor"
          />
        </svg>
      </span>
      <div>
        <h3>{{ deviceId }}</h3>
        <p>
          虚拟巡检机器人
          <span>·</span>
          单设备模式
        </p>
      </div>
    </div>
    <div class="control-facts">
      <div>
        <span>连接协议</span>
        <strong>Modbus TCP</strong>
      </div>
      <div>
        <span>操作范围</span>
        <strong>模拟运动启停</strong>
      </div>
    </div>
    <div class="control-buttons">
      <button
        class="button primary"
        data-testid="start"
        :disabled="!canStart"
        @click="$emit('control', 'START')"
      >
        <svg
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="m7 4 9 6-9 6z"
            fill="currentColor"
          />
        </svg>
        启动机器人
      </button>
      <button
        class="button secondary"
        data-testid="stop"
        :disabled="!canStop"
        @click="$emit('control', 'STOP')"
      >
        <svg
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
        >
          <rect
            x="5"
            y="5"
            width="10"
            height="10"
            rx="1"
            stroke="currentColor"
            stroke-width="1.6"
          />
        </svg>
        停止机器人
      </button>
    </div>
    <p
      v-if="error"
      class="inline-error"
      role="alert"
    >
      {{ error }}
    </p>
    <div class="last-command">
      <span>最近一次指令</span>
      <strong
        v-if="latestCommand"
        :class="`status-${latestCommand.status.toLowerCase()}`"
      >
        {{ statusLabels[latestCommand.status] }}
      </strong>
      <strong v-else>等待操作</strong>
      <p>{{ pending ? '指令已提交，等待设备读回反馈。' : '执行成功以设备状态读回为准。' }}</p>
    </div>
    <div class="planned-control">
      <div>
        <span>巡检任务</span>
        <span class="badge neutral">规划中</span>
      </div>
      <button
        class="button ghost"
        disabled
        title="尚未实现：下一步先设计新建任务表单，再接后端任务用例"
      >
        ＋ 新建巡检任务
      </button>
    </div>
  </section>
</template>
