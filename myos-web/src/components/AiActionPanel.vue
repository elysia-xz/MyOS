<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'

const props = defineProps<{ available: boolean; result: string }>()
const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'configure'): void
  (e: 'result', text: string): void
}>()

// 生成结果由父组件持有（见下方注释），这里只保留瞬时状态
const busy = ref('')
const err = ref('')

const actions = [
  { key: 'summary', label: '和 AI 讨论', run: () => api.aiDailySummary() },
  { key: 'report', label: '生成日报', run: () => api.aiDailyReport() },
  { key: 'review', label: '复盘今天', run: () => api.aiDailyReview() },
  { key: 'month', label: '本月总结', run: () => api.aiMonthlySummary() }
]

/**
 * 模型偶尔会自带 markdown 记号（**加粗**、# 标题、- 列表）。
 * 本面板是纯文本渲染，直接显示会露出星号；而生成日报/复盘的下一步
 * 往往是复制到聊天或文档里，星号会一起被带走，所以在这里做最小清理。
 */
function tidy(text: string) {
  return text
    .replace(/\*\*(.+?)\*\*/g, '$1') // **加粗** → 加粗
    .replace(/^\s*#{1,6}\s+/gm, '') // # 标题 → 标题
    .replace(/^\s*[-*]\s+/gm, '· ') // - 列表 → · 列表
    .trim()
}

/**
 * 生成结果为什么放在父组件：
 *
 * 之前把 output 存在本组件里，点「生成日报」后必然看不到结果。
 * 链路是：go() 里 output.value = 正文 → emit('refresh')
 *        → 父组件 load() 第一行 loading.value = true
 *        → 首页 <template v-else> 整块卸载，本组件实例连同 output 一起销毁
 *        → loading.value = false 重新挂载一个全新实例，output 回到 ''
 * 现象就是「内容生成了、toast 也弹了，但正文一片空白，且没有任何报错」。
 *
 * 结论：跨刷新的结果必须由不受刷新影响的父组件持有。
 */
async function go(a: (typeof actions)[number]) {
  if (busy.value) return
  busy.value = a.key
  err.value = ''
  emit('result', '') // 开始新一次生成时先清掉上一次的正文
  try {
    const res: any = await a.run()
    const body = res?.data
    if (import.meta.env.DEV) {
      console.log(`[ai:${a.key}] 响应外壳 =`, JSON.stringify(body)?.slice(0, 300))
    }
    // 逐层判断并保证每个失败分支都落到 err 上。
    // 不能写成 `if (body.success) output.value = body.data?.content || ''`：
    // 一旦响应形状不是预期的，就会 err 和正文同时为空，界面完全静默，无从排查。
    const text = body?.success ? body?.data?.content : ''
    if (typeof text === 'string' && text.trim()) {
      emit('result', tidy(text))
      emit('refresh')
    } else if (body?.success) {
      err.value = 'AI 返回了空内容，请重试或检查模型配置'
    } else {
      err.value = body?.message || 'AI 调用失败'
    }
  } catch (e: any) {
    err.value = e?.response?.data?.message || e?.message || 'AI 调用失败'
  } finally {
    busy.value = ''
  }
}

// 生成日报/复盘的下一步动作就是「拿走这段文字」，所以给一个复制入口。
// clipboard API 在非 HTTPS 的局域网地址下不可用，故保留 execCommand 兜底。
const copied = ref(false)
async function copy() {
  const text = props.result
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
    } finally {
      document.body.removeChild(ta)
    }
  }
  copied.value = true
  setTimeout(() => (copied.value = false), 1500)
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
      <div v-else-if="props.result" class="ai-out">
        <pre>{{ props.result }}</pre>
        <button class="ai-copy" type="button" @click="copy">{{ copied ? '已复制' : '复制正文' }}</button>
      </div>
    </template>
  </div>
</template>
