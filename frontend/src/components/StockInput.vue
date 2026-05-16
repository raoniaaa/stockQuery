<script setup lang="ts">
import { ref, watch } from 'vue'
import { getStockName } from '../api'

const emit = defineEmits<{
  search: [stockCode: string, stockName: string]
}>()

defineProps<{
  loading: boolean
}>()

const stockCode = ref('')
const stockName = ref('')
const nameLoading = ref(false)

let nameTimer: ReturnType<typeof setTimeout> | null = null

watch(stockCode, (val) => {
  if (nameTimer) clearTimeout(nameTimer)
  const code = val.trim()
  if (code.length >= 6) {
    nameLoading.value = true
    nameTimer = setTimeout(async () => {
      try {
        const name = await getStockName(code)
        if (name) stockName.value = name
      } catch {
        // ignore
      } finally {
        nameLoading.value = false
      }
    }, 500)
  } else {
    stockName.value = ''
  }
})

const hotStocks = [
  { code: '002423', name: '中原资本' },
  { code: '600519', name: '贵州茅台' },
  { code: '000001', name: '平安银行' },
  { code: '300750', name: '宁德时代' },
]

const handleSearch = () => {
  if (stockCode.value.trim()) {
    emit('search', stockCode.value.trim(), stockName.value.trim())
  }
}

const quickSelect = (code: string, name: string) => {
  stockCode.value = code
  stockName.value = name
  emit('search', code, name)
}
</script>

<template>
  <div class="stock-input-wrapper">
    <div class="input-row">
      <div class="input-group">
        <div class="input-field">
          <label class="field-label">股票代码</label>
          <div class="input-container">
            <span class="input-prefix">></span>
            <input
              v-model="stockCode"
              type="text"
              placeholder="002423"
              @keyup.enter="handleSearch"
              :disabled="loading"
            />
          </div>
        </div>
        <div class="input-field name-field">
          <label class="field-label">股票名称 <span class="optional">（自动填充）</span></label>
          <div class="input-container">
            <span class="input-prefix">></span>
            <input
              v-model="stockName"
              type="text"
              placeholder="输入代码后自动填充"
              @keyup.enter="handleSearch"
              :disabled="loading"
            />
            <span v-if="nameLoading" class="name-loading"></span>
          </div>
        </div>
        <button
          class="search-btn"
          @click="handleSearch"
          :disabled="loading || !stockCode.trim()"
        >
          <span v-if="!loading">查询</span>
          <span v-else class="btn-loading"></span>
        </button>
      </div>
    </div>

    <div class="hot-stocks">
      <span class="hot-label">快捷入口</span>
      <div class="hot-list">
        <button
          v-for="stock in hotStocks"
          :key="stock.code"
          class="hot-item"
          @click="quickSelect(stock.code, stock.name)"
          :disabled="loading"
        >
          <span class="hot-code">{{ stock.code }}</span>
          <span class="hot-name">{{ stock.name }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stock-input-wrapper {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 28px;
  position: relative;
  overflow: hidden;
}

.stock-input-wrapper::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent-cyan), var(--accent-blue));
}

.input-row {
  margin-bottom: 20px;
}

.input-group {
  display: flex;
  gap: 16px;
  align-items: flex-end;
}

.input-field {
  flex: 1;
}

.name-field {
  flex: 0.8;
}

.field-label {
  display: block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  letter-spacing: 2px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.optional {
  color: var(--text-muted);
  opacity: 0.5;
  font-size: 0.8rem;
}

.input-container {
  display: flex;
  align-items: center;
  background: var(--bg-primary);
  border: 1px solid var(--border);
  border-radius: 6px;
  transition: border-color 0.2s;
}

.input-container:focus-within {
  border-color: var(--accent-cyan);
  box-shadow: 0 0 0 3px rgba(6, 182, 212, 0.08);
}

.input-prefix {
  font-family: 'JetBrains Mono', monospace;
  color: var(--accent-cyan);
  padding: 0 4px 0 14px;
  font-size: 1rem;
  opacity: 0.6;
}

.name-loading {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid var(--border);
  border-top-color: var(--accent-cyan);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  margin-right: 10px;
  flex-shrink: 0;
}

input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  padding: 12px 14px 12px 6px;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
  font-size: 1.1rem;
  letter-spacing: 1px;
}

input::placeholder {
  color: var(--text-muted);
  opacity: 0.5;
}

.search-btn {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.95rem;
  font-weight: 600;
  letter-spacing: 2px;
  padding: 12px 28px;
  background: var(--accent-cyan);
  color: var(--bg-primary);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.search-btn:hover:not(:disabled) {
  background: var(--accent-blue);
  box-shadow: 0 0 20px rgba(6, 182, 212, 0.3);
  transform: translateY(-1px);
}

.search-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.btn-loading {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid var(--bg-primary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Hot Stocks */
.hot-stocks {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.hot-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.8rem;
  letter-spacing: 2px;
  color: var(--text-muted);
  white-space: nowrap;
}

.hot-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.hot-item:hover:not(:disabled) {
  border-color: var(--accent-cyan);
  background: rgba(6, 182, 212, 0.05);
}

.hot-item:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.hot-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.9rem;
  color: var(--accent-cyan);
  font-weight: 500;
}

.hot-name {
  font-size: 0.9rem;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .input-group {
    flex-direction: column;
  }
  .search-btn {
    width: 100%;
  }
  .hot-stocks {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
