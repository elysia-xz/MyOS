/**
 * 项目状态机：未开始 → 进行中 → 待复盘 → 已完成。
 *
 * 「待复盘」不是数据库里的新状态，而是由现有数据派生出来的
 * （进度已满但状态还没标完成）——这样前端就能呈现完整状态机，
 * 不需要后端加字段、加迁移。
 */
export const PROJECT_STAGES = [
  { key: 'PLANNED', label: '未开始' },
  { key: 'IN_PROGRESS', label: '进行中' },
  { key: 'REVIEW', label: '待复盘' },
  { key: 'DONE', label: '已完成' }
] as const

export type ProjectStageKey = (typeof PROJECT_STAGES)[number]['key']

const STAGE_CLS: Record<ProjectStageKey, string> = {
  PLANNED: 'plan',
  IN_PROGRESS: 'doing',
  REVIEW: 'review',
  DONE: 'done'
}

export function projectStage(p: { status?: string; progress?: number }): ProjectStageKey {
  if (p.status === 'DONE') return 'DONE'
  if ((p.progress || 0) >= 100) return 'REVIEW'
  if ((p.progress || 0) > 0 || p.status === 'IN_PROGRESS') return 'IN_PROGRESS'
  return 'PLANNED'
}

/** 给项目补上状态机所需的展示字段（阶段 key / 序号 / 中文名 / chip 样式） */
export function withStage<T extends { status?: string; progress?: number }>(p: T) {
  const stage = projectStage(p)
  const idx = PROJECT_STAGES.findIndex(s => s.key === stage)
  const meta = PROJECT_STAGES[idx] ?? PROJECT_STAGES[1]
  return { ...p, stage, idx, stageLabel: meta.label, stageCls: STAGE_CLS[stage] }
}
