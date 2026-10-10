<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { useToast } from '../composables/useToast'
import { QUOTES, quoteOfDay } from '../data/quotes'
import { REVIEW_TEMPLATES, TAG_SUGGESTIONS, FORM_KEYS } from '../data/reviewTemplates'

const { push: toast } = useToast()

const tabs = [
  { key: 'DAY', label: '日复盘' },
  { key: 'WEEK', label: '周复盘' },
  { key: 'MONTH', label: '月复盘' }
]
const active = ref('DAY')
const current = ref<any>({ periodStart: '', periodEnd: '' })
const history = ref<any[]>([])
const saving = ref(false)

/* ---------------- 复盘正文（六个字段是后端固定口径） ---------------- */
const form = ref<Record<string, string>>({
  goodThings: '',
  badThings: '',
  wastedTime: '',
  unfinished: '',
  biggestGain: '',
  nextFocus: ''
})

/* ---------------- 模板库 ---------------- */
const templateKey = ref(REVIEW_TEMPLATES[0].key)
const template = computed(() => REVIEW_TEMPLATES.find(t => t.key === templateKey.value) || REVIEW_TEMPLATES[0])
const visibleFields = computed(() => template.value.fields)

/** 当前模板没展示、但已经写了内容的字段 —— 提示用户「换模板不会清空」 */
const hiddenFilled = computed(() => {
  const shown = new Set(visibleFields.value.map(f => f.key))
  return FORM_KEYS.filter(k => !shown.has(k) && (form.value[k] || '').trim())
})

function applyTemplate(t: (typeof REVIEW_TEMPLATES)[number]) {
  if (t.key === templateKey.value) return
  templateKey.value = t.key
  toast(`已套用「${t.name}」`)
}

/* ---------------- 结构化自评 ---------------- */
const METRIC_KEYS = { mood: 0, focus: 0, completion: 0, wasteSource: '', taskType: '' }
const metrics = ref({ ...METRIC_KEYS, tags: [] as string[] })

const WASTE_SOURCES = ['社交媒体', '无目标刷信息', '拖延', '被频繁打断', '低效会议', '其他']
const TASK_TYPES = ['学习', '项目', '输出', '生活']

/* ---------------- 标签（存进 metricsJson，零数据库迁移） ---------------- */
const tagInput = ref('')
function addTag(raw?: string) {
  const t = (raw ?? tagInput.value).trim()
  if (!t) return
  if (!metrics.value.tags.includes(t)) metrics.value.tags = [...metrics.value.tags, t]
  tagInput.value = ''
}
function removeTag(t: string) {
  metrics.value.tags = metrics.value.tags.filter(x => x !== t)
}

const monthSummary = ref('')
const aiBusy = ref(false)

/* ---------------- 格言 ---------------- */
const quoteIdx = ref(Math.max(0, QUOTES.indexOf(quoteOfDay())))
const quote = computed(() => QUOTES[quoteIdx.value])
function nextQuote() {
  quoteIdx.value = (quoteIdx.value + 1) % QUOTES.length
}

/* ---------------- 本地自评（非 AI，纯前端估算） ---------------- */
const filledCount = computed(() => visibleFields.value.filter(f => (form.value[f.key] || '').trim()).length)
const totalChars = computed(() => FORM_KEYS.reduce((sum, k) => sum + (form.value[k] || '').trim().length, 0))

const scores = computed(() => {
  const nextLen = (form.value.nextFocus || '').trim().length
  const total = visibleFields.value.length || 1
  return [
    { label: '完整度', value: Math.round((filledCount.value / total) * 100) },
    { label: '深度', value: Math.min(100, Math.round(totalChars.value / 4)) },
    { label: '可执行性', value: nextLen ? Math.min(100, 40 + nextLen * 2) : 0 }
  ]
})

function parseMetrics(raw: any) {
  try {
    const o = typeof raw === 'string' ? JSON.parse(raw) : raw
    return {
      mood: Number(o?.mood) || 0,
      focus: Number(o?.focus) || 0,
      completion: Number(o?.completion) || 0,
      wasteSource: o?.wasteSource || '',
      taskType: o?.taskType || '',
      tags: Array.isArray(o?.tags) ? o.tags.map((t: any) => String(t)).filter(Boolean) : []
    }
  } catch {
    return { ...METRIC_KEYS, tags: [] as string[] }
  }
}

/* ---------------- 与上期对照 ---------------- */
/** 历史里第一条「不是当前周期」的记录，就是上一期 */
const prevReview = computed(() => {
  const cur = current.value?.reviewDate
  return history.value.find(h => h.reviewDate !== cur) || null
})
const prevMetrics = computed(() => (prevReview.value ? parseMetrics(prevReview.value.metricsJson) : null))
const prevFocus = computed(() => (prevReview.value?.nextFocus || '').trim())

