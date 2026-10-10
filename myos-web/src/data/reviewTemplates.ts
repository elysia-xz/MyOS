/**
 * 复盘模板库
 *
 * 设计约束：后端的复盘字段是固定的六个
 * （goodThings / badThings / wastedTime / unfinished / biggestGain / nextFocus），
 * 所以模板**只改变「问什么、怎么问」**，不新增字段。
 * 好处：切换模板不会清空已写的内容，也完全不牵动数据库。
 */

export interface TemplateField {
  /** 对应后端字段名，必须是六个之一 */
  key: string
  /** 该模板下这个字段叫什么 */
  label: string
  /** 引导问题：避免写成流水账 */
  q: string
  /** 输入框的占位提示（可选） */
  placeholder?: string
}

export interface ReviewTemplate {
  key: string
  name: string
  desc: string
  fields: TemplateField[]
}

/** 后端固定的六个字段，顺序即界面顺序 */
export const FORM_KEYS = [
  'goodThings',
  'badThings',
  'wastedTime',
  'unfinished',
  'biggestGain',
  'nextFocus'
] as const

export const REVIEW_TEMPLATES: ReviewTemplate[] = [
  {
    key: 'standard',
    name: '标准六问',
    desc: '日复盘默认结构，六个角度过一遍',
    fields: [
      { key: 'goodThings', label: '做得好的事', q: '这一段最重要的 1 件事是什么？为什么它重要？', placeholder: '写一件具体的事，别写「还行」' },
      { key: 'badThings', label: '没做好的事', q: '哪个行为导致了不理想的结果？' },
      { key: 'wastedTime', label: '浪费的时间', q: '时间主要漏在了哪里？下次怎么堵住？' },
      { key: 'unfinished', label: '未完成的事', q: '哪件事被拖下来了？卡在哪一步？' },
      { key: 'biggestGain', label: '最大收获', q: '哪个决策或方法值得复用？' },
      { key: 'nextFocus', label: '下一步重点', q: '接下来最重要的一件事是什么？', placeholder: '只写一件 —— 写完就能立刻开始的那种' }
    ]
  },
  {
    key: 'minimal',
    name: '极简三问',
    desc: '时间紧时用，三分钟写完',
    fields: [
      { key: 'goodThings', label: '今天做成了什么', q: '一句话，具体到事。', placeholder: '例如：把复盘页的模板库做完了' },
      { key: 'unfinished', label: '什么没做完', q: '卡在哪一步？' },
      { key: 'nextFocus', label: '明天第一件事', q: '只写一件，写完就能开始。', placeholder: '明天打开电脑先做的第一件事' }
    ]
  },
  {
    key: 'project',
    name: '项目复盘',
    desc: '一个项目收尾时用，看结果也看过程',
    fields: [
      { key: 'goodThings', label: '交付了什么结果', q: '哪个具体产出可以拿出来给人看？' },
      { key: 'badThings', label: '哪里出了问题', q: '哪个决策导致了返工或延期？' },
      { key: 'biggestGain', label: '可复用的经验', q: '下次做同类项目，哪一步能直接照搬？' },
      { key: 'nextFocus', label: '下一步', q: '这个项目接下来最重要的一件事？' }
    ]
  },
  {
    key: 'weekly',
    name: '周复盘',
    desc: '一周结束时用，看趋势不看细节',
    fields: [
      { key: 'goodThings', label: '本周高光', q: '这一周最值得记的一件事？' },
      { key: 'badThings', label: '本周拖累', q: '什么反复消耗了你？' },
      { key: 'wastedTime', label: '时间漏在哪', q: '哪类事吃掉了最多时间？' },
      { key: 'biggestGain', label: '本周最大收获', q: '哪个方法值得固化下来？' },
      { key: 'nextFocus', label: '下周主线', q: '下周只推进一件大事，是哪件？' }
    ]
  }
]

/** 常用标签建议，减少每次手打 */
export const TAG_SUGGESTIONS = ['专注', '拖延', '输出', '学习', '身体', '财务', '人际', '返工']
