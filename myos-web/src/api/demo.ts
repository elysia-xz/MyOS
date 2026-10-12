/**
 * 演示数据兜底层
 *
 * 用途：在后端未启动（或未接 PostgreSQL）时，前端仍可用真实界面浏览全部页面。
 * 触发条件：网络请求失败（后端连不上）时自动回落到这里的数据。
 *
 * 注意：这是开发/演示用途。后端一旦可用，请求会正常走真实接口，此文件不参与。
 * 可通过 localStorage.setItem('myos_demo', 'off') 关闭兜底。
 */

const TODAY = new Date().toISOString().slice(0, 10)
const MONTH = TODAY.slice(0, 7)
/** 上两期的日期，用来演示「与上期对照」 */
const YESTERDAY = new Date(Date.now() - 86400000).toISOString().slice(0, 10)
const DAY_BEFORE = new Date(Date.now() - 2 * 86400000).toISOString().slice(0, 10)

const SYSTEMS = [
  { code: 'AI', name: 'AI 能力', unit: 'min', dailyTarget: 60 },
  { code: 'TECH', name: '技术能力', unit: 'min', dailyTarget: 60 },
  { code: 'CONTENT', name: '内容能力', unit: '篇', dailyTarget: 1 },
  { code: 'ENGLISH', name: '英语与信息', unit: 'min', dailyTarget: 60 },
  { code: 'BODY', name: '身体', unit: 'min', dailyTarget: 30 },
  { code: 'FINANCE', name: '财务', unit: '次', dailyTarget: 1 },
  { code: 'SOCIAL', name: '人际', unit: '次', dailyTarget: 1 }
]

const waterBars = [
  { systemCode: 'AI', systemName: 'AI 能力', unit: 'min', value: 45, target: 60, done: false, percent: 75, recorded: true },
  { systemCode: 'TECH', systemName: '技术能力', unit: 'min', value: 90, target: 60, done: true, percent: 100, recorded: true },
  { systemCode: 'CONTENT', systemName: '内容能力', unit: '篇', value: 1, target: 1, done: true, percent: 100, recorded: true },
  { systemCode: 'ENGLISH', systemName: '英语与信息', unit: 'min', value: 60, target: 60, done: true, percent: 100, recorded: true },
  { systemCode: 'BODY', systemName: '身体', unit: 'min', value: 30, target: 30, done: true, percent: 100, recorded: true },
  { systemCode: 'FINANCE', systemName: '财务', unit: '次', value: 1, target: 1, done: true, percent: 100, recorded: true },
  { systemCode: 'SOCIAL', systemName: '人际', unit: '次', value: 0, target: 1, done: false, percent: 0, recorded: false }
]

// 顺序与后端 common/SystemCatalog.ALL 保持一致，避免预览与真实数据错位
const levels = [
  { systemCode: 'AI', systemName: 'AI 能力', level: 3, amount: 1980, nextLevelAt: 2400 },
  { systemCode: 'TECH', systemName: '技术能力', level: 4, amount: 2520, nextLevelAt: 3000 },
  { systemCode: 'CONTENT', systemName: '内容能力', level: 2, amount: 23, nextLevelAt: 30 },
  { systemCode: 'ENGLISH', systemName: '英语与信息', level: 2, amount: 1440, nextLevelAt: 1800 },
  { systemCode: 'BODY', systemName: '身体', level: 1, amount: 26, nextLevelAt: 40 },
  { systemCode: 'FINANCE', systemName: '财务', level: 2, amount: 7, nextLevelAt: 9 },
  { systemCode: 'SOCIAL', systemName: '人际', level: 1, amount: 12, nextLevelAt: 20 }
]

