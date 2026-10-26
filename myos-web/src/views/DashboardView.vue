<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { runtime } from '../api/http'
import { useToast } from '../composables/useToast'
import { useAiChat } from '../composables/useAiChat'
import { PROJECT_STAGES as STAGES, withStage } from '../utils/projectStage'
import SystemWaterBars from '../components/SystemWaterBars.vue'
import LevelCards from '../components/LevelCards.vue'
import LevelRadar from '../components/LevelRadar.vue'
import MonthlyTargets from '../components/MonthlyTargets.vue'
import AiActionPanel from '../components/AiActionPanel.vue'
import ProgressRing from '../components/ProgressRing.vue'
import SystemDots from '../components/SystemDots.vue'
import FlowGuide from '../components/FlowGuide.vue'

const router = useRouter()
const { push: toast } = useToast()

const data = ref<any>({
  waterBars: [],
  levels: [],
  currentProjects: [],
  monthlyTargets: [],
  recentKnowledge: [],
  todayTasks: { total: 0, done: 0 }
})
const loading = ref(true)
const demoMode = ref(false)
const showAiHelp = ref(false)

// AI 生成结果由本组件持有，不能放在 AiActionPanel 内部：
// AI 生成完会触发刷新，而刷新会让首页切到 loading 态、卸载整个内容区，
// 子组件里的结果会随之被销毁，界面看起来就是「生成成功但什么都不显示」。
const aiResult = ref('')

/* ---------------- AI 对话（问答 + 带确认的办事）----------------
   同理，对话状态也必须放在这里而不是面板里：用户点「刷新」会让内容区
   卸载重建，存在面板内的对话会整段消失。这里由首页持有，
   写入成功后走 load(true) 静默刷新 —— 不能走非静默刷新，
   那会让内容区闪一下，对话也跟着消失。 */
const chat = useAiChat(() => load(true))

/* ---------------- AI 网关配置：配置界面在前端，Key 存在后端 ----------------
   为什么 Key 不下发到前端：它不是「设置项」而是「凭据」——前端要记住它只能塞进
   浏览器本地存储，本机任何人都能读出来，网络面板里也会明文出现，泄露即不可逆
   （直接挂账单）。所以这里只负责「填」，存和读都归后端管，
   GET 回来的永远只有 sk-123****cdef 这种脱敏形态。

   三个输入框一律「留空 = 不改动」，当前生效值只出现在 placeholder 与状态区。
   ⚠️ 早期版本把生效值预填进输入框，结果粘贴新值时没覆盖 → 拼成两段 URL
   （https://api.deepseek.comhttps://…），调用必然失败且极难一眼看出来。
   预填本身就是个陷阱，所以改成与 Key 一致的空框语义。 */
const aiForm = ref({ baseUrl: '', model: '', apiKey: '' })
const aiCurrent = ref({
  baseUrl: '', model: '', masked: '', source: 'NONE', stored: false, available: false
})
const aiAvailable = computed(() => aiCurrent.value.available)
const aiSaving = ref(false)
const aiTesting = ref(false)
const aiTestMsg = ref('')
const aiTestOk = ref(false)

/** 后端返回的配置状态统一从这里落到界面，避免各处零散赋值走样 */
function applyAiStatus(d: any) {
  aiCurrent.value = {
    baseUrl: d?.baseUrl || '',
    model: d?.model || '',
    masked: d?.masked || '',
    source: d?.source || 'NONE',
    stored: !!d?.stored,
    available: !!d?.available
  }
}

async function openAiConfig() {
  showAiHelp.value = true
  aiTestMsg.value = ''
  // 每次从空表单开始 —— 想改哪项就填哪项，不填的保持原样
  aiForm.value = { baseUrl: '', model: '', apiKey: '' }
  try {
    const res: any = await api.aiConfig()
    applyAiStatus(res.data.data)
  } catch {
    // 后端版本较旧没有该接口时保持空表单，用户仍可填写并提交
  }
}

async function saveAiConfig() {
  if (aiSaving.value) return
  const baseUrl = aiForm.value.baseUrl.trim()
  const model = aiForm.value.model.trim()
  const key = aiForm.value.apiKey.trim()
  if (!baseUrl && !model && !key) {
    toast('三项都没填，没有需要保存的内容')
    return
  }
  if (!key && !aiCurrent.value.masked) {
    toast('先填入 API Key')
    return
  }
  aiSaving.value = true
  aiTestMsg.value = ''
  try {
    const res: any = await api.saveAiConfig({ baseUrl, model, apiKey: key })
    applyAiStatus(res.data.data)
    aiForm.value = { baseUrl: '', model: '', apiKey: '' }
    toast('已保存，Key 只存在后端', 'ok')
    runAiTest()
  } catch (e: any) {
    toast(e?.response?.data?.message || '保存失败')
  } finally {
    aiSaving.value = false
  }
}

