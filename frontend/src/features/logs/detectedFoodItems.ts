import type { LogItemResponse } from '../../types/log'

export interface DetectedItem {
  label: string
  quantity?: number
}

export function detectedFoodItems(log: LogItemResponse): DetectedItem[] {
  const structured = log.structuredJson ?? {}
  const items = structured.items

  if (Array.isArray(items) && items.length > 0) {
    return items.flatMap((entry) => {
      if (typeof entry === 'string' && entry.trim()) {
        return [{ label: titleCase(entry.trim()) }]
      }
      if (entry && typeof entry === 'object') {
        const row = entry as Record<string, unknown>
        const name = String(row.item ?? row.name ?? '').trim()
        if (!name) {
          return []
        }
        const quantity = toQuantity(row.quantity)
        return [{ label: titleCase(name), quantity }]
      }
      return []
    })
  }

  return []
}

function toQuantity(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed
    }
  }
  return undefined
}

function titleCase(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}
