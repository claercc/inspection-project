<script setup>
import { computed, nextTick, ref } from 'vue';
import { statusLabels } from '../../config/deviceStatus';
import { formatTime } from '../../utils/format';
const props = defineProps({
  commands: { type: Array, default: () => [] },
  diagnostics: { type: Array, default: () => [] },
});
// 只保存选中的编号，让弹窗继续跟随 SSE 中该命令的最新结果，而不是锁住打开时的状态。
const selectedCommandId = ref(null);
const commandDialog = ref(null);
const selectedCommand = computed(() =>
  props.commands.find((item) => item.command.commandId === selectedCommandId.value),
);
// 诊断快照按新到旧排列，详情中反转为时间顺序，方便顺着阅读指令经过的事件。
const commandEvents = computed(() =>
  props.diagnostics
    .filter((item) => item.commandId === selectedCommand.value?.command.commandId)
    .slice()
    .reverse(),
);
async function showCommand(item) {
  selectedCommandId.value = item.command.commandId;
  await nextTick();
  commandDialog.value.showModal();
}
</script>

<template>
  <section
    class="panel command-panel"
    aria-labelledby="commands-title"
  >
    <div class="panel-header">
      <div>
        <span class="panel-index">05</span>
        <h2 id="commands-title">指令与执行反馈</h2>
        <span class="count-badge">{{ commands.length }}</span>
      </div>
      <span class="subtle-label">当前服务进程 · 最近 100 条</span>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>指令编号</th>
            <th>操作类型</th>
            <th>执行状态</th>
            <th>反馈说明</th>
            <th>更新时间</th>
            <th class="cell-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in commands"
            :key="item.command.commandId"
          >
            <td class="mono command-id">
              {{ item.command.commandId.slice(0, 8) }}
              <span>…</span>
            </td>
            <td>
              <span
                class="command-type"
                :class="{ start: item.command.type === 'START' }"
              >
                {{ item.command.type === 'START' ? '启动' : '停止' }}
              </span>
            </td>
            <td>
              <span
                class="result-status"
                :class="`status-${item.status.toLowerCase()}`"
              >
                <i></i>
                {{ statusLabels[item.status] || item.status }}
              </span>
            </td>
            <td
              class="feedback-cell"
              :title="item.message"
            >
              {{ item.message }}
            </td>
            <td class="mono time-cell">{{ formatTime(item.updatedAt) }}</td>
            <td class="cell-right">
              <button
                class="text-button"
                :data-testid="`command-detail-${item.command.commandId}`"
                @click="showCommand(item)"
              >
                详情 ↗
              </button>
            </td>
          </tr>
          <tr v-if="!commands.length">
            <td colspan="6">
              <div class="table-empty">
                <span>⌁</span>
                <strong>等待第一条设备指令</strong>
                <p>点击「启动机器人」，查看从发送到设备确认的执行反馈。</p>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <dialog
    ref="commandDialog"
    class="modal command-modal"
    aria-labelledby="command-dialog-title"
    @click="(event) => event.target === commandDialog && commandDialog.close()"
  >
    <template v-if="selectedCommand">
      <div class="modal-heading">
        <div>
          <span class="section-kicker">COMMAND DETAIL</span>
          <h2 id="command-dialog-title">设备指令详情</h2>
        </div>
        <button
          class="icon-button"
          aria-label="关闭指令详情"
          @click="commandDialog.close()"
        >
          ×
        </button>
      </div>
      <dl class="detail-list">
        <div>
          <dt>完整 commandId</dt>
          <dd class="mono">{{ selectedCommand.command.commandId }}</dd>
        </div>
        <div>
          <dt>设备 / 操作</dt>
          <dd>{{ selectedCommand.command.deviceId }} / {{ selectedCommand.command.type }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatTime(selectedCommand.command.createdAt, true) }}</dd>
        </div>
        <div>
          <dt>结果</dt>
          <dd>{{ statusLabels[selectedCommand.status] }} · {{ selectedCommand.message }}</dd>
        </div>
      </dl>
      <h3 class="detail-section-title">已记录的通信事件</h3>
      <ol class="command-events">
        <li
          v-for="(event, index) in commandEvents"
          :key="index"
        >
          <time class="mono">{{ formatTime(event.timestamp) }}</time>
          <strong>{{ event.direction }}</strong>
          <p>{{ event.summary }}</p>
        </li>
      </ol>
      <p
        v-if="!commandEvents.length"
        class="modal-intro"
      >
        当前进程没有保留这条命令的关联事件。
      </p>
      <p class="detail-note">这里仅显示服务端已记录的事件，不推断尚未上报的接收或执行阶段。</p>
      <form
        method="dialog"
        class="modal-footer"
      >
        <button class="button secondary">返回工作台</button>
      </form>
    </template>
  </dialog>
</template>
