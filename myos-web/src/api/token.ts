/**
 * 登录令牌存储。
 *
 * 多端改造后不再依赖 Cookie：令牌由客户端显式保存并随请求携带。
 * Web 端用 localStorage；小程序/桌面/原生端可替换为各自的持久化实现，
 * 只要保持 get/set/clear 三个方法语义一致即可。
 */
const TOKEN_KEY = 'myos_token'
const USER_KEY = 'myos_user'

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },

  set(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      // 隐私模式下 localStorage 可能不可写，降级为仅内存有效
    }
  },

  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    } catch {
      // 忽略
    }
  }
}

/** 缓存登录用户信息，刷新页面时可立即渲染，避免闪烁 */
export const userCache = {
  get(): any | null {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  },
  set(user: any) {
    try {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch {
      // 忽略
    }
  }
}