/** 真发一次最小请求。光看「已配置」不够——Key 填错、模型名写错都要当场暴露 */
async function runAiTest() {
  if (aiTesting.value) return
  if (runtime.demoMode) {
    aiTestOk.value = false
    aiTestMsg.value = '当前是演示模式，后端未连接，无法真正测试'
    return
  }
  aiTesting.value = true
  aiTestMsg.value = ''
  try {
    const res: any = await api.testAiConfig()
    const body = res.data || {}
    aiTestOk.value = !!body.success
    aiTestMsg.value = body.success
      ? `连接正常，AI 助手已可用${body.data?.reply ? `（模型回复：${body.data.reply}）` : ''}`
      : body.message || '连接失败'
  } catch (e: any) {
    aiTestOk.value = false
    aiTestMsg.value = e?.response?.data?.message || '连接失败'
  } finally {
    aiTesting.value = false
  }
}

/** 整体清空，退回环境变量 / 内置默认值。
    只清 Key 是不够的：库的优先级高于环境变量，base-url 留在库里会静默盖掉
    部署时注入的 MYOS_AI_BASE_URL，而表单里又没法把字段改回空。 */
async function resetAiConfig() {
  if (aiSaving.value) return
  aiSaving.value = true
  aiTestMsg.value = ''
  try {
    const res: any = await api.saveAiConfig({ reset: true })
    applyAiStatus(res.data.data)
    aiForm.value = { baseUrl: '', model: '', apiKey: '' }
    toast('已清空配置，回到默认值')
  } catch (e: any) {
    toast(e?.response?.data?.message || '清空失败')
  } finally {
    aiSaving.value = false
  }
}

