<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'

const props = defineProps<{ available: boolean }>()
const emit = defineEmits<{ (e: 'refresh'): void; (e: 'configure'): void }>()

const output = ref('')
const busy = ref('')
const err = ref('')

const actions = [
  { key: 'summary', label: '和 AI 讨论', run: () => api.aiDailySummary() },
  { key: 'report', label: '生成日报', run: () => api.aiDailyReport() },
  { key: 'review', label: '复盘今天', run: () => api.aiDailyReview() },
  { key: 'month', label: '本月总结', run: () => api.aiMonthlySummary() }
]

async function go(a: (typeof actions)[number]) {
  if (busy.value) return
  busy.value = a.key
  err.value = ''
  output.value = ''
  try {
    const res: any = await a.run()
    const body = res.data
    if (body.success) {
      output.value = body.data?.content || ''
      emit('refresh')
    } else {
      err.value = body.message || 'AI 调用失败'
    }
  } catch (e: any) {
    err.value = e?.response?.data?.message || 'AI 调用失败'
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div class="ai-panel">
    <p class="ai-title">今日 AI 助手</p>
    <p class="ai-sub">基于今天的真实数据，不是空谈。</p>

    <!-- 未启用：给出明确的用途说明与行动入口，而不是一句冷冰冰的报错 -->
    <div v-if="!props.available" class="ai-off">
      <strong>AI 助手未启用</strong>
      <p>配置后可自动生成：今日任务建议、沉淀总结、项目复盘</p>
      <button class="ai-configure" type="button" @click="emit('configure')">立即配置</button>
    </div>

    <template v-else>
      <div class="ai-actions">
        <button v-for="a in actions" :key="a.key" class="ai-btn" :disabled="!!busy" @click="go(a)">
          {{ busy === a.key ? '生成中…' : a.label }}
        </button>
      </div>
      <div v-if="err" class="ai-out"><pre>{{ err }}</pre></div>
      <div v-else-if="output" class="ai-out"><pre>{{ output }}</pre></div>
    </template>
  </div>
</template>
