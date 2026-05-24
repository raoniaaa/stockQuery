import axios from 'axios'
import type { StockData, Analysis, MarketOverview } from '../types'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000
})

export const getStockData = async (stockCode: string, days: number = 30): Promise<StockData[]> => {
  const response = await api.get(`/stock/data/${stockCode}`, { params: { days } })
  return response.data
}

export const getStockName = async (stockCode: string): Promise<string> => {
  const response = await api.get(`/stock/name/${stockCode}`)
  return response.data.name || ''
}

export const searchStock = async (keyword: string): Promise<string> => {
  const response = await api.get('/stock/search', { params: { keyword } })
  return response.data.code || ''
}

export const getAllAnalyses = async (): Promise<Analysis[]> => {
  const response = await api.get('/stock/analyses/all')
  return response.data
}

export const getAnalyses = async (stockCode: string): Promise<Analysis[]> => {
  const response = await api.get(`/stock/analysis/${stockCode}`)
  return response.data
}

export const analyzeStock = async (stockCode: string, stockName?: string): Promise<string> => {
  const response = await api.post(`/stock/analyze/${stockCode}`, { stockName }, { timeout: 120000 })
  return response.data.analysis
}

export const getMarketOverview = async (): Promise<MarketOverview> => {
  const response = await api.get('/stock/market-overview')
  return response.data
}

export async function* chatStream(message: string): AsyncGenerator<string> {
  const response = await fetch('/api/ai/chat?message=' + encodeURIComponent(message))
  if (!response.ok) throw new Error('Chat request failed: ' + response.status)

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5)
        if (data === '[DONE]') return
        // Empty data events represent newlines lost during SSE token streaming
        yield data === '' ? '\n' : data
      }
    }
  }

  if (buffer.startsWith('data:')) {
    const data = buffer.slice(5)
    if (data !== '[DONE]') yield data === '' ? '\n' : data
  }
}
