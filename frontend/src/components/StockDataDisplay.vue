<script setup lang="ts">
import { computed } from 'vue'
import type { StockData } from '../types'

const props = defineProps<{
  data: StockData[]
}>()

const displayData = computed(() => {
  return [...props.data].reverse().map(item => ({
    ...item,
    change: ((parseFloat(item.close) - parseFloat(item.open)) / parseFloat(item.open) * 100).toFixed(2),
    isUp: parseFloat(item.close) >= parseFloat(item.open)
  }))
})

const stats = computed(() => {
  if (props.data.length === 0) return null
  const closes = props.data.map(d => parseFloat(d.close))
  return {
    latest: closes[closes.length - 1]?.toFixed(2),
    highest: Math.max(...closes.map((_, i) => parseFloat(props.data[i].high))).toFixed(2),
    lowest: Math.min(...closes.map((_, i) => parseFloat(props.data[i].low))).toFixed(2),
    avgVolume: (props.data.reduce((sum, d) => sum + parseInt(d.volume), 0) / props.data.length / 10000).toFixed(0),
  }
})
</script>

<template>
  <div class="stock-data">
    <!-- Stats Bar -->
    <div v-if="stats" class="stats-bar">
      <div class="stat-item">
        <span class="stat-label">最新价</span>
        <span class="stat-value">{{ stats.latest }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">最高价</span>
        <span class="stat-value up">{{ stats.highest }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">最低价</span>
        <span class="stat-value down">{{ stats.lowest }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">平均成交量</span>
        <span class="stat-value">{{ stats.avgVolume }}万</span>
      </div>
    </div>

    <!-- Table -->
    <div class="table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>日期</th>
            <th class="num">开盘</th>
            <th class="num">最高</th>
            <th class="num">最低</th>
            <th class="num">收盘</th>
            <th class="num">成交量</th>
            <th class="num">涨跌幅</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in displayData" :key="item.day">
            <td class="date-cell">{{ item.day }}</td>
            <td class="num">{{ item.open }}</td>
            <td class="num">{{ item.high }}</td>
            <td class="num">{{ item.low }}</td>
            <td class="num close-cell">{{ item.close }}</td>
            <td class="num vol-cell">{{ (parseInt(item.volume) / 10000).toFixed(0) }}万</td>
            <td class="num change-cell" :class="item.isUp ? 'up' : 'down'">
              {{ item.isUp ? '+' : '' }}{{ item.change }}%
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.stock-data {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Stats Bar */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.75rem;
  letter-spacing: 2px;
  color: var(--text-muted);
}

.stat-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-value.up { color: var(--accent-green); }
.stat-value.down { color: var(--accent-red); }

.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--border);
}

/* Table */
.table-wrapper {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.95rem;
}

.data-table thead {
  background: var(--bg-secondary);
  position: sticky;
  top: 0;
}

.data-table th {
  padding: 12px 16px;
  text-align: left;
  font-weight: 500;
  font-size: 0.8rem;
  letter-spacing: 2px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border);
}

.data-table th.num {
  text-align: right;
}

.data-table td {
  padding: 11px 16px;
  border-bottom: 1px solid rgba(30, 45, 74, 0.5);
  color: var(--text-secondary);
}

.data-table td.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.data-table tbody tr {
  transition: background 0.15s;
}

.data-table tbody tr:hover {
  background: rgba(6, 182, 212, 0.03);
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.date-cell {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.close-cell {
  color: var(--text-primary);
  font-weight: 500;
}

.vol-cell {
  color: var(--text-muted);
}

.change-cell.up {
  color: var(--accent-green);
}

.change-cell.down {
  color: var(--accent-red);
}

@media (max-width: 768px) {
  .stats-bar {
    gap: 16px;
    padding: 12px 16px;
  }
  .stat-divider {
    display: none;
  }
  .data-table th,
  .data-table td {
    padding: 8px 10px;
  }
}
</style>
