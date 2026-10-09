<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api } from '../api'

const overview = ref<any>({ income: 0, expense: 0, balance: 0, savingsRate: 0, targetRate: 30, expenseByCategory: [], trend: [] })
const records = ref<any[]>([])
const assets = ref<any>({ snapshots: [], latest: null })
const loading = ref(true)

const form = ref({
  recordType: 'EXPENSE',
  category: '餐饮',
  amount: '',
  recordDate: new Date().toISOString().slice(0, 10),
  note: ''
})

const assetForm = ref({ cash: '', cnyAssets: '', usdAssets: '', otherAssets: '' })

const rateHit = computed(() => Number(overview.value.savingsRate) >= Number(overview.value.targetRate))
const assetTotal = computed(() => {
  const a = assets.value.latest
  if (!a) return 0
  return Number(a.cash || 0) + Number(a.cnyAssets || 0) + Number(a.usdAssets || 0) + Number(a.otherAssets || 0)
})

function fmt(n: any) {
  const v = Number(n || 0)
  return '¥' + v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function pct(n: any, total: any) {
  const t = Number(total || 0)
  if (!t) return 0
  return Math.round((Number(n || 0) / t) * 100)
}

async function load() {
  loading.value = true
  try {
    const [o, r, a] = await Promise.all([
      api.financeOverview(),
      api.financeRecords(),
      api.assets()
    ])
    overview.value = o.data.data
    records.value = r.data.data
    assets.value = a.data.data
    const l = a.data.data?.latest
    if (l) {
      assetForm.value = {
        cash: String(l.cash ?? ''),
        cnyAssets: String(l.cnyAssets ?? ''),
        usdAssets: String(l.usdAssets ?? ''),
        otherAssets: String(l.otherAssets ?? '')
      }
    }
  } finally {
    loading.value = false
  }
}

async function addRecord() {
  if (!form.value.amount) return
  await api.addFinanceRecord({
    recordDate: form.value.recordDate,
    recordType: form.value.recordType,
    category: form.value.category,
    amount: Number(form.value.amount),
    currency: 'CNY',
    note: form.value.note
  })
  form.value.amount = ''
  form.value.note = ''
  await load()
}

async function delRecord(id: number) {
  await api.deleteFinanceRecord(id)
  await load()
}

async function saveAssets() {
  await api.saveAssets({
    snapshotMonth: new Date().toISOString().slice(0, 8) + '01',
    cash: Number(assetForm.value.cash || 0),
    cnyAssets: Number(assetForm.value.cnyAssets || 0),
    usdAssets: Number(assetForm.value.usdAssets || 0),
    otherAssets: Number(assetForm.value.otherAssets || 0)
  })
  await load()
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <div class="eyebrow">FINANCE</div>
        <h1>财务</h1>
        <p>只做记录、统计、提醒、复盘 —— 不替你做任何投资决策。</p>
      </div>
    </div>

    <div v-if="loading" class="card loading">加载中…</div>
    <template v-else>
      <!-- ① 现金流 -->
      <div class="fin-grid">
        <div class="fin-cell"><div class="k">本月收入</div><div class="v up">{{ fmt(overview.income) }}</div><div class="note">现金流 ①</div></div>
        <div class="fin-cell"><div class="k">本月支出</div><div class="v down">{{ fmt(overview.expense) }}</div><div class="note">现金流 ①</div></div>
        <div class="fin-cell"><div class="k">本月结余</div><div class="v">{{ fmt(overview.balance) }}</div><div class="note">收入 - 支出</div></div>
        <div class="fin-cell">
          <div class="k">储蓄率</div>
          <div class="v" :class="rateHit ? 'down' : 'up'">{{ overview.savingsRate }}%</div>
          <div class="note">② 目标 {{ overview.targetRate }}% · {{ rateHit ? '已达标' : '未达标' }}</div>
        </div>
      </div>

      <div class="grid-2 section-gap">
        <!-- 记账 -->
        <div class="card">
          <div class="card-title"><span>记一笔</span><span class="hint">支持收入与支出</span></div>
          <div class="form-card">
            <div class="form-row">
              <select v-model="form.recordType">
                <option value="EXPENSE">支出</option>
                <option value="INCOME">收入</option>
              </select>
              <input v-model="form.category" placeholder="分类" />
              <input v-model="form.amount" type="number" placeholder="金额" />
            </div>
            <div class="form-row">
              <input v-model="form.recordDate" type="date" />
              <input v-model="form.note" placeholder="备注" />
              <button class="primary small" style="flex: 0 0 auto" @click="addRecord">记录</button>
            </div>
          </div>

          <div class="card-title" style="margin-top: 24px"><span>本月流水</span></div>
          <div v-if="!records.length" class="empty">本月还没有记录。</div>
          <div v-for="r in records" :key="r.id" class="list-row">
            <div class="row-main">
              <strong>{{ r.category || '未分类' }}</strong>
              <small>{{ r.recordDate }} · {{ r.note || '—' }}</small>
            </div>
            <span :class="r.recordType === 'INCOME' ? 'amount-in' : 'amount-out'">
              {{ r.recordType === 'INCOME' ? '+' : '-' }}{{ fmt(r.amount) }}
            </span>
            <button class="ghost tiny" @click="delRecord(r.id)">删除</button>
          </div>
        </div>

        <!-- 支出构成 -->
        <div class="card">
          <div class="card-title"><span>支出构成</span><span class="hint">本月</span></div>
          <div v-if="!overview.expenseByCategory?.length" class="empty">暂无支出数据。</div>
          <div v-for="c in overview.expenseByCategory" :key="c.category" class="water-row" style="grid-template-columns: 88px 1fr 108px">
            <span class="water-name">{{ c.category }}</span>
            <div class="bar-track"><div class="bar-fill partial" :style="{ width: pct(c.total, overview.expense) + '%' }"></div></div>
            <span class="water-val">{{ fmt(c.total) }}</span>
          </div>

          <!-- ③ 资产配置 -->
          <div class="card-title" style="margin-top: 26px"><span>资产配置 · 月度快照</span></div>
          <div class="form-card">
            <div class="form-row">
              <input v-model="assetForm.cash" type="number" placeholder="现金" />
              <input v-model="assetForm.cnyAssets" type="number" placeholder="人民币资产" />
            </div>
            <div class="form-row">
              <input v-model="assetForm.usdAssets" type="number" placeholder="美元资产" />
              <input v-model="assetForm.otherAssets" type="number" placeholder="其他资产" />
              <button class="primary small" style="flex: 0 0 auto" @click="saveAssets">保存快照</button>
            </div>
          </div>
          <div v-if="assets.latest" style="margin-top: 16px">
            <div class="pct" style="margin-bottom: 8px">
              <span>总资产 {{ fmt(assetTotal) }}</span>
              <span>{{ assets.latest.snapshotMonth }}</span>
            </div>
            <div class="water-row" style="grid-template-columns: 76px 1fr 60px">
              <span class="water-name">现金</span>
              <div class="bar-track"><div class="bar-fill partial" :style="{ width: pct(assets.latest.cash, assetTotal) + '%' }"></div></div>
              <span class="water-val">{{ pct(assets.latest.cash, assetTotal) }}%</span>
            </div>
            <div class="water-row" style="grid-template-columns: 76px 1fr 60px">
              <span class="water-name">人民币</span>
              <div class="bar-track"><div class="bar-fill partial" :style="{ width: pct(assets.latest.cnyAssets, assetTotal) + '%' }"></div></div>
              <span class="water-val">{{ pct(assets.latest.cnyAssets, assetTotal) }}%</span>
            </div>
            <div class="water-row" style="grid-template-columns: 76px 1fr 60px">
              <span class="water-name">美元</span>
              <div class="bar-track"><div class="bar-fill partial" :style="{ width: pct(assets.latest.usdAssets, assetTotal) + '%' }"></div></div>
              <span class="water-val">{{ pct(assets.latest.usdAssets, assetTotal) }}%</span>
            </div>
            <div class="water-row" style="grid-template-columns: 76px 1fr 60px">
              <span class="water-name">其他</span>
              <div class="bar-track"><div class="bar-fill partial" :style="{ width: pct(assets.latest.otherAssets, assetTotal) + '%' }"></div></div>
              <span class="water-val">{{ pct(assets.latest.otherAssets, assetTotal) }}%</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>
