import { apiFetch } from './client'
import type { CreateLogResponse } from '../types/log'
import type { CreateWeightRequest, WeightHistoryResponse, WeightPoint } from '../types/weight'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export function createWeight(body: CreateWeightRequest): Promise<CreateLogResponse> {
  return apiFetch<CreateLogResponse>('/api/weight', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/** 404 means no weigh-ins yet — that is a valid empty state, not an error. */
export async function getLatestWeight(): Promise<WeightPoint | null> {
  const response = await fetch(`${API_BASE_URL}/api/weight/latest`)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`API ${response.status}: /api/weight/latest`)
  }
  return response.json() as Promise<WeightPoint>
}

export function getWeightHistory(from?: string, to?: string): Promise<WeightHistoryResponse> {
  const params = new URLSearchParams()
  if (from) {
    params.set('from', from)
  }
  if (to) {
    params.set('to', to)
  }
  const query = params.toString()
  return apiFetch<WeightHistoryResponse>(`/api/weight${query ? `?${query}` : ''}`)
}
