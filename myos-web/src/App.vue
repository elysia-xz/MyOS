<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from './stores/auth'
import { runtime } from './api/http'
import { useToast } from './composables/useToast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const demo = ref(false)
const { items: toasts } = useToast()

const isLogin = computed(() => route.path === '/login')

const userInitial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || ''
  return name ? name.trim().charAt(0).toUpperCase() : '·'
})

/** 统一图标的公共属性，保证 7 个图标粗细与视觉重量一致 */
const icon = (body: string) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" ` +
  `stroke-linecap="round" stroke-linejoin="round">${body}</svg>`

// 6 大模块导航（对应 PRD §3）
const menus = [
  {
    path: '/dashboard',
    label: '首页',
    svg: icon('<path d="M3 10.2 12 3l9 7.2V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>')
  },
  {
    path: '/today',
    label: '今日',
    svg: icon(
      '<rect x="3" y="4.5" width="18" height="16.5" rx="2.5"/>' +
        '<path d="M8 2.6v3.6M16 2.6v3.6M3 9.8h18"/>' +
        '<path d="m9.2 15.2 2 2 3.6-3.8"/>'
    )
  },
  {
    path: '/learning',
    label: '学习',
    svg: icon(
      '<path d="M4 5.5A1.5 1.5 0 0 1 5.5 4H10a2 2 0 0 1 2 2v13a2 2 0 0 0-2-2H4z"/>' +
        '<path d="M20 5.5A1.5 1.5 0 0 0 18.5 4H14a2 2 0 0 0-2 2v13a2 2 0 0 1 2-2h6z"/>'
    )
  },
  {
    path: '/content',
    label: '输出',
    svg: icon('<path d="M4.5 19.5h15"/><path d="M15.2 4.6a2.1 2.1 0 0 1 3 3L9 16.8l-4.2 1.2 1.2-4.2z"/>')
  },
  {
    path: '/projects',
    label: '项目',
    svg: icon('<path d="m12 3 8.5 4.6v8.8L12 21l-8.5-4.6V7.6z"/><path d="m3.5 7.6 8.5 4.7 8.5-4.7M12 12.3V21"/>')
  },
  {
    path: '/finance',
    label: '财务',
    svg: icon(
      '<path d="M3 7.5A2.5 2.5 0 0 1 5.5 5h13A2.5 2.5 0 0 1 21 7.5v9a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 16.5z"/>' +
        '<path d="M3 9.8h18"/><circle cx="16.6" cy="14.3" r="1.15"/>'
    )
  },
  {
    path: '/review',
    label: '复盘',
    svg: icon(
      '<path d="M3.2 12a8.8 8.8 0 1 0 2.6-6.2"/><path d="M3.2 4.6v5h5"/>' +
        '<path d="M12 8v4.4l3 1.8"/>'
    )
  }
]

const logoutIcon = icon(
  '<path d="M15 17l5-5-5-5"/><path d="M20 12H9"/>' +
    '<path d="M12 20H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h6"/>'
)

function go(path: string) {
  router.push(path)
}

async function logout() {
  try {
    await auth.logout()
  } finally {
    router.push('/login')
  }
}

onMounted(() => {
  if (!isLogin.value) auth.loadMe()
  // 轮询检测是否进入演示模式（由 http 拦截器标记）
  setInterval(() => (demo.value = runtime.demoMode), 800)
})
</script>

<template>
  <div v-if="isLogin"><router-view /></div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand">MyOS</div>
        <div class="brand-sub">私人工作台 V1.0</div>
      </div>

      <nav aria-label="主导航">
        <button
          v-for="item in menus"
          :key="item.path"
          type="button"
          :class="['nav-item', { active: route.path === item.path }]"
          :aria-current="route.path === item.path ? 'page' : undefined"
          :title="item.label"
          @click="go(item.path)"
        >
          <span class="nav-icon" aria-hidden="true" v-html="item.svg"></span>
          <span class="nav-label">{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-foot">
        <div v-if="auth.user" class="user-chip">
          <span class="avatar">{{ userInitial }}</span>
          <span class="user-name">{{ auth.user.nickname || auth.user.username }}</span>
        </div>
        <button type="button" class="logout-btn" @click="logout">
          <span class="nav-icon" aria-hidden="true" v-html="logoutIcon"></span>
          <span class="nav-label">退出登录</span>
        </button>
      </div>
    </aside>

    <main class="main">
      <div v-if="demo" class="demo-banner">
        演示数据模式 —— 后端未连接，当前展示的是示例数据。启动 Spring Boot 后自动切换为真实数据。
        <button class="ghost tiny" style="margin-left: auto" @click="demo = false; $router.go(0)">知道了</button>
      </div>
      <router-view />
    </main>
  </div>

  <!-- 全局即时反馈挂载点 -->
  <div class="toast-layer" aria-live="polite" aria-atomic="true">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="t.kind">{{ t.text }}</div>
  </div>
</template>
