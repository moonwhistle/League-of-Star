import type { RankingResponse } from '@/types/ranking'

import { requestJson } from './apiClient'

export function getRankings(signal?: AbortSignal, limit = 5): Promise<RankingResponse> {
  return requestJson<RankingResponse>(`/api/v1/rankings?limit=${limit}`, {
    method: 'GET',
    signal,
  })
}