const dateText = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}  ${week}`
})

const todayTasks = computed(() => data.value.todayTasks || { total: 0, done: 0 })

const taskPercent = computed(() =>
  todayTasks.value.total ? Math.round((todayTasks.value.done / todayTasks.value.total) * 100) : 0
)

const todayDoneCount = computed(
  () => data.value.waterBars.filter((r: any) => r.done || r.percent >= 100).length
)

const headline = computed(() => {
  if (todayDoneCount.value >= 7) return '今天，7 个系统全部向前走了。'
  if (todayDoneCount.value > 0) return '今天，向前走一点。'
  return '今天，从第一个系统开始。'
})

const subline = computed(() => {
  const parts = [`7 个系统已达标 ${todayDoneCount.value} 个`]
  if (todayTasks.value.total) parts.push(`任务完成 ${todayTasks.value.done}/${todayTasks.value.total}`)
  const sink = data.value.recentKnowledge?.length || 0
  if (sink) parts.push(`累计沉淀 ${sink} 条`)
  return parts.join(' · ')
})

/**
 * 今日主线（行为链路）：打点水位 → 完成任务 → 记录沉淀 → 推进项目 → 查看 AI 建议。
 * 五步全绿即为"今日闭环"，是首页最核心的一条链路。
 */
const flowSteps = computed(() => [
  {
    key: 'habit',
    label: '打点今日水位',
    hint: `${todayDoneCount.value} / 7 系统达标`,
    done: todayDoneCount.value > 0,
    to: '/today'
  },
  {
    key: 'task',
    label: '完成今日任务',
    hint: `${todayTasks.value.done} / ${todayTasks.value.total} 项`,
    done: !!todayTasks.value.total && todayTasks.value.done >= todayTasks.value.total,
    to: '/today'
  },
  {
    key: 'sink',
    label: '记录一条沉淀',
    hint: `累计 ${data.value.recentKnowledge?.length || 0} 条`,
    done: (data.value.recentKnowledge?.length || 0) > 0,
    to: '/learning'
  },
  {
    key: 'proj',
    label: '推进项目进度',
    hint: `${(data.value.currentProjects || []).length} 个进行中`,
    done: (data.value.currentProjects || []).some((p: any) => (p.progress || 0) > 0),
    to: '/projects'
  },
  {
    key: 'ai',
    label: '查看 AI 建议',
    hint: aiAvailable.value ? '网关已就绪' : '加分项 · 待配置',
    done: aiAvailable.value,
    to: '#ai',
    optional: true
  }
])

/**
 * 闭环只算前 4 步（打点 / 任务 / 沉淀 / 项目）。
 * 「查看 AI 建议」是加分项 —— 否则 AI 没配就永远达不成闭环，庆祝反馈形同虚设。
 */
const CORE_FLOW = 4
const coreDone = computed(() => flowSteps.value.slice(0, CORE_FLOW).filter(s => s.done).length)
const flowClosed = computed(() => coreDone.value === CORE_FLOW)

/** 链路跳转：AI 那一步不是页面跳转，而是打开面板或引导配置 */
function goStep(to: string) {
  if (to === '#ai') {
    if (!aiAvailable.value) {
      openAiConfig()
    } else {
      toast('AI 网关已就绪，可在下方「AI 行动」面板生成内容', 'ok')
    }
    return
  }
  router.push(to)
}

/* ---------------- 项目状态机：未开始 → 进行中 → 待复盘 → 已完成 ----------------
   派生逻辑统一放在 utils/projectStage，首页与项目页共用同一口径。 */
const projectCards = computed(() => (data.value.currentProjects || []).slice(0, 2).map(withStage))

function fmtDate(s: any) {
  if (!s) return ''
  return String(s).slice(5, 10)
}

/**
 * silent = true 时不进入 loading 态。
 * AI 生成完的刷新必须走静默：否则内容区会被卸载重建，
 * 用户看到页面闪一下，AI 正文也跟着消失。
 */
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [dash, ai] = await Promise.all([api.dashboard(), api.aiStatus()])
    data.value = dash.data.data
    // /ai/status 只回一个 available，其余字段等打开配置弹窗时再拉
    aiCurrent.value = { ...aiCurrent.value, available: !!ai.data.data?.available }
  } catch (e) {
    // 保持空状态，页面已做兜底
  } finally {
    demoMode.value = runtime.demoMode
    loading.value = false
  }
}

/**
 * 导出全部数据为 JSON 文件。
 *
 * 后端带 Content-Disposition 返回文件，这里用 blob + 临时 a 标签触发下载，
 * 文件名的中文/英文都由后端给，前端不重复拼一遍。
 *
 * 演示模式下没有真实数据可导 —— 明确告知，而不是导出一份假的。
 */
const exporting = ref(false)
async function exportData() {
  if (runtime.demoMode) {
    toast('演示模式没有真实数据可导出，请连接后端后再试')
    return
  }
  exporting.value = true
  try {
    const res: any = await api.exportData()
    const blob = new Blob([res.data], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `myos-export-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(a)
    a.click()
    a.remove()
    // 不 revoke 的话 blob 一直占内存，导出几次之后会明显变卡
    URL.revokeObjectURL(url)
    toast('已导出全部数据', 'ok')
  } catch {
    toast('导出失败：请确认后端已启动')
  } finally {
    exporting.value = false
  }
}

