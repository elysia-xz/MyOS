<script setup lang="ts">
import { ref } from 'vue'

/**
 * 目标链路：年目标 → 它下面的项目 → 项目进度。
 *
 * 这一条是整个「成长 OS」的脊柱，但此前 goal 表有完整后端、界面一次都没出现过。
 * 放在首页而不是新开一个页面 —— 六模块是导航上限，加第七个入口只会让人迷路。
 */
defineProps<{ goals: any[] }>()
const emit = defineEmits<{ (e: 'create', title: string): void }>()

const adding = ref(false)
const title = ref('')

/**
 * 目标的进度**不取** goal.progress —— 那是个手填后就不再更新的死数字。
 * 这里用「它下面所有项目进度的平均」来算，并明确标注是平均值，
 * 避免出现「目标是 80%、项目全是 0%」这种自相矛盾的画面。
 */
function avgProgress(g: any) {
  const ps = g.projects || []
  if (!ps.length) return 0
  return Math.round(ps.reduce((s: number, p: any) => s + (p.progress || 0), 0) / ps.length)
}

function submit() {
  const t = title.value.trim()
  if (!t) return
  emit('create', t)
  title.value = ''
  adding.value = false
}
</script>

<template>
  <div v-if="!goals.length" class="empty">
    还没有年度目标。目标不写下来，项目就只是一个一个孤立的作品。
  </div>

  <div v-for="g in goals" :key="g.id" class="goal-block">
    <div class="goal-head">
      <strong>{{ g.title }}</strong>
      <span class="goal-meta">{{ g.projects.length }} 个项目 · 平均 {{ avgProgress(g) }}%</span>
    </div>
    <div class="progress"><span :style="{ width: avgProgress(g) + '%' }"></span></div>

    <p v-if="!g.projects.length" class="goal-empty">
      这个目标下还没有项目。去「项目」新建一个，归属选它。
    </p>
    <div v-for="p in g.projects" :key="p.id" class="goal-proj">
      <span class="goal-proj-name">{{ p.name }}</span>
      <span class="goal-proj-tasks">{{ p.taskDone }} / {{ p.taskTotal }} 任务</span>
      <span class="goal-proj-pct">{{ p.progress }}%</span>
    </div>
  </div>

  <div class="goal-add">
    <button v-if="!adding" class="ghost tiny" @click="adding = true">+ 新建年度目标</button>
    <div v-else class="form-row" style="gap: 8px">
      <input v-model="title" placeholder="今年想往哪个方向走？一句话就够" @keyup.enter="submit" />
      <button class="primary small" style="flex: 0 0 auto" @click="submit">创建</button>
      <button class="ghost tiny" style="flex: 0 0 auto" @click="adding = false">取消</button>
    </div>
  </div>
</template>

<style scoped>
.goal-block {
  padding: 14px 0;
  border-top: 1px solid var(--line2);
}

.goal-block:first-of-type {
  border-top: 0;
  padding-top: 4px;
}

.goal-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.goal-head strong {
  font-size: 13.5px;
  color: var(--ink);
}

.goal-meta {
  font-size: 11.5px;
  color: var(--faint);
  white-space: nowrap;
}

.goal-empty {
  margin: 8px 0 0;
  font-size: 11.5px;
  color: var(--faint);
}

/* 项目行：名称吃掉剩余宽度，任务数与百分比固定在右侧 */
.goal-proj {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 5px 0 5px 12px;
  border-left: 2px solid var(--line2);
  margin-left: 2px;
}

.goal-proj-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goal-proj-tasks {
  font-size: 11px;
  color: var(--faint);
  white-space: nowrap;
}

.goal-proj-pct {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--primary-ink);
  min-width: 34px;
  text-align: right;
}

.goal-add {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line2);
}
</style>
