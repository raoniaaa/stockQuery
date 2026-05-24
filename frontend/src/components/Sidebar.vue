<script setup lang="ts">
import type { Analysis } from '../types'

defineProps<{
  history: Analysis[]
  collapsed?: boolean
}>()

const emit = defineEmits<{
  select: [code: string]
  refresh: []
  kline: [code: string, name: string]
  toggle: []
}>()

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return (d.getMonth() + 1) + '/' + d.getDate() + ' ' +
    String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}

function sentimentDot(s?: string) {
  if (!s) return ''
  const sl = s.toLowerCase()
  if (sl.includes('bull') || sl.includes('看多') || sl.includes('积极')) return 'green'
  if (sl.includes('bear') || sl.includes('看空') || sl.includes('消极')) return 'red'
  return 'gold'
}
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="sidebar-head">
      <button class="sidebar-close" @click="emit('toggle')" title="关闭">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
          <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
      </button>
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/>
        <path d="M12 7V12L15.5 14.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <span>历史分析</span>
      <button class="sidebar-refresh" @click.stop="emit('refresh')" title="刷新">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
          <path d="M4 12a8 8 0 0115.2-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M20 12a8 8 0 01-15.2 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M20 4v4h-4M4 20v-4h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
    <div class="sidebar-list">
      <div v-if="history.length === 0" class="sidebar-empty">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" style="margin-bottom: 8px; opacity: 0.3;">
          <path d="M16 7H8a3 3 0 00-3 3v7a2 2 0 002 2h10a2 2 0 002-2v-7a3 3 0 00-3-3z" stroke="currentColor" stroke-width="1.5"/>
          <path d="M12 3v4M9 7h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <div>暂无历史记录</div>
      </div>
      <div
        v-for="h in history" :key="h.id"
        class="hist-item"
        @click="emit('select', h.stockCode)"
      >
        <div class="hist-top">
          <div class="hist-left">
            <span class="hist-dot" :class="sentimentDot(h.sentiment)"></span>
            <span class="hist-name" v-if="h.stockName">{{ h.stockName }}</span>
            <span class="hist-code">{{ h.stockCode }}</span>
          </div>
          <span class="hist-time">{{ formatTime(h.createdAt) }}</span>
        </div>
        <div class="hist-preview">{{ h.summary || 'AI 分析' }}</div>
        <div class="hist-actions">
          <button class="kline-btn" @click.stop="emit('kline', h.stockCode, h.stockName)">K线</button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 270px;
  background: var(--bg);
  border-left: 1px solid var(--mist);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 0.3s var(--ease), opacity 0.25s;
}
.sidebar.collapsed {
  width: 0;
  border-left: none;
  opacity: 0;
  pointer-events: none;
}
.sidebar-head {
  padding: 16px 18px;
  border-bottom: 1px solid var(--mist);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--ash);
  letter-spacing: 1.2px;
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--white);
}
.sidebar-close {
  background: none;
  border: none;
  color: var(--ash);
  cursor: pointer;
  padding: 5px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s var(--ease);
}
.sidebar-close:hover {
  color: var(--stock-down);
  background: var(--stock-down-bg);
}
.sidebar-refresh {
  margin-left: auto;
  background: none;
  border: none;
  color: var(--ash);
  cursor: pointer;
  padding: 5px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s var(--ease);
}
.sidebar-refresh:hover {
  color: var(--accent);
  background: var(--accent-whisper);
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}
.hist-item {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  margin-bottom: 4px;
  cursor: pointer;
  transition: all 0.2s var(--ease);
}
.hist-item:hover {
  background: var(--white);
  border-color: var(--mist);
  box-shadow: var(--shadow-sm);
}
.hist-item:active {
  transform: scale(0.98);
}
.hist-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.hist-left {
  display: flex;
  align-items: center;
  gap: 7px;
}
.hist-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}
.hist-dot.green { background: var(--stock-up); }
.hist-dot.red { background: var(--stock-down); }
.hist-dot.gold { background: var(--stock-neutral); }
.hist-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--ink);
}
.hist-code {
  font-family: var(--mono);
  font-size: 10px;
  color: var(--ash);
}
.hist-time {
  font-family: var(--mono);
  font-size: 9px;
  color: #d6d3d1;
}
.hist-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.kline-btn {
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: var(--radius-xs);
  border: 1px solid var(--mist);
  background: var(--white);
  color: var(--ash);
  cursor: pointer;
  transition: all 0.2s var(--ease);
}
.kline-btn:hover {
  color: var(--accent);
  border-color: var(--accent-veil);
  background: var(--accent-whisper);
}
.hist-preview {
  font-size: 11.5px;
  color: var(--ash);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sidebar-empty {
  text-align: center;
  padding: 48px 16px;
  font-size: 12px;
  color: var(--ash);
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>
