import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'
import { tokenStore, userCache } from '../api/token'

/**
 * 认证状态。
 *
 * 多端改造后：登录成功会把令牌写入本地存储，后续请求由 axios 拦截器
 * 自动带上 Authorization 头；登出则清掉令牌并通知后端撤销。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<any>(userCache.get())
  const loading = ref(false)

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await api.login(username, password)
      const payload = res.data.data
      tokenStore.set(payload.token)
      userCache.set(payload.user)
      user.value = payload.user
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await api.logout()
    } catch {
      // 即便后端撤销失败，本地也要清干净，避免残留无效登录态
    } finally {
      tokenStore.clear()
      user.value = null
    }
  }

  async function loadMe() {
    // 没有令牌就不必发请求
    if (!tokenStore.get()) {
      user.value = null
      return false
    }
    try {
      const res = await api.me()
      userCache.set(res.data.data)
      user.value = res.data.data
      return true
    } catch {
      user.value = null
      return false
    }
  }

  /** 纯前端演示登录（后端不可用时预览界面） */
  function loginDemo() {
    user.value = { id: 0, username: 'demo', nickname: '演示用户' }
  }

  return { user, loading, login, logout, loadMe, loginDemo }
})
