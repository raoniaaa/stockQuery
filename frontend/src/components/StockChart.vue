<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { Chart, registerables } from 'chart.js'
import type { StockData } from '../types'

Chart.register(...registerables)

const props = defineProps<{
  data: StockData[]
}>()

const canvasRef = ref<HTMLCanvasElement>()
let chartInstance: Chart | null = null

const isUp = (item: StockData) => parseFloat(item.close) >= parseFloat(item.open)

const createChart = () => {
  if (!canvasRef.value || props.data.length === 0) return

  if (chartInstance) {
    chartInstance.destroy()
  }

  const labels = props.data.map(d => {
    const parts = d.day.split('-')
    return parts.length === 3 ? parts[1] + '/' + parts[2] : d.day
  })
  const closes = props.data.map(d => parseFloat(d.close))
  const highs = props.data.map(d => parseFloat(d.high))
  const lows = props.data.map(d => parseFloat(d.low))
  const volumes = props.data.map(d => parseInt(d.volume))

  const firstClose = closes[0]
  const lastClose = closes[closes.length - 1]
  const isOverallUp = lastClose >= firstClose
  const lineColor = isOverallUp ? '#10b981' : '#ef4444'
  const fillColor = isOverallUp ? 'rgba(16, 185, 129, 0.08)' : 'rgba(239, 68, 68, 0.08)'

  chartInstance = new Chart(canvasRef.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: '收盘价',
          data: closes,
          borderColor: lineColor,
          backgroundColor: fillColor,
          borderWidth: 2,
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: lineColor,
          pointHoverBorderColor: '#fff',
          pointHoverBorderWidth: 2,
          yAxisID: 'y',
        },
        {
          label: '最高价',
          data: highs,
          borderColor: 'rgba(245, 158, 11, 0.3)',
          borderWidth: 1,
          borderDash: [4, 4],
          fill: false,
          tension: 0.3,
          pointRadius: 0,
          pointHoverRadius: 3,
          yAxisID: 'y',
        },
        {
          label: '最低价',
          data: lows,
          borderColor: 'rgba(99, 102, 241, 0.3)',
          borderWidth: 1,
          borderDash: [4, 4],
          fill: false,
          tension: 0.3,
          pointRadius: 0,
          pointHoverRadius: 3,
          yAxisID: 'y',
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          align: 'end',
          labels: {
            color: '#8892a4',
            font: { family: "'JetBrains Mono', monospace", size: 11 },
            boxWidth: 12,
            boxHeight: 2,
            padding: 16,
            usePointStyle: false,
          }
        },
        tooltip: {
          backgroundColor: 'rgba(10, 14, 23, 0.95)',
          titleColor: '#e2e8f0',
          bodyColor: '#8892a4',
          borderColor: '#1e2d4a',
          borderWidth: 1,
          padding: 12,
          titleFont: { family: "'JetBrains Mono', monospace", size: 12 },
          bodyFont: { family: "'JetBrains Mono', monospace", size: 11 },
          displayColors: true,
          boxWidth: 8,
          boxHeight: 8,
          boxPadding: 4,
          callbacks: {
            title: (items) => {
              const idx = items[0].dataIndex
              return props.data[idx].day
            },
            label: (item) => {
              if (item.datasetIndex === 0) return ` 收盘: ${item.formattedValue}`
              if (item.datasetIndex === 1) return ` 最高: ${item.formattedValue}`
              return ` 最低: ${item.formattedValue}`
            }
          }
        }
      },
      scales: {
        x: {
          grid: {
            color: 'rgba(30, 45, 74, 0.4)',
            drawBorder: false,
          },
          ticks: {
            color: '#4a5568',
            font: { family: "'JetBrains Mono', monospace", size: 10 },
            maxRotation: 0,
          },
          border: { display: false }
        },
        y: {
          position: 'right',
          grid: {
            color: 'rgba(30, 45, 74, 0.4)',
            drawBorder: false,
          },
          ticks: {
            color: '#4a5568',
            font: { family: "'JetBrains Mono', monospace", size: 10 },
          },
          border: { display: false }
        }
      }
    }
  })
}

onMounted(() => {
  createChart()
})

watch(() => props.data, () => {
  createChart()
}, { deep: true })

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.destroy()
  }
})
</script>

<template>
  <div class="chart-container">
    <canvas ref="canvasRef"></canvas>
  </div>
</template>

<style scoped>
.chart-container {
  width: 100%;
  height: 320px;
  position: relative;
}
</style>
