<script setup>
import { computed, nextTick, ref } from 'vue';

const props = defineProps({
  consoleState: { type: Object, default: null },
  streamConnected: Boolean,
});
const drawer = ref(null);
const snapshot = ref(null);
const copyStatus = ref('');
const evidence = computed(() => snapshot.value?.telemetry?.evidence);
// 当前链路可用性持续更新，下面的 snapshot 则保持锁定，两者不能混为同一时刻的状态。
const liveAvailable = computed(() => props.streamConnected && props.consoleState?.online);
// 温度的零基地址为 4；原始数组从 startAddress 开始，因此需要换算成数组下标。
const rawTemperature = computed(() => evidence.value?.registers?.[4 - evidence.value.startAddress]);
const snapshotTemperature = computed(() => snapshot.value?.telemetry?.temperature);
const hasEvidence = computed(
  () =>
    snapshot.value?.telemetry?.robotOnline &&
    evidence.value?.protocol === 'MODBUS_TCP' &&
    evidence.value.functionCode === 3 &&
    Number.isInteger(rawTemperature.value),
);
const observerCommand = computed(() => {
  const deviceId = snapshot.value?.deviceId;
  return `node scripts/observe-telemetry.mjs${/^[A-Za-z0-9_-]+$/.test(deviceId || '') ? ` --device ${deviceId}` : ''}`;
});
/**
 * 将来源证据中的时间转为本地日期和时间，供抽屉模板调用。
 * @param {string|null|undefined} value 采集或接收时间的 ISO-8601 字符串。
 * @returns {string} 中文日期时间；空值返回“尚未收到”。
 */
const dateTime = (value) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未收到';

/**
 * 锁定当前采样、实际主题和接收时间，无参数；打开抽屉和点击刷新时调用。
 * 深拷贝便于按 sampleId 核对，后续 SSE 推送不会改写这份快照。
 * @returns {void} 更新 snapshot 并清空复制提示，不向服务端请求历史数据。
 */
function captureLatest() {
  const state = props.consoleState;
  snapshot.value = {
    deviceId: state?.deviceId || null,
    telemetry: state?.telemetry ? JSON.parse(JSON.stringify(state.telemetry)) : null,
    receivedAt: state?.lastReceivedAt || null,
    topic: state?.telemetryTopic || null,
    statusAtCapture: state?.connectionStatus || 'UNKNOWN',
  };
  copyStatus.value = '';
}
/**
 * 锁定最新采样并打开来源抽屉，无参数；通过 defineExpose 暴露给父组件。
 * @returns {Promise<void>} Vue 完成 DOM 更新并调用 showModal 后兑现。
 * @example
 * await temperatureSource.value.open(); // 在持有组件 ref 的父组件中调用
 */
async function open() {
  captureLatest();
  await nextTick();
  drawer.value.showModal();
}
/**
 * 将当前采样编号复制到剪贴板，无参数；仅在有 sampleId 时由复制按钮调用。
 * @returns {Promise<void>} 完成复制尝试后兑现；成功或失败均通过 copyStatus 提示，不向外抛出。
 */
async function copySampleId() {
  try {
    await navigator.clipboard.writeText(evidence.value.sampleId);
    copyStatus.value = '已复制采集编号';
  } catch {
    copyStatus.value = '无法访问剪贴板，请选中上方采集编号复制。';
  }
}
defineExpose({ open });
</script>