/** AI 生成完成后刷新数据并给出反馈 */function onAiRefresh() {
  toast('AI 已生成内容', 'ok')
  load(true)
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">{{ dateText }}</div>
        <h1>{{ headline }}</h1>
        <p>{{ subline }}</p>
      </div>
      <!-- 必须写成 load()：直接写 load 会把点击事件当成 silent 参数传进去，
           手动点刷新就再也看不到加载态了 -->
      <div class="head-actions">
        <button class="ghost" :disabled="exporting" @click="exportData">
          {{ exporting ? '导出中…' : '导出数据' }}
        </button>
        <button class="ghost" @click="load()">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="card loading">正在加载成长数据…</div>

    <template v-else>
      <!-- ==================== ① 今日核心 ==================== -->
      <div class="block-head">
        <h2>今日</h2>
        <span>今天要做什么</span>
      </div>

      <div class="card elevated">
        <div class="overview">
          <ProgressRing
            :percent="taskPercent"
            label="今日任务完成率"
            :caption="`${todayTasks.done} / ${todayTasks.total} 项`"
          />
          <div class="overview-main">
            <p class="overview-sub">7 大系统今日水位 · 点亮即达标</p>
            <SystemDots :rows="data.waterBars" @pick="router.push('/today')" />
          </div>
        </div>
      </div>

      <div class="card section-gap">
        <div class="card-title">
          <span>今日流程</span>
          <span class="hint">闭环进度 {{ coreDone }} / {{ CORE_FLOW }}</span>
        </div>
        <FlowGuide :steps="flowSteps" @go="goStep" />

        <!-- 核心四步全绿：给出今日闭环的正反馈 -->
        <div v-if="flowClosed" class="celebrate">
          <span class="badge" aria-hidden="true">✓</span>
          <div>
            <strong>今日闭环已完成</strong>
            <span>水位、任务、沉淀、项目都走过了，保持这个节奏。</span>
          </div>
        </div>
      </div>

      <div class="grid-2 section-gap">
        <div class="card">
          <div class="card-title">
            <span>今日水位明细</span>
            <button class="ghost tiny" @click="router.push('/today')">去打点</button>
          </div>
          <SystemWaterBars :rows="data.waterBars" />
        </div>

        <AiActionPanel
          :available="aiAvailable"
          :result="aiResult"
          :conv-id="chat.convId.value"
          :msgs="chat.msgs.value"
          :pending="chat.pending.value"
          :chat-busy="chat.busy.value"
          :chat-err="chat.err.value"
          :conversations="chat.conversations.value"
          :history-open="chat.historyOpen.value"
          :loading-history="chat.loadingHistory.value"
          @result="aiResult = $event"
          @refresh="onAiRefresh"
          @configure="openAiConfig"
          @send="chat.send($event)"
          @confirm="chat.confirm($event)"
          @cancel="chat.cancel($event)"
          @new-chat="chat.startNew()"
          @toggle-history="chat.openHistory()"
          @open-conversation="chat.openConversation($event)"
        />
      </div>

      <!-- ==================== ② 长期积累 ==================== -->
      <div class="block-head">
        <h2>长期积累</h2>
        <span>正在攒什么</span>
      </div>

      <div class="grid-2">
        <div class="card">
          <div class="card-title">
            <span>能力雷达</span>
            <span class="hint">7 大系统分布</span>
          </div>
          <LevelRadar :items="data.levels" />
        </div>

        <div class="card">
          <div class="card-title">
            <span>能力等级明细</span>
            <button
              class="ghost tiny"
              @click="toast('等级由学习时长、完成项目、打卡次数自动累计，无需手动维护')"
            >
              计算规则
            </button>
          </div>
          <LevelCards :levels="data.levels" />
        </div>
      </div>

      <div class="grid-2 section-gap">
        <div class="card lift">
          <div class="card-title">
            <span>本月项目</span>
            <button class="ghost tiny" @click="router.push('/projects')">创建 / 管理</button>
          </div>
          <div v-if="!projectCards.length" class="empty">
            还没有进行中的项目。每月完成一个小项目。
          </div>
          <div v-for="p in projectCards" :key="p.id" class="proj-block">
            <div class="proj-head">
              <p class="proj-name">「{{ p.name }}」</p>
              <span class="tag" :class="p.stageCls">{{ p.stageLabel }}</span>
            </div>
            <p class="proj-desc">{{ p.description || '暂无描述' }}</p>
            <div class="progress"><span :style="{ width: p.progress + '%' }"></span></div>
            <div class="pct"><span>{{ p.progress }}%</span><span>{{ p.techStack || '' }}</span></div>

            <!-- 项目状态机：未开始 → 进行中 → 待复盘 → 已完成 -->
            <div class="stage-track" aria-label="项目状态流转">
              <template v-for="(s, i) in STAGES" :key="s.key">
                <span v-if="i > 0" class="stage-line" :class="{ done: p.idx >= i }"></span>
                <span class="stage-node" :class="{ done: p.idx > i, current: p.idx === i }">
                  <span class="n" aria-hidden="true">{{ p.idx > i ? '✓' : i + 1 }}</span>
                  {{ s.label }}
                </span>
              </template>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-title">
            <span>本月完成度</span>
            <span class="hint">按达标天数</span>
          </div>
          <MonthlyTargets :rows="data.monthlyTargets" />
        </div>
      </div>

      <!-- ==================== ③ 沉淀与系统状态 ==================== -->
      <div class="block-head">
        <h2>沉淀与系统</h2>
        <span>留下了什么 · 系统是否正常</span>
      </div>

      <div class="grid-2">
        <div class="card">
          <div class="card-title">
            <span>最近沉淀</span>
            <button class="ghost tiny" @click="router.push('/learning')">记录学习</button>
          </div>
          <div v-if="!data.recentKnowledge.length" class="empty">还没有沉淀。今天可以记第一条。</div>
          <div v-else class="timeline">
            <div v-for="(k, i) in data.recentKnowledge" :key="i" class="tl-item">
              <div class="tl-title">{{ k.title }}</div>
              <div class="tl-meta">
                {{ k.category || '未分类' }} · {{ fmtDate(k.learned_at || k.learnedAt) }}
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-title"><span>系统状态</span></div>
          <div class="status-list">
            <div class="status-row">
              <span class="status-dot" :class="aiAvailable ? 'ok' : 'warn'"></span>
              <span>AI 网关</span>
              <span class="status-note">{{ aiAvailable ? '已配置' : '未配置' }}</span>
            </div>
            <div class="status-row">
              <span class="status-dot" :class="demoMode ? 'warn' : 'ok'"></span>
              <span>数据来源</span>
              <span class="status-note">{{ demoMode ? '演示数据' : '后端已连接' }}</span>
            </div>
            <div class="status-row">
              <span class="status-dot" :class="todayDoneCount > 0 ? 'ok' : 'warn'"></span>
              <span>今日打点</span>
              <span class="status-note">{{ todayDoneCount }} / 7 个系统达标</span>
            </div>
            <div class="status-row">
              <span class="status-dot" :class="taskPercent >= 100 ? 'ok' : 'warn'"></span>
              <span>今日任务</span>
              <span class="status-note">{{ taskPercent }}%</span>
            </div>
          </div>

          <div v-if="todayDoneCount === 0 && taskPercent === 0" class="notice-bar section-gap">
            <div class="notice-body">
              <strong>今天还没有任何记录</strong>
              <span>从打点一个系统开始，哪怕只走一点点</span>
            </div>
            <button class="ghost tiny" @click="router.push('/today')">去打点</button>
          </div>
        </div>
      </div>
    </template>

    <!-- AI 网关配置：填写界面在前端，Key 存在后端，读回永远只有脱敏值 -->
    <div v-if="showAiHelp" class="modal-mask" @click.self="showAiHelp = false">
      <div class="modal modal-narrow">
        <h2>配置 AI 网关</h2>
        <p class="sub">AI 是贯穿层，不做独立聊天页。配置后首页的 4 个场景入口即可用。</p>

        <div class="form-card">
          <label class="field">
            <span>API Key<em class="ai-hint">只往上传，页面永不回显</em></span>
            <input
              v-model="aiForm.apiKey"
              type="password"
              autocomplete="off"
              :placeholder="aiCurrent.masked ? `已保存 ${aiCurrent.masked}，留空表示不改动` : 'sk-...'"
            />
          </label>

          <div class="ai-row">
            <label class="field">
              <span>Base URL<em class="ai-hint">留空 = 不改动</em></span>
              <input
                v-model="aiForm.baseUrl"
                type="text"
                :placeholder="aiCurrent.baseUrl || 'https://api.deepseek.com'"
              />
            </label>
            <label class="field">
              <span>模型<em class="ai-hint">留空 = 不改动</em></span>
              <input v-model="aiForm.model" type="text" :placeholder="aiCurrent.model || 'deepseek-chat'" />
            </label>
          </div>

          <div class="ai-state">
            <div class="ai-state-line">
              <span>当前状态</span>
              <b :class="aiAvailable ? 'ok' : 'warn'">{{ aiAvailable ? '已就绪' : '未配置' }}</b>
              <em v-if="aiCurrent.source === 'ENV'">来自环境变量</em>
              <em v-else-if="aiCurrent.source === 'DB'">来自此处保存</em>
            </div>
            <div class="ai-state-line">
              <span>生效地址</span>
              <code>{{ aiCurrent.baseUrl || '默认' }}</code>
            </div>
            <div class="ai-state-line">
              <span>生效模型</span>
              <code>{{ aiCurrent.model || '默认' }}</code>
            </div>
          </div>

          <p v-if="aiTestMsg" class="ai-test" :class="aiTestOk ? 'ok' : 'bad'">{{ aiTestMsg }}</p>
        </div>

        <div class="modal-actions">
          <button v-if="aiCurrent.stored" class="ghost" :disabled="aiSaving" @click="resetAiConfig">清空配置</button>
          <button class="ghost" :disabled="aiSaving || aiTesting" @click="runAiTest">
            {{ aiTesting ? '测试中…' : '测试连接' }}
          </button>
          <button class="primary" :disabled="aiSaving" @click="saveAiConfig">
            {{ aiSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
