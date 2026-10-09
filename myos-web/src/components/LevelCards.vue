<script setup lang="ts">
defineProps<{
  levels: Array<{
    systemCode: string
    systemName: string
    level: number
    amount: number
    nextLevelAt: number
  }>
}>()

function pad(n: number) {
  return 'Lv.' + String(n).padStart(2, '0')
}
</script>

<template>
  <div class="level-grid">
    <div v-for="l in levels" :key="l.systemCode" class="level-row">
      <span class="level-name">{{ l.systemName }}</span>
      <span class="level-badge" :class="{ hi: l.level >= 3 }">{{ pad(l.level) }}</span>
    </div>
  </div>
</template>
