import type { EventTimePrecision } from '../../types/log'

export type { EventTimePrecision }

export const TIMELINE_ZONE = 'Asia/Kolkata'

const PERIOD_BY_HOUR: Record<number, string> = {
  8: 'Morning',
  13: 'Lunch',
  14: 'Afternoon',
  18: 'Evening',
  21: 'Night',
}

export function localDateKey(iso: string, now = new Date()): string {
  const date = Number.isNaN(Date.parse(iso)) ? now : new Date(iso)
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: TIMELINE_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function dateGroupLabel(dateKey: string, now = new Date()): string {
  const todayKey = localDateKey(now.toISOString(), now)
  const yesterdayKey = shiftDateKey(todayKey, -1)
  if (dateKey === todayKey) {
    return 'Today'
  }
  if (dateKey === yesterdayKey) {
    return 'Yesterday'
  }
  return formatLongDate(dateKey)
}

export function formatEventTimeLabel(
  timestamp: string,
  precision: EventTimePrecision | null | undefined
): string {
  const resolved = precision ?? 'NOW'
  if (resolved === 'PERIOD') {
    return periodLabel(timestamp)
  }
  if (resolved === 'DAY') {
    return formatLongDate(localDateKey(timestamp))
  }
  return formatDateTime(timestamp)
}

export function periodLabel(timestamp: string): string {
  const hour = localHour(timestamp)
  if (PERIOD_BY_HOUR[hour]) {
    return PERIOD_BY_HOUR[hour]
  }
  if (hour >= 5 && hour < 12) {
    return 'Morning'
  }
  if (hour === 12 || hour === 13) {
    return 'Lunch'
  }
  if (hour >= 14 && hour < 17) {
    return 'Afternoon'
  }
  if (hour >= 17 && hour < 20) {
    return 'Evening'
  }
  return 'Night'
}

function localHour(iso: string): number {
  const hour = new Intl.DateTimeFormat('en-GB', {
    timeZone: TIMELINE_ZONE,
    hour: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(iso))
  return Number(hour)
}

function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: TIMELINE_ZONE,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).format(new Date(iso))
}

function formatLongDate(dateKey: string): string {
  const [year, month, day] = dateKey.split('-').map(Number)
  const utcNoon = new Date(Date.UTC(year, month - 1, day, 12))
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'UTC',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(utcNoon)
}

function shiftDateKey(dateKey: string, days: number): string {
  const [year, month, day] = dateKey.split('-').map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + days))
  return shifted.toISOString().slice(0, 10)
}
