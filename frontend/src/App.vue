<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios'
import StockInput from './components/StockInput.vue'
import StockDataDisplay from './components/StockDataDisplay.vue'
import AnalysisResult from './components/AnalysisResult.vue'
import StockChart from './components/StockChart.vue'
import type { StockData, Analysis, GLMAnalysisResult } from './types'
import { getStockData, analyzeStock, getAllAnalyses } from './api'

const stockData = ref<StockData[]>([])
const analyses = ref<Analysis[]>([])
const currentStockCode = ref('')
const currentStockName = ref('')
const loading = ref(false)
const analyzing = ref(false)
const error = ref('')
const latestAnalysis = ref<GLMAnalysisResult | null>(null)
const latestRecord = ref<Analysis | null>(null)
const currentTab = ref<'analysis' | 'history'>('analysis')
const historyList = ref<Analysis[]>([])
const historyLoading = ref(false)

const parseAnalysis = (content: string): GLMAnalysisResult | null => {
  try {
    const cleaned = content.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim()
    return JSON.parse(cleaned)
  } catch {
    return null
  }
}

const switchTab = async (tab: 'analysis' | 'history') => {
  currentTab.value = tab
  if (tab === 'history') {
    historyLoading.value = true
    try {
      historyList.value = await getAllAnalyses()
    } catch {
      error.value = '获取历史记录失败'
    } finally {
      historyLoading.value = false
    }
  }
}

const handleSearch = async (stockCode: string, stockName: string) => {
  error.value = ''
  stockData.value = []
  analyses.value = []
  latestAnalysis.value = null
  latestRecord.value = null
  currentStockCode.value = stockCode
  currentStockName.value = stockName

  loading.value = true
  try {
    const data = await getStockData(stockCode, 30)
    stockData.value = data
  } catch {
    error.value = '获取股票数据失败，请检查股票代码'
  } finally {
    loading.value = false
  }
}

const handleAnalyze = async () => {
  analyzing.value = true
  error.value = ''
  try {
    const result = await analyzeStock(currentStockCode.value, currentStockName.value)
    const parsed = parseAnalysis(result)
    latestAnalysis.value = parsed

    const newAnalysis: Analysis = {
      stock_code: currentStockCode.value,
      stock_name: currentStockName.value,
      analysis_type: 'ai_analysis',
      content: result,
      model_used: 'GLM-4.7-Flash',
      summary: parsed?.summary || '',
      sentiment: parsed?.sentiment || '',
      risk_level: parsed?.risk_level || '',
      created_at: new Date().toISOString()
    }
    latestRecord.value = newAnalysis
    analyses.value.unshift(newAnalysis)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 429) {
      error.value = e.response.data?.error || '请求太频繁，请稍后再试'
    } else {
      error.value = 'AI 分析失败，请稍后重试'
    }
  } finally {
    analyzing.value = false
  }
}
</script>

