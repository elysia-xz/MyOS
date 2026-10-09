<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('admin')
const password = ref('admin123')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    await auth.login(username.value, password.value)
    router.push('/dashboard')
  } catch (e: any) {
    error.value = e?.response?.data?.message || '登录失败，请检查账号密码'
  }
}

/** 演示模式：后端未启动时直接进入（仅用于本地预览界面） */
function enterDemo() {
  auth.loginDemo()
  router.push('/dashboard')
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="eyebrow">PERSONAL OS</div>
      <h1>MyOS</h1>
      <p>个人成长操作系统 —— 一次行动，产生多个结果。<br />让每一份行动都沉淀成内容、作品与成长报告。</p>
      <form @submit.prevent="submit">
        <label>账号<input v-model="username" autocomplete="username" /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" /></label>
        <button class="primary" type="submit" :disabled="auth.loading">
          {{ auth.loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <div v-if="error" class="error">{{ error }}</div>
      <button class="ghost" style="width: 100%; margin-top: 10px" @click="enterDemo">
        跳过登录，预览界面（演示数据）
      </button>
      <div class="tip">默认账号 admin / admin123</div>
    </div>
  </div>
</template>
