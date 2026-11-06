<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { ChatMsg, PendingCall } from '../composables/useAiChat'

const props = defineProps<{
  available: boolean
  result: string
  convId: number | null
  msgs: ChatMsg[]
  pending: PendingCall[]
  chatBusy: boolean
  chatErr: string
  conversations: any[]
  historyOpen: boolean
  loadingHistory: boolean
}>()
const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'configure'): void
  (e: 'result', text: string): void
  (e: 'send', text: string): void
  (e: 'confirm', ids: string[]): void
  (e: 'cancel', ids: string[]): void
  (e: 'newChat'): void
  (e: 'toggleHistory'): void
  (e: 'openConversation', id: number): void
}>()

// 生成结果由父组件持有（见下方注释），这里只保留瞬时状态
const busy = ref('')
const err = ref('')

/* ---------------- 产出存档 ----------------
   早先生成的日报/复盘只活在内存里，刷新即失，也进不了「月复盘 → 年度档案」。
   现在后端 `run()` 会把每次生成的正文写进 ai_output，这里只是把它读回来。
   列表故意放在本组件而不是父组件：它不需要跨刷新存活，刷新后重取即可。 */
const outputs = ref<any[]>([])
const showOutputs = ref(false)

const SCENES: Record<string, string> = {
  daily_summary: '今日总结',
  daily_report: '日报',
  daily_review: '今日复盘',
  monthly_summary: '月度总结',
  annual_report: '年度报告'
}
function sceneLabel(s: string) {
  return SCENES[s] || s
}

async function loadOutputs() {
  try {
    const res: any = await api.aiOutputs(6)
    outputs.value = res.data?.data || []
  } catch {
    // 老库没跑 v1.8 时这个接口会 500；存档只是加分项，不能因此报错刷屏
    outputs.value = []
  }
}

onMounted(loadOutputs)

const actions = [
  { key: 'summary', label: '和 AI 讨论', run: () => api.aiDailySummary() },
  { key: 'report', label: '生成日报', run: () => api.aiDailyReport() },
  { key: 'review', label: '复盘今天', run: () => api.aiDailyReview() },
  { key: 'month', label: '本月总结', run: () => api.aiMonthlySummary() }
]

const draft = ref('')

/** 回车发送。shift+enter 留给换行（输入框是单行的，这里只是防呆） */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSend()
  }
}

function doSend() {
  const text = draft.value.trim()
  if (!text || props.chatBusy) return
  draft.value = ''
  emit('send', text)
}

/** 行内时间：只到分，列表里不需要秒 */
function hm(s?: string) {
  if (!s) return ''
  return String(s).slice(11, 16)
}

