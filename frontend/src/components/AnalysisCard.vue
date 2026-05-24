<script setup lang="ts">
import type { AnalysisResult } from '../types'

const props = defineProps<{
  analysis: AnalysisResult
  rawContent?: string
}>()

function displayText(): string {
  const a = props.analysis
  const text = a.detail || a.summary
  if (text && !text.trim().startsWith('{')) return text
  if (props.rawContent && !props.rawContent.trim().startsWith('{')) return props.rawContent
  return text || props.rawContent || ''
}

function sentimentClass(s?: string) {
  if (!s) return ''
  const sl = s.toLowerCase()
  if (sl.includes('bull') || sl.includes('看多') || sl.includes('积极')) return 'green'
  if (sl.includes('bear') || sl.includes('看空') || sl.includes('消极')) return 'red'
  return 'gold'
}

function sentimentLabel(s?: string) {
  if (!s) return '--'
  const sl = s.toLowerCase()
  if (sl.includes('bull') || sl.includes('看多') || sl.includes('积极')) return '看多'
  if (sl.includes('bear') || sl.includes('看空') || sl.includes('消极')) return '看空'
  if (sl.includes('neutral') || sl.includes('中性') || sl.includes('震荡')) return '中性'
  return s
}

function riskIcon(r?: string) {
  if (!r) return ''
  const rl = r.toLowerCase()
  if (rl === 'low' || rl === '低') return 'low'
  if (rl === 'high' || rl === '高') return 'high'
  return 'mid'
}

function riskLabel(r?: string) {
  if (!r) return '--'
  const rl = r.toLowerCase()
  if (rl === 'low' || rl === '低') return '低'
  if (rl === 'high' || rl === '高') return '高'
  if (rl === 'mid' || rl === '中' || rl === 'medium') return '中'
  return r
}
</script>

<template>
  <div class="analysis-card">
    <div class="analysis-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/>
        <path d="M12 7V12L15.5 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <span>Analysis Result</span>
    </div>

    <div class="analysis-metrics" v-if="analysis.sentiment || analysis.risk_level">
      <div class="metric" v-if="analysis.sentiment">
        <div class="metric-label">趋势</div>
        <div class="metric-val" :class="sentimentClass(analysis.sentiment)">
          {{ sentimentLabel(analysis.sentiment) }}
        </div>
      </div>
      <div class="metric" v-if="analysis.risk_level">
        <div class="metric-label">风险</div>
        <div class="metric-val" :class="riskIcon(analysis.risk_level)">
          {{ riskLabel(analysis.risk_level) }}
        </div>
      </div>
    </div>
    <div class="analysis-text" v-if="displayText()">
      {{ displayText() }}
    </div>
  </div>
</template>

<style scoped>
.analysis-card {
  background: var(--white);
  border: 1px solid var(--mist);
  border-radius: var(--radius);
  padding: 20px 24px;
  margin: 6px 0;
  box-shadow: var(--shadow-md);
}
.analysis-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 1.2px;
  color: var(--ash);
  text-transform: uppercase;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--mist-subtle);
}
.analysis-header svg { color: var(--accent); }
.analysis-metrics {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.metric {
  flex: 1;
  background: var(--bg);
  border-radius: var(--radius-sm);
  padding: 16px;
  border: 1px solid var(--mist-subtle);
  text-align: center;
  transition: border-color 0.2s var(--ease);
}
.metric:hover { border-color: var(--accent-veil); }
.metric-label {
  font-family: var(--mono);
  font-size: 9.5px;
  font-weight: 500;
  color: var(--ash);
  text-transform: uppercase;
  letter-spacing: 1.5px;
  margin-bottom: 8px;
}
.metric-val {
  font-family: var(--display);
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.5px;
}
.metric-val.gold { color: var(--stock-neutral); }
.metric-val.green { color: var(--stock-up); }
.metric-val.red { color: var(--stock-down); }
.metric-val.low { color: var(--stock-up); }
.metric-val.mid { color: var(--stock-neutral); }
.metric-val.high { color: var(--stock-down); }
.analysis-text {
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--charcoal);
}

@media (max-width: 768px) {
  .analysis-card { padding: 14px 16px; }
  .analysis-header { margin-bottom: 12px; padding-bottom: 10px; }
  .analysis-metrics { gap: 8px; margin-bottom: 12px; }
  .metric { padding: 12px 8px; }
  .metric-val { font-size: 20px; }
  .analysis-text { font-size: 13px; line-height: 1.7; }
}
</style>
