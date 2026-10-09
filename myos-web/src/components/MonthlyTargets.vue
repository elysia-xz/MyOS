<script setup lang="ts">
defineProps<{
  rows: Array<{
    systemCode: string
    systemName: string
    doneDays: number
    targetDays: number
    percent: number
  }>
}>()
</script>

<template>
  <div class="water-list">
    <div v-for="r in rows" :key="r.systemCode" class="water-row">
      <span class="water-name">{{ r.systemName }}</span>
      <div class="bar-track">
        <div class="bar-fill" :class="r.percent >= 100 ? 'ok' : 'partial'" :style="{ width: r.percent + '%' }"></div>
      </div>
      <span class="water-val">{{ r.doneDays }} / {{ r.targetDays }} 天</span>
    </div>
  </div>
</template>
