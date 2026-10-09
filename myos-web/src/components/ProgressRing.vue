<script setup lang="ts">
import { computed } from 'vue'
import { useCountUp } from '../composables/useCountUp'

const props = withDefaults(
  defineProps<{
    percent: number
    size?: number
    stroke?: number
    label?: string
    caption?: string
  }>(),
  { size: 132, stroke: 8, label: '', caption: '' }
)

const clamped = computed(() => Math.max(0, Math.min(100, Math.round(props.percent || 0))))
const center = computed(() => props.size / 2)
const radius = computed(() => (props.size - props.stroke) / 2)
/** 圆周长，用于把进度换算成 dashoffset */
const circumference = computed(() => 2 * Math.PI * radius.value)
const offset = computed(() => circumference.value * (1 - clamped.value / 100))
/** 中心数字滚动到位，而不是一帧跳出 */
const shown = useCountUp(clamped)
</script>

<template>
  <div class="ring-wrap">
    <svg
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      role="img"
      :aria-label="`完成度 ${clamped}%`"
    >
      <!-- 轨道 -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        style="stroke: #EDEFF2"
        :stroke-width="stroke"
      />
      <!-- 进度：从 12 点方向顺时针 -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        style="stroke: var(--primary)"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        :transform="`rotate(-90 ${center} ${center})`"
        class="ring-fill"
      />
      <text
        :x="center"
        :y="center"
        text-anchor="middle"
        dominant-baseline="central"
        font-size="27"
        font-weight="700"
        style="fill: var(--ink)"
      >
        {{ shown }}<tspan font-size="13" font-weight="500" style="fill: var(--muted)">%</tspan>
      </text>
    </svg>
    <div v-if="label || caption" class="ring-caption">
      <strong>{{ label }}</strong>
      <span v-if="caption">{{ caption }}</span>
    </div>
  </div>
</template>
