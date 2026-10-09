<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { useToast } from '../composables/useToast'
import { QUOTES, quoteOfDay } from '../data/quotes'

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

/** 引导式问题：每个字段配一个具体的追问，避免写成流水账 */
const FIELDS = [
  { key: 'goodThings', label: '做得好的事', q: '这一段最重要的 1 件事是什么？为什么它重要？' },
  { key: 'badThings', label: '没做好的事', q: '哪个行为导致了不理想的结果？' },
  { key: 'wastedTime', label: '浪费的时间', q: '时间主要漏在了哪里？下次怎么堵住？' },
  { key: 'unfinished', label: '未完成的事', q: '哪件事被拖下来了？卡在哪一步？' },
  { key: 'biggestGain', label: '最大收获', q: '哪个决策或方法值得复用？' },
  { key: 'nextFocus', label: '下一步重点', q: '接下来最重要的一件事是什么？' }
] as const

const form = ref<Record<string, string>>({
  goodThings: '',
  badThings: '',
  wastedTime: '',
  unfinished: '',
  biggestGain: '',
  nextFocus: ''
})

/** 结构化自评：让复盘从「日记」变成「可统计的数据」 */
const METRIC_KEYS = { mood: 0, focus: 0, completion: 0, wasteSource: '', taskType: '' }
const metrics = ref({ ...METRIC_KEYS })

const WASTE_SOURCES = ['社交媒体', '无目标刷信息', '拖延', '被频繁打断', '低效会议', '其他']
const TASK_TYPES = ['学习', '项目', '输出', '生活']

const monthSummary = ref('')
const aiBusy = ref(false)

/* ---------------- 格言 ---------------- */
const quoteIdx = ref(Math.max(0, QUOTES.indexOf(quoteOfDay())))
const quote = computed(() => QUOTES[quoteIdx.value])
function nextQuote() {
  quoteIdx.value = (quoteIdx.value + 1) % QUOTES.length
}

/* ---------------- 本地自评（非 AI，纯前端估算） ---------------- */
const filledCount = computed(() => FIELDS.filter(f => (form.value[f.key] || '').trim()).length)
const totalChars = computed(() =>
  FIELDS.reduce((sum, f) => sum + (form.value[f.key] || '').trim().length, 0)
)

const scores = computed(() => {
  const nextLen = (form.value.nextFocus || '').trim().length
  return [
    { label: '完整度', value: Math.round((filledCount.value / FIELDS.length) * 100) },
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
      taskType: o?.taskType || ''
    }
  } catch {
    return { ...METRIC_KEYS }
  }
}

async function load() {
  const [cur, his] = await Promise.all([api.currentReview(active.value), api.reviews(active.value, 8)])
  current.value = cur.data.data
  history.value = his.data.data
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

watch(active, load)
onMounted(load)
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

        <!-- 引导式问题 -->
        <div v-for="f in FIELDS" :key="f.key" class="review-field">
          <label class="field">
            <span class="field-head">
              {{ f.label }}
              <small>{{ f.q }}</small>
            </span>
            <textarea v-model="form[f.key]" placeholder="写下你的复盘…"></textarea>
          </label>
        </div>

        <div class="form-row" style="justify-content: flex-end">
          <button class="primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存复盘' }}</button>
        </div>
      </div>

      <div class="stack">
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
          <p class="score-note">写满 6 个问题、并给出明确「下一步重点」，分数自然会上去。</p>
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
          <div class="card-title"><span>历史复盘</span></div>
          <div v-if="!history.length" class="empty">还没有历史记录。</div>
          <div v-for="h in history" :key="h.id" class="sink-item">
            <span class="dot"></span>
            <span class="txt">{{ h.reviewDate }}</span>
            <span class="meta">{{ (h.biggestGain || h.goodThings || '—').slice(0, 20) }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