/** 参数摘要：title=甲任务A | priority=HIGH */
function argSummary(c: PendingCall) {
  try {
    const o = JSON.parse(c.arguments || '{}')
    return Object.entries(o)
      .map(([k, v]) => `${k}=${typeof v === 'string' ? v : JSON.stringify(v)}`)
      .join(' | ')
  } catch {
    return ''
  }
}

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
      // 后端已经落库，这里把存档列表刷一下 —— 让「生成过」这件事看得见
      loadOutputs()
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

      <!-- 产出存档：每次生成都会落库，刷新页面、换设备都还拿得回来 -->
      <div v-if="outputs.length" class="ai-archive">
        <button class="ai-archive-toggle" type="button" @click="showOutputs = !showOutputs">
          {{ showOutputs ? '收起存档' : `最近产出 · ${outputs.length} 条已存档` }}
        </button>
        <div v-if="showOutputs" class="ai-archive-list">
          <button
            v-for="o in outputs"
            :key="o.id"
            class="ai-archive-item"
            type="button"
            @click="emit('result', tidy(o.content))"
          >
            <span class="ai-archive-scene">{{ sceneLabel(o.scene) }}</span>
            <span class="ai-archive-time">{{ o.createdAt }}</span>
          </button>
        </div>
      </div>

      <!-- ============ 问答 / 办事（二期）============ -->
      <div class="chat-block">
        <div class="chat-head">
          <span>或者直接问我</span>
          <span class="chat-head-ops">
            <button class="chat-op" type="button" :disabled="props.chatBusy" @click="emit('newChat')">
              新对话
            </button>
            <button class="chat-op" type="button" :disabled="props.chatBusy" @click="emit('toggleHistory')">
              {{ props.historyOpen ? '收起历史' : '历史' }}
            </button>
          </span>
        </div>

        <!-- 历史会话列表 -->
        <div v-if="props.historyOpen" class="chat-history">
          <p v-if="props.loadingHistory" class="chat-history-empty">正在读取…</p>
          <p v-else-if="!props.conversations.length" class="chat-history-empty">还没有历史对话</p>
          <button
            v-for="c in props.conversations"
            :key="c.id"
            class="chat-history-item"
            type="button"
            :class="{ active: String(c.id) === String(props.convId) }"
            @click="emit('openConversation', Number(c.id))"
          >
            <span class="chat-history-title">{{ c.title || '未命名对话' }}</span>
            <span class="chat-history-meta">{{ c.messageCount ?? 0 }} 条</span>
          </button>
        </div>

        <!-- 消息流 -->
        <div v-if="props.msgs.length || props.pending.length" class="chat-log">
          <div v-for="(m, i) in props.msgs" :key="i" class="chat-row" :class="m.role">
            <template v-if="m.role === 'tool'">
              <div class="chat-tool">已执行：{{ m.content }}</div>
            </template>
            <template v-else>
              <div class="chat-bubble" :class="m.role">
                <span v-if="hm(m.createdAt)" class="chat-time">{{ hm(m.createdAt) }}</span>
                {{ m.content }}
              </div>
            </template>
          </div>

          <!-- 待确认卡片：写操作一律停在这里，点了确认才会真的落库 -->
          <div v-if="props.pending.length" class="confirm-card">
            <p class="confirm-title">
              待确认（{{ props.pending.filter(c => c.risk === 'WRITE').length }} 项会改动数据）
            </p>
            <div v-for="c in props.pending" :key="c.id" class="confirm-row" :class="c.risk">
              <span class="confirm-risk" :class="c.risk">{{ c.risk === 'WRITE' ? '写' : '读' }}</span>
              <span class="confirm-label">{{ c.label || c.name }}</span>
              <span class="confirm-args">{{ argSummary(c) }}</span>
            </div>
            <div class="confirm-ops">
              <button
                class="confirm-ok"
                type="button"
                :disabled="props.chatBusy"
                @click="emit('confirm', props.pending.map(c => c.id))"
              >
                {{ props.chatBusy ? '处理中…' : '确认执行' }}
              </button>
              <button
                class="confirm-no"
                type="button"
                :disabled="props.chatBusy"
                @click="emit('cancel', props.pending.map(c => c.id))"
              >
                取消
              </button>
            </div>
          </div>
        </div>

        <!-- 空态：给两句可直接点的提示，降低「不知道能干嘛」的门槛 -->
        <div v-else-if="!props.pending.length" class="chat-empty">
          <button class="chat-chip" type="button" :disabled="props.chatBusy" @click="emit('send', '我现在有哪些任务没完成？')">
            我现在有哪些任务没完成？
          </button>
          <button class="chat-chip" type="button" :disabled="props.chatBusy" @click="emit('send', '帮我记一下：今天英语学了 45 分钟')">
            今天英语学了 45 分钟，帮我记一下
          </button>
          <button class="chat-chip" type="button" :disabled="props.chatBusy" @click="emit('send', '帮我建一条任务：周末整理本周复盘')">
            帮我建一条任务：周末整理本周复盘
          </button>
        </div>

        <p v-if="props.chatErr" class="chat-err">{{ props.chatErr }}</p>

        <div class="chat-input">
          <input
            v-model="draft"
            type="text"
            placeholder="问点什么，或者说「帮我创建一条任务」"
            :disabled="props.chatBusy"
            @keydown="onKeydown"
          />
          <button class="chat-send" type="button" :disabled="props.chatBusy || !draft.trim()" @click="doSend">
            {{ props.chatBusy ? '…' : '发送' }}
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/*
 * 产出存档。刻意做得很轻：它是「刚才那段字还在」的证据，
 * 不是要抢对话区的位置，所以默认收起、只占一行。
 */
.ai-archive {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line2);
}

.ai-archive-toggle {
  border: 0;
  background: none;
  padding: 0;
  font-size: 11.5px;
  color: var(--faint);
  cursor: pointer;
}

.ai-archive-toggle:hover {
  color: var(--primary-ink);
}

.ai-archive-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 8px;
}

.ai-archive-item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: none;
  text-align: left;
  cursor: pointer;
  font-size: 11.5px;
  color: var(--muted);
}

.ai-archive-item:hover {
  background: var(--line2);
  color: var(--ink);
}

.ai-archive-scene {
  font-weight: 600;
}

.ai-archive-time {
  color: var(--faint);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
