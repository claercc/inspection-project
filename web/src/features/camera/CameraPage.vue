<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import { captureCameraImage, getCameraInfo, previewUrl } from './api';

const info = ref(null);
const preview = ref(previewUrl());
const previewError = ref('');
const captureError = ref('');
const capturing = ref(false);
const captures = ref([]);
let refreshTimer;

function refreshPreview() {
  preview.value = previewUrl();
}

async function capture() {
  capturing.value = true;
  captureError.value = '';
  try {
    const blob = await captureCameraImage();
    const item = {
      id: crypto.randomUUID(),
      url: URL.createObjectURL(blob),
      capturedAt: new Date(),
    };
    const next = [item, ...captures.value];
    next.slice(6).forEach((discarded) => URL.revokeObjectURL(discarded.url));
    captures.value = next.slice(0, 6);
  } catch (error) {
    captureError.value = error.message;
  } finally {
    capturing.value = false;
  }
}

function download(item) {
  const link = document.createElement('a');
  link.href = item.url;
  link.download = `camera-${item.capturedAt.toISOString().replaceAll(':', '-')}.jpg`;
  link.click();
}

onMounted(async () => {
  try {
    info.value = await getCameraInfo();
  } catch (error) {
    previewError.value = error.message;
  }
  refreshTimer = window.setInterval(refreshPreview, 1000);
});

onUnmounted(() => {
  window.clearInterval(refreshTimer);
  captures.value.forEach((item) => URL.revokeObjectURL(item.url));
});
</script>

<template>
  <div class="page-heading">
    <div>
      <div class="section-kicker">
        <span class="heading-line"></span>
        图像采集
      </div>
      <h1>
        摄像头
        <span class="heading-period">.</span>
      </h1>
      <p>预览现场画面，手动抓拍并下载当前图像。</p>
    </div>
    <span class="status-pill online">
      <i></i>
      {{ info?.simulated ? '模拟画面' : '摄像头在线' }}
    </span>
  </div>

  <div
    v-if="info?.simulated"
    class="mode-notice"
  >
    <span class="notice-label">模拟摄像头</span>
    <p>当前图像由软件生成，用于先跑通页面和抓拍流程；海康摄像头尚未实际接入。</p>
    <span class="notice-scope">目标型号 · DS-2CD3T25-I3</span>
  </div>

  <div class="camera-grid">
    <section class="panel camera-preview-panel">
      <div class="panel-header">
        <div>
          <span class="panel-index">01</span>
          <h2>实时预览</h2>
        </div>
        <span class="subtle-label">每秒刷新一帧</span>
      </div>
      <div class="camera-frame">
        <img
          :src="preview"
          alt="摄像头当前预览画面"
          data-testid="camera-preview"
          @load="previewError = ''"
          @error="previewError = '预览画面加载失败'"
        />
        <span class="camera-live-mark">● 预览</span>
      </div>
      <p
        v-if="previewError"
        class="inline-error"
        role="alert"
      >
        {{ previewError }}
      </p>
    </section>

    <section class="panel camera-action-panel">
      <div class="panel-header">
        <div>
          <span class="panel-index">02</span>
          <h2>手动抓拍</h2>
        </div>
      </div>
      <div class="camera-model">
        <span>目标设备</span>
        <strong>海康 DS-2CD3T25-I3</strong>
        <span>镜头</span>
        <strong>4 mm</strong>
        <span>当前驱动</span>
        <strong>{{ info?.simulated ? '软件模拟驱动' : '海康网络摄像头' }}</strong>
      </div>
      <button
        class="button primary camera-capture-button"
        data-testid="capture-camera"
        :disabled="capturing"
        @click="capture"
      >
        {{ capturing ? '正在抓拍…' : '抓拍当前画面' }}
      </button>
      <p class="camera-action-note">抓拍只保留在当前浏览器会话，可立即下载；刷新后清空。</p>
      <p
        v-if="captureError"
        class="inline-error"
        role="alert"
      >
        {{ captureError }}
      </p>
    </section>
  </div>

  <section class="panel camera-captures-panel">
    <div class="panel-header">
      <div>
        <span class="panel-index">03</span>
        <h2>本次抓拍</h2>
      </div>
      <span class="subtle-label">当前会话 · 最近 6 张</span>
    </div>
    <div
      v-if="captures.length"
      class="camera-capture-list"
    >
      <article
        v-for="item in captures"
        :key="item.id"
      >
        <img
          :src="item.url"
          alt="手动抓拍图像"
        />
        <time>{{ item.capturedAt.toLocaleString('zh-CN', { hour12: false }) }}</time>
        <button
          class="text-button"
          @click="download(item)"
        >
          下载图片 ↓
        </button>
      </article>
    </div>
    <div
      v-else
      class="camera-empty"
    >
      点击“抓拍当前画面”后，图片会显示在这里。
    </div>
  </section>
</template>