const projects = [
  {
    id: 1, name: 'MyOS 私人工作台', description: '个人成长操作系统 V1.0',
    status: 'IN_PROGRESS', progress: 70, techStack: 'Vue3 / Spring Boot / PostgreSQL',
    githubUrl: 'https://github.com/example/myos', demoUrl: '',
    problemSolved: '把每天的行动自动沉淀成作品集与成长报告，解决「学了很多却没有产出」的问题',
    difficulty: '中等',
    solution: '用一次行动触发多路产出：任务→项目进度→作品集→日报→月复盘',
    startDate: TODAY, targetDate: TODAY, finishedAt: null
  },
  {
    id: 2, name: 'AI Prompt 管理工具', description: '统一管理常用 Prompt 模板',
    status: 'DONE', progress: 100, techStack: 'Vue3 / IndexedDB',
    githubUrl: 'https://github.com/example/prompt-manager', demoUrl: '',
    problemSolved: 'Prompt 散落各处，复用成本高', difficulty: '简单',
    solution: '本地 IndexedDB 存储 + 标签检索',
    startDate: TODAY, targetDate: TODAY, finishedAt: TODAY
  },
  {
    id: 3, name: '英语阅读助手', description: '划词翻译 + 生词沉淀',
    status: 'PLANNED', progress: 0, techStack: 'Vue3 / AI Gateway',
    githubUrl: '', demoUrl: '', problemSolved: '阅读英文材料时生词无法沉淀',
    difficulty: '', solution: '', startDate: TODAY, targetDate: TODAY, finishedAt: null
  },
  {
    // 进度已满但尚未标记完成 —— 用来演示状态机的「待复盘」态
    id: 4, name: '每日复盘模板', description: '把日/周/月复盘固化成可复用模板',
    status: 'IN_PROGRESS', progress: 100, techStack: 'Vue3 / Markdown',
    githubUrl: '', demoUrl: '', problemSolved: '每次复盘都要重新想结构，容易半途而废',
    difficulty: '简单', solution: '抽出固定六问模板，一键生成初稿',
    startDate: TODAY, targetDate: TODAY, finishedAt: null
  }
]

const learning = [
  {
    id: 1, title: 'Claude 代码分析能力实测', category: 'AI', systemCode: 'AI',
    source: 'https://docs.anthropic.com',
    content: 'Claude 在长上下文代码理解上的表现',
    understanding: '相比其他模型，它在跨文件依赖分析上更稳定',
    practice: '拿 MyOS 后端做了一次重构建议测试',
    conclusion: '适合做代码审查搭档，但不适合直接生成大段业务逻辑',
    durationMinutes: 90, learnedAt: TODAY + 'T10:00:00'
  },
  {
    id: 2, title: 'GitHub 开源项目目录结构分析', category: 'TECH', systemCode: 'TECH',
    source: 'https://github.com',
    content: '研究了一个 Spring Boot 项目的目录划分',
    understanding: '按领域分包比按技术分层更好维护',
    practice: '在 MyOS 里按 com.myos.<domain> 重构',
    conclusion: '领域分包让新增模块成本明显下降',
    durationMinutes: 60, learnedAt: TODAY + 'T09:00:00'
  },
  {
    id: 3, title: '行业报告：AI 编程工具对比', category: 'ENGLISH', systemCode: 'ENGLISH',
    source: '一手行业报告',
    content: '对比主流 AI 编程助手的能力边界',
    understanding: '工具差异主要在上下文长度与工程集成',
    practice: '在真实项目里各跑了一轮',
    conclusion: '选型要看工作流集成，而不是跑分',
    durationMinutes: 45, learnedAt: TODAY + 'T08:00:00'
  }
]

const contents = [
  {
    id: 1, title: '我是如何用工作台管理每天行动的', platform: 'X',
    content: '把「我要学很多东西」换成「今天 7 个系统分别有没有向前走一点」。',
    quote: '不要用忙碌感代替进展。',
    status: 'PUBLISHED', publishedAt: TODAY + 'T12:00:00', views: 128, likes: 23
  },
  {
    id: 2, title: '为什么我放弃了待办清单', platform: '国内自媒体',
    content: '清单只记录「要做什么」，不记录「做成了什么」。',
    quote: '把注意力放在能积累的事情上，而不是能立刻被看见的事情上。',
    status: 'DRAFT', publishedAt: null, views: 0, likes: 0
  }
]

