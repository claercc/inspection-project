import { computed, onMounted, onUnmounted, ref } from 'vue';
import { sendDeviceCommand, subscribeDeviceState } from '../api/device';
import { statusLabels } from '../config/deviceStatus';

/**
 * @typedef {object} DeviceConsoleSession useDeviceConsole 暴露给页面的会话对象。
 * @property {import('vue').Ref<import('../api/device.js').DeviceState|null>} state 最新服务端快照。
 * @property {import('vue').Ref<boolean>} stream 网页实时订阅是否可用。
 * @property {import('vue').ComputedRef<import('../api/device.js').Telemetry|null|undefined>} telemetry 当前采样。
 * @property {import('vue').ComputedRef<boolean>} fresh 当前采样是否可作为实时测量值使用。
 * @property {import('vue').ComputedRef<boolean>} running 新鲜采样是否表明机器人正在运行。
 * @property {import('vue').ComputedRef<import('../api/device.js').CommandView[]>} commands 当前命令历史。
 * @property {import('vue').ComputedRef<object[]>} diagnostics 当前诊断事件。
 * @property {import('vue').Ref<Array<{at: string, temperature: number}>>} samples 当前会话最近 60 个温度采样。
 * @property {import('vue').Ref<string>} error 页面操作或订阅错误提示。
 * @property {import('vue').Ref<boolean>} sending 是否正在等待 HTTP 提交处理。
 * @property {import('vue').ComputedRef<boolean>} pending 是否有命令仍在等待设备反馈。
 * @property {import('vue').ComputedRef<boolean>} canStart 启动按钮是否可用。
 * @property {import('vue').ComputedRef<boolean>} canStop 停止按钮是否可用。
 * @property {import('vue').ComputedRef<string>} connectionText 链路状态显示文字。
 * @property {(type: 'START'|'STOP') => Promise<void>} control 提交控制；结果从 commands 观察。
 * @property {() => void} reconnect 关闭旧 SSE 并建立新订阅。
 */

/**
 * 管理当前页面会话的状态、SSE 生命周期和控制请求。
 * 设备状态以服务端推送为准，按钮点击后不在本地预先改成“运行中”或“已停止”。
 * 在组件 setup 中调用，无参数；组件挂载时订阅，卸载时关闭订阅。
 *
 * @returns {DeviceConsoleSession} 页面会话对象。脚本中通过 .value 访问 Ref/ComputedRef，
 * 模板中由 Vue 自动解包；control 结束只表示提交已处理，执行状态仍由 SSE 更新。
 * @example
 * const consoleSession = useDeviceConsole();
 * await consoleSession.control('STOP');
 * console.log(consoleSession.commands.value); // 实际执行结果仍由 SSE 更新
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

  /**
   * 手动重建当前页面的 SSE 订阅，无参数。
   *
   * @returns {void} 关闭旧连接并重置连接提示；后续快照通过回调更新响应式状态。
   */
  function reconnect() {
    // 手动重连前关闭旧订阅，避免重复接收同一快照或在反复点击后积累连接。
    closeSubscription?.();
    stream.value = false;
    error.value = '';
    closeSubscription = subscribeDeviceState({
      /**
       * 处理一次 SSE 状态推送，更新页面状态与本次会话的温度曲线。
       * @param {import('../api/device.js').DeviceState} next 已解析的服务端快照。
       * @returns {void} 直接更新 state、stream 和 samples。
       */
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
      /**
       * 处理订阅异常，无参数；只清除实时连接标志。
       * @returns {void} 更新 stream，EventSource 后续仍可自动重连。
       */
      onDisconnect() {
        stream.value = false;
      },
      /**
       * 显示订阅处理错误。
       * @param {string} message 给用户展示的错误说明。
       * @returns {void} 更新 error，不重发设备命令。
       */
      onError(message) {
        error.value = message;
      },
    });
  }

  /**
   * 提交控制意图并维护请求中的界面状态；错误会转换成 error 文案。
   *
   * @param {'START'|'STOP'} type 要提交的控制类型。
   * @returns {Promise<void>} HTTP 处理结束后兑现，不返回设备结果；结果从 commands 中观察。
   */
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