<template>
  <div class="app-container">
    <!-- Header -->
    <header class="app-header">
      <div class="header-inner">
        <div class="header-left">
          <div class="logo-mark">
            <span class="logo-icon">&#9670;</span>
            <span class="logo-pulse"></span>
          </div>
          <div class="header-text">
            <h1>StockPulse</h1>
            <span class="header-sub">AI 智能股票分析</span>
          </div>
        </div>
        <div class="header-right">
          <span class="status-dot"></span>
          <span class="status-text">在线</span>
        </div>
      </div>
    </header>

    <!-- Tab Bar -->
    <div class="tab-bar">
      <div class="tab-bar-inner">
        <button
          class="tab-btn"
          :class="{ active: currentTab === 'analysis' }"
          @click="switchTab('analysis')"
        >
          <span class="tab-icon">&#9670;</span>
          分析查询
        </button>
        <button
          class="tab-btn"
          :class="{ active: currentTab === 'history' }"
          @click="switchTab('history')"
        >
          <span class="tab-icon">&#9776;</span>
          历史记录
        </button>
      </div>
    </div>

    <main class="app-main">
      <!-- Analysis Tab -->
      <template v-if="currentTab === 'analysis'">
        <!-- Search Section -->
        <section class="search-section">
          <StockInput @search="handleSearch" :loading="loading" />
        </section>

      <!-- Error -->
      <Transition name="fade">
        <div v-if="error" class="error-banner">
          <span class="error-icon">&#9888;</span>
          {{ error }}
        </div>
      </Transition>

      <!-- Loading -->
      <Transition name="fade">
        <div v-if="loading" class="loading-overlay">
          <div class="loading-spinner">
            <div class="spinner-ring"></div>
            <span>正在获取行情数据...</span>
          </div>
        </div>
      </Transition>

      <!-- Results -->
      <Transition name="slide-up">
        <div v-if="stockData.length > 0 && !loading" class="results-grid">
          <!-- Analysis Card (Latest) -->
          <section v-if="latestAnalysis" class="analysis-hero">
            <div class="section-label">
              <span class="label-dot"></span>
              AI 分析结果 &mdash; {{ currentStockCode }}
            </div>
            <AnalysisResult :analysis="latestAnalysis" :record="latestRecord" :stockName="currentStockName" />
          </section>

          <!-- Analyze Button -->
          <section class="action-bar" v-if="currentStockCode">
            <div class="action-group">
              <button
                class="analyze-btn"
                @click="handleAnalyze"
                :disabled="analyzing"
                :class="{ 'is-analyzing': analyzing }"
              >
                <span v-if="!analyzing" class="btn-content">
                  <span class="btn-icon">&#9881;</span>
                  开始 AI 分析
                </span>
                <span v-else class="btn-content">
                  <span class="btn-spinner"></span>
                  分析中...
                </span>
              </button>
              <span class="rate-hint">每分钟最多 5 次分析</span>
            </div>
          </section>

          <!-- Trend Chart -->
          <section class="chart-section">
            <div class="section-label">
              <span class="label-dot"></span>
              价格走势 &mdash; {{ currentStockName || currentStockCode }}
            </div>
            <StockChart :data="stockData" />
          </section>

          <!-- K-Line Data -->
          <section class="data-section">
            <div class="section-label">
              <span class="label-dot"></span>
              行情数据 &mdash; {{ currentStockName || currentStockCode }}
            </div>
            <StockDataDisplay :data="stockData" />
          </section>

          <!-- History -->
          <section v-if="analyses.length > 1" class="history-section">
            <div class="section-label">
              <span class="label-dot"></span>
              分析历史记录
            </div>
            <div class="history-list">
              <div
                v-for="(item, index) in analyses.slice(1)"
                :key="item.id || index"
                class="history-item"
              >
                <div class="history-meta">
                  <span class="history-model">{{ item.model_used }}</span>
                  <span class="history-date">{{ item.created_at ? new Date(item.created_at).toLocaleString('zh-CN') : '-' }}</span>
                </div>
                <div class="history-content">{{ parseAnalysis(item.content)?.summary || item.content }}</div>
              </div>
            </div>
          </section>
        </div>
      </Transition>
      </template>

      <!-- History Tab -->
      <template v-if="currentTab === 'history'">
        <section class="history-hero">
          <div class="section-label">
            <span class="label-dot"></span>
            分析历史记录
          </div>
          <div v-if="historyLoading" class="loading-overlay" style="padding:40px 0">
            <div class="loading-spinner">
              <div class="spinner-ring"></div>
              <span>正在加载历史记录...</span>
            </div>
          </div>
          <div v-else-if="historyList.length === 0" class="history-empty">
            暂无分析记录，去「分析查询」开始你的第一次分析
          </div>
          <div v-else class="history-table">
            <div class="history-table-header">
              <span class="col-code">股票代码</span>
              <span class="col-name">股票名称</span>
              <span class="col-summary">分析摘要</span>
              <span class="col-sentiment">情绪</span>
              <span class="col-risk">风险</span>
              <span class="col-model">模型</span>
              <span class="col-time">分析时间</span>
            </div>
            <div
              v-for="item in historyList"
              :key="item.id"
              class="history-table-row"
            >
              <span class="col-code">{{ item.stock_code }}</span>
              <span class="col-name">{{ item.stock_name || '-' }}</span>
              <span class="col-summary">{{ item.summary || parseAnalysis(item.content)?.summary || '-' }}</span>
              <span class="col-sentiment">
                <span :class="['sentiment-tag', item.sentiment === 'Bullish' ? 'bullish' : item.sentiment === 'Bearish' ? 'bearish' : 'neutral']">
                  {{ item.sentiment === 'Bullish' ? '看涨' : item.sentiment === 'Bearish' ? '看跌' : '中性' }}
                </span>
              </span>
              <span class="col-risk">{{ item.risk_level || '-' }}</span>
              <span class="col-model">{{ item.model_used }}</span>
              <span class="col-time">{{ item.created_at ? item.created_at.replace('T', ' ').slice(0, 19).replace(/-/g, '/') : '-' }}</span>
            </div>
          </div>
        </section>
      </template>
    </main>

    <footer class="app-footer">
      <span class="footer-warning">&#9888;</span>
      股市有风险，投资需谨慎。以上分析仅用于学习识别规则，不构成任何买卖建议。
    </footer>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  position: relative;
}

