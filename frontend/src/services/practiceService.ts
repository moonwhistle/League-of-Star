import type { PracticeGameStartResponse } from '@/types/game'

import { requestJson } from './apiClient'

export function startPractice(signal?: AbortSignal): Promise<PracticeGameStartResponse> {
  return requestJson<PracticeGameStartResponse>('/api/v1/games/practice', {
    method: 'POST',
    signal,
  })
}
