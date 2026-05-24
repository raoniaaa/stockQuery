<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import KlineChart from './components/KlineChart.vue'
import AnalysisCard from './components/AnalysisCard.vue'
import Sidebar from './components/Sidebar.vue'
import { chatStream, getStockData, getStockName, searchStock, getAllAnalyses, analyzeStock, getMarketOverview } from './api'
import type { ChatMessage, KlineData, AnalysisResult, Analysis, MarketOverview } from './types'

const input = ref('')
const messages = ref<ChatMessage[]>([])
const loading = ref(false)
const connected = ref(false)
const messagesRef = ref<HTMLDivElement>()
const inputFocused = ref(false)
const isMobile = ref(window.innerWidth < 768)
const panelKline = ref<KlineData | null>(null)
const panelVisible = ref(false)
const mobileKline = ref<KlineData | null>(null)
const mobileKlineVisible = ref(false)
let msgId = 0
const sidebarVisible = ref(false)
const histories = ref<Analysis[]>([])
let lastAnalysisMsgId: number | null = null
const marketOverview = ref<MarketOverview | null>(null)

function now() {
  const d = new Date()
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}

function scrollBottom() {
  nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight })
}

function findStockCodes(text: string): string[] {
  const matches = text.match(/\b[036]\d{5}\b/g)
  return matches ? [...new Set(matches)] : []
}

function extractStockName(text: string): string | null {
  const patterns = [
    /(?:分析|看看|查一下?|查询|看下|帮我看看|帮我查|帮我分析|关于|说说|讲讲|聊聊)\s*([^\s\u3000，。！？,]{2,8}?)(?:的|最近|怎么样|走势|行情|财务|基本面|技术面|股价|估值|K线|k线|$)/,
    /([^\s\u3000，。！？,]{2,8}?)(?:的走势|的行情|怎么样|最近走势|最近行情|的财务|的基本面|的技术面|的估值|分析一下|的K线)/,
  ]
  for (const p of patterns) {
    const m = text.match(p)
    if (m && m[1] && /^[\u4e00-\u9fa5]+$/.test(m[1])) return m[1]
  }
  return null
}

