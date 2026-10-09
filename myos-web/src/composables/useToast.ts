import { ref } from 'vue'

export interface ToastItem {
  id: number
  text: string
  kind: 'info' | 'ok'
}

/**
 * 极简全局提示。
 *
 * 模块级 state 让任意组件调用 push 后，App.vue 里的挂载点统一渲染，
 * 不需要为每个页面单独实现反馈 UI。
 */
const items = ref<ToastItem[]>([])
let seq = 0

export function useToast() {
  function push(text: string, kind: ToastItem['kind'] = 'info', duration = 2400) {
    const id = ++seq
    items.value = [...items.value, { id, text, kind }]
    window.setTimeout(() => {
      items.value = items.value.filter(t => t.id !== id)
    }, duration)
  }

  return { items, push }
}
