<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api } from '../api'
import { useToast } from '../composables/useToast'
import { PROJECT_STAGES, withStage } from '../utils/projectStage'

const { push: toast } = useToast()

const projects = ref<any[]>([])
const portfolio = ref<any[]>([])
const goals = ref<any[]>([])
const view = ref<'list' | 'portfolio'>('list')
const detail = ref<any>(null)
const showForm = ref(false)
const logText = ref('')
const taskText = ref('')

const blank = () => ({
  id: null as number | null,
  name: '',
  description: '',
  status: 'PLANNED',
  progress: 0,
  // AUTO：进度由任务完成度推导；MANUAL：用下面这个手填值
  progressMode: 'AUTO',
  goalId: null as number | null,
  techStack: '',
  githubUrl: '',
  demoUrl: '',
  problemSolved: '',
  difficulty: '',
  solution: '',
  coverImage: '',
  startDate: new Date().toISOString().slice(0, 10)
})
const form = ref(blank())

const doneCount = computed(() => projects.value.filter((p) => p.status === 'DONE').length)
/** 列表带上状态机字段：未开始 / 进行中 / 待复盘 / 已完成 */
const projectCards = computed(() => projects.value.map(withStage))

/** 只有顶层目标（年目标）值得挂项目，子目标挂上去会让链路变成一团 */
const yearGoals = computed(() =>
  goals.value.filter((g) => !g.parentId && g.status === 'ACTIVE')
)

/** 是否自动进度：详情里用来决定显示「自动」还是「手动」 */
const detailAuto = computed(() => (detail.value?.project?.progressMode || 'AUTO') === 'AUTO')

async function load() {
  const [p, pf, g] = await Promise.all([api.projects(), api.portfolio(), api.goals()])
  projects.value = p.data.data
  portfolio.value = pf.data.data
  goals.value = g.data.data
}

function openCreate() {
  form.value = blank()
  showForm.value = true
}

function openEdit(p: any) {
  form.value = { ...blank(), ...p }
  showForm.value = true
}

async function submit() {
  if (!form.value.name.trim()) return
  const payload = {
    ...form.value,
    finishedAt: form.value.status === 'DONE' ? new Date().toISOString().slice(0, 10) : null
  }
  if (form.value.id) await api.updateProject(form.value.id, payload)
  else await api.createProject(payload)
  showForm.value = false
  await load()
}

async function openDetail(p: any) {
  const res = await api.projectDetail(p.id)
  detail.value = res.data.data
}

/** 详情里做完任何写入后按 id 重取 —— 进度条要跟着立刻动，那才是这套机制的说服力 */
async function refreshDetail() {
  if (!detail.value) return
  const res = await api.projectDetail(detail.value.project.id)
  detail.value = res.data.data
}

async function addLog() {
  if (!logText.value.trim() || !detail.value) return
  await api.addProjectLog(detail.value.project.id, { content: logText.value })
  logText.value = ''
  await refreshDetail()
}

/** 在项目里直接加任务 —— 让「推进项目」和「干活」是同一个动作 */
async function addTask() {
  if (!taskText.value.trim() || !detail.value) return
  await api.createTask({
    title: taskText.value.trim(),
    dueDate: new Date().toISOString().slice(0, 10),
    projectId: detail.value.project.id
  })
  taskText.value = ''
  await refreshDetail()
  await load()
  toast('已加入项目，进度已更新', 'ok')
}

async function toggleTask(t: any) {
  await api.updateTaskStatus(t.id, t.status === 'DONE' ? 'TODO' : 'DONE')
  await refreshDetail()
  await load()
}

/** 交还给「按任务自动算」 */
async function toAuto(p: any) {
  await api.updateProject(p.id, {
    name: p.name,
    description: p.description,
    status: p.status,
    progress: p.progress,
    progressMode: 'AUTO',
    goalId: p.goalId,
    startDate: p.startDate,
    targetDate: p.targetDate,
    githubUrl: p.githubUrl,
    demoUrl: p.demoUrl,
    problemSolved: p.problemSolved,
    techStack: p.techStack,
    coverImage: p.coverImage,
    difficulty: p.difficulty,
    solution: p.solution,
    finishedAt: p.finishedAt
  })
  await load()
  toast('已改回按任务自动计算', 'ok')
}

