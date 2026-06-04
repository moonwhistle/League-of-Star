import type { GameWaitingPayload } from './gameWaitingPayload'

export const GAME_WAITING_PROGRESS_STEP_PERCENT = 20

export type GameWaitingProgressStepKey =
  | 'matchId'
  | 'opponent'
  | 'gameRoomId'
  | 'videoUrl'
  | 'webSocketUrl'

export interface GameWaitingProgressStep {
  key: GameWaitingProgressStepKey
  ready: boolean
}

export interface GameWaitingProgress {
  steps: GameWaitingProgressStep[]
  readyCount: number
  progress: number
}

export function calculateGameWaitingProgress(
  payload: GameWaitingPayload | null | undefined,
): GameWaitingProgress {
  const steps: GameWaitingProgressStep[] = [
    {
      key: 'matchId',
      ready: String(payload?.matchId ?? '').trim() !== '',
    },
    {
      key: 'opponent',
      ready: payload?.opponent !== null && payload?.opponent !== undefined,
    },
    {
      key: 'gameRoomId',
      ready: Number.isFinite(payload?.game.gameRoomId),
    },
    {
      key: 'videoUrl',
      ready: String(payload?.game.videoUrl ?? '').trim() !== '',
    },
    {
      key: 'webSocketUrl',
      ready: String(payload?.game.webSocketUrl ?? '').trim() !== '',
    },
  ]
  const readyCount = steps.filter((step) => step.ready).length

  return {
    steps,
    readyCount,
    progress: readyCount * GAME_WAITING_PROGRESS_STEP_PERCENT,
  }
}
