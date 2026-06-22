import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildPracticeGameStartPayloadKey,
  readPracticeGameStartPayload,
  savePracticeGameStartPayload,
  savePracticeGameStartPayloadFromResponse,
} from './practiceGameStartPayload'

describe('practiceGameStartPayload', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.useRealTimers()
  })

  it('stores and reads practice start payloads by gameRoomId', () => {
    savePracticeGameStartPayload({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      webSocketUrl: '/ws/game/100',
      scenario: {
        starCoreMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [
          { timeMs: 0, hp: 10000 },
          { timeMs: 15000, hp: 0 },
        ],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    expect(readPracticeGameStartPayload('100')).toEqual({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      webSocketUrl: '/ws/game/100',
      scenario: {
        starCoreMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [
          { timeMs: 0, hp: 10000 },
          { timeMs: 15000, hp: 0 },
        ],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })
  })

  it('creates stored payloads from practice start responses', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))

    const payload = savePracticeGameStartPayloadFromResponse({
      gameRoomId: 100,
      serverTime: 1716192000000,
      startAt: 1716192004000,
      webSocketUrl: '/ws/game/100',
      scenario: {
        starCoreMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [{ timeMs: 0, hp: 10000 }],
      },
    })

    expect(payload?.receivedAt).toBe('2026-06-01T00:00:00.000Z')
    expect(readPracticeGameStartPayload(100)?.webSocketUrl).toBe('/ws/game/100')
  })

  it('does not store invalid practice start responses', () => {
    expect(
      savePracticeGameStartPayloadFromResponse({
        gameRoomId: 100,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        webSocketUrl: '',
        scenario: {
          starCoreMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: 0, hp: 10000 }],
        },
      }),
    ).toBeNull()

    expect(window.sessionStorage.length).toBe(0)
  })

  it('returns null when payload is missing, malformed, or for a different route gameRoomId', () => {
    expect(readPracticeGameStartPayload(100)).toBeNull()

    window.sessionStorage.setItem(buildPracticeGameStartPayloadKey(100), '{')
    expect(readPracticeGameStartPayload(100)).toBeNull()

    window.sessionStorage.setItem(
      buildPracticeGameStartPayloadKey(100),
      JSON.stringify({
        gameRoomId: 101,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        webSocketUrl: '/ws/game/101',
        scenario: {
          starCoreMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: 0, hp: 10000 }],
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readPracticeGameStartPayload(100)).toBeNull()
  })

  it('returns null when stored scenario has invalid timeline shape', () => {
    window.sessionStorage.setItem(
      buildPracticeGameStartPayloadKey(100),
      JSON.stringify({
        gameRoomId: 100,
        serverTime: 1716192000000,
        startAt: 1716192004000,
        webSocketUrl: '/ws/game/100',
        scenario: {
          starCoreMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [{ timeMs: 0, hp: '10000' }],
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    expect(readPracticeGameStartPayload(100)).toBeNull()
  })
})