async function del(id: number) {
  await api.deleteProject(id)
  await load()
}

function goalName(id: number | null) {
  if (!id) return ''
  return goals.value.find((g) => g.id === id)?.title || ''
}

/** 进度来源的一句话说明 —— 让「这个数字是谁算的」始终可见 */
function progressHint(p: any) {
  if (p.progressMode === 'MANUAL') return '手动填写'
  if (!p.taskTotal) return '自动 · 还没有任务'
  return `自动 · ${p.taskDone}/${p.taskTotal} 个任务`
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">PROJECTS</div>
        <h1>项目</h1>
        <p>每个月完成一个小项目，一年后就是 12 个真实作品。</p>
      </div>
      <div class="form-row" style="flex: 0 0 auto">
        <button class="ghost tiny" :class="{ primary: view === 'list' }" @click="view = 'list'">项目列表</button>
        <button class="ghost tiny" :class="{ primary: view === 'portfolio' }" @click="view = 'portfolio'">作品集</button>
        <button class="primary tiny" @click="openCreate">+ 新建项目</button>
      </div>
    </div>

    <template v-if="view === 'list'">
      <div class="grid-3">
        <div v-for="p in projectCards" :key="p.id" class="card lift">
          <div class="proj-head">
            <span class="tag" :class="p.stageCls">{{ p.stageLabel }}</span>
            <strong style="font-size: 13px">{{ p.progress }}%</strong>
          </div>
          <h3 class="proj-name" style="margin-top: 12px">{{ p.name }}</h3>
          <p class="proj-desc">{{ p.description || '暂无描述' }}</p>
          <div class="progress"><span :style="{ width: p.progress + '%' }"></span></div>
          <div class="proj-meta">
            <span>{{ progressHint(p) }}</span>
            <span v-if="goalName(p.goalId)" class="proj-goal">↳ {{ goalName(p.goalId) }}</span>
            <span v-else>{{ p.techStack || '未填写技术栈' }}</span>
          </div>

          <!-- 项目状态机：未开始 → 进行中 → 待复盘 → 已完成 -->
          <div class="stage-track" style="margin-top: 12px" aria-label="项目状态流转">
            <template v-for="(s, i) in PROJECT_STAGES" :key="s.key">
              <span v-if="i > 0" class="stage-line" :class="{ done: p.idx >= i }"></span>
              <span class="stage-node" :class="{ done: p.idx > i, current: p.idx === i }">
                <span class="n" aria-hidden="true">{{ p.idx > i ? '✓' : i + 1 }}</span>
                {{ s.label }}
              </span>
            </template>
          </div>

          <div class="form-row" style="margin-top: 14px; gap: 6px">
            <button class="ghost tiny" @click="openDetail(p)">详情</button>
            <button class="ghost tiny" @click="openEdit(p)">编辑</button>
            <!-- 「+10%」已删除：手拖进度条是这条链路上最假的一环，现在改由任务推动 -->
            <button v-if="p.progressMode === 'MANUAL'" class="ghost tiny" @click="toAuto(p)">改回自动</button>
            <button class="ghost tiny" @click="del(p.id)">删除</button>
          </div>
        </div>
      </div>
      <div v-if="!projects.length" class="card empty">还没有项目。从本月的一个小项目开始。</div>
    </template>

    <template v-else>
      <div v-if="!portfolio.length" class="card empty">作品集还是空的。完成第一个项目后，它会自动出现在这里。</div>
      <div v-for="y in portfolio" :key="y.year" class="card" style="margin-bottom: 16px">
        <div class="card-title"><span>{{ y.year }} 年</span><span class="hint">共 {{ y.items.reduce((s: number, i: any) => s + i.projects.length, 0) }} 个项目</span></div>
        <div v-for="m in y.items" :key="m.month" style="margin-bottom: 16px">
          <div style="font-size: 12.5px; color: var(--muted); margin-bottom: 8px">{{ m.month }} 月</div>
          <div class="grid-3">
            <div v-for="p in m.projects" :key="p.id" class="fin-cell">
              <h3 style="margin: 0 0 6px; font-size: 14.5px">{{ p.name }}</h3>
              <p style="font-size: 12.5px; color: var(--muted); margin: 0 0 8px">{{ p.description }}</p>
              <div style="font-size: 11.5px; color: var(--faint)">{{ p.techStack }}</div>
              <div class="form-row" style="margin-top: 10px; gap: 6px">
                <a v-if="p.githubUrl" :href="p.githubUrl" target="_blank" class="ghost tiny" style="text-decoration: none; display: inline-block">GitHub</a>
                <button class="ghost tiny" @click="openDetail(p)">详情</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 表单模态 -->
    <div v-if="showForm" class="modal-mask" @click.self="showForm = false">
      <div class="modal">
        <h2>{{ form.id ? '编辑项目' : '新建项目' }}</h2>
        <p class="sub">Portfolio 字段尽量填全，作品集就是靠这些撑起来的。</p>
        <div class="form-card">
          <div class="form-row">
            <label class="field" style="flex: 2"><span>项目名称</span><input v-model="form.name" /></label>
            <label class="field" style="flex: 1"><span>状态</span>
              <select v-model="form.status">
                <option value="PLANNED">计划</option>
                <option value="IN_PROGRESS">进行中</option>
                <option value="DONE">已完成</option>
              </select>
            </label>
          </div>
          <div class="form-row">
            <label class="field" style="flex: 1"><span>归属目标</span>
              <select v-model="form.goalId">
                <option :value="null">不归属目标</option>
                <option v-for="g in yearGoals" :key="g.id" :value="g.id">{{ g.title }}</option>
              </select>
            </label>
            <label class="field" style="flex: 0 0 132px"><span>进度来源</span>
              <select v-model="form.progressMode">
                <option value="AUTO">按任务自动</option>
                <option value="MANUAL">手动填写</option>
              </select>
            </label>
            <label class="field" style="flex: 0 0 96px"><span>进度 %</span>
              <input
                v-model.number="form.progress"
                type="number"
                min="0"
                max="100"
                :disabled="form.progressMode === 'AUTO'"
              />
            </label>
          </div>
          <p class="form-hint">
            按任务自动时，进度 = 该项目下已完成任务 / 总任务。想让进度条一直是「真的」，就保持自动。
          </p>
          <label class="field"><span>项目简介</span><input v-model="form.description" /></label>
          <label class="field"><span>解决的问题</span><textarea v-model="form.problemSolved"></textarea></label>
          <div class="form-row">
            <label class="field" style="flex: 1"><span>技术栈</span><input v-model="form.techStack" placeholder="Vue3 / Spring Boot / PostgreSQL" /></label>
            <label class="field" style="flex: 1"><span>难点</span><input v-model="form.difficulty" /></label>
          </div>
          <label class="field"><span>解决方案</span><textarea v-model="form.solution"></textarea></label>
          <div class="form-row">
            <label class="field" style="flex: 1"><span>GitHub</span><input v-model="form.githubUrl" /></label>
            <label class="field" style="flex: 1"><span>Demo</span><input v-model="form.demoUrl" /></label>
          </div>
          <label class="field"><span>截图地址</span><input v-model="form.coverImage" /></label>
        </div>
        <div class="modal-actions">
          <button class="ghost" @click="showForm = false">取消</button>
          <button class="primary" @click="submit">保存</button>
        </div>
      </div>
    </div>

    <!-- 详情模态 -->
    <div v-if="detail" class="modal-mask" @click.self="detail = null">
      <div class="modal">
        <h2>{{ detail.project.name }}</h2>
        <p class="sub">{{ detail.project.description }}</p>
        <div class="detail-progress">
          <div class="progress"><span :style="{ width: detail.project.progress + '%' }"></span></div>
          <small>{{ detail.project.progress }}% · {{ progressHint(detail.project) }}</small>
        </div>
        <div class="grid-2" style="gap: 14px">
          <div><div class="hint" style="font-size: 11.5px; color: var(--faint)">解决的问题</div><p style="font-size: 13px; margin: 4px 0 0">{{ detail.project.problemSolved || '—' }}</p></div>
          <div><div class="hint" style="font-size: 11.5px; color: var(--faint)">技术栈</div><p style="font-size: 13px; margin: 4px 0 0">{{ detail.project.techStack || '—' }}</p></div>
          <div><div class="hint" style="font-size: 11.5px; color: var(--faint)">难点</div><p style="font-size: 13px; margin: 4px 0 0">{{ detail.project.difficulty || '—' }}</p></div>
          <div><div class="hint" style="font-size: 11.5px; color: var(--faint)">解决方案</div><p style="font-size: 13px; margin: 4px 0 0">{{ detail.project.solution || '—' }}</p></div>
        </div>

        <div class="card-title" style="margin-top: 24px"><span>这个项目产出的内容</span><span class="hint">{{ detail.contents?.length || 0 }} 篇</span></div>
        <div v-if="!detail.contents?.length" class="empty">还没有挂在这个项目下的输出。去「输出」写一篇并选择归属本项目。</div>
        <div v-for="c in detail.contents" :key="c.id" class="sink-item content-sink">
          <span class="dot"></span>
          <span class="txt">{{ c.title }}</span>
          <span class="tag" :class="c.status === 'PUBLISHED' ? 'ok' : ''">{{ c.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
        </div>

        <!-- 推动这个项目的任务：进度条的分子分母，就地可勾 -->
        <div class="card-title" style="margin-top: 24px">
          <span>推动这个项目的任务</span>
          <span class="hint">{{ detail.tasks?.filter((t: any) => t.status === 'DONE').length || 0 }} / {{ detail.tasks?.length || 0 }} 完成</span>
        </div>
        <div class="form-row" style="margin-bottom: 10px">
          <input v-model="taskText" placeholder="为这个项目加一个任务…" @keyup.enter="addTask" />
          <button class="primary small" style="flex: 0 0 auto" @click="addTask">添加</button>
        </div>
        <div v-if="!detail.tasks?.length" class="empty">还没有任务。加上任务后，这个项目的进度会自己往前走。</div>
        <div v-for="t in detail.tasks" :key="t.id" class="sink-item task-sink">
          <button class="circle" :class="{ done: t.status === 'DONE' }" @click="toggleTask(t)">
            {{ t.status === 'DONE' ? '✓' : '' }}
          </button>
          <span class="txt" :class="{ strike: t.status === 'DONE' }">{{ t.title }}</span>
          <span class="meta">{{ t.dueDate || '' }}</span>
        </div>

        <div class="card-title" style="margin-top: 24px"><span>开发日志</span></div>
        <div class="form-row" style="margin-bottom: 10px">
          <input v-model="logText" placeholder="今天这个项目推进了什么？" @keyup.enter="addLog" />
          <button class="primary small" style="flex: 0 0 auto" @click="addLog">记录</button>
        </div>
        <div v-if="!detail.logs?.length" class="empty">还没有开发日志。</div>
        <div v-for="l in detail.logs" :key="l.id" class="sink-item">
          <span class="dot"></span>
          <span class="txt">{{ l.content }}</span>
          <span class="meta">{{ l.logDate }}</span>
        </div>

        <div class="modal-actions">
          <button class="ghost" @click="detail = null">关闭</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
/*
 * 进度来源说明：一行小字，把「这个数字是谁算的」始终讲清楚。
 * 自动进度最怕的就是用户不知道它是自动的，然后以为进度条坏了。
 */
.proj-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--faint);
  min-width: 0;
}

.proj-meta > span:first-child {
  white-space: nowrap;
}

.proj-goal {
  color: var(--primary-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-hint {
  margin: -2px 0 14px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--faint);
}

.detail-progress {
  margin: 14px 0 4px;
}

.detail-progress small {
  display: block;
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--faint);
}

.task-sink {
  gap: 10px;
}

.task-sink .txt {
  flex: 1;
  min-width: 0;
}
</style>