<template>
  <dialog
    ref="drawer"
    class="source-drawer"
    data-testid="temperature-source-drawer"
    aria-labelledby="temperature-source-title"
    @click="(event) => event.target === drawer && drawer.close()"
  >
    <div class="source-drawer-header">
      <div>
        <span class="section-kicker">采样核对</span>
        <h2 id="temperature-source-title">温度数据来源</h2>
      </div>
      <button
        class="icon-button"
        aria-label="关闭温度数据来源"
        @click="drawer.close()"
      >
        ×
      </button>
    </div>
    <div
      class="source-quality"
      :class="{ unavailable: !liveAvailable }"
    >
      <span
        class="small-dot"
        :class="{ lit: liveAvailable }"
      ></span>
      <strong data-testid="source-live-status">
        {{ liveAvailable ? '当前实时采集可用' : '当前实时采集不可用' }}
      </strong>
      <span>下方为已锁定的单次采样</span>
    </div>

    <section class="source-section source-summary">
      <div class="source-label">{{ snapshot?.deviceId || '等待设备身份' }} · 模拟设备温度</div>
      <div
        class="source-reading mono"
        data-testid="source-temperature"
      >
        {{ hasEvidence ? snapshotTemperature.toFixed(1) : '—' }}
        <span>°C</span>
      </div>
      <p>设备为软件模拟器；数据由边缘端通过 Modbus TCP 读取，经 MQTT、服务端和 SSE 到达本页面。</p>
      <div class="source-snapshot-actions">
        <button
          class="button secondary"
          data-testid="source-refresh"
          :disabled="!liveAvailable"
          @click="captureLatest"
        >
          更新到最新采样
        </button>
        <span>锁定快照便于与独立订阅记录核对。</span>
      </div>
    </section>

    <section
      v-if="hasEvidence"
      class="source-section"
    >
      <div class="source-section-heading">
        <h3>01 / 这一个数值怎样得到</h3>
        <span class="badge positive">FC03 读取成功</span>
      </div>
      <div class="source-formula">
        <div>
          <small>零基寄存器地址 4</small>
          <strong
            class="mono"
            data-testid="source-raw-temperature"
          >
            {{ rawTemperature }}
          </strong>
        </div>
        <span>× 0.1</span>
        <div>
          <small>换算后的温度</small>
          <strong class="mono">
            {{ snapshotTemperature.toFixed(1) }}
            <em>°C</em>
          </strong>
        </div>
      </div>
      <p class="source-explanation">
        原值来自 edge 的实际读取结果。读取范围从 {{ evidence.startAddress }} 开始，共
        {{ evidence.registers.length }} 个保持寄存器。
      </p>
      <dl class="source-fields">
        <div>
          <dt>协议与功能码</dt>
          <dd>{{ evidence.protocol }} / {{ evidence.functionCode.toString().padStart(2, '0') }}</dd>
        </div>
        <div>
          <dt>设备连接地址</dt>
          <dd
            class="mono"
            data-testid="source-endpoint"
          >
            {{ evidence.host }}:{{ evidence.port }}
          </dd>
        </div>
        <div>
          <dt>设备单元（Unit ID）</dt>
          <dd>{{ evidence.unitId }}</dd>
        </div>
        <div>
          <dt>原始寄存器序列</dt>
          <dd
            class="mono"
            data-testid="source-registers"
          >
            {{ evidence.registers.join(', ') }}
          </dd>
        </div>
        <div>
          <dt>采集端</dt>
          <dd class="mono">{{ evidence.collectorId }}</dd>
        </div>
      </dl>
    </section>
    <section
      v-else
      class="source-section"
    >
      <div class="source-empty">
        <h3>没有可核对的寄存器样本</h3>
        <p>当前采集失败或采集端尚未提供原始值，本页面不会用换算后的温度反推原始寄存器。</p>
        <p v-if="snapshot?.telemetry?.error">采集反馈：{{ snapshot.telemetry.error }}</p>
      </div>
    </section>

    <section class="source-section">
      <div class="source-section-heading">
        <h3>02 / 关联同一次采样</h3>
        <button
          class="text-button"
          :disabled="!evidence?.sampleId"
          @click="copySampleId"
        >
          复制采集编号
        </button>
      </div>
      <dl class="source-fields">
        <div>
          <dt>采集编号 sampleId</dt>
          <dd
            class="mono"
            data-testid="source-sample-id"
          >
            {{ evidence?.sampleId || '未提供' }}
          </dd>
        </div>
        <div>
          <dt>edge 读回时间</dt>
          <dd>{{ dateTime(snapshot?.telemetry?.capturedAt) }}</dd>
        </div>
        <div>
          <dt>server 接收时间</dt>
          <dd>{{ dateTime(snapshot?.receivedAt) }}</dd>
        </div>
        <div>
          <dt>实际 MQTT 主题</dt>
          <dd
            class="mono"
            data-testid="source-topic"
          >
            {{ snapshot?.topic || '未提供' }}
          </dd>
        </div>
        <div>
          <dt>来源声明</dt>
          <dd class="mono">{{ snapshot?.telemetry?.source || '未提供' }}</dd>
        </div>
      </dl>
      <p
        v-if="copyStatus"
        class="source-explanation"
        role="status"
      >
        {{ copyStatus }}
      </p>
      <p class="source-explanation">
        capturedAt 是 edge 完成读取的时刻，不是物理传感器的设备时钟；采集编号由 edge
        生成，未使用前端编号。
      </p>
    </section>

    <section class="source-section">
      <h3>03 / 独立核对</h3>
      <p class="source-explanation">
        先在开发终端运行以下命令，再打开抽屉或更新采样。在终端记录中查找相同
        sampleId，核对寄存器、温度与 MQTT 主题。
      </p>
      <pre class="source-code">{{ observerCommand }}</pre>
      <p class="source-explanation">
        这是本机默认 MQTT 消息服务（1883）的命令。其他环境使用 --broker
        mqtt://地址:端口。脚本直接订阅
        MQTT，不访问服务端接口；控制台里的相同采样由独立连接接收。消息服务不保留旧采样，订阅前的记录不能补取。
      </p>
    </section>

    <section class="source-section">
      <h3>04 / 模拟规则说明</h3>
      <p class="source-explanation">
        当前温度由独立 Java 机器人模拟器产生：初始 26
        °C，运行时随模拟位置周期变化。它没有物理温度传感器，也未关联某个柜体测点。停掉模拟器可以验证采集失效；场景化温度模型留待后续单元。
      </p>
    </section>
    <details class="source-section source-json">
      <summary>查看服务端转发的这条遥测 JSON</summary>
      <pre data-testid="source-telemetry-json">{{
        JSON.stringify(snapshot?.telemetry, null, 2)
      }}</pre>
    </details>
    <form
      method="dialog"
      class="source-drawer-footer"
    >
      <span>来源元数据用于开发核对，不是硬件认证。</span>
      <button class="button secondary">返回工作台</button>
    </form>
  </dialog>
</template>
