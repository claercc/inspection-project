<script setup>
import { ref } from 'vue';
import { featureRoadmap, navigation } from '../config/workspace';

const active = ref('monitoring');
const roadmap = ref(null);
const icons = {
  monitoring: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  inspections: 'M5 4h14v17H5z M9 2h6v4H9z M8 10h8 M8 14h8 M8 18h5',
  alarms: 'M6 9a6 6 0 0 1 12 0v7l2 2H4l2-2z M10 21h4',
  history: 'M4 5h16v16H4z M8 2v6 M16 2v6 M4 10h16 M8 14h3 M8 17h7',
  diagnostics: 'M2 12h4l3-8 6 16 3-8h4',
  devices: 'M3 4h18v12H3z M8 21h8 M12 16v5 M7 8h4 M7 12h7',
};
function navigate(item) {
  active.value = item.id;
  if (item.id === 'diagnostics')
    document.getElementById('diagnostics')?.scrollIntoView({ behavior: 'smooth' });
  else window.scrollTo({ top: 0, behavior: 'smooth' });
}
</script>

<template>
  <div class="app-shell">
    <a
      class="skip-link"
      href="#main-content"
    >
      跳到主要内容
    </a>
    <aside
      class="sidebar"
      aria-label="工作空间导航"
    >
      <a
        class="brand"
        href="#main-content"
        aria-label="巡检控制台首页"
      >
        <span class="brand-mark">
          <svg
            viewBox="0 0 32 32"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="m16 3 11 6v14l-11 6-11-6V9z"
              stroke="currentColor"
              stroke-width="1.5"
            />
            <path
              d="M11 12h10v8H11z M16 8v4 M13 16h.1 M19 16h.1"
              stroke="currentColor"
              stroke-width="1.5"
            />
          </svg>
        </span>
        <span class="brand-copy">
          巡检控制台
          <small>INSPECTION OPERATIONS</small>
        </span>
      </a>
      <div class="sidebar-label">
        运行工作空间
        <span>WORKSPACE</span>
      </div>
      <nav>
        <button
          v-for="item in navigation"
          :key="item.id"
          type="button"
          class="nav-item"
          :class="{ active: active === item.id }"
          :disabled="!item.implemented"
          :title="item.implemented ? item.label : `${item.label}：规划中，尚未实现`"
          :aria-current="active === item.id ? 'location' : undefined"
          @click="navigate(item)"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.4"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path :d="icons[item.id]" />
          </svg>
          <span>{{ item.label }}</span>
          <small>{{ item.implemented ? item.mark : '待实现' }}</small>
        </button>
      </nav>
      <div class="sidebar-project">
        <span class="section-kicker">PROJECT SCOPE</span>
        <h3>一台机器人，一个现场。</h3>
        <p>
          从设备反馈开始，逐步构建
          <br />
          完整的工业巡检工作流。
        </p>
        <button
          class="text-button"
          data-testid="roadmap"
          @click="roadmap.showModal()"
        >
          查看功能建设路线
          <span>↗</span>
        </button>
      </div>
      <div class="sidebar-bottom">
        <span class="environment-mark"></span>
        <div>
          本地模拟环境
          <small>NO HARDWARE REQUIRED</small>
        </div>
        <span class="local-label">LOCAL</span>
      </div>
    </aside>

    <div class="app-body">
      <header class="topbar">
        <div class="breadcrumb">
          <span>设备运行</span>
          <b>/</b>
          配电室工作空间
        </div>
        <div class="topbar-tools">
          <button
            class="text-button"
            data-testid="roadmap-shortcut"
            @click="roadmap.showModal()"
          >
            功能路线 ↗
          </button>
          <span class="scope-indicator">单设备演示</span>
          <span class="environment-tag">SIMULATION</span>
        </div>
      </header>
      <main
        id="main-content"
        class="main-content"
        tabindex="-1"
      >
        <slot />
      </main>
    </div>

    <dialog
      ref="roadmap"
      class="modal roadmap-modal"
      aria-labelledby="roadmap-title"
      @click="(event) => event.target === roadmap && roadmap.close()"
    >
      <div class="modal-heading">
        <div>
          <span class="section-kicker">PRODUCT ROADMAP</span>
          <h2 id="roadmap-title">从一个按钮，到一条业务链路</h2>
        </div>
        <button
          class="icon-button"
          aria-label="关闭功能建设路线"
          @click="roadmap.close()"
        >
          ×
        </button>
      </div>
      <p class="modal-intro">
        后续按下列顺序，每次实现一个小单元。未实现模块仅保留目录和入口说明。
      </p>
      <ol class="roadmap-list">
        <li
          v-for="feature in featureRoadmap"
          :key="feature.order"
        >
          <span class="roadmap-order">{{ feature.order }}</span>
          <div>
            <div class="roadmap-title">
              <h3>{{ feature.name }}</h3>
              <span
                class="badge"
                :class="feature.status === '已接通' ? 'positive' : 'neutral'"
              >
                {{ feature.status }}
              </span>
            </div>
            <p>{{ feature.description }}</p>
            <small>{{ feature.next }}</small>
          </div>
        </li>
      </ol>
      <form
        method="dialog"
        class="modal-footer"
      >
        <button class="button secondary">返回工作台</button>
      </form>
    </dialog>
  </div>
</template>