function renderText(text: string): string {
  if (!text) return ''
  let s = text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

  // Pre-process: convert || row separators to newlines
  s = s.replace(/\|\|/g, '\n')

  // Split a row into cells (handles both | A | B | and A | B formats)
  const splitCells = (row: string) =>
    row.replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => c.trim()).filter(c => c.length > 0)
  // Detect separator row: only dashes, pipes, spaces (e.g. |---|---| or ---|---)
  const isSepRow = (row: string) => {
    const t = row.trim()
    if (t.split('|').filter((c: string) => c.trim().length > 0).length < 2) return false
    return /^[|\s:>-]+$/.test(t)
  }
  // A line with ≥2 pipes (after trimming) is a potential table row
  const pipeCount = (row: string) => (row.match(/\|/g) || []).length
  const isTableRow = (row: string) => pipeCount(row) >= 2

  // Two-pass table detection:
  // 1) Find separator rows to anchor tables
  // 2) Expand up/down to include header and data rows
  const lines = s.split('\n')
  const inTable = new Set<number>()

  for (let i = 0; i < lines.length; i++) {
    if (!isSepRow(lines[i])) continue
    // Found a separator — expand upward to find header row(s)
    let start = i - 1
    while (start >= 0 && isTableRow(lines[start]) && !isSepRow(lines[start])) start--
    start++
    // Expand downward to find data row(s)
    let end = i + 1
    while (end < lines.length && isTableRow(lines[end]) && !isSepRow(lines[end])) end++
    // Mark all rows in this table range
    for (let j = start; j < end; j++) inTable.add(j)
  }

  // Render tables
  const result: string[] = []
  let i = 0
  while (i < lines.length) {
    if (inTable.has(i)) {
      // Collect all consecutive rows belonging to this table
      const tableLines: string[] = []
      while (i < lines.length && inTable.has(i)) {
        tableLines.push(lines[i]); i++
      }
      if (tableLines.length >= 2) {
        // First non-separator row is the header
        let html = '<div class="table-scroll"><table><thead><tr>'
        const headerCells = splitCells(tableLines[0])
        headerCells.forEach(c => { html += '<th>' + c + '</th>' })
        html += '</tr></thead><tbody>'
        for (let r = 1; r < tableLines.length; r++) {
          if (isSepRow(tableLines[r])) continue
          html += '<tr>'
          splitCells(tableLines[r]).forEach(c => {
            c = c.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            html += '<td>' + c + '</td>'
          })
          html += '</tr>'
        }
        html += '</tbody></table></div>'
        result.push(html); continue
      }
    }
    result.push(lines[i]); i++
  }
  s = result.join('\n')

  // Protect <table>...</table> blocks from header/list/bold transformations
  const TABLE_PH = '\x00T'
  const tableBlocks: string[] = []
  s = s.replace(/<table>[\s\S]*?<\/table>/g, m => { tableBlocks.push(m); return TABLE_PH + (tableBlocks.length - 1) })

  // Horizontal rules
  s = s.replace(/^---+$/gm, '<hr>')
  // Insert line breaks before markdown headers when inline
  s = s.replace(/([^#\n])(#{2,4})\s+/g, '$1<br>$2 ')
  // Insert line breaks before list dashes that follow text
  s = s.replace(/([^\n])[^\S\n]+- [^\S\n]*(?=[^\s])/g, '$1<br>- ')
  // Convert headers to HTML tags
  s = s.replace(/#{4}\s+([^<\n]+)/g, '<h4>$1</h4>')
  s = s.replace(/#{3}\s+([^<\n]+)/g, '<h3>$1</h3>')
  s = s.replace(/#{2}\s+([^<\n]+)/g, '<h2>$1</h2>')
  // Convert list items
  s = s.replace(/^- ([^\n]+)/gm, '<li>$1')
  s = s.replace(/((?:<br>|\n))- ([^\n]+)/g, '$1<li>$2')
  // Bold & code
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/`(.+?)`/g, '<code>$1</code>')
  // <li> is inline; <hr>/<h> are block-level (CSS margin handles spacing)
  s = s.replace(/<li>/g, '<br><li>')
  s = s.replace(/\n{2,}/g, '<br><br>')
  s = s.replace(/\n/g, '<br>')
  s = s.replace(/(<br>){3,}/g, '<br><br>')
  s = s.replace(/^(<br>)+/, '')
  s = s.replace(/(<br>)+$/, '')
  // Block-level elements manage their own spacing — strip redundant <br>
  s = s.replace(/(<br>)+<hr>/g, '<hr>')
  s = s.replace(/<hr>(<br>)+/g, '<hr>')
  s = s.replace(/(<br>)+<\/(h[234])>/g, '</$2>')
  s = s.replace(/(<br>)+<(h[234])/g, '<$2')
  s = s.replace(/<\/(h[234])>(<br>)+/g, '</$1><br>')

  // Restore table blocks
  tableBlocks.forEach((block, idx) => { s = s.replace(TABLE_PH + idx, block) })

  return s
}

async function sendQuick(text: string) { input.value = text; await send() }

async function refreshHistory() {
  try { histories.value = await getAllAnalyses() } catch {}
}

function showLatestAnalysis(code: string, name: string, analysisJson: string) {
  if (lastAnalysisMsgId !== null) {
    messages.value = messages.value.filter(m => m.id !== lastAnalysisMsgId)
    lastAnalysisMsgId = null
  }
  try {
    const parsed = JSON.parse(analysisJson)
    const msg: ChatMessage = {
      id: ++msgId, role: 'ai',
      text: `📊 ${name || code} 分析结果`,
      time: now() + ' - Agent',
      analysis: {
        summary: parsed.summary,
        detail: parsed.detail,
        sentiment: parsed.sentiment,
        risk_level: parsed.risk_level,
        stockCode: code,
        stockName: name,
      },
    }
    lastAnalysisMsgId = msg.id
    messages.value.push(msg)
    scrollBottom()
  } catch {}
}

function onHistorySelect(code: string) {
  const item = histories.value.find(h => h.stockCode === code)
  if (item) {
    const parsed = (() => { try { return JSON.parse(item.content) as AnalysisResult } catch { return null } })()
    const result: AnalysisResult = {
      summary: item.summary || parsed?.summary,
      detail: parsed?.detail || item.content,
      sentiment: item.sentiment || parsed?.sentiment,
      risk_level: item.riskLevel || parsed?.risk_level,
      stockCode: item.stockCode,
      stockName: item.stockName,
    }
    messages.value.push({
      id: ++msgId, role: 'ai', text: `📋 历史分析 — ${item.stockName || item.stockCode}`,
      time: formatTime(new Date(item.createdAt || '')) + ' - 历史记录',
      analysis: result, rawContent: item.content,
    })
    scrollBottom()
    refreshHistory()
  }
  if (isMobile.value) sidebarVisible.value = false
}

function formatTime(d: Date) {
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}

function showKlineOnMobile(kdata: KlineData) {
  if (isMobile.value) { mobileKline.value = kdata; mobileKlineVisible.value = true }
  else { panelKline.value = kdata; panelVisible.value = true }
}

async function onSidebarKline(code: string, _name: string) {
  const kdata = await fetchKline(code)
  if (kdata) showKlineOnMobile(kdata)
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  loading.value = true
  messages.value.push({ id: ++msgId, role: 'user', text, time: now() })
  scrollBottom()

  let stockCode = findStockCodes(text)[0] || ''
  let klinePromise: Promise<KlineData | null> | null = null

  if (!stockCode) {
    const name = extractStockName(text)
    if (name) { try { stockCode = await searchStock(name) } catch {} }
  }
  if (stockCode) klinePromise = fetchKline(stockCode)

  const aiMsg = reactive<ChatMessage>({ id: ++msgId, role: 'ai', text: '', time: '', thinking: true })
  messages.value.push(aiMsg)
  scrollBottom()

  try {
    let fullText = ''
    for await (const chunk of chatStream(text)) {
      fullText += chunk; aiMsg.text = fullText; aiMsg.thinking = false; scrollBottom()
    }
    if (aiMsg.thinking) aiMsg.thinking = false
    aiMsg.time = now() + ' - Agent'
    if (!stockCode) {
      stockCode = findStockCodes(fullText)[0] || ''
      if (stockCode) klinePromise = fetchKline(stockCode)
    }
    if (klinePromise) {
      const kdata = await klinePromise
      if (kdata) { aiMsg.kline = kdata; showKlineOnMobile(kdata) }
    }
    // 尝试从回复末尾提取 JSON 摘要
    const jsonIdx = fullText.lastIndexOf('{"summary"')
    if (jsonIdx >= 0) {
      // 不管 parse 是否成功，都从显示文本中剥离 JSON
      aiMsg.text = fullText.substring(0, jsonIdx).trim()
      try {
        const jsonStr = fullText.substring(jsonIdx).replace(/\s+$/, '')
        aiMsg.analysis = JSON.parse(jsonStr)
      } catch {}
    }
    if (!aiMsg.analysis && stockCode) {
      // 流式回复中没有JSON摘要时，用 analyzeStock 补充
      const stockName = aiMsg.kline?.name || ''
      analyzeStock(stockCode, stockName || undefined)
        .then(res => { refreshHistory(); showLatestAnalysis(stockCode, stockName, res) })
        .catch(() => {})
    }
  } catch {
    aiMsg.text = '网络连接失败，请检查网络后重试。'
    aiMsg.thinking = false; aiMsg.time = now()
  }
  loading.value = false; scrollBottom()
}

async function fetchKline(code: string): Promise<KlineData | null> {
  try {
    const [raw, name] = await Promise.all([getStockData(code, 30), getStockName(code)])
    if (!raw || raw.length === 0) return null
    const last = raw[raw.length - 1]; const first = raw[0]
    const change = +((((+last.close) - (+first.open)) / (+first.open)) * 100).toFixed(2)
    return { code, name: name || code, change, raw }
  } catch { return null }
}

async function checkConnection() {
  try { await getStockName('000001'); connected.value = true }
  catch { setTimeout(checkConnection, 3000) }
}

function handleResize() { isMobile.value = window.innerWidth < 768 }

async function showDailyWelcome() {
  try {
    marketOverview.value = await getMarketOverview()
  } catch {}
}

onMounted(() => {
  checkConnection()
  window.addEventListener('resize', handleResize)
  refreshHistory()
  showDailyWelcome()
})
onUnmounted(() => { window.removeEventListener('resize', handleResize) })
</script>

<template>
  <div class="app">
    <!-- HEADER -->
    <header class="header">
      <div class="header-left">
        <div class="logo-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M5 18L9.5 11.5L13 15L19 6" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <circle cx="19" cy="6" r="2.2" fill="white" fill-opacity="0.9"/>
            <path d="M15.5 6H19V9.5" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="logo-group">
          <div class="logo-text">StockGenie</div>
          <div class="logo-divider hide-mobile"></div>
          <div class="logo-sub hide-mobile">AI Research</div>
        </div>
      </div>
      <div class="header-right">
        <button class="btn-ghost" @click="sidebarVisible = !sidebarVisible" :class="{ active: sidebarVisible }">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/>
            <path d="M12 7V12L15.5 14.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>{{ sidebarVisible ? '隐藏' : '历史' }}</span>
        </button>
        <div class="status-chip" :class="{ online: connected }">
          <span class="status-dot"></span>
          <span class="status-label" v-if="connected">Mimo-v2.5</span>
          <span class="status-sep" v-if="connected">/</span>
          <span class="status-label" v-if="connected">RAG</span>
          <span class="status-sep hide-mobile" v-if="connected">/</span>
          <span class="status-label hide-mobile" v-if="connected">Tools</span>
          <span class="status-label offline-text" v-if="!connected">连接中...</span>
        </div>
      </div>
    </header>

    <div class="main">
      <div class="chat-col">
        <div class="messages" ref="messagesRef">
          <!-- WELCOME -->
          <div class="welcome" v-if="messages.length === 0">
            <h2 class="welcome-title">你好，这里是<br><em>StockGenie</em></h2>
            <p class="welcome-desc">输入股票代码或自然语言，获取专业级 AI 投研分析。支持实时行情、财务报表、K线解读与行业研究。</p>
            <div class="market-strip" v-if="marketOverview">
              <div class="market-item">
                <span class="market-label">{{ marketOverview.name }}</span>
                <span class="market-value">{{ marketOverview.current }}</span>
                <span class="market-change" :class="parseFloat(marketOverview.changePercent) >= 0 ? 'up' : 'down'">
                  {{ parseFloat(marketOverview.changePercent) >= 0 ? '+' : '' }}{{ marketOverview.changePercent }}%
                </span>
              </div>
            </div>
            <div class="quick-grid">
              <button class="quick-card" @click="sendQuick('帮我分析比亚迪最近走势')">
                <div class="quick-icon green">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                    <path d="M3 20L3 16L8 12L13 15L21 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M16 6H21V11" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
                <div class="quick-text">
                  <div class="quick-title">比亚迪走势</div>
                  <div class="quick-hint">分析最近30日K线</div>
                </div>
              </button>
              <button class="quick-card" @click="sendQuick('今天哪些行业板块涨得好')">
                <div class="quick-icon blue">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                    <rect x="3" y="3" width="7.5" height="7.5" rx="2" stroke="currentColor" stroke-width="1.5"/>
                    <rect x="13.5" y="3" width="7.5" height="4.5" rx="2" stroke="currentColor" stroke-width="1.5"/>
                    <rect x="13.5" y="10.5" width="7.5" height="10.5" rx="2" stroke="currentColor" stroke-width="1.5"/>
                    <rect x="3" y="13.5" width="7.5" height="7.5" rx="2" stroke="currentColor" stroke-width="1.5"/>
                  </svg>
                </div>
                <div class="quick-text">
                  <div class="quick-title">今日板块</div>
                  <div class="quick-hint">行业板块涨跌排行</div>
                </div>
              </button>
              <button class="quick-card" @click="sendQuick('贵州茅台的财务数据怎么样')">
                <div class="quick-icon amber">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                    <path d="M16 7H8a3 3 0 00-3 3v7a2 2 0 002 2h10a2 2 0 002-2v-7a3 3 0 00-3-3z" stroke="currentColor" stroke-width="1.5"/>
                    <path d="M12 3v4M9 7h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M10 14h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </div>
                <div class="quick-text">
                  <div class="quick-title">茅台财务</div>
                  <div class="quick-hint">财报数据深度解读</div>
                </div>
              </button>
            </div>
            <div class="feature-row">
              <span class="feature-tag">实时行情</span>
              <span class="feature-dot"></span>
              <span class="feature-tag">财务报表</span>
              <span class="feature-dot"></span>
              <span class="feature-tag">K线分析</span>
              <span class="feature-dot"></span>
              <span class="feature-tag">行业研究</span>
              <span class="feature-dot"></span>
              <span class="feature-tag">RAG 知识库</span>
            </div>
          </div>

          <!-- MESSAGES -->
          <TransitionGroup name="msg">
            <div v-for="msg in messages" :key="msg.id" class="msg" :class="msg.role">
              <div class="msg-avatar" :class="msg.role">
                <svg v-if="msg.role === 'ai'" width="15" height="15" viewBox="0 0 24 24" fill="none">
                  <path d="M12 2L4 8L12 22L20 8L12 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
                  <path d="M4 8L12 14L20 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M12 14V22" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
                <span v-else>U</span>
              </div>
              <div class="msg-body">
                <div class="msg-text" v-if="msg.text" v-html="renderText(msg.text)"></div>
                <AnalysisCard v-if="msg.analysis" :analysis="msg.analysis" :rawContent="msg.rawContent" />
                <div v-if="isMobile && msg.kline" class="kline-inline">
                  <div class="kline-inline-head">
                    <span class="kline-inline-name">{{ msg.kline.name }}</span>
                    <code class="kline-inline-code">{{ msg.kline.code }}</code>
                    <span class="kline-change" :class="msg.kline.change >= 0 ? 'up' : 'down'">{{ msg.kline.change >= 0 ? '+' : '' }}{{ msg.kline.change }}%</span>
                  </div>
                  <KlineChart :data="msg.kline.raw" :name="msg.kline.name" :code="msg.kline.code" />
                </div>
                <button v-if="!isMobile && msg.kline" class="kline-trigger" @click="panelKline = msg.kline as any; panelVisible = true">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                    <path d="M4 18V14M8 18V10M12 18V13M16 18V7M20 18V4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                  </svg>
                  查看 {{ msg.kline.name }} K线图
                </button>
                <div class="thinking" v-if="msg.thinking">
                  <div class="thinking-dots"><span></span><span></span><span></span></div>
                  <span>Agent 正在分析...</span>
                </div>
                <div class="msg-meta" v-if="msg.time">{{ msg.time }}</div>
              </div>
            </div>
          </TransitionGroup>
        </div>

        <!-- INPUT -->
        <div class="input-area">
          <div class="input-bar" :class="{ focused: inputFocused, 'has-text': input.trim() }">
            <div class="input-icon">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="1.5"/>
                <path d="M21 21L16.65 16.65" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </div>
            <input v-model="input" @keydown.enter="send" @focus="inputFocused = true" @blur="inputFocused = false" placeholder="输入股票代码或自然语言提问..." :disabled="loading" />
            <button class="send-btn" @click="send" :disabled="loading || !input.trim()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                <path d="M5 12H19M19 12L12 5M19 12L12 19" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- KLINE PANEL -->
      <Transition name="panel">
        <div v-if="!isMobile && panelVisible && panelKline" class="kline-panel">
          <div class="kline-panel-head">
            <div class="kline-panel-title">
              <div class="kline-panel-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                  <path d="M5 18V14M9 18V9M13 18V12M17 18V6M21 18V3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                </svg>
              </div>
              <div class="kline-panel-info">
                <span class="kline-panel-name">{{ panelKline.name }}</span>
                <code class="kline-panel-code">{{ panelKline.code }}</code>
              </div>
              <span class="kline-change" :class="panelKline.change >= 0 ? 'up' : 'down'">{{ panelKline.change >= 0 ? '+' : '' }}{{ panelKline.change }}%</span>
            </div>
            <button class="kline-close" @click="panelVisible = false" title="关闭">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            </button>
          </div>
          <KlineChart :data="panelKline.raw" :name="panelKline.name" :code="panelKline.code" />
        </div>
      </Transition>

      <Sidebar
        v-if="!isMobile"
        :history="histories"
        :collapsed="!sidebarVisible"
        @select="onHistorySelect"
        @refresh="refreshHistory"
        @kline="onSidebarKline"
        @toggle="sidebarVisible = !sidebarVisible"
      />
    </div>

    <!-- MOBILE SIDEBAR OVERLAY -->
    <Transition name="sidebar-overlay">
      <div v-if="isMobile && sidebarVisible" class="sidebar-overlay" @click.self="sidebarVisible = false">
        <Sidebar
          :history="histories"
          @select="onHistorySelect"
          @refresh="refreshHistory"
          @kline="onSidebarKline"
          @toggle="sidebarVisible = !sidebarVisible"
        />
      </div>
    </Transition>

    <!-- MOBILE KLINE OVERLAY -->
    <Transition name="overlay">
      <div v-if="mobileKlineVisible && mobileKline" class="mobile-overlay" @click.self="mobileKlineVisible = false">
        <div class="mobile-overlay-content">
          <div class="mobile-overlay-head">
            <span class="mobile-overlay-title">{{ mobileKline.name }} <code>{{ mobileKline.code }}</code></span>
            <span class="kline-change" :class="mobileKline.change >= 0 ? 'up' : 'down'">{{ mobileKline.change >= 0 ? '+' : '' }}{{ mobileKline.change }}%</span>
            <button class="kline-close" @click="mobileKlineVisible = false">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            </button>
          </div>
          <KlineChart :data="mobileKline.raw" :name="mobileKline.name" :code="mobileKline.code" />
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ═══ APP ═══ */
.app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
}

/* ═══ HEADER ═══ */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  height: 56px;
  border-bottom: 1px solid var(--mist);
  background: var(--white);
  flex-shrink: 0;
  position: relative;
  z-index: 20;
}
.header-left { display: flex; align-items: center; gap: 14px; }
.logo-icon {
  width: 34px; height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #9f1239, #be123c);
  display: flex; align-items: center; justify-content: center;
  color: white; flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(159, 18, 57, 0.25);
}
.logo-group { display: flex; align-items: baseline; gap: 12px; }
.logo-text {
  font-family: var(--display);
  font-size: 19px; font-weight: 600;
  color: var(--ink); letter-spacing: -0.3px;
}
.logo-divider {
  width: 1px; height: 16px;
  background: var(--mist); align-self: center;
}
.logo-sub {
  font-family: var(--mono);
  font-size: 10px; font-weight: 500;
  color: var(--ash); letter-spacing: 1.5px;
  text-transform: uppercase;
}
.header-right { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }

.btn-ghost {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: var(--radius-sm);
  background: transparent; border: 1px solid var(--mist);
  color: var(--ash); font-family: var(--mono);
  font-size: 11px; font-weight: 500; letter-spacing: 0.3px;
  cursor: pointer; transition: all 0.2s var(--ease); white-space: nowrap;
}
.btn-ghost:hover, .btn-ghost.active {
  border-color: var(--accent-veil); color: var(--accent);
  background: var(--accent-whisper);
}

.status-chip {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 5px 14px; border-radius: 20px;
  font-family: var(--mono); font-size: 10.5px; font-weight: 500;
  letter-spacing: 0.3px; border: 1px solid var(--mist);
  background: transparent; color: var(--ash);
  transition: all 0.3s var(--ease); white-space: nowrap;
}
.status-chip.online {
  border-color: var(--accent-veil); background: var(--accent-whisper); color: var(--accent);
}
.status-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--ash); flex-shrink: 0; transition: all 0.3s;
}
.status-chip.online .status-dot {
  background: var(--stock-up);
  box-shadow: 0 0 6px rgba(21,128,61,0.35);
  animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(21,128,61,0.3); }
  50% { box-shadow: 0 0 0 4px rgba(21,128,61,0); }
}
.status-label { white-space: nowrap; }
.status-sep { opacity: 0.3; }
.offline-text { color: var(--stock-down); }

