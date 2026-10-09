import axios from 'axios'
import { mockFor, demoEnabled, applyDemoWrite } from './demo'
import { tokenStore } from './token'

/**
 * 统一请求出口。
 *
 * 多端改造要点：
 *  - baseURL 带上 /api/v1 版本前缀，为后续接口演进留出空间
 *  - 不再使用 withCredentials（Cookie 在小程序/桌面/原生端不可靠），
 *    改为在请求拦截器里注入 Authorization: Bearer 令牌
 *  - 401 时清理本地令牌并回登录页
 */
export const http = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' }
})

/** 标记当前是否处于演示模式（后端不可达时自动开启） */
export const runtime = { demoMode: false }

/** 构造一个形似后端 ApiResponse 的响应 */
function makeMockResponse(config: any, data: any) {
  return {
    data: { success: true, data, message: 'DEMO' },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  }
}

// 注入登录令牌
http.interceptors.request.use(cfg => {
  const token = tokenStore.get()
  if (token) {
    cfg.headers = cfg.headers || {}
    cfg.headers.Authorization = `Bearer ${token}`
  }
  return cfg
})

http.interceptors.response.use(
  r => r,
  e => {
    const cfg: any = e.config || {}
    const status = e.response?.status

    // 「后端不可达」有两种表现：
    //   ① 直连后端 —— 网络错误，拿不到 response
    //   ② 走 Vite 代理 —— 代理连不上后端，把它包成 502/503/504
    // 漏掉 ② 会导致「后端没起但演示数据也不生效」，页面一片空白。
    // 注意 500 不算：那是后端真的报错了，不能拿演示数据盖住。
    const backendDown = !e.response || status === 502 || status === 503 || status === 504

    // 演示兜底：仅在后端不可达、且演示模式开启时生效
    if (backendDown && demoEnabled()) {
      const markDemo = () => {
        if (!runtime.demoMode) {
          runtime.demoMode = true
          console.info('[MyOS] 后端未连接，已切换到演示数据模式。')
        }
      }
      const method = (cfg.method || 'get').toLowerCase()

      if (method === 'get') {
        const mock = mockFor(cfg.url || '')
        // 没准备对应演示数据就如实报错，避免"假装有数据"掩盖问题
        if (mock === null) return Promise.reject(e)
        markDemo()
        return Promise.resolve(makeMockResponse(cfg, mock))
      }

      // 写操作（POST/PUT/PATCH/DELETE）：演示模式下静默成功，避免界面报错。
      // 注意这段必须在 GET 判断之外 —— 之前被 GET 条件挡住，导致演示模式下
      // 所有保存/新增/删除都直接失败。
      markDemo()
      let body: any = cfg.data
      if (typeof body === 'string') {
        try {
          body = JSON.parse(body)
        } catch {
          body = null
        }
      }
      applyDemoWrite(cfg.url || '', method, body)
      return Promise.resolve(makeMockResponse(cfg, true))
    }

    // 登录态失效：清令牌回登录页（演示模式下不跳转，避免打断预览）
    if (status === 401 && !runtime.demoMode) {
      tokenStore.clear()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(e)
  }
)
