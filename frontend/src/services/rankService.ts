import type { UserRankResponse } from '@/types/user'

import { requestJson } from './apiClient'

export function getMyRank(signal?: AbortSignal): Promise<UserRankResponse> {
  return requestJson<UserRankResponse>('/api/v1/users/me/rank', {
    method: 'GET',
    signal,
  })
}
