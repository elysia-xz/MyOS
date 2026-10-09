<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    items: Array<{ systemCode: string; systemName: string; level: number }>
    size?: number
  }>(),
  { size: 268 }
)

const center = computed(() => props.size / 2)
/** 留出外圈标签的空间 */
const radius = computed(() => props.size / 2 - 42)

/** 刻度上限：取最高等级向上取整到 5 的倍数，最低 5，避免等级低时图形缩成一点 */
const maxLevel = computed(() => {
  const top = props.items.reduce((m, i) => Math.max(m, i.level || 0), 0)
  return Math.max(5, Math.ceil((top + 1) / 5) * 5)
})

const axes = computed(() => {
  const n = props.items.length || 1
  return props.items.map((item, i) => ({
    item,
    angle: -Math.PI / 2 + (i * 2 * Math.PI) / n
  }))
})

function pointAt(angle: number, ratio: number) {
  return {
    x: center.value + radius.value * ratio * Math.cos(angle),
    y: center.value + radius.value * ratio * Math.sin(angle)
  }
}

const gridRings = computed(() =>
  [0.25, 0.5, 0.75, 1].map(r =>
    axes.value
      .map(a => {
        const p = pointAt(a.angle, r)
        return `${p.x.toFixed(1)},${p.y.toFixed(1)}`
      })
      .join(' ')
  )
)

const spokes = computed(() => axes.value.map(a => pointAt(a.angle, 1)))

const dataPolygon = computed(() =>
  axes.value
    .map(a => {
      const p = pointAt(a.angle, (a.item.level || 0) / maxLevel.value)
      return `${p.x.toFixed(1)},${p.y.toFixed(1)}`
    })
    .join(' ')
)

const dataDots = computed(() =>
  axes.value.map(a => pointAt(a.angle, (a.item.level || 0) / maxLevel.value))
)

const labels = computed(() =>
  axes.value.map(a => {
    const p = pointAt(a.angle, 1.24)
    const cos = Math.cos(a.angle)
    const anchor = Math.abs(cos) < 0.25 ? 'middle' : cos > 0 ? 'start' : 'end'
    return { text: a.item.systemName, x: p.x, y: p.y, anchor }
  })
)
</script>

<template>
  <div class="radar-wrap">
    <svg
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      role="img"
      aria-label="7 大系统能力等级雷达图"
    >
      <!-- 网格 -->
      <polygon
        v-for="(ring, i) in gridRings"
        :key="'g' + i"
        :points="ring"
        fill="none"
        style="stroke: #E9EBEF"
        stroke-width="1"
      />
      <!-- 轴线 -->
      <line
        v-for="(s, i) in spokes"
        :key="'s' + i"
        :x1="center"
        :y1="center"
        :x2="s.x"
        :y2="s.y"
        style="stroke: #E9EBEF"
        stroke-width="1"
      />
      <!-- 数据面 -->
      <polygon
        :points="dataPolygon"
        style="fill: var(--primary); stroke: var(--primary)"
        fill-opacity="0.16"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <circle
        v-for="(d, i) in dataDots"
        :key="'d' + i"
        :cx="d.x"
        :cy="d.y"
        r="3.2"
        style="fill: var(--primary)"
      />
      <!-- 标签 -->
      <text
        v-for="(l, i) in labels"
        :key="'l' + i"
        :x="l.x"
        :y="l.y"
        :text-anchor="l.anchor"
        dominant-baseline="central"
        font-size="11.5"
        style="fill: var(--muted)"
      >
        {{ l.text }}
      </text>
    </svg>
    <p class="radar-hint">刻度上限 Lv.{{ maxLevel }} · 由累计数据自动计算</p>
  </div>
</template>
