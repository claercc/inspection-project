import { computed, onMounted, onUnmounted, ref } from 'vue';
import { sendDeviceCommand, subscribeDeviceState } from '../api/device';
import { statusLabels } from '../config/deviceStatus';

/**
 * 管理当前页面会话的状态、SSE 生命周期和控制请求。
 * 设备状态以服务端推送为准，按钮点击后不在本地预先改成“运行中”或“已停止”。
 */
export function useDeviceConsole() {
  const state = ref(null);
  const stream = ref(false);
  const error = ref('');
  const sending = ref(false);
  const samples = ref([]);
  let closeSubscription;
  let lastSampleAt;

  const telemetry = computed(() => state.value?.telemetry);
  // 网页收到推送和设备在线是两个条件，任一失效都不能把旧测量值当作实时值。
  const fresh = computed(() => stream.value && Boolean(state.value?.online));
  const running = computed(() => fresh.value && telemetry.value?.runState === 'RUNNING');
  const commands = computed(() => state.value?.commands || []);
  const diagnostics = computed(() => state.value?.diagnostics || []);
  const pending = computed(() =>
    commands.value.some((item) => ['PENDING', 'SENT'].includes(item.status)),
  );
  const canStart = computed(
    () => fresh.value && !running.value && !sending.value && !pending.value,
  );
  // 只拦截重复的待确认 STOP；等待 START 反馈时，停止入口仍应保持可用。
  const pendingStop = computed(() =>
    commands.value.some(
      (item) => ['PENDING', 'SENT'].includes(item.status) && item.command.type === 'STOP',
    ),
  );
  const canStop = computed(
    () => stream.value && state.value?.mqttConnected && !sending.value && !pendingStop.value,
  );
  const connectionText = computed(() =>
    !stream.value ? '实时连接中断' : statusLabels[state.value?.connectionStatus] || '等待设备连接',
  );

  function reconnect() {
    // 手动重连前关闭旧订阅，避免重复接收同一快照或在反复点击后积累连接。
    closeSubscription?.();
    stream.value = false;
    error.value = '';
    closeSubscription = subscribeDeviceState({
      onState(next) {
        state.value = next;
        stream.value = true;
        const sample = next.telemetry;
        // SSE 可能多次推送同一个采样；按采集时间去重，只保留本次页面会话的最近 60 个点。
        if (next.online && sample?.capturedAt && sample.capturedAt !== lastSampleAt) {
          lastSampleAt = sample.capturedAt;
          samples.value = [
            ...samples.value,
            { at: sample.capturedAt, temperature: sample.temperature },
          ].slice(-60);
        }
      },
      onDisconnect() {
        stream.value = false;
      },
      onError(message) {
        error.value = message;
      },
    });
  }

  async function control(type) {
    sending.value = true;
    error.value = '';
    try {
      // HTTP 返回后只结束“提交中”；设备是否执行由后续 SSE 中的命令状态确认。
      await sendDeviceCommand(type);
    } catch (cause) {
      error.value = cause.message;
    } finally {
      sending.value = false;
    }
  }

  onMounted(reconnect);
  onUnmounted(() => closeSubscription?.());
  return {
    state,
    stream,
    telemetry,
    fresh,
    running,
    commands,
    diagnostics,
    samples,
    error,
    sending,
    pending,
    canStart,
    canStop,
    connectionText,
    control,
    reconnect,
  };
}
