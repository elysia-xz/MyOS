<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api } from '../api'
import { useToast } from '../composables/useToast'

const { push: toast } = useToast()

const tasks = ref<any[]>([])
const habits = ref<any[]>([])
const title = ref('')
const priority = ref('MEDIUM')
const saving = ref(false)

const today = new Date().toISOString().slice(0, 10)
const doneCount = computed(() => habits.value.filter((h) => h.done || h.percent >= 100).length)

async function load() {
  const [t, h] = await Promise.all([api.tasks(), api.habits()])
  tasks.value = t.data.data
  habits.value = h.data.data.map((r: any) => ({
    ...r,
    done: !!(r.done || (r.targetValue > 0 && r.metricValue >= r.targetValue))
  }))
}

async function add() {
  if (!title.value.trim()) return
  await api.createTask({ title: title.value, priority: priority.value, dueDate: today })
  title.value = ''
  await load()
  toast('已添加今日任务', 'ok')
}

async function toggle(task: any) {
  const next = task.status === 'DONE' ? 'TODO' : 'DONE'
  await api.updateTaskStatus(task.id, next)
  await load()
  if (next === 'DONE') {
    const total = tasks.value.length
    const done = tasks.value.filter((t) => t.status === 'DONE').length
    toast(`任务已完成 · 今日进度 ${total ? Math.round((done / total) * 100) : 0}%`, 'ok')
  } else {
    toast('已恢复为待办')
  }
}

function recalc(h: any) {
  h.done = h.targetValue > 0 && Number(h.metricValue) >= Number(h.targetValue)
}

async function saveHabits() {
  saving.value = true
  try {
    await api.saveHabits(
      today,
      habits.value.map((h) => ({
        systemCode: h.systemCode,
        metricValue: Number(h.metricValue) || 0,
        targetValue: Number(h.targetValue) || 0,
        done: !!h.done,
        note: h.note || null
      }))
    )
    await load()
    toast(`已保存今日打点 · ${doneCount.value} / ${habits.value.length} 个系统达标`, 'ok')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">TODAY</div>
        <h1>今日</h1>
        <p>今天这 7 个系统分别有没有向前走一点？</p>
      </div>
      <div style="font-size: 13px; color: var(--muted)">已达标 {{ doneCount }} / {{ habits.length }}</div>
    </div>

    <div class="grid-2">
      <!-- 习惯打点 -->
      <div class="card">
        <div class="card-title">
          <span>习惯打点 · 7 大系统</span>
          <span class="hint">{{ today }}</span>
        </div>
        <div class="water-list">
          <div v-for="h in habits" :key="h.systemCode" class="water-row" style="grid-template-columns: 88px 1fr 76px 62px">
            <span class="water-name">{{ h.systemName }}</span>
            <div class="form-row" style="gap: 6px">
              <input
                v-model.number="h.metricValue"
                type="number"
                min="0"
                style="padding: 7px 9px; font-size: 13px"
                @input="recalc(h)"
              />
              <span style="font-size: 11.5px; color: var(--faint); white-space: nowrap">/ {{ h.targetValue }} {{ h.unit }}</span>
            </div>
            <div class="bar-track"><div class="bar-fill" :class="h.done ? 'ok' : 'partial'" :style="{ width: Math.min(100, h.targetValue ? (h.metricValue / h.targetValue) * 100 : 0) + '%' }"></div></div>
            <button
              class="ghost tiny"
              :class="{ primary: h.done }"
              style="padding: 6px 8px"
              @click="h.done = !h.done"
            >
              {{ h.done ? '✓ 达标' : '未达标' }}
            </button>
          </div>
        </div>
        <div class="form-row" style="margin-top: 16px; justify-content: flex-end">
          <button class="primary" :disabled="saving" @click="saveHabits">{{ saving ? '保存中…' : '保存今日打点' }}</button>
        </div>
      </div>

      <!-- 今日任务 -->
      <div class="card">
        <div class="card-title"><span>今日任务</span></div>
        <div class="form-row" style="margin-bottom: 8px">
          <input v-model="title" placeholder="添加一个今天要完成的任务…" @keyup.enter="add" />
          <select v-model="priority" style="flex: 0 0 84px">
            <option value="HIGH">高</option>
            <option value="MEDIUM">中</option>
            <option value="LOW">低</option>
          </select>
          <button class="primary small" style="flex: 0 0 auto" @click="add">添加</button>
        </div>
        <div v-if="!tasks.length" class="empty">今天还没有任务。</div>
        <div v-for="task in tasks" :key="task.id" class="list-row">
          <button class="circle" :class="{ done: task.status === 'DONE' }" @click="toggle(task)">
            {{ task.status === 'DONE' ? '✓' : '' }}
          </button>
          <div class="row-main">
            <strong :class="{ strike: task.status === 'DONE' }">{{ task.title }}</strong>
            <small>{{ task.priority }} · {{ task.dueDate || '无截止日期' }}</small>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
