import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildGameWaitingPayloadKey,
  readGameWaitingPayload,
  saveGameWaitingPayload,
  saveGameWaitingPayloadFromMatchResult,
} from './gameWaitingPayload'

describe('gameWaitingPayload', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.useRealTimers()
  })

  it('stores and reads game waiting payloads by gameRoomId', () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: {
        userId: 2,
        nickname: 'opponent',
        tier: 'Gold IV',
        tierScore: 13,
      },
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    expect(readGameWaitingPayload('100')).toEqual({
      matchId: 'match-1',
      opponent: {
        userId: 2,
        nickname: 'opponent',
        tier: 'Gold IV',
        tierScore: 13,
      },
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })
  })

  it('reads legacy smite namespace payloads during the League of Star migration', () => {
    window.sessionStorage.setItem(
      'smite.gameWaitingPayload:100',
      JSON.stringify({
        matchId: 'match-1',
        opponent: null,
        game: {
          gameRoomId: 100,
          videoUrl: '/assets/game/dragon-view.mp4',
          webSocketUrl: '/ws/game/100',
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameWaitingPayload(100)?.matchId).toBe('match-1')
  })

  it('creates payloads from GO_TO_GAME_WAITING match results', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))

    const payload = saveGameWaitingPayloadFromMatchResult({
      matchId: 'match-1',
      outcome: 'MATCHED',
      reason: 'BOTH_ACCEPTED',
      action: 'GO_TO_GAME_WAITING',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
    })

    expect(payload?.receivedAt).toBe('2026-06-01T00:00:00.000Z')
    expect(readGameWaitingPayload(100)?.matchId).toBe('match-1')
  })

  it('does not store non-waiting actions or invalid game payloads', () => {
    expect(
      saveGameWaitingPayloadFromMatchResult({
        matchId: 'match-1',
        outcome: 'FAILED',
        reason: 'MY_REJECTED',
        action: 'GO_TO_MATCH_START',
        opponent: null,
        game: null,
      }),
    ).toBeNull()

    expect(window.sessionStorage.length).toBe(0)
  })

  it('returns null when payload is missing, malformed, or for a different route gameRoomId', () => {
    expect(readGameWaitingPayload(100)).toBeNull()

    window.sessionStorage.setItem(buildGameWaitingPayloadKey(100), '{')
    expect(readGameWaitingPayload(100)).toBeNull()

    window.sessionStorage.setItem(
      buildGameWaitingPayloadKey(100),
      JSON.stringify({
        matchId: 'match-1',
        opponent: null,
        game: {
          gameRoomId: 101,
          videoUrl: '/assets/game/dragon-view.mp4',
          webSocketUrl: '/ws/game/101',
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameWaitingPayload(100)).toBeNull()
  })
})
