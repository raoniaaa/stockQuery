export interface StockData {
  day: string
  open: string
  high: string
  low: string
  close: string
  volume: string
}

export interface GLMAnalysisResult {
  summary: string
  sentiment: 'Bullish' | 'Neutral' | 'Bearish' | string
  risk_level: string
  detail: string
}

export interface Analysis {
  id?: number
  stockCode: string
  stockName: string
  analysisType: string
  content: string
  modelUsed: string
  summary?: string
  sentiment?: string
  riskLevel?: string
  createdAt?: string
}

export interface ChatMessage {
  id: number
  role: 'user' | 'ai'
  text: string
  time: string
  kline?: KlineData
  analysis?: AnalysisResult
  rawContent?: string
  thinking?: boolean
}

export interface KlineData {
  code: string
  name: string
  change: number
  raw: StockData[]
}

export interface AnalysisResult {
  summary?: string
  detail?: string
  sentiment?: string
  risk_level?: string
  stockCode?: string
  stockName?: string
}

export interface MarketOverview {
  name: string
  current: string
  change: string
  changePercent: string
}
