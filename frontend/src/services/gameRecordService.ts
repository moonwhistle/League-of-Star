import type { GameRecordListResponse } from '@/types/gameRecord'

import { requestJson } from './apiClient'

export function getMyGameRecords(
  page: number,
  signal?: AbortSignal,
): Promise<GameRecordListResponse> {
  return requestJson<GameRecordListResponse>(`/api/v1/users/me/game-records?page=${page}`, {
    method: 'GET',
    signal,
  })
}
