<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api } from '../api'

const records = ref<any[]>([])
const systems = ref<any[]>([])
const filter = ref('')
const saving = ref(false)

const form = ref({
  title: '',
  systemCode: 'AI',
  category: 'AI',
  source: '',
  understanding: '',
  practice: '',
  conclusion: '',
  durationMinutes: 60
})

const filtered = computed(() =>
  filter.value ? records.value.filter((r) => r.systemCode === filter.value) : records.value
)

async function load() {
  const [r, s] = await Promise.all([api.learning(), api.systems()])
  records.value = r.data.data
  systems.value = s.data.data
}

async function add() {
  if (!form.value.title.trim()) return
  saving.value = true
  try {
    await api.createLearning({ ...form.value, category: form.value.systemCode })
    form.value = {
      title: '', systemCode: 'AI', category: 'AI', source: '',
      understanding: '', practice: '', conclusion: '', durationMinutes: 60
    }
    await load()
  } finally {
    saving.value = false
  }
}

async function del(id: number) {
  await api.deleteLearning(id)
  await load()
}

function fmt(t: any) {
  return t ? String(t).slice(0, 10) : ''
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">LEARNING</div>
        <h1>学习</h1>
        <p>把「看过」变成「理解过、实践过、输出过」。</p>
      </div>
      <select v-model="filter" style="width: 150px">
        <option value="">全部系统</option>
        <option v-for="s in systems" :key="s.code" :value="s.code">{{ s.name }}</option>
      </select>
    </div>

    <div class="grid-2">
      <div class="card form-card">
        <div class="card-title"><span>新增学习记录</span><span class="hint">五段式</span></div>
        <input v-model="form.title" placeholder="标题 —— 今天学到了什么？" />
        <div class="form-row">
          <select v-model="form.systemCode">
            <option v-for="s in systems" :key="s.code" :value="s.code">{{ s.name }}</option>
          </select>
          <input v-model.number="form.durationMinutes" type="number" placeholder="分钟" style="flex: 0 0 100px" />
        </div>
        <label class="field"><span>① 原始资料 / 来源</span><input v-model="form.source" placeholder="链接或出处" /></label>
        <label class="field"><span>② 我学到了什么</span><textarea v-model="form.understanding" placeholder="用自己的话复述一遍"></textarea></label>
        <label class="field"><span>③ 我实际测试了什么</span><textarea v-model="form.practice" placeholder="动手做了什么"></textarea></label>
        <label class="field"><span>④ 测试结果</span><textarea v-model="form.conclusion" placeholder="结论是什么"></textarea></label>
        <button class="primary" :disabled="saving" @click="add">{{ saving ? '保存中…' : '保存' }}</button>
      </div>

      <div class="card list-card">
        <div class="card-title"><span>学习记录</span><span class="hint">共 {{ filtered.length }} 条</span></div>
        <div v-if="!filtered.length" class="empty">还没有记录。</div>
        <article v-for="r in filtered" :key="r.id" class="entry">
          <span class="tag">{{ r.category || '其他' }}</span>
          <span class="tag" style="margin-left: 6px">{{ r.durationMinutes || 0 }} 分钟</span>
          <h3>{{ r.title }}</h3>
          <div class="five">
            <div v-if="r.source"><b>来源：</b>{{ r.source }}</div>
            <div v-if="r.understanding"><b>理解：</b>{{ r.understanding }}</div>
            <div v-if="r.practice"><b>实践：</b>{{ r.practice }}</div>
            <div v-if="r.conclusion"><b>结论：</b>{{ r.conclusion }}</div>
          </div>
          <div class="form-row" style="gap: 6px">
            <small style="color: var(--faint); font-size: 11.5px">{{ fmt(r.learnedAt) }}</small>
            <button class="ghost tiny" style="margin-left: auto" @click="del(r.id)">删除</button>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>