const tasks = [
  { id: 1, title: '完成 MyOS V1.0 数据库与后端骨架', description: '新增 habit/finance/review/ai 模块', priority: 'HIGH', status: 'DONE', dueDate: TODAY },
  { id: 2, title: '每天英语学习 60 分钟', priority: 'MEDIUM', status: 'TODO', dueDate: TODAY },
  { id: 3, title: '输出一篇开发日志', priority: 'MEDIUM', status: 'TODO', dueDate: TODAY }
]

/** AI 网关配置：演示模式下由 applyDemoWrite 就地改写，所以必须提升到模块作用域 */
const aiConfig = {
  configured: false,
  available: false,
  source: 'NONE',
  baseUrl: 'https://api.deepseek.com',
  model: 'deepseek-chat',
  masked: '',
  stored: false,
  updatedAt: null as string | null
}

/** 与 aiConfig 同步，否则演示模式下配好 Key、刷新一次又变回「未启用」 */
const aiStatus = { available: false }

/** 与后端 AiConfigService.mask() 保持同一口径，演示数据才不会被识破 */
function maskKey(key: string): string {
  const k = key.trim()
  if (k.length <= 10) return '****'
  return k.slice(0, 6) + '****' + k.slice(-4)
}

export const demoData: Record<string, any> = {
  '/dashboard': {
    today: TODAY, month: MONTH,
    waterBars, levels,
    currentProjects: projects.filter(p => p.status === 'IN_PROGRESS'),
    monthlyTargets: [
      { systemCode: 'CONTENT', systemName: '内容能力', doneDays: 18, targetDays: 30, percent: 60 },
      { systemCode: 'ENGLISH', systemName: '英语与信息', doneDays: 21, targetDays: 24, percent: 88 },
      { systemCode: 'BODY', systemName: '身体', doneDays: 16, targetDays: 24, percent: 67 },
      { systemCode: 'AI', systemName: 'AI 能力', doneDays: 24, targetDays: 24, percent: 100 }
    ],
    recentKnowledge: learning.slice(0, 5).map(l => ({
      title: l.title, category: l.category, conclusion: l.conclusion, learned_at: l.learnedAt
    })),
    aiEnabled: true,
    todayTasks: { total: 3, done: 1 }
  },

  '/habit/systems': SYSTEMS.map(s => ({ code: s.code, name: s.name, unit: s.unit, dailyTarget: s.dailyTarget, icon: s.code })),
  '/habit': waterBars.map(w => ({
    systemCode: w.systemCode, systemName: w.systemName, unit: w.unit, logDate: TODAY,
    metricValue: w.value, targetValue: w.target, done: w.done, note: null, recorded: w.recorded
  })),

  '/tasks': tasks,

  '/learning': learning,
  '/contents': contents,
  '/contents/calendar': {
    month: MONTH,
    days: Array.from({ length: new Date(Number(TODAY.slice(0, 4)), Number(TODAY.slice(5, 7)), 0).getDate() }, (_, i) => ({
      day: i + 1,
      date: `${MONTH}-${String(i + 1).padStart(2, '0')}`,
      count: [2, 4, 5, 8, 9, 11, 12, 15, 16, 18, 19, 22, 24, 25, 29].includes(i + 1) ? 1 : 0,
      published: [2, 4, 5, 8, 9, 11, 12, 15, 16, 18, 19, 22, 24, 25, 29].includes(i + 1)
    })),
    publishedDays: 15, targetDays: 30
  },

  '/projects': projects,
  '/projects/portfolio': [
    {
      year: new Date().getFullYear(),
      items: [{ month: new Date().getMonth() + 1, projects: projects.filter(p => p.status === 'DONE') }]
    }
  ],
  '/projects/1': {
    project: projects[0],
    logs: [
      { id: 1, projectId: 1, logDate: TODAY, content: '完成 7 系统打点与首页水位条联动', commitRef: 'a1b2c3d' },
      { id: 2, projectId: 1, logDate: TODAY, content: '重写 Dashboard 聚合接口', commitRef: 'e4f5g6h' }
    ]
  },

  '/goals/tree': [
    {
      id: 1, title: '掌握 AI 编程并完成 12 个项目', level: 'YEAR', systemCode: 'TECH',
      status: 'ACTIVE', progress: 35,
      children: [{
        id: 2, title: '完成私人工作台 V1.0', level: 'MONTH', systemCode: 'TECH',
        status: 'ACTIVE', progress: 70, children: []
      }]
    }
  ],

  '/finance/overview': {
    month: MONTH, income: 20000, expense: 267.5, balance: 19732.5,
    savingsRate: 98.7, targetRate: 30,
    expenseByCategory: [
      { category: '餐饮', total: 68.5 },
      { category: '学习', total: 199 }
    ],
    trend: [{ month: MONTH, income: 20000, expense: 267.5 }]
  },
  '/finance/records': [
    { id: 1, recordDate: MONTH + '-01', recordType: 'INCOME', category: '工资', amount: 20000, currency: 'CNY', note: '本月收入' },
    { id: 2, recordDate: TODAY, recordType: 'EXPENSE', category: '餐饮', amount: 68.5, currency: 'CNY', note: '午饭' },
    { id: 3, recordDate: TODAY, recordType: 'EXPENSE', category: '学习', amount: 199, currency: 'CNY', note: '订阅课程' }
  ],
  '/finance/assets': {
    latest: { id: 1, snapshotMonth: MONTH + '-01', cash: 50000, cnyAssets: 120000, usdAssets: 30000, otherAssets: 10000, note: '本月资产快照' },
    snapshots: [
      { id: 1, snapshotMonth: MONTH + '-01', cash: 50000, cnyAssets: 120000, usdAssets: 30000, otherAssets: 10000 }
    ]
  },

  '/review/current': {
    reviewType: 'DAY', reviewDate: TODAY,
    periodStart: TODAY, periodEnd: TODAY,
    existing: {
      id: 1, reviewType: 'DAY', reviewDate: TODAY,
      goodThings: '把首页的信息架构重排成「今日 / 长期积累 / 沉淀与系统」三块，终于能一眼看出今天该做什么。',
      badThings: '上午先刷了半小时信息流才开始干活，进入状态被推迟了。',
      wastedTime: '早上的碎片时间被社交媒体吃掉，没有用来读那篇存了三天的文章。',
      unfinished: '趋势图还没做，卡在后端还没有聚合接口。',
      biggestGain: '把设计令牌统一后，改一处就能影响全局，比逐页调样式省事得多。',
      nextFocus: '先给后端补一个近 30 天的聚合接口，再把趋势折线图接上。',
      metricsJson: '{"mood":4,"focus":3,"completion":75,"wasteSource":"社交媒体","taskType":"项目","tags":["输出","专注"]}'
    }
  },
  '/review': [
    {
      id: 1, reviewType: 'DAY', reviewDate: YESTERDAY,
      goodThings: '完成了 V1 后端骨架', biggestGain: '领域分包确实更好维护',
      nextFocus: '把复盘页的结构化自评补上。',
      metricsJson: '{"mood":4,"focus":4,"completion":80,"wasteSource":"社交媒体","taskType":"项目","tags":["后端","专注"]}'
    },
    {
      id: 2, reviewType: 'DAY', reviewDate: DAY_BEFORE,
      goodThings: '统一了设计令牌', biggestGain: '改一处影响全局',
      nextFocus: '补一个近 30 天的聚合接口。',
      metricsJson: '{"mood":3,"focus":3,"completion":60,"wasteSource":"拖延","taskType":"输出","tags":["设计"]}'
    }
  ],

  '/ai/status': aiStatus,
  '/ai/config': aiConfig
}

