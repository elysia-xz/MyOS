import { onMounted, ref, watch, type Ref } from 'vue'

/**
 * 数字滚动：把显示值从当前值平滑推到目标值。
 *
 * 用途是让"完成率""已达标数"这类关键数字有生长感，
 * 而不是一帧跳到位。尊重系统「减少动态效果」偏好，开启时直接落位。
 */
export function useCountUp(source: Ref<number>, duration = 650) {
  const shown = ref(0)

  const reduce =
    typeof window !== 'undefined' &&
    !!window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches

  let raf = 0

  function animate(to: number) {
    cancelAnimationFrame(raf)
    const from = shown.value
    if (reduce || from === to) {
      shown.value = to
      return
    }
    const start = performance.now()
    const step = (now: number) => {
      const p = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - p, 3) // easeOutCubic
      shown.value = Math.round(from + (to - from) * eased)
      if (p < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  watch(source, v => animate(Number(v) || 0))
  onMounted(() => animate(Number(source.value) || 0))

  return shown
}
