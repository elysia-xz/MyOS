import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import TodayView from '../views/TodayView.vue'
import LearningView from '../views/LearningView.vue'
import ContentView from '../views/ContentView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import FinanceView from '../views/FinanceView.vue'
import ReviewView from '../views/ReviewView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: DashboardView },
    { path: '/today', component: TodayView },
    { path: '/learning', component: LearningView },
    { path: '/content', component: ContentView },
    { path: '/projects', component: ProjectsView },
    { path: '/finance', component: FinanceView },
    { path: '/review', component: ReviewView },
    // 兼容 V0.1 的旧地址
    { path: '/tasks', redirect: '/today' },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

export default router