/* Header */
.app-header {
  border-bottom: 1px solid var(--border);
  background: rgba(10, 14, 23, 0.85);
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-mark {
  position: relative;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-icon {
  font-size: 20px;
  color: var(--accent-cyan);
  filter: drop-shadow(0 0 8px rgba(6, 182, 212, 0.5));
  z-index: 1;
}

.logo-pulse {
  position: absolute;
  inset: 0;
  border: 1px solid var(--accent-cyan);
  border-radius: 6px;
  opacity: 0.3;
  animation: logo-pulse 3s ease-in-out infinite;
}

@keyframes logo-pulse {
  0%, 100% { transform: scale(1); opacity: 0.3; }
  50% { transform: scale(1.15); opacity: 0.1; }
}

.header-text h1 {
  font-family: 'JetBrains Mono', monospace;
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--text-primary);
  text-transform: uppercase;
}

.header-sub {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8rem;
  color: var(--text-muted);
  letter-spacing: 3px;
  text-transform: uppercase;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-green);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
  animation: blink 2s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  color: var(--accent-green);
  letter-spacing: 2px;
}

/* Tab Bar */
.tab-bar {
  border-bottom: 1px solid var(--border);
  background: rgba(10, 14, 23, 0.6);
}

.tab-bar-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  gap: 4px;
}

.tab-btn {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.95rem;
  letter-spacing: 1px;
  padding: 16px 24px;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 2px solid transparent;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-btn:hover {
  color: var(--text-secondary);
}

.tab-btn.active {
  color: var(--accent-cyan);
  border-bottom-color: var(--accent-cyan);
}

.tab-icon {
  font-size: 1rem;
}

/* Main */
.app-main {
  max-width: 1400px;
  margin: 0 auto;
  padding: 32px;
}

.search-section {
  margin-bottom: 32px;
}

/* Error */
.error-banner {
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 8px;
  padding: 14px 20px;
  margin-bottom: 24px;
  color: var(--accent-amber);
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: 10px;
}

.error-icon {
  font-size: 1.3rem;
  flex-shrink: 0;
}

/* Loading */
.loading-overlay {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 1rem;
  letter-spacing: 1px;
}

.spinner-ring {
  width: 32px;
  height: 32px;
  border: 2px solid var(--border);
  border-top-color: var(--accent-cyan);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Results Grid */
.results-grid {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

/* Section Labels */
.section-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  letter-spacing: 3px;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.label-dot {
  width: 5px;
  height: 5px;
  background: var(--accent-cyan);
  border-radius: 50%;
}

/* Action Bar */
.action-bar {
  display: flex;
  justify-content: center;
}

.action-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.rate-hint {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.75rem;
  color: var(--text-muted);
  letter-spacing: 1px;
}

.analyze-btn {
  font-family: 'JetBrains Mono', monospace;
  font-size: 1rem;
  font-weight: 500;
  letter-spacing: 1px;
  padding: 14px 40px;
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.12), rgba(59, 130, 246, 0.12));
  border: 1px solid var(--accent-cyan);
  color: var(--accent-cyan);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.analyze-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.15), rgba(59, 130, 246, 0.15));
  opacity: 0;
  transition: opacity 0.3s;
}

