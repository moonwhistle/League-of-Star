import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildGameResultPayloadKey,
  readGameResultPayload,
  saveGameResultPayload,
  saveGameResultPayloadFromMessage,
} from './gameResultPayload'

describe('gameResultPayload', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.useRealTimers()
  })

  it('stores and reads game result payloads by gameRoomId', () => {
    saveGameResultPayload({
      gameRoomId: 100,
      result: 'PLAYER1_WIN',
      winnerUserId: 1,
      reason: 'LIGHTNING_KILL',
      finishedAt: 1716192017000,
      actions: [
        {
          userId: 1,
          serverReceiveTime: 1716192010000,
          lightningTimeMs: 6000,
          starCoreHpAtLightning: 1000,
          damage: 1200,
          afterHp: 0,
          isKill: true,
        },
      ],
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    expect(readGameResultPayload('100')).toEqual({
      gameRoomId: 100,
      result: 'PLAYER1_WIN',
      winnerUserId: 1,
      reason: 'LIGHTNING_KILL',
      finishedAt: 1716192017000,
      actions: [
        {
          userId: 1,
          serverReceiveTime: 1716192010000,
          lightningTimeMs: 6000,
          starCoreHpAtLightning: 1000,
          damage: 1200,
          afterHp: 0,
          isKill: true,
        },
      ],
      receivedAt: '2026-06-01T00:00:00.000Z',
    })
  })

  it('creates stored payloads from GAME_RESULT messages', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))

    const payload = saveGameResultPayloadFromMessage({
      gameRoomId: 100,
      result: 'DRAW',
      winnerUserId: null,
      reason: 'NATURAL_DEATH_DRAW',
      finishedAt: 1716192017000,
      actions: [],
    })

    expect(payload?.receivedAt).toBe('2026-06-01T00:00:00.000Z')
    expect(readGameResultPayload(100)?.reason).toBe('NATURAL_DEATH_DRAW')
  })

  it('does not store invalid GAME_RESULT messages', () => {
    expect(
      saveGameResultPayloadFromMessage({
        gameRoomId: 100,
        result: 'PLAYER1_WIN',
        winnerUserId: 1,
        reason: 'LIGHTNING_KILL',
        finishedAt: 1716192017000,
        actions: [
          {
            userId: 1,
            serverReceiveTime: 1716192010000,
            lightningTimeMs: 6000,
            starCoreHpAtLightning: 1000,
            damage: 1200,
            afterHp: 0,
            isKill: 'true',
          },
        ],
      }),
    ).toBeNull()

    expect(window.sessionStorage.length).toBe(0)
  })

  it('returns null when payload is missing, malformed, or for a different route gameRoomId', () => {
    expect(readGameResultPayload(100)).toBeNull()

    window.sessionStorage.setItem(buildGameResultPayloadKey(100), '{')
    expect(readGameResultPayload(100)).toBeNull()

    window.sessionStorage.setItem(
      buildGameResultPayloadKey(100),
      JSON.stringify({
        gameRoomId: 101,
        result: 'DRAW',
        winnerUserId: null,
        reason: 'NATURAL_DEATH_DRAW',
        finishedAt: 1716192017000,
        actions: [],
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameResultPayload(100)).toBeNull()
  })
})
