import type { LogItemResponse } from '../../types/log'

export function formatKg(value: number): string {
  return `${value.toFixed(1)} kg`
}

export function weightKgFromLog(log: LogItemResponse): number | null {
  if (log.eventType !== 'WEIGHT') {
    return null
  }
  const raw = log.structuredJson?.value
  const value = typeof raw === 'number' ? raw : Number(raw)
  if (!Number.isFinite(value)) {
    return null
  }
  return value
}

export const WEIGHT_MIN_KG = 30
export const WEIGHT_MAX_KG = 250

export function parseWeightInput(raw: string): number | null {
  const value = Number(raw.trim().replace(',', '.'))
  if (!Number.isFinite(value)) {
    return null
  }
  if (value < WEIGHT_MIN_KG || value > WEIGHT_MAX_KG) {
    return null
  }
  return Math.round(value * 10) / 10
}
