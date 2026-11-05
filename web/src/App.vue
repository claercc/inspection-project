<script setup>
import { ref } from 'vue';
import ConsoleLayout from './layouts/ConsoleLayout.vue';
import DevicePage from './features/device/DevicePage.vue';
import CameraPage from './features/camera/CameraPage.vue';

const activePage = ref('monitoring');
</script>

<template>
  <ConsoleLayout
    :active-page="activePage"
    @navigate="activePage = $event"
  >
    <CameraPage v-if="activePage === 'camera'" />
    <DevicePage v-else />
  </ConsoleLayout>
</template>
