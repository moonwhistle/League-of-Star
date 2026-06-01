import type { GameId, GameSummaryResponse } from '@/types/game'

import { requestJson } from './apiClient'

export function getGameSummary(gameId: GameId, signal?: AbortSignal): Promise<GameSummaryResponse> {
  return requestJson<GameSummaryResponse>(`/api/v1/games/${gameId}/summary`, {
    method: 'GET',
    signal,
  })
}
