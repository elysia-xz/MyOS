<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api } from '../api'
import { useToast } from '../composables/useToast'

const { push: toast } = useToast()

const records = ref<any[]>([])
const calendar = ref<any>({ days: [], publishedDays: 0, targetDays: 30 })
// quote：本篇的一句话提炼。它属于这篇文章，不单独存一个语录库。
const form = ref({ title: '', platform: 'X', content: '', quote: '' })
const saving = ref(false)

const weekHeads = ['一', '二', '三', '四', '五', '六', '日']

// 计算当月 1 号是周几，前面补空格
const leadingBlanks = computed(() => {
  if (!calendar.value.days?.length) return 0
  const first = calendar.value.days[0].date as string
  const d = new Date(first).getDay()
  return d === 0 ? 6 : d - 1
})

async function load() {
  const [c, cal] = await Promise.all([api.contents(), api.contentCalendar()])
  // 包成新数组：演示数据每次返回同一个数组引用，
  // 直接赋值会被 Vue 判定为"没变化"，新增/删除后列表不刷新。
  records.value = [...(c.data.data || [])]
  calendar.value = cal.data.data
}

async function add() {
  if (!form.value.title.trim()) return
  saving.value = true
  try {
    await api.createContent({ ...form.value, status: 'DRAFT' })
    form.value = { title: '', platform: 'X', content: '', quote: '' }
    await load()
    toast('已保存草稿', 'ok')
  } finally {
    saving.value = false
  }
}

async function publish(r: any) {
  await api.updateContentStatus(r.id, r.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED')
  await load()
}

async function del(id: number) {
  await api.deleteContent(id)
  await load()
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">OUTPUT</div>
        <h1>输出</h1>
        <p>不追求爆款，只追求留下痕迹。一年 365 条成长记录。</p>
      </div>
      <div style="font-size: 13px; color: var(--muted)">本月已输出 {{ calendar.publishedDays }} / {{ calendar.targetDays }} 天</div>
    </div>

    <div class="grid-2">
      <!-- align-self: start —— 右列（日历 + 列表）通常更高，左列别跟着拉出一大块空白 -->
      <div class="card" style="align-self: start">
        <div class="card-title"><span>写一篇</span><span class="hint">先存草稿，想清楚再发布</span></div>
        <div class="form-card">
          <input v-model="form.title" placeholder="标题 —— 今天你真实做了什么？" />
          <select v-model="form.platform">
            <option>X</option>
            <option>国内自媒体</option>
            <option>技术文章</option>
            <option>博客</option>
            <option>其他</option>
          </select>
          <textarea v-model="form.content" class="large" placeholder="正文…"></textarea>

          <!-- 语录：跟着这一篇走，不单独建语录库 -->
          <div class="quote-field">
            <input v-model="form.quote" placeholder="留一句最想留下的话（可选）" />
            <span class="quote-hint">从这篇里提炼一句。它会跟着这篇文章保存，在下面以引用样式显示。</span>
          </div>

          <button class="primary" :disabled="saving" @click="add">{{ saving ? '保存中…' : '保存草稿' }}</button>
        </div>
      </div>

      <div class="stack">
        <div class="card">
          <div class="card-title"><span>输出打卡日历</span><span class="hint">{{ calendar.month }}</span></div>
          <div class="cal">
            <div v-for="w in weekHeads" :key="w" class="head">{{ w }}</div>
            <div v-for="n in leadingBlanks" :key="'b' + n" class="cell blank"></div>
            <div v-for="d in calendar.days" :key="d.day" class="cell" :class="{ on: d.published }" :title="d.date">
              {{ d.day }}
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-title"><span>最近输出</span></div>
          <div v-if="!records.length" class="empty">还没有输出。今天可以写第一篇。</div>
          <div v-for="r in records" :key="r.id" class="entry">
            <span class="tag" :class="r.status === 'PUBLISHED' ? 'ok' : ''">{{ r.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
            <span class="tag" style="margin-left: 6px">{{ r.platform }}</span>
            <h3>{{ r.title }}</h3>
            <p v-if="r.quote" class="entry-quote">「{{ r.quote }}」</p>
            <p>{{ (r.content || '暂无正文').slice(0, 120) }}</p>
            <div class="form-row" style="gap: 6px">
              <button class="ghost tiny" @click="publish(r)">{{ r.status === 'PUBLISHED' ? '转回草稿' : '标记发布' }}</button>
              <button class="ghost tiny" @click="del(r.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
