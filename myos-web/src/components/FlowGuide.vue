<script setup lang="ts">
defineProps<{
  steps: Array<{ key: string; label: string; hint?: string; done: boolean; to: string; optional?: boolean }>
}>()

const emit = defineEmits<{ (e: 'go', to: string): void }>()
</script>

<template>
  <ol class="flow-guide">
    <li v-for="(s, i) in steps" :key="s.key" class="flow-item">
      <button
        type="button"
        class="flow-step"
        :class="{ done: s.done, optional: s.optional }"
        @click="emit('go', s.to)"
      >
        <span class="flow-idx" aria-hidden="true">
          <template v-if="s.done">✓</template>
          <template v-else>{{ i + 1 }}</template>
        </span>
        <span class="flow-text">
          <strong>{{ s.label }}</strong>
          <small>{{ s.hint || (s.done ? '已完成' : '待完成') }}</small>
        </span>
      </button>
      <span v-if="i < steps.length - 1" class="flow-arrow" aria-hidden="true">→</span>
    </li>
  </ol>
</template>
