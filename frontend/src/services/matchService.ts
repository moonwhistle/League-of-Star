import { requestVoid } from './apiClient'
import type { MatchId } from '@/types/match'

export function joinMatchQueue(signal?: AbortSignal): Promise<void> {
  return requestVoid('/api/v1/match/join', {
    method: 'POST',
    signal,
  })
}

export function leaveMatchQueue(signal?: AbortSignal): Promise<void> {
  return requestVoid('/api/v1/match/leave', {
    method: 'DELETE',
    signal,
  })
}

export function acceptMatch(matchId: MatchId, signal?: AbortSignal): Promise<void> {
  return requestVoid(`/api/v1/match/${encodeURIComponent(matchId)}/accept`, {
    method: 'POST',
    signal,
  })
}

export function rejectMatch(matchId: MatchId, signal?: AbortSignal): Promise<void> {
  return requestVoid(`/api/v1/match/${encodeURIComponent(matchId)}/reject`, {
    method: 'POST',
    signal,
  })
}
