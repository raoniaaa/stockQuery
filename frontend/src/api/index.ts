import axios from 'axios'
import type { StockData, Analysis } from '../types'

const api = axios.create({
  baseURL: 'https://stockquery.onrender.com/api',
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

export const getAllAnalyses = async (): Promise<Analysis[]> => {
  const response = await api.get('/stock/analyses/all')
  return response.data
}

export const getAnalyses = async (stockCode: string): Promise<Analysis[]> => {
  const response = await api.get(`/stock/analysis/${stockCode}`)
  return response.data
}

export const analyzeStock = async (stockCode: string, stockName?: string): Promise<string> => {
  const response = await api.post(`/stock/analyze/${stockCode}`, { stockName })
  return response.data.analysis
}
