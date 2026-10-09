<script setup lang="ts">
defineProps<{
  rows: Array<{
    systemCode: string
    systemName: string
    unit: string
    value: number
    target: number
    done: boolean
    percent: number
    recorded: boolean
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
      <span class="water-val">
        <template v-if="r.done"><span class="done-mark">✓ 已达标</span></template>
        <template v-else-if="!r.recorded || r.value === 0"><span class="none">— 无记录</span></template>
        <template v-else>{{ r.value }} / {{ r.target }} {{ r.unit }}</template>
      </span>
    </div>
  </div>
</template>
