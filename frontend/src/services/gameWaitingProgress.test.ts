import { describe, expect, it } from 'vitest'

import {
  calculateGameWaitingProgress,
  GAME_WAITING_PROGRESS_STEP_PERCENT,
} from './gameWaitingProgress'
import type { GameWaitingPayload } from './gameWaitingPayload'

function createPayload(overrides: Partial<GameWaitingPayload> = {}): GameWaitingPayload {
  return {
    matchId: 'match-1',
    opponent: {
      userId: 2,
      nickname: 'Voidwalker',
      tier: 'Gold IV',
      tierScore: 13,
    },
    game: {
      gameRoomId: 100,
      webSocketUrl: '/ws/game/100',
    },
    receivedAt: '2026-06-01T00:00:00.000Z',
    ...overrides,
  }
}

describe('gameWaitingProgress', () => {
  it('defines each required payload field as a 25 percent step', () => {
    expect(GAME_WAITING_PROGRESS_STEP_PERCENT).toBe(25)

    expect(calculateGameWaitingProgress(undefined)).toEqual({
      steps: [
        { key: 'matchId', ready: false },
        { key: 'opponent', ready: false },
        { key: 'gameRoomId', ready: false },
        { key: 'webSocketUrl', ready: false },
      ],
      readyCount: 0,
      progress: 0,
    })
  })

  it.each([
    [
      1,
      25,
      createPayload({
        opponent: null,
        game: { gameRoomId: Number.NaN, webSocketUrl: '' },
      }),
    ],
    [2, 50, createPayload({ opponent: null, game: { gameRoomId: 100, webSocketUrl: '' } })],
    [
      3,
      75,
      createPayload({
        opponent: null,
        game: { gameRoomId: 100, webSocketUrl: '/ws/game/100' },
      }),
    ],
    [4, 100, createPayload()],
  ])('returns %i ready steps as %i percent progress', (readyCount, progress, payload) => {
    expect(calculateGameWaitingProgress(payload).readyCount).toBe(readyCount)
    expect(calculateGameWaitingProgress(payload).progress).toBe(progress)
  })

  it('does not include WebSocket connection or CLIENT_READY state in progress', () => {
    const progress = calculateGameWaitingProgress(createPayload())

    expect(progress.steps.map((step) => step.key)).toEqual([
      'matchId',
      'opponent',
      'gameRoomId',
      'webSocketUrl',
    ])
    expect(progress.progress).toBe(100)
  })
})