/* ═══ MAIN LAYOUT ═══ */
.main { flex: 1; display: flex; overflow: hidden; min-height: 0; }
.chat-col { flex: 1; min-width: 300px; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }

/* ═══ MESSAGES ═══ */
.messages {
  flex: 1; overflow-y: auto; overflow-x: hidden;
  padding: 20px 40px 16px;
  display: flex; flex-direction: column; gap: 16px;
  scroll-behavior: smooth; -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

/* ═══ WELCOME ═══ */
.welcome {
  text-align: left; padding: 20px 0 24px;
  max-width: 640px; margin: 0 auto; width: 100%;
  animation: fadeInUp 0.6s var(--ease);
}
.welcome-badge {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 12px; border-radius: 20px;
  background: var(--accent-whisper); border: 1px solid var(--accent-veil);
  font-family: var(--mono); font-size: 10px; font-weight: 500;
  color: var(--accent); letter-spacing: 0.5px; margin-bottom: 24px;
  animation: fadeInUp 0.6s var(--ease) 0.1s both;
}
.welcome-badge-dot {
  width: 5px; height: 5px; border-radius: 50%; background: var(--accent);
}
.welcome-title {
  font-family: var(--display);
  font-size: 44px; font-weight: 600; color: var(--ink);
  line-height: 1.15; letter-spacing: -1.2px; margin-bottom: 14px;
  animation: fadeInUp 0.6s var(--ease) 0.15s both;
}
.welcome-title em {
  font-style: normal;
  background: linear-gradient(135deg, #9f1239, #be123c);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  background-clip: text;
}
.welcome-desc {
  font-size: 15px; color: var(--ash); line-height: 1.7;
  margin-bottom: 24px; max-width: 480px;
  animation: fadeInUp 0.6s var(--ease) 0.2s both;
}

/* Market Strip */
.market-strip {
  display: inline-flex; align-items: center; gap: 20px;
  padding: 14px 24px; background: var(--white);
  border: 1px solid var(--mist); border-radius: var(--radius);
  margin-bottom: 32px; animation: fadeInUp 0.6s var(--ease) 0.25s both;
  box-shadow: var(--shadow-sm);
}
.market-item { display: flex; align-items: baseline; gap: 8px; }
.market-label { font-family: var(--mono); font-size: 10.5px; color: var(--ash); letter-spacing: 0.5px; }
.market-value { font-family: var(--display); font-size: 16px; font-weight: 600; color: var(--ink); }
.market-change { font-family: var(--mono); font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: var(--radius-xs); }
.market-change.up { color: var(--stock-up); background: var(--stock-up-bg); }
.market-change.down { color: var(--stock-down); background: var(--stock-down-bg); }

/* Quick Grid */
.quick-grid {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 10px; margin-bottom: 20px;
  animation: fadeInUp 0.6s var(--ease) 0.3s both;
}
.quick-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 18px; background: var(--white);
  border: 1px solid var(--mist); border-radius: var(--radius);
  cursor: pointer; transition: all 0.25s var(--ease);
  box-shadow: var(--shadow-sm); text-align: left;
}
.quick-card:hover {
  border-color: var(--accent-veil); box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.quick-card:active { transform: translateY(0) scale(0.98); }
.quick-icon {
  width: 36px; height: 36px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.quick-icon.green { background: var(--accent-whisper); color: var(--accent); }
.quick-icon.blue { background: rgba(37, 99, 235, 0.07); color: #2563eb; }
.quick-icon.amber { background: var(--stock-neutral-bg); color: var(--stock-neutral); }
.quick-text { display: flex; flex-direction: column; gap: 2px; }
.quick-title { font-size: 13px; font-weight: 500; color: var(--ink); }
.quick-hint { font-size: 11px; color: var(--ash); }

/* Features */
.feature-row {
  display: flex; align-items: center; gap: 8px;
  animation: fadeInUp 0.6s var(--ease) 0.35s both;
}
.feature-tag {
  font-family: var(--mono); font-size: 10px; font-weight: 500;
  color: var(--ash); letter-spacing: 0.5px;
  padding: 3px 10px; border-radius: 4px;
  background: var(--bg); border: 1px solid var(--mist-subtle);
}
.feature-dot { width: 3px; height: 3px; border-radius: 50%; background: #d6d3d1; }

/* ═══ MESSAGE BUBBLES ═══ */
.msg { display: flex; gap: 14px; max-width: 760px; animation: fadeInUp 0.35s var(--ease); }
.msg.user { flex-direction: row-reverse; margin-left: auto; }
.msg-enter-active { animation: fadeInUp 0.3s var(--ease); }
.msg-avatar {
  width: 34px; height: 34px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; font-family: var(--display); margin-top: 2px;
}
.msg-avatar.user {
  background: var(--bg); color: var(--ash); border: 1px solid var(--mist);
}
.msg-avatar.ai {
  background: var(--accent-whisper); color: var(--accent); border: 1px solid var(--accent-veil);
}
.msg-body { max-width: 760px; min-width: 200px; display: flex; flex-direction: column; gap: 6px; flex: 1; overflow: hidden; }
.msg-text {
  font-size: 14px; line-height: 1.8; padding: 12px 18px;
  border-radius: var(--radius); word-break: break-word; overflow-wrap: anywhere;
  max-width: 100%; overflow: hidden;
}
.msg.user .msg-text {
  background: var(--white); border: 1px solid var(--mist);
  border-top-right-radius: 4px; color: var(--ink); box-shadow: var(--shadow-sm);
}
.msg.ai .msg-text { color: var(--charcoal); line-height: 1.85; }
.msg.ai .msg-text :deep(h2),
.msg.ai .msg-text :deep(h3),
.msg.ai .msg-text :deep(h4) {
  color: var(--ink); font-weight: 600; margin: 4px 0 6px; line-height: 1.4;
}
.msg.ai .msg-text :deep(h2) { font-size: 16px; }
.msg.ai .msg-text :deep(h3) { font-size: 14.5px; }
.msg.ai .msg-text :deep(h4) { font-size: 14px; }
.msg.ai .msg-text :deep(li) {
  display: block; padding: 3px 0 3px 18px; position: relative; line-height: 1.75;
}
.msg.ai .msg-text :deep(li)::before {
  content: ''; position: absolute; left: 4px; top: 11px;
  width: 5px; height: 5px; border-radius: 50%; background: var(--accent-veil);
}
.msg.ai .msg-text :deep(strong) { color: var(--ink); font-weight: 600; }
.msg.ai .msg-text :deep(code) {
  font-family: var(--mono); font-size: 12.5px;
  background: var(--bg); padding: 2px 7px; border-radius: var(--radius-xs);
  color: var(--accent); border: 1px solid var(--mist);
}
.msg.ai .msg-text :deep(.table-scroll) {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 8px 0;
}
.msg.ai .msg-text :deep(.table-scroll table) {
  width: 100%; border-collapse: collapse; font-size: 12.5px;
  font-family: var(--mono);
}
.msg.ai .msg-text :deep(.table-scroll th) {
  text-align: left; padding: 6px 10px; font-weight: 600; color: var(--ink);
  border-bottom: 2px solid var(--mist); white-space: nowrap;
}
.msg.ai .msg-text :deep(.table-scroll td) {
  padding: 5px 10px; border-bottom: 1px solid var(--mist-subtle); color: var(--charcoal);
  white-space: nowrap;
}
.msg.ai .msg-text :deep(.table-scroll tr:hover td) { background: var(--accent-whisper); }
.msg.ai .msg-text :deep(.table-scroll td:last-child),
.msg.ai .msg-text :deep(.table-scroll th:last-child) {
  padding-right: 16px;
}
.msg.ai .msg-text :deep(hr) {
  border: none; border-top: 1px solid var(--mist);
  margin: 12px 0;
}
.msg-meta {
  font-family: var(--mono); font-size: 10px; color: #d6d3d1; padding: 0 4px;
}
.msg-meta strong { color: var(--ash); font-weight: 500; }

/* K-Line Trigger */
.kline-trigger {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 8px 16px; border-radius: var(--radius-sm);
  background: var(--white); border: 1px solid var(--mist);
  color: var(--accent); font-family: var(--mono);
  font-size: 11.5px; font-weight: 500;
  cursor: pointer; transition: all 0.2s var(--ease);
  width: fit-content; box-shadow: var(--shadow-sm);
}
.kline-trigger:hover {
  border-color: var(--accent-veil); background: var(--accent-whisper);
  box-shadow: var(--shadow-md); transform: translateY(-1px);
}
.kline-trigger:active { transform: translateY(0) scale(0.98); }

/* K-Line Inline */
.kline-inline {
  background: var(--white); border: 1px solid var(--mist);
  border-radius: var(--radius); overflow: hidden; margin: 4px 0;
}
.kline-inline-head {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-bottom: 1px solid var(--mist-subtle);
}
.kline-inline-name { font-family: var(--display); font-size: 14px; font-weight: 600; color: var(--ink); }
.kline-inline-code {
  font-family: var(--mono); font-size: 10px; color: var(--ash);
  background: var(--bg); padding: 2px 6px; border-radius: var(--radius-xs);
}
.kline-change {
  font-family: var(--mono); font-size: 12px; font-weight: 600;
  padding: 3px 10px; border-radius: var(--radius-xs); margin-left: auto;
}
.kline-change.up { color: var(--stock-up); background: var(--stock-up-bg); }
.kline-change.down { color: var(--stock-down); background: var(--stock-down-bg); }

/* Thinking */
.thinking {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 4px; font-size: 12px; color: var(--ash);
}
.thinking-dots { display: flex; gap: 5px; }
.thinking-dots span {
  width: 5px; height: 5px; border-radius: 50%; background: var(--accent);
  animation: dotPulse 1.4s ease-in-out infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dotPulse {
  0%, 100% { opacity: 0.2; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

/* ═══ INPUT ═══ */
.input-area {
  padding: 12px 40px 24px; flex-shrink: 0;
}
.input-bar {
  display: flex; align-items: center; gap: 12px;
  background: var(--white); border: 1.5px solid var(--mist);
  border-radius: 16px; padding: 12px 12px 12px 22px;
  transition: all 0.3s var(--ease); box-shadow: var(--shadow-md);
  max-width: 760px; margin: 0 auto; min-height: 62px;
}
.input-bar.focused {
  border-color: var(--accent-veil);
  box-shadow: 0 0 0 3px var(--accent-whisper), var(--shadow-md);
}
.input-icon {
  color: #d6d3d1; flex-shrink: 0; display: flex; transition: color 0.2s;
}
.input-bar.focused .input-icon { color: var(--accent); }
.input-bar input {
  flex: 1; background: none; border: none; outline: none;
  font-size: 17px; color: var(--ink); font-family: var(--sans);
  padding: 12px 0; min-width: 0;
}
.input-bar input::placeholder { color: #d6d3d1; }
.send-btn {
  width: 44px; height: 44px; border-radius: 12px; border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #9f1239, #be123c);
  color: white; display: flex; align-items: center; justify-content: center;
  transition: all 0.25s var(--ease); flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(159, 18, 57, 0.25);
}
.send-btn:hover:not(:disabled) {
  transform: scale(1.06); box-shadow: 0 4px 16px rgba(159, 18, 57, 0.35);
}
.send-btn:active:not(:disabled) { transform: scale(0.95); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; transform: none; box-shadow: none; }

/* ═══ KLINE PANEL ═══ */
.kline-panel {
  width: 440px; flex-shrink: 0; border-left: 1px solid var(--mist);
  background: var(--white); display: flex; flex-direction: column; overflow: hidden;
}
.kline-panel-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-bottom: 1px solid var(--mist); flex-shrink: 0;
}
.kline-panel-title { display: flex; align-items: center; gap: 10px; }
.kline-panel-icon {
  width: 30px; height: 30px; border-radius: 8px;
  background: var(--accent-whisper); color: var(--accent);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.kline-panel-info { display: flex; align-items: baseline; gap: 8px; }
.kline-panel-name { font-family: var(--display); font-size: 15px; font-weight: 600; color: var(--ink); }
.kline-panel-code {
  font-family: var(--mono); font-size: 10px; color: var(--ash);
  background: var(--bg); padding: 2px 7px; border-radius: 4px;
}
.kline-close {
  width: 30px; height: 30px; border-radius: var(--radius-sm);
  border: none; background: var(--bg); color: var(--ash);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s var(--ease); flex-shrink: 0;
}
.kline-close:hover { background: var(--stock-down-bg); color: var(--stock-down); }

/* Panel transition */
.panel-enter-active, .panel-leave-active {
  transition: width 0.35s var(--ease), opacity 0.3s ease, margin 0.35s var(--ease);
}
.panel-enter-from, .panel-leave-to { width: 0; opacity: 0; margin-left: 0; }
.panel-enter-to, .panel-leave-from { width: 440px; opacity: 1; }

/* Mobile overlay */
.mobile-overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(28,25,23,0.2); backdrop-filter: blur(6px);
  display: flex; align-items: flex-end; justify-content: center;
}
.mobile-overlay-content {
  width: 100%; max-height: 85vh; background: var(--white);
  border-top-left-radius: 16px; border-top-right-radius: 16px;
  overflow-y: auto; padding-bottom: env(safe-area-inset-bottom, 0);
}
.mobile-overlay-head {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 20px; border-bottom: 1px solid var(--mist);
  position: sticky; top: 0; background: var(--white); z-index: 1;
}
.mobile-overlay-title { font-family: var(--display); font-size: 16px; font-weight: 600; color: var(--ink); flex: 1; }
.mobile-overlay-title code {
  font-family: var(--mono); font-size: 10px; color: var(--ash);
  background: var(--bg); padding: 2px 6px; border-radius: var(--radius-xs); margin-left: 6px;
}

.overlay-enter-active, .overlay-leave-active { transition: opacity 0.3s ease; }
.overlay-enter-active .mobile-overlay-content, .overlay-leave-active .mobile-overlay-content {
  transition: transform 0.3s var(--ease);
}
.overlay-enter-from, .overlay-leave-to { opacity: 0; }
.overlay-enter-from .mobile-overlay-content, .overlay-leave-to .mobile-overlay-content {
  transform: translateY(100%);
}

/* ═══ SIDEBAR OVERLAY ═══ */
.sidebar-overlay {
  position: fixed; inset: 0; z-index: 90;
  background: rgba(28,25,23,0.12); backdrop-filter: blur(4px);
}
.sidebar-overlay > :deep(.sidebar) {
  position: absolute; left: 0; top: 0; bottom: 0;
  width: 280px; max-width: 80vw;
  border-left: none; border-right: 1px solid var(--mist); z-index: 91;
}
.sidebar-overlay-enter-active, .sidebar-overlay-leave-active { transition: opacity 0.25s ease; }
.sidebar-overlay-enter-active :deep(.sidebar), .sidebar-overlay-leave-active :deep(.sidebar) {
  transition: transform 0.3s var(--ease);
}
.sidebar-overlay-enter-from, .sidebar-overlay-leave-to { opacity: 0; }
.sidebar-overlay-enter-from :deep(.sidebar), .sidebar-overlay-leave-to :deep(.sidebar) {
  transform: translateX(-100%);
}

/* ═══ RESPONSIVE ═══ */
.hide-mobile { display: none; }
@media (max-width: 768px) {
  .header { padding: 0 16px; height: 50px; }
  .logo-text { font-size: 16px; }
  .main { flex-direction: column; }
  .chat-col { min-width: 0; }
  .kline-panel { display: none; }
  .messages { padding: 12px 12px; gap: 12px; }
  .msg { gap: 8px; }
  .msg-avatar { width: 30px; height: 30px; font-size: 12px; }
  .msg-body { max-width: 100%; }
  .msg-text { font-size: 13.5px; padding: 10px 14px; line-height: 1.75; }
  .input-area { padding: 8px 12px 16px; }
  .input-bar { border-radius: 14px; min-height: 50px; padding: 6px 6px 6px 14px; }
  .input-bar input { font-size: 16px; padding: 8px 0; }
  .send-btn { width: 36px; height: 36px; border-radius: 10px; }
  .welcome { padding: 16px 0 20px; }
  .welcome-title { font-size: 30px; }
  .welcome-desc { font-size: 13px; }
  .market-strip { flex-wrap: wrap; gap: 12px; padding: 10px 16px; }
  .quick-grid { grid-template-columns: 1fr; gap: 8px; }
  .feature-row { flex-wrap: wrap; gap: 6px; }
  /* Mobile table: smaller font, scrollable wrapper */
  .msg.ai .msg-text :deep(.table-scroll table) { font-size: 11px; }
  .msg.ai .msg-text :deep(.table-scroll th),
  .msg.ai .msg-text :deep(.table-scroll td) { padding: 4px 6px; white-space: normal; word-break: break-word; }
  .msg.ai .msg-text :deep(.table-scroll td:last-child),
  .msg.ai .msg-text :deep(.table-scroll th:last-child) { padding-right: 6px; }
  .msg.ai .msg-text :deep(.table-scroll) { margin: 4px 0; }
  /* Compact lists on mobile */
  .msg.ai .msg-text :deep(li) { padding: 2px 0 2px 14px; font-size: 13px; }
  .msg.ai .msg-text :deep(h2) { font-size: 14.5px; }
  .msg.ai .msg-text :deep(h3) { font-size: 13.5px; }
  .msg.ai .msg-text :deep(h4) { font-size: 13px; }
}
@media (max-width: 480px) {
  .header { padding: 0 10px; height: 46px; }
  .logo-text { font-size: 14px; }
  .logo-icon { width: 28px; height: 28px; border-radius: 7px; }
  .messages { padding: 8px 8px; gap: 10px; }
  .msg-avatar { width: 26px; height: 26px; font-size: 10px; border-radius: 8px; }
  .msg-text { font-size: 13px; padding: 8px 10px; }
  .input-area { padding: 6px 8px 12px; }
  .input-bar { min-height: 44px; padding: 4px 4px 4px 10px; border-radius: 12px; }
  .input-bar input { font-size: 15px; padding: 6px 0; }
  .send-btn { width: 32px; height: 32px; border-radius: 8px; }
  .welcome-title { font-size: 24px; }
  .welcome-desc { font-size: 12px; }
  .quick-grid { gap: 6px; }
  .quick-card { padding: 10px; }
  .market-strip { gap: 8px; padding: 8px 10px; }
}
</style>