const cmpRows = computed(() => {
  const p = prevMetrics.value
  if (!p) return []
  return [
    { name: '情绪', prev: p.mood, cur: metrics.value.mood, unit: '' },
    { name: '专注', prev: p.focus, cur: metrics.value.focus, unit: '' },
    { name: '完成度', prev: p.completion, cur: metrics.value.completion, unit: '%' }
  ]
})
function deltaOf(r: { prev: number; cur: number }) {
  return (r.cur || 0) - (r.prev || 0)
}
function deltaText(r: { prev: number; cur: number; unit: string }) {
  const d = deltaOf(r)
  if (!d) return '持平'
  return `${d > 0 ? '▲' : '▼'} ${Math.abs(d)}${r.unit}`
}
function deltaClass(r: { prev: number; cur: number }) {
  const d = deltaOf(r)
  return d ? (d > 0 ? 'up' : 'down') : 'flat'
}

/* ---------------- 行动区：把复盘变成任务 ---------------- */
const actions = ref<any[]>([])
const actionTitle = ref('')
const actionPriority = ref('MEDIUM')
const actionBusy = ref(false)

function todayLocal() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function addAction(raw?: string) {
  const t = (raw ?? actionTitle.value).trim()
  if (!t) {
    toast('先写下要做的事')
    return
  }
  actionBusy.value = true
  try {
    await api.createTask({ title: t, priority: actionPriority.value, dueDate: todayLocal() })
    // 演示模式下接口只回 true，所以本地自己记一条，界面才能立刻看到
    actions.value = [...actions.value, { id: Date.now(), title: t, priority: actionPriority.value }]
    if (raw === undefined) actionTitle.value = ''
    toast('已加入今日任务', 'ok')
  } catch {
    toast('加入失败，稍后再试')
  } finally {
    actionBusy.value = false
  }
}

/* ---------------- 历史搜索 + 标签筛选 ---------------- */
const q = ref('')
const tagFilter = ref('')

interface HistoryRow {
  [k: string]: any
  tags: string[]
}
const historyView = computed<HistoryRow[]>(() =>
  history.value.map((h): HistoryRow => ({ ...h, tags: parseMetrics(h.metricsJson).tags }))
)

const allTags = computed(() => {
  const s = new Set<string>()
  historyView.value.forEach(h => h.tags.forEach(t => s.add(t)))
  metrics.value.tags.forEach(t => s.add(t))
  return [...s]
})

const filteredHistory = computed(() => {
  const kw = q.value.trim().toLowerCase()
  return historyView.value.filter(h => {
    if (tagFilter.value && !h.tags.includes(tagFilter.value)) return false
    if (!kw) return true
    const hay = [h.reviewDate, h.goodThings, h.badThings, h.wastedTime, h.unfinished, h.biggestGain, h.nextFocus, h.tags.join(' ')]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return hay.includes(kw)
  })
})

/* ---------------- 加载与保存 ---------------- */
async function load(reset = false) {
  const [cur, his] = await Promise.all([api.currentReview(active.value), api.reviews(active.value, 8)])
  current.value = cur.data.data
  history.value = his.data.data || []
  const ex = cur.data.data?.existing
  form.value = {
    goodThings: ex?.goodThings || '',
    badThings: ex?.badThings || '',
    wastedTime: ex?.wastedTime || '',
    unfinished: ex?.unfinished || '',
    biggestGain: ex?.biggestGain || '',
    nextFocus: ex?.nextFocus || ''
  }
  metrics.value = parseMetrics(ex?.metricsJson)
  // 只有切换周期时才清空「本次已加入的任务」，保存后保留，否则刚加完就消失
  if (reset) {
    actions.value = []
    tagFilter.value = ''
  }
}

async function save() {
  saving.value = true
  try {
    await api.saveReview({
      reviewType: active.value,
      reviewDate: current.value.reviewDate,
      ...form.value,
      metricsJson: JSON.stringify(metrics.value)
    })
    await load()
    toast('复盘已保存', 'ok')
  } finally {
    saving.value = false
  }
}

async function genMonthly() {
  aiBusy.value = true
  monthSummary.value = ''
  try {
    const res: any = await api.aiMonthlySummary()
    monthSummary.value = res.data.success ? res.data.data.content : res.data.message
  } catch (e: any) {
    monthSummary.value = e?.response?.data?.message || 'AI 调用失败'
  } finally {
    aiBusy.value = false
  }
}

