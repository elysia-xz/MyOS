import { http } from './http'

export const api = {
  // ---- 认证 ----
  // device 用于后端区分多端登录（web / miniprogram / desktop / app），
  // 各端令牌相互独立，登出只影响当前设备。
  login: (username: string, password: string, device = 'web') =>
    http.post('/auth/login', { username, password, device }),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),
  // 改自己的密码：必须验旧密码（令牌 30 天有效，只看令牌就能改等于令牌泄露即可锁死本人）
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    http.post('/auth/password', data),

  // ---- 首页成长 Dashboard ----
  dashboard: () => http.get('/dashboard'),

  // ---- 今日任务 ----
  tasks: (date?: string) => http.get('/tasks', { params: date ? { date } : {} }),
  createTask: (data: any) => http.post('/tasks', data),
  updateTaskStatus: (id: number, status: string) =>
    http.put(`/tasks/${id}/status`, null, { params: { status } }),
  deleteTask: (id: number) => http.delete(`/tasks/${id}`),

  // ---- 今日习惯打点（7 系统水位）----
  systems: () => http.get('/habit/systems'),
  habits: (date?: string) => http.get('/habit', { params: date ? { date } : {} }),
  saveHabits: (logDate: string, items: any[]) => http.put('/habit', { logDate, items }),
  habitRange: (from: string, to: string) => http.get('/habit/range', { params: { from, to } }),

  // ---- 学习 ----
  learning: (systemCode?: string) =>
    http.get('/learning', { params: systemCode ? { systemCode } : {} }),
  createLearning: (data: any) => http.post('/learning', data),
  deleteLearning: (id: number) => http.delete(`/learning/${id}`),

  // ---- 内容输出 ----
  contents: () => http.get('/contents'),
  createContent: (data: any) => http.post('/contents', data),
  updateContentStatus: (id: number, status: string) =>
    http.put(`/contents/${id}/status`, null, { params: { status } }),
  deleteContent: (id: number) => http.delete(`/contents/${id}`),
  contentCalendar: (month?: string) =>
    http.get('/contents/calendar', { params: month ? { month } : {} }),

  // ---- 项目 ----
  projects: () => http.get('/projects'),
  projectDetail: (id: number) => http.get(`/projects/${id}`),
  createProject: (data: any) => http.post('/projects', data),
  updateProject: (id: number, data: any) => http.put(`/projects/${id}`, data),
  updateProjectProgress: (id: number, value: number) =>
    http.put(`/projects/${id}/progress`, null, { params: { value } }),
  deleteProject: (id: number) => http.delete(`/projects/${id}`),
  portfolio: () => http.get('/projects/portfolio'),
  projectLogs: (id: number) => http.get(`/projects/${id}/logs`),
  addProjectLog: (id: number, data: any) => http.post(`/projects/${id}/logs`, data),

  // ---- 目标层级（年→月→周）----
  goals: () => http.get('/goals'),
  goalTree: () => http.get('/goals/tree'),
  createGoal: (data: any) => http.post('/goals', data),
  updateGoalProgress: (id: number, value: number) =>
    http.put(`/goals/${id}/progress`, null, { params: { value } }),
  deleteGoal: (id: number) => http.delete(`/goals/${id}`),

  // ---- 财务 ----
  financeRecords: (from?: string, to?: string) =>
    http.get('/finance/records', { params: { ...(from ? { from } : {}), ...(to ? { to } : {}) } }),
  addFinanceRecord: (data: any) => http.post('/finance/records', data),
  deleteFinanceRecord: (id: number) => http.delete(`/finance/records/${id}`),
  financeOverview: (month?: string) =>
    http.get('/finance/overview', { params: month ? { month } : {} }),
  assets: () => http.get('/finance/assets'),
  saveAssets: (data: any) => http.put('/finance/assets', data),

  // ---- 复盘 ----
  reviews: (type: string, limit = 10) => http.get('/review', { params: { type, limit } }),
  currentReview: (type: string) => http.get('/review/current', { params: { type } }),
  saveReview: (data: any) => http.put('/review', data),
  deleteReview: (type: string, date?: string) =>
    http.delete('/review', { params: { type, ...(date ? { date } : {}) } }),

  // ---- AI 助手（场景化，无独立聊天页）----
  aiStatus: () => http.get('/ai/status'),
  // 网关配置：配置界面在前端，Key 存在后端。GET 只回脱敏值（sk-123****cdef），
  // 完整 Key 只往上传、不下来。reset=true 表示整体清空（退回环境变量/默认值）。
  aiConfig: () => http.get('/ai/config'),
  saveAiConfig: (data: { baseUrl?: string; model?: string; apiKey?: string; reset?: boolean }) =>
    http.post('/ai/config', data),
  testAiConfig: () => http.post('/ai/config/test'),
  aiDailySummary: () => http.post('/ai/daily/summary'),
  aiDailyReport: () => http.post('/ai/daily/report'),
  aiDailyReview: () => http.post('/ai/daily/review'),
  aiMonthlySummary: () => http.post('/ai/monthly/summary'),
  aiAnnualReport: () => http.post('/ai/annual/report'),

  // ---- AI 对话（二期：能问答，也能带确认地办事）----
  // conversationId 为空表示新开一轮对话；返回 status = DONE（直接显示）
  // 或 PENDING（带 toolCalls，要用户确认后才执行）。
  aiChat: (data: { conversationId?: number | null; message: string }) =>
    http.post('/ai/chat', data),
  // 只传要确认的调用 id。工具名和参数由后端回读，前端给的其它信息不算数。
  aiToolExecute: (data: { conversationId: number; calls: { id: string }[] }) =>
    http.post('/ai/tool/execute', data),
  aiToolCancel: (data: { conversationId: number; calls: { id: string }[] }) =>
    http.post('/ai/tool/cancel', data),
  aiConversations: () => http.get('/ai/conversations'),
  aiConversation: (id: number) => http.get(`/ai/conversations/${id}`),

  // ---- 数据导出 ----
  // responseType: 'blob' —— 后端带 Content-Disposition 直接给文件，
  // 走 blob 才能保留「另存为」的原生体验与正确文件名。
  exportData: () => http.get('/export', { responseType: 'blob' })
}
