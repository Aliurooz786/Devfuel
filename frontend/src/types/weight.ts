import type { EventTimePrecision } from './log'

export interface CreateWeightRequest {
  value: number
  unit?: 'kg'
  occurrenceDate?: string
  source?: string
}

export interface WeightPoint {
  id: string
  value: number
  unit: string
  timestamp: string
  loggedAt?: string
  eventTimePrecision?: EventTimePrecision
  loggedLater: boolean
  localDate: string
}

export interface WeightHistoryResponse {
  unit: string
  points: WeightPoint[]
}
