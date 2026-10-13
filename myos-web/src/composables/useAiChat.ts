import { ref } from 'vue'
import { api } from '../api'

/**
 * AI 对话状态（二期：问答 + 带确认的工具调用）。
 *
 * 为什么单独抽成 composable：对话状态要放在**不会被首页刷新卸载**的地方。
 * 之前 AiActionPanel 把生成结果存在自己身上，点一次生成就触发首页 load()，
 * loading=true 让整个内容区卸载重建，组件实例连同步没，界面看着就是
 * 「内容生成了但正文一片空白，而且没有任何报错」。
 * 现在由调用方（首页）持有本 composable，面板只负责渲染与派发。
 */

export type ChatRole = 'user' | 'assistant' | 'tool'

export type ChatMsg = {
  role: ChatRole
  content: string
  /** assistant 消息携带的调用意图，PENDING 时用于渲染确认卡片 */
  toolCalls?: any[]
  toolCallId?: string
  createdAt?: string
}

export type PendingCall = {
  id: string
  name: string
  label: string
  risk: 'READ' | 'WRITE'
  arguments: string
}

export function useAiChat(onWritten?: () => void) {
  const msgs = ref<ChatMsg[]>([])
  const convId = ref<number | null>(null)
  const pending = ref<PendingCall[]>([])
  const busy = ref(false)
  const err = ref('')
  const conversations = ref<any[]>([])
  const historyOpen = ref(false)
  const loadingHistory = ref(false)

  /** 模型返回的 arguments 是「字符串里套 JSON」，摊开给确认卡片显示 */
  function prettyArgs(raw: string): { key: string; value: string }[] {
    try {
      const o = JSON.parse(raw || '{}')
      return Object.entries(o).map(([key, value]) => ({
        key,
        value: typeof value === 'string' ? value : JSON.stringify(value)
      }))
    } catch {
      return []
    }
  }

  /** 把一次 /chat 或 /tool/execute 的返回落到界面上 */
  function applyTurn(d: any) {
    if (!d) return
    if (d.conversationId) convId.value = Number(d.conversationId)
    const calls: any[] = Array.isArray(d.toolCalls) ? d.toolCalls : []
    if (d.status === 'PENDING' && calls.length) {
      if (d.content) msgs.value.push({ role: 'assistant', content: d.content })
      pending.value = calls
      return
    }
    if (d.content) msgs.value.push({ role: 'assistant', content: d.content })
    pending.value = []
  }

  function fail(msg: string) {
    err.value = msg
  }

  /**
   * 从库里重读整段对话。
   *
   * 执行完必须调它：tool 结果消息由后端落库，前端本地是猜不出来的
   * （尤其是「只确认了一部分」时剩下那些会被后端自动补成取消记录）。
   * 以前靠本地乐观追加，界面上就少了「已执行」这一行，
   * 用户看不到系统到底做了什么 —— 对写操作来说这是不能省的反馈。
   */
  async function reload() {
    if (convId.value == null) return
    try {
      const res: any = await api.aiConversation(convId.value)
      const body = res?.data
      if (!body?.success || !Array.isArray(body.data)) return
      msgs.value = body.data.map((m: any) => ({
        role: m.role,
        content: m.content || '',
        toolCalls: m.toolCalls || undefined,
        toolCallId: m.toolCallId || undefined,
        createdAt: m.createdAt || undefined
      }))
    } catch {
      // 读失败就保留本地那份，至少用户还看得到自己刚说的话
    }
  }

  async function send(text: string) {
    const t = (text || '').trim()
    if (!t || busy.value) return
    busy.value = true
    err.value = ''
    msgs.value.push({ role: 'user', content: t })
    try {
      const res: any = await api.aiChat({ conversationId: convId.value, message: t })
      const body = res?.data
      if (!body?.success) {
        fail(body?.message || 'AI 调用失败')
        return
      }
      applyTurn(body.data)
    } catch (e: any) {
      fail(e?.response?.data?.message || e?.message || 'AI 调用失败')
    } finally {
      busy.value = false
    }
  }

  async function confirm(ids: string[]) {
    if (busy.value || !ids.length) return
    busy.value = true
    err.value = ''
    try {
      const res: any = await api.aiToolExecute({
        conversationId: convId.value as number,
        calls: ids.map(id => ({ id }))
      })
      const body = res?.data
      if (!body?.success) {
        fail(body?.message || '执行失败')
        return
      }
      pending.value = []
      applyTurn(body.data)
      await reload()
      // 写操作可能改变了任务/打点/记账，让首页数字跟上（静默刷新，
      // 否则内容区卸载重建，这段对话就白聊了）
      onWritten?.()
    } catch (e: any) {
      fail(e?.response?.data?.message || e?.message || '执行失败')
    } finally {
      busy.value = false
    }
  }

  async function cancel(ids: string[]) {
    if (busy.value || !ids.length) return
    busy.value = true
    err.value = ''
    try {
      const res: any = await api.aiToolCancel({
        conversationId: convId.value as number,
        calls: ids.map(id => ({ id }))
      })
      const body = res?.data
      if (!body?.success) {
        fail(body?.message || '取消失败')
        return
      }
      pending.value = []
      await reload()
    } catch (e: any) {
      fail(e?.response?.data?.message || e?.message || '取消失败')
    } finally {
      busy.value = false
    }
  }

  function startNew() {
    msgs.value = []
    convId.value = null
    pending.value = []
    err.value = ''
    historyOpen.value = false
  }

  async function openHistory() {
    historyOpen.value = !historyOpen.value
    if (!historyOpen.value || loadingHistory.value) return
    loadingHistory.value = true
    try {
      const res: any = await api.aiConversations()
      const body = res?.data
      conversations.value = body?.success && Array.isArray(body.data) ? body.data : []
    } catch {
      conversations.value = []
    } finally {
      loadingHistory.value = false
    }
  }

  async function openConversation(id: number) {
    if (busy.value) return
    busy.value = true
    err.value = ''
    try {
      const res: any = await api.aiConversation(id)
      const body = res?.data
      if (!body?.success || !Array.isArray(body.data)) {
        fail(body?.message || '读取历史失败')
        return
      }
      convId.value = id
      historyOpen.value = false
      msgs.value = body.data.map((m: any) => ({
        role: m.role,
        content: m.content || '',
        toolCalls: m.toolCalls || undefined,
        toolCallId: m.toolCallId || undefined,
        createdAt: m.createdAt || undefined
      }))
      // 最后一条 assistant 若还挂着未回填的调用，说明上次停在待确认，
      // 重新摊开让用户可以接着确认（刷新页面不该把待办弄丢）
      const last = [...msgs.value].reverse().find(m => m.role === 'assistant' && m.toolCalls?.length)
      const answered = new Set(
        msgs.value.filter(m => m.role === 'tool').map(m => m.toolCallId)
      )
      const unanswered = (last?.toolCalls || []).filter((c: any) => !answered.has(c.id))
      pending.value = unanswered.length ? unanswered : []
    } catch (e: any) {
      fail(e?.response?.data?.message || e?.message || '读取历史失败')
    } finally {
      busy.value = false
    }
  }

  return {
    msgs,
    convId,
    pending,
    busy,
    err,
    conversations,
    historyOpen,
    loadingHistory,
    prettyArgs,
    send,
    confirm,
    cancel,
    startNew,
    reload,
    openHistory,
    openConversation
  }
}
