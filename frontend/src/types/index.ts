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
  sentiment: 'Bullish' | 'Neutral' | 'Bearish'
  risk_level: '低' | '中' | '高'
  detail: string
}

export interface Analysis {
  id?: number
  stock_code: string
  stock_name: string
  analysis_type: string
  content: string
  model_used: string
  summary?: string
  sentiment?: string
  risk_level?: string
  created_at?: string
}