watch(active, () => load(true))
onMounted(() => load(true))
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">REVIEW</div>
        <h1>复盘</h1>
        <p>日 / 周 / 月 —— 让每一次行动都有回声。</p>
      </div>
    </div>

    <!-- 格言：每天一句，供自己对照 -->
    <div class="quote-card">
      <div class="quote-body">
        <p class="quote-text">「{{ quote.text }}」</p>
        <p class="quote-source">—— {{ quote.source }}</p>
      </div>
      <button class="ghost tiny quote-next" @click="nextQuote">换一句</button>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.key" class="tab" :class="{ active: active === t.key }" @click="active = t.key">
        {{ t.label }}
      </button>
    </div>

    <!-- 模板库：只换问法，不换字段，所以换模板不会丢内容 -->
    <div class="tpl-bar">
      <span class="tpl-label">模板</span>
      <button
        v-for="t in REVIEW_TEMPLATES"
        :key="t.key"
        class="tpl-chip"
        :class="{ on: t.key === templateKey }"
        :title="t.desc"
        @click="applyTemplate(t)"
      >
        {{ t.name }}
      </button>
      <span class="tpl-desc">{{ template.desc }}</span>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="card-title">
          <span>本期复盘</span>
          <span class="hint">{{ current.periodStart }} → {{ current.periodEnd }}</span>
        </div>

        <!-- 结构化自评 -->
        <div class="metric-block">
          <div class="metric-row">
            <span class="metric-label">情绪评分</span>
            <div class="seg">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="seg-btn"
                :class="{ on: metrics.mood === n }"
                @click="metrics.mood = metrics.mood === n ? 0 : n"
              >
                {{ n }}
              </button>
            </div>
            <span class="metric-val">{{ metrics.mood ? metrics.mood + ' / 5' : '未评' }}</span>
          </div>

          <div class="metric-row">
            <span class="metric-label">专注度</span>
            <div class="seg">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="seg-btn"
                :class="{ on: metrics.focus === n }"
                @click="metrics.focus = metrics.focus === n ? 0 : n"
              >
                {{ n }}
              </button>
            </div>
            <span class="metric-val">{{ metrics.focus ? metrics.focus + ' / 5' : '未评' }}</span>
          </div>

          <div class="metric-row">
            <span class="metric-label">完成度</span>
            <input v-model.number="metrics.completion" type="range" min="0" max="100" step="5" class="metric-range" />
            <span class="metric-val">{{ metrics.completion }}%</span>
          </div>

          <div class="metric-row">
            <span class="metric-label">时间漏在哪</span>
            <select v-model="metrics.wasteSource" class="metric-select">
              <option value="">未选择</option>
              <option v-for="s in WASTE_SOURCES" :key="s" :value="s">{{ s }}</option>
            </select>
            <span class="metric-val"></span>
          </div>

          <div class="metric-row">
            <span class="metric-label">任务类型</span>
            <select v-model="metrics.taskType" class="metric-select">
              <option value="">未选择</option>
              <option v-for="t in TASK_TYPES" :key="t" :value="t">{{ t }}</option>
            </select>
            <span class="metric-val"></span>
          </div>
        </div>

        <!-- 标签：存进 metricsJson，可用于历史搜索 -->
        <div class="tag-edit">
          <div class="tag-chips">
            <span v-for="t in metrics.tags" :key="t" class="tag-chip on">
              {{ t }}
              <span class="x" @click="removeTag(t)">×</span>
            </span>
            <span v-if="!metrics.tags.length" class="tag-hint">还没打标签 —— 加几个，之后可按标签翻历史。</span>
          </div>
          <div class="tag-add">
            <input v-model="tagInput" placeholder="输入标签后回车，例如：专注" @keyup.enter="addTag()" />
            <button class="ghost tiny" @click="addTag()">添加</button>
          </div>
          <div class="tag-suggest">
            <button
              v-for="t in TAG_SUGGESTIONS.filter(s => !metrics.tags.includes(s))"
              :key="t"
              class="tag-chip"
              @click="addTag(t)"
            >
              + {{ t }}
            </button>
          </div>
        </div>

        <!-- 引导式问题（随模板变化） -->
        <div v-for="f in visibleFields" :key="f.key" class="review-field">
          <label class="field">
            <span class="field-head">
              {{ f.label }}
              <small>{{ f.q }}</small>
            </span>
            <textarea v-model="form[f.key]" :placeholder="f.placeholder || '写下你的复盘…'"></textarea>
          </label>
        </div>

        <p v-if="hiddenFilled.length" class="tpl-note">
          另有 {{ hiddenFilled.length }} 项已写内容未在当前模板中显示，保存时仍会一并提交。
        </p>

        <div class="form-row" style="justify-content: flex-end">
          <button class="primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存复盘' }}</button>
        </div>
      </div>

      <div class="stack">
        <!-- 行动区：把复盘结论直接变成今天要做的事 -->
        <div class="card elevated">
          <div class="card-title">
            <span>把复盘变成行动</span>
            <span class="hint">直接写进今日任务</span>
          </div>
          <div class="form-row">
            <input v-model="actionTitle" placeholder="下一步要做什么？" @keyup.enter="addAction()" />
            <select v-model="actionPriority" class="prio-sel">
              <option value="HIGH">高</option>
              <option value="MEDIUM">中</option>
              <option value="LOW">低</option>
            </select>
            <button class="primary tiny" :disabled="actionBusy" @click="addAction()">加入</button>
          </div>
          <button
            v-if="(form.nextFocus || '').trim()"
            class="ghost tiny action-from-focus"
            :disabled="actionBusy"
            @click="addAction(form.nextFocus)"
          >
            ↑ 用「下一步重点」生成任务
          </button>
          <div v-if="actions.length" class="action-list">
            <div v-for="a in actions" :key="a.id" class="action-item">
              <span class="tag ok">已加入</span>
              <span class="action-txt">{{ a.title }}</span>
            </div>
          </div>
          <p v-else class="action-hint">复盘完别停在感想上 —— 写下一条，它就出现在「今日」里。</p>
        </div>

        <!-- 与上期对照 -->
        <div class="card">
          <div class="card-title">
            <span>与上期对照</span>
            <span class="hint">{{ prevReview ? prevReview.reviewDate : '暂无上期' }}</span>
          </div>
          <div v-if="!prevReview" class="empty">还没有上一期记录。保存一次，下次打开就有对照了。</div>
          <template v-else>
            <div class="cmp-list">
              <div v-for="r in cmpRows" :key="r.name" class="cmp-row">
                <span class="cmp-name">{{ r.name }}</span>
                <span class="cmp-prev">{{ r.prev }}{{ r.unit }}</span>
                <span class="cmp-arrow">→</span>
                <span class="cmp-cur">{{ r.cur }}{{ r.unit }}</span>
                <span class="cmp-delta" :class="deltaClass(r)">{{ deltaText(r) }}</span>
              </div>
            </div>
            <div v-if="prevFocus" class="cmp-focus">
              <div class="cmp-focus-h">上期你定的重点</div>
              <p>{{ prevFocus }}</p>
            </div>
          </template>
        </div>

        <!-- 自评（本地估算，非 AI） -->
        <div class="card">
          <div class="card-title">
            <span>本次复盘自评</span>
            <span class="hint">本地估算 · 仅供参考</span>
          </div>
          <div class="score-list">
            <div v-for="s in scores" :key="s.label" class="score-row">
              <span class="score-name">{{ s.label }}</span>
              <div class="score-track"><span :style="{ width: s.value + '%' }"></span></div>
              <span class="score-val">{{ s.value }}</span>
            </div>
          </div>
          <p class="score-note">当前模板有 {{ visibleFields.length }} 个问题，写完并给出明确「下一步重点」，分数自然会上去。</p>
        </div>

        <div class="card">
          <div class="card-title">
            <span>AI 月度总结</span>
            <button class="ghost tiny" :disabled="aiBusy" @click="genMonthly">{{ aiBusy ? '生成中…' : '生成' }}</button>
          </div>
          <p v-if="!monthSummary" class="empty">基于本月真实数据生成复盘初稿。</p>
          <p v-else class="ai-out-text">{{ monthSummary }}</p>
        </div>

        <div class="card">
          <div class="card-title">
            <span>历史复盘</span>
            <span class="hint">{{ filteredHistory.length }} / {{ history.length }}</span>
          </div>

          <div class="search-row">
            <input v-model="q" placeholder="搜索日期 / 内容 / 标签…" />
          </div>

          <div v-if="allTags.length" class="tag-filter">
            <button class="tag-chip" :class="{ on: !tagFilter }" @click="tagFilter = ''">全部</button>
            <button
              v-for="t in allTags"
              :key="t"
              class="tag-chip"
              :class="{ on: tagFilter === t }"
              @click="tagFilter = tagFilter === t ? '' : t"
            >
              {{ t }}
            </button>
          </div>

          <div v-if="!filteredHistory.length" class="empty">
            {{ history.length ? '没有匹配的记录。' : '还没有历史记录。' }}
          </div>
          <div v-for="h in filteredHistory" :key="h.id" class="hist-item">
            <div class="hist-head">
              <span class="hist-date">{{ h.reviewDate }}</span>
              <span class="hist-gain">{{ (h.biggestGain || h.goodThings || '—').slice(0, 24) }}</span>
            </div>
            <div v-if="h.tags.length" class="hist-tags">
              <span v-for="t in h.tags" :key="t" class="tag">{{ t }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
