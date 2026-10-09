<script setup lang="ts">
defineProps<{
  rows: Array<{ systemCode: string; systemName: string; percent: number; done: boolean }>
}>()

const emit = defineEmits<{ (e: 'pick'): void }>()

/** 三种状态：已达标 / 有进展未达标 / 无记录 */
function state(r: { percent: number; done: boolean }) {
  if (r.done || r.percent >= 100) return 'on'
  return r.percent > 0 ? 'partial' : 'off'
}

const stateText: Record<string, string> = {
  on: '已达标',
  partial: '有进展',
  off: '无记录'
}
</script>

<template>
  <div class="sys-dots">
    <button
      v-for="r in rows"
      :key="r.systemCode"
      type="button"
      class="sys-dot"
      :class="state(r)"
      :title="`${r.systemName} · ${stateText[state(r)]}`"
      @click="emit('pick')"
    >
      <span class="dot-mark" aria-hidden="true"></span>
      <span class="dot-name">{{ r.systemName }}</span>
    </button>
  </div>
</template>