/**
 * 演示模式下让写操作对本地演示数据也生效。
 *
 * 否则用户点「保存草稿」只会弹一个成功提示、列表却纹丝不动，看起来像坏了。
 * 这里只处理已经支持增删改的模块，其余写操作仍走"静默成功"。
 *
 * 注意：这里改的是 demoData 里那个原始数组，而 Vue 的 ref 包的是它的一份代理，
 * 直接改原数组不会触发视图更新。所以调用方必须用 [...arr] 重新赋值（见 ContentView.load）。
 */
export function applyDemoWrite(url: string, method: string, body: any) {
  const [rawPath, query = ''] = (url || '').split('?')
  const path = rawPath.replace(/\/+$/, '')

  // ---- 今日任务（复盘页「把复盘变成行动」会用到）----
  if (path === '/tasks' && method === 'post') {
    tasks.unshift({
      id: Date.now(),
      title: body?.title || '',
      description: body?.description || null,
      priority: body?.priority || 'MEDIUM',
      status: 'TODO',
      dueDate: body?.dueDate || TODAY
    })
    return
  }

  // ---- AI 网关配置（首页「配置 AI 网关」表单）----
  if (path === '/ai/config' && method === 'post') {
    if (body?.reset) {
      // 与后端一致：整体清空，退回默认值
      aiConfig.configured = false
      aiConfig.available = false
      aiConfig.source = 'NONE'
      aiConfig.masked = ''
      aiConfig.baseUrl = 'https://api.deepseek.com'
      aiConfig.model = 'deepseek-chat'
      aiConfig.stored = false
    } else {
      if (body?.baseUrl) aiConfig.baseUrl = body.baseUrl
      if (body?.model) aiConfig.model = body.model
      if (body?.apiKey) {
        aiConfig.configured = true
        aiConfig.available = true
        aiConfig.source = 'DB'
        aiConfig.masked = maskKey(String(body.apiKey))
      }
      aiConfig.stored = true
    }
    aiStatus.available = aiConfig.available
    aiConfig.updatedAt = new Date().toISOString()
    return
  }

  // ---- 内容输出 ----
  if (path === '/contents' && method === 'post') {
    contents.unshift({
      id: Date.now(),
      title: body?.title || '',
      platform: body?.platform || '其他',
      content: body?.content || '',
      quote: body?.quote || null,
      status: body?.status || 'DRAFT',
      publishedAt: null,
      views: 0,
      likes: 0
    })
    return
  }

  const contentMatch = path.match(/^\/contents\/(\d+)(\/status)?$/)
  if (contentMatch) {
    const id = Number(contentMatch[1])
    const i = contents.findIndex(c => c.id === id)
    if (i < 0) return
    if (method === 'delete') {
      contents.splice(i, 1)
      return
    }
    if (contentMatch[2] && method === 'put') {
      const status = new URLSearchParams(query).get('status') || contents[i].status
      contents[i].status = status
      if (status === 'PUBLISHED') contents[i].publishedAt = new Date().toISOString()
    }
  }
}

/** 给指定路径取演示数据（按前缀匹配） */
export function mockFor(url: string): any | null {
  const path = url.split('?')[0]
  if (demoData[path] !== undefined) return demoData[path]
  // 前缀匹配，处理 /projects/1/logs 这类
  const keys = Object.keys(demoData).sort((a, b) => b.length - a.length)
  for (const k of keys) {
    if (path.startsWith(k) && k.length > 1) return demoData[k]
  }
  return null
}

/** 演示模式是否开启（默认开启，可手动关掉） */
export function demoEnabled(): boolean {
  return localStorage.getItem('myos_demo') !== 'off'
}