.analyze-btn:hover::before {
  opacity: 1;
}

.analyze-btn:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 0 20px rgba(6, 182, 212, 0.15);
  transform: translateY(-1px);
}

.analyze-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.analyze-btn.is-analyzing {
  border-color: var(--accent-amber);
  color: var(--accent-amber);
}

.btn-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-icon {
  font-size: 1.2rem;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid transparent;
  border-top-color: var(--accent-amber);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

/* Analysis Hero */
.analysis-hero {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 28px;
  position: relative;
  overflow: hidden;
}

.analysis-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent-cyan), var(--accent-blue), var(--accent-cyan));
}

/* Data Section */
.data-section,
.chart-section,
.history-section {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 28px;
}

/* History */
.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-item {
  padding: 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  transition: border-color 0.2s;
}

.history-item:hover {
  border-color: var(--border-active);
}

.history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.history-model {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  padding: 3px 8px;
  background: rgba(6, 182, 212, 0.1);
  color: var(--accent-cyan);
  border-radius: 4px;
  letter-spacing: 1px;
}

.history-date {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.history-content {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* History Hero (full page) */
.history-hero {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 28px;
  position: relative;
  overflow: hidden;
}

.history-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent-cyan), var(--accent-blue), var(--accent-cyan));
}

.history-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--text-muted);
  font-size: 1rem;
  letter-spacing: 1px;
}

.history-table {
  display: flex;
  flex-direction: column;
}

.history-table-header {
  display: grid;
  grid-template-columns: 90px 90px 1fr 70px 50px 100px 170px;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8rem;
  letter-spacing: 2px;
  color: var(--text-muted);
  text-transform: uppercase;
}

.history-table-row {
  display: grid;
  grid-template-columns: 90px 90px 1fr 70px 50px 100px 170px;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(30, 45, 74, 0.3);
  font-size: 0.95rem;
  color: var(--text-secondary);
  transition: background 0.2s;
  align-items: center;
}

.sentiment-tag {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8rem;
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 1px;
}

.sentiment-tag.bullish {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.sentiment-tag.bearish {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.sentiment-tag.neutral {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.history-table-row:last-child {
  border-bottom: none;
}

.history-table-row:hover {
  background: rgba(6, 182, 212, 0.03);
}

.history-table-row .col-code {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  color: var(--accent-cyan);
  letter-spacing: 1px;
}

.history-table-row .col-model {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  padding: 3px 8px;
  background: rgba(6, 182, 212, 0.1);
  color: var(--accent-cyan);
  border-radius: 4px;
  letter-spacing: 1px;
  width: fit-content;
}

.history-table-row .col-time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active {
  transition: all 0.5s ease;
}
.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

/* Footer */
.app-footer {
  text-align: center;
  padding: 24px 32px;
  border-top: 1px solid var(--border);
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 1px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.footer-warning {
  color: var(--accent-amber);
  font-size: 1rem;
}

/* Responsive */
@media (max-width: 768px) {
  .app-main {
    padding: 16px;
  }
  .header-inner {
    padding: 12px 16px;
  }
  .analysis-hero,
  .data-section,
  .chart-section,
  .history-section {
    padding: 20px 16px;
  }
  .tab-bar-inner {
    padding: 0 16px;
  }
  .history-table-header,
  .history-table-row {
    grid-template-columns: 80px 80px 1fr 60px 100px;
    font-size: 0.85rem;
    gap: 8px;
    padding: 12px;
  }
  .history-table-header .col-risk,
  .history-table-row .col-risk,
  .history-table-header .col-time,
  .history-table-row .col-time {
    display: none;
  }
}
</style>
