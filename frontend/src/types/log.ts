export type EventType =
  | 'SMOKING'
  | 'FOOD'
  | 'WEIGHT'
  | 'EXERCISE'
  | 'ALCOHOL'
  | 'MOOD'
  | 'SLEEP'
  | 'NOTE'
  | 'UNKNOWN'

export type EventTimePrecision = 'NOW' | 'EXACT' | 'PERIOD' | 'DAY' | 'UNRESOLVED'

export interface CreateLogRequest {
  message: string
  source?: string
}

export interface CreateLogResponse {
  success: boolean
  id: string
  eventType: EventType
  rawText: string
  structuredJson: Record<string, unknown>
  source: string
  parserVersion: string
  timestamp: string
  imageRef?: string | null
  loggedAt?: string
  eventTimePrecision?: EventTimePrecision
  eventTimezone?: string
  backdated?: boolean
  loggedLater?: boolean
}

export interface LogItemResponse {
  id: string
  timestamp: string
  eventType: EventType
  rawText: string
  structuredJson: Record<string, unknown>
  source: string
  parserVersion: string
  imageRef?: string | null
  loggedAt?: string
  eventTimePrecision?: EventTimePrecision
  eventTimezone?: string
  backdated?: boolean
  loggedLater?: boolean
}

export interface ErrorResponse {
  success: false
  error: {
    code: string
    message: string
  }
}
