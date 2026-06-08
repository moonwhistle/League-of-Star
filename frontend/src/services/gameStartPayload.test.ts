import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildGameStartPayloadKey,
  readGameStartPayload,
  saveGameStartPayload,
  saveGameStartPayloadFromMessage,
} from './gameStartPayload'

describe('gameStartPayload', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.useRealTimers()
  })

  it('stores and reads game start payloads by gameRoomId', () => {
    saveGameStartPayload({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      scenario: {
        dragonMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [
          { timeMs: 0, hp: 10000 },
          { timeMs: 15000, hp: 0 },
        ],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    expect(readGameStartPayload('100')).toEqual({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      scenario: {
        dragonMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [
          { timeMs: 0, hp: 10000 },
          { timeMs: 15000, hp: 0 },
        ],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })
  })

  it('reads legacy smite namespace payloads during the League of Star migration', () => {
    window.sessionStorage.setItem(
      'smite.gameStartPayload:100',
      JSON.stringify({
        gameRoomId: 100,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        scenario: {
          dragonMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: 0, hp: 10000 }],
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameStartPayload(100)?.scenario.dragonMaxHp).toBe(10000)
  })

  it('accepts the League of Star starCoreMaxHp scenario field', () => {
    saveGameStartPayload({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      scenario: {
        starCoreMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [{ timeMs: 0, hp: 10000 }],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    expect(readGameStartPayload(100)?.scenario.starCoreMaxHp).toBe(10000)
  })

  it('creates stored payloads from GAME_START messages', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))

    const payload = saveGameStartPayloadFromMessage({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      scenario: {
        dragonMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [{ timeMs: 0, hp: 10000 }],
      },
    })

    expect(payload?.receivedAt).toBe('2026-06-01T00:00:00.000Z')
    expect(readGameStartPayload(100)?.startAt).toBe(1716192004000)
  })

  it('does not store invalid GAME_START messages', () => {
    expect(
      saveGameStartPayloadFromMessage({
        gameRoomId: 100,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        scenario: {
          dragonMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: Number.NaN, hp: 10000 }],
        },
      }),
    ).toBeNull()

    expect(window.sessionStorage.length).toBe(0)
  })

  it('returns null when payload is missing, malformed, or for a different route gameRoomId', () => {
    expect(readGameStartPayload(100)).toBeNull()

    window.sessionStorage.setItem(buildGameStartPayloadKey(100), '{')
    expect(readGameStartPayload(100)).toBeNull()

    window.sessionStorage.setItem(
      buildGameStartPayloadKey(100),
      JSON.stringify({
        gameRoomId: 101,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        scenario: {
          dragonMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: 0, hp: 10000 }],
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameStartPayload(100)).toBeNull()
  })

  it('returns null when stored scenario is missing required fields or has invalid timeline shape', () => {
    window.sessionStorage.setItem(
      buildGameStartPayloadKey(100),
      JSON.stringify({
        gameRoomId: 100,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        scenario: {},
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameStartPayload(100)).toBeNull()

    window.sessionStorage.setItem(
      buildGameStartPayloadKey(100),
      JSON.stringify({
        gameRoomId: 100,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        scenario: {
          dragonMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: 0, hp: '10000' }],
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readGameStartPayload(100)).toBeNull()
  })
})
