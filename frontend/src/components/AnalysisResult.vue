<script setup lang="ts">
import { computed } from 'vue'
import type { GLMAnalysisResult, Analysis } from '../types'

const props = defineProps<{
  analysis: GLMAnalysisResult | null
  record?: Analysis | null
  stockName?: string
}>()

const summary = computed(() => props.record?.summary || props.analysis?.summary || '')
const sentiment = computed(() => props.record?.sentiment || props.analysis?.sentiment || '')
const riskLevel = computed(() => props.record?.risk_level || props.analysis?.risk_level || '')
const detail = computed(() => props.analysis?.detail || '')
const model = computed(() => props.record?.model_used || 'GLM-4.7-Flash')

const sentimentConfig: Record<string, { label: string; color: string; bg: string }> = {
  Bullish: { label: '看涨', color: '#10b981', bg: 'rgba(16, 185, 129, 0.1)' },
  Neutral: { label: '中性', color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.1)' },
  Bearish: { label: '看跌', color: '#ef4444', bg: 'rgba(239, 68, 68, 0.1)' },
}

const riskConfig: Record<string, { color: string }> = {
  '低': { color: '#10b981' },
  '中': { color: '#f59e0b' },
  '高': { color: '#ef4444' },
}
</script>

<template>
  <div class="analysis-card">
    <!-- Summary -->
    <div class="summary-row">
      <p class="summary-text">{{ summary }}</p>
    </div>

    <!-- Metrics Row -->
    <div class="metrics-row">
      <!-- Sentiment -->
      <div class="metric-block">
        <span class="metric-label">市场情绪</span>
        <div
          class="sentiment-badge"
          :style="{
            color: sentimentConfig[sentiment]?.color,
            background: sentimentConfig[sentiment]?.bg,
            borderColor: sentimentConfig[sentiment]?.color + '40'
          }"
        >
          <span
            class="sentiment-dot"
            :style="{ background: sentimentConfig[sentiment]?.color }"
          ></span>
          {{ sentimentConfig[sentiment]?.label || sentiment }}
        </div>
      </div>

      <!-- Risk Level -->
      <div class="metric-block">
        <span class="metric-label">风险等级</span>
        <div class="risk-display">
          <div class="risk-bars">
            <div
              v-for="level in ['低', '中', '高']"
              :key="level"
              class="risk-bar"
              :class="{ active: level === riskLevel }"
              :style="{
                background: level === riskLevel
                  ? riskConfig[level]?.color
                  : 'var(--border)',
              }"
            ></div>
          </div>
          <span
            class="risk-text"
            :style="{ color: riskConfig[riskLevel]?.color }"
          >
            {{ riskLevel }}风险
          </span>
        </div>
      </div>

      <!-- Model -->
      <div class="metric-block">
        <span class="metric-label">分析模型</span>
        <span class="model-text">{{ model }}</span>
      </div>
    </div>

    <!-- Detail -->
    <div v-if="detail" class="detail-section">
      <span class="detail-label">详细分析</span>
      <p class="detail-text">{{ detail }}</p>
    </div>
  </div>
</template>

<style scoped>
.analysis-card {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.summary-row {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border);
}

.summary-text {
  font-size: 1.4rem;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.7;
}

/* Metrics */
.metrics-row {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}

.metric-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8rem;
  letter-spacing: 3px;
  color: var(--text-muted);
}

.sentiment-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid;
  font-family: 'JetBrains Mono', monospace;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 2px;
}

.sentiment-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.85); }
}

.risk-display {
  display: flex;
  align-items: center;
  gap: 12px;
}

.risk-bars {
  display: flex;
  gap: 4px;
  align-items: flex-end;
}

.risk-bar {
  width: 8px;
  border-radius: 2px;
  transition: all 0.3s;
}

.risk-bar:nth-child(1) { height: 10px; }
.risk-bar:nth-child(2) { height: 16px; }
.risk-bar:nth-child(3) { height: 22px; }

.risk-bar.active {
  box-shadow: 0 0 8px currentColor;
}

.risk-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 1rem;
  font-weight: 500;
}

.model-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 1rem;
  color: var(--accent-cyan);
  padding: 8px 0;
}

/* Detail */
.detail-section {
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

.detail-label {
  display: block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8rem;
  letter-spacing: 3px;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.detail-text {
  font-size: 1.05rem;
  color: var(--text-secondary);
  line-height: 1.8;
}

@media (max-width: 768px) {
  .metrics-row {
    gap: 20px;
  }
}
</style>
