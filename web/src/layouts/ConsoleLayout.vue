<script setup>
import { navigation } from '../config/workspace';

const props = defineProps({
  activePage: { type: String, default: 'monitoring' },
});
const emit = defineEmits(['navigate']);

function navigate(page) {
  emit('navigate', page);
  window.scrollTo({ top: 0, behavior: 'smooth' });
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
        aria-label="设备工作台首页"
      >
        <span class="brand-mark">◈</span>
        <span class="brand-copy">
          设备工作台
          <small>监控与采集</small>
        </span>
      </a>
      <div class="sidebar-label">工作空间</div>
      <nav>
        <button
          v-for="item in navigation"
          :key="item.id"
          type="button"
          class="nav-item"
          :class="{ active: item.id === props.activePage }"
          :disabled="!item.implemented"
          :title="item.implemented ? item.label : `${item.label}：尚未接入`"
          :aria-current="item.id === props.activePage ? 'page' : undefined"
          @click="navigate(item.id)"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.mark }}</small>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <span class="environment-mark"></span>
        <div>
          本地演示
          <small>设备数据来自模拟器</small>
        </div>
      </div>
    </aside>
    <div class="app-body">
      <header class="topbar">
        <div class="breadcrumb">
          <span>工作空间</span>
          <b>/</b>
          {{ navigation.find((item) => item.id === props.activePage)?.label || '设备监控' }}
        </div>
        <div class="topbar-tools">
          <span class="scope-indicator">单设备</span>
          <span class="environment-tag">模拟数据</span>
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
  </div>
</template>
