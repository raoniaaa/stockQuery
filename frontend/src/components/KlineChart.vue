<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { StockData } from '../types'

const props = defineProps<{
  data: StockData[]
  name?: string
  code?: string
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function initChart() {
  if (!chartRef.value || props.data.length === 0) return

  if (chart) chart.dispose()
  chart = echarts.init(chartRef.value)

  const closes = props.data.map(d => +d.close)
  const opens = props.data.map(d => +d.open)
  const dates = props.data.map(d => {
    const parts = d.day.split('-')
    return parts.length >= 3 ? parts[1] + '/' + parts[2] : d.day
  })
  const ohlc = props.data.map(d => [+d.open, +d.close, +d.low, +d.high])
  const vols = props.data.map(d => +d.volume)

  // A股配色：涨红跌绿
  const upColor = '#c23531'
  const downColor = '#2ba35a'

  // 计算 MA 均线
  function calcMA(period: number) {
    return closes.map((_, i) => {
      if (i < period - 1) return null
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) sum += closes[j]
      return +(sum / period).toFixed(2)
    })
  }

  const ma5 = calcMA(5)
  const ma10 = calcMA(10)
  const ma20 = calcMA(20)

  // 逐根成交量着色
  const volData = vols.map((v, i) => ({
    value: v,
    itemStyle: {
      color: closes[i] >= opens[i]
        ? 'rgba(194,53,49,0.45)'
        : 'rgba(43,163,90,0.45)'
    }
  }))

  chart.setOption({
    animation: true,
    animationDuration: 600,
    animationEasing: 'cubicOut',
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', crossStyle: { color: 'rgba(0,0,0,0.06)' } },
      backgroundColor: 'rgba(255,255,255,0.97)',
      borderColor: '#e4e0db',
      borderWidth: 1,
      textStyle: { color: '#1c1917', fontSize: 11, fontFamily: 'JetBrains Mono' },
      extraCssText: 'border-radius: 8px; box-shadow: 0 4px 20px rgba(0,0,0,0.08);',
      formatter(params: any) {
        const k = params.find((p: any) => p.seriesType === 'candlestick')
        if (!k) return ''
        const [o, c, l, h] = k.data
        const isUp = c >= o
        const color = isUp ? upColor : downColor
        const vol = vols[k.dataIndex]
        const volStr = vol >= 1e8 ? (vol / 1e8).toFixed(2) + '亿'
          : vol >= 1e4 ? (vol / 1e4).toFixed(0) + '万' : vol.toString()
        const maLines = params
          .filter((p: any) => p.seriesName?.startsWith('MA'))
          .map((p: any) => `<span style="color:${p.color}">${p.seriesName}: ${p.data ?? '-'}</span>`)
          .join('&nbsp;&nbsp;')
        return `<div style="font-family:JetBrains Mono;font-size:11px;line-height:1.6">
          <div style="color:#a8a29e;margin-bottom:2px">${k.axisValue}</div>
          <div>开 <b>${o}</b>&nbsp;&nbsp;收 <b style="color:${color}">${c}</b>&nbsp;&nbsp;低 ${l}&nbsp;&nbsp;高 ${h}</div>
          <div>量 ${volStr}</div>
          <div style="margin-top:2px">${maLines}</div>
        </div>`
      }
    },
    legend: {
      show: false
    },
    grid: [
      { left: 56, right: 20, top: 20, height: '55%' },
      { left: 56, right: 20, top: '78%', height: '12%' }
    ],
    xAxis: [
      {
        type: 'category', data: dates, boundaryGap: true,
        axisLine: { lineStyle: { color: '#e4e0db' } },
        axisLabel: { color: '#a8a29e', fontSize: 9, fontFamily: 'JetBrains Mono' },
        splitLine: { show: false }
      },
      {
        type: 'category', data: dates, gridIndex: 1, boundaryGap: true,
        axisLine: { lineStyle: { color: '#e4e0db' } },
        axisLabel: { show: false },
        splitLine: { show: false }
      }
    ],
    yAxis: [
      {
        scale: true,
        splitLine: { lineStyle: { color: '#e4e0db', type: 'dashed' } },
        axisLabel: { color: '#a8a29e', fontSize: 9, fontFamily: 'JetBrains Mono' },
        axisLine: { show: false }
      },
      {
        scale: true, gridIndex: 1,
        splitLine: { show: false },
        axisLabel: { show: false },
        axisLine: { show: false }
      }
    ],
    series: [
      {
        type: 'candlestick',
        data: ohlc,
        itemStyle: {
          color: upColor,        // 涨：红色实体
          color0: downColor,     // 跌：绿色实体
          borderColor: upColor,
          borderColor0: downColor,
          borderWidth: 1
        },
        barWidth: '50%'
      },
      {
        name: 'MA5', type: 'line', data: ma5,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#e6a23c' }
      },
      {
        name: 'MA10', type: 'line', data: ma10,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#409eff' }
      },
      {
        name: 'MA20', type: 'line', data: ma20,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#e040fb' }
      },
      {
        type: 'bar',
        xAxisIndex: 1, yAxisIndex: 1,
        data: volData,
        barWidth: '50%'
      }
    ]
  })

  const ro = new ResizeObserver(() => chart?.resize())
  ro.observe(chartRef.value)
}

onMounted(() => nextTick(initChart))
watch(() => props.data, initChart, { deep: true })
onUnmounted(() => chart?.dispose())
</script>

<template>
  <div class="kline-wrap">
    <div class="kline-head">
      <div class="kline-head-left">
        <span class="kline-name">{{ name || code || 'K线图' }}</span>
        <span class="kline-code" v-if="code">{{ code }}</span>
      </div>
      <div class="kline-badge">30D</div>
    </div>
    <div ref="chartRef" class="kline-chart"></div>
  </div>
</template>

<style scoped>
.kline-wrap {
  background: var(--white);
  border: 1px solid var(--mist);
  border-radius: var(--radius);
  overflow: hidden;
  margin: 6px 0;
}
.kline-head {
  padding: 14px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--mist-subtle);
}
.kline-head-left { display: flex; align-items: baseline; gap: 8px; }
.kline-name {
  font-family: var(--display);
  font-size: 18px;
  font-weight: 600;
  color: var(--ink);
}
.kline-code {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--ash);
}
.kline-badge {
  font-family: var(--mono);
  font-size: 9px;
  font-weight: 500;
  letter-spacing: 1px;
  color: var(--ash);
  padding: 3px 10px;
  border: 1px solid var(--mist);
  border-radius: var(--radius-xs);
  background: var(--bg);
}
.kline-chart {
  width: 100%;
  height: 320px;
}
</style>
