import { apiFetch } from './client'
import type { CreateLogRequest, CreateLogResponse, LogItemResponse } from '../types/log'

/** Scaffolding stubs — wired later when UI and backend logic land. */

export function createLog(body: CreateLogRequest): Promise<CreateLogResponse> {
  return apiFetch<CreateLogResponse>('/api/logs', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function createImageLog(
  file: File,
  source = 'web',
  note?: string
): Promise<CreateLogResponse> {
  const body = new FormData()
  body.append('file', file)
  body.append('source', source)
  if (note?.trim()) {
    body.append('note', note.trim())
  }
  return apiFetch<CreateLogResponse>('/api/logs/image', {
    method: 'POST',
    body,
  })
}

export function getTimeline(): Promise<LogItemResponse[]> {
  return apiFetch<LogItemResponse[]>('/api/logs')
}

export function searchLogs(q: string, eventType?: string): Promise<LogItemResponse[]> {
  const params = new URLSearchParams({ q })
  if (eventType) {
    params.set('eventType', eventType)
  }
  return apiFetch<LogItemResponse[]>(`/api/logs/search?${params.toString()}`)
}
