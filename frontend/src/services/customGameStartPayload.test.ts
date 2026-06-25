import { beforeEach, describe, expect, it } from 'vitest'

import {
  buildCustomGameStartPayloadKey,
  readCustomGameStartPayload,
  saveCustomGameStartPayloadFromResponse,
} from './customGameStartPayload'

describe('customGameStartPayload', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('saves and reads a custom game start payload with local participant context', () => {
    const payload = saveCustomGameStartPayloadFromResponse(createStartResponse(), 1, 2)

    expect(payload).toMatchObject({
      roomId: 100,
      gameRoomId: 200,
      gameMode: 'CUSTOM',
      myUserId: 1,
      opponentUserId: 2,
    })
    expect(readCustomGameStartPayload(200)).toMatchObject({
      roomId: 100,
      gameRoomId: 200,
      gameMode: 'CUSTOM',
      myUserId: 1,
      opponentUserId: 2,
    })
  })

  it('uses a separate sessionStorage key from match and practice payloads', () => {
    saveCustomGameStartPayloadFromResponse(createStartResponse(), 1, 2)

    expect(window.sessionStorage.getItem(buildCustomGameStartPayloadKey(200))).toContain(
      '"gameMode":"CUSTOM"',
    )
  })

  it('returns null when response payload is not CUSTOM', () => {
    expect(
      saveCustomGameStartPayloadFromResponse({ ...createStartResponse(), gameMode: 'MATCH' }, 1, 2),
    ).toBeNull()
    expect(readCustomGameStartPayload(200)).toBeNull()
  })

  it('returns null when participants are invalid', () => {
    expect(saveCustomGameStartPayloadFromResponse(createStartResponse(), 1, 1)).toBeNull()
    expect(saveCustomGameStartPayloadFromResponse(createStartResponse(), Number.NaN, 2)).toBeNull()
  })

  it('returns null when stored payload has a different gameRoomId', () => {
    saveCustomGameStartPayloadFromResponse(createStartResponse(), 1, 2)

    expect(readCustomGameStartPayload(201)).toBeNull()
  })

  it('returns null when stored payload is malformed', () => {
    window.sessionStorage.setItem(buildCustomGameStartPayloadKey(200), '{')

    expect(readCustomGameStartPayload(200)).toBeNull()
  })
})

function createStartResponse() {
  return {
    roomId: 100,
    gameRoomId: 200,
    gameMode: 'CUSTOM',
    serverTime: 10_000,
    startAt: 13_000,
    webSocketUrl: '/ws/game/200',
    scenario: {
      starCoreMaxHp: 10000,
      durationMs: 12000,
      hpTimeline: [
        {
          timeMs: 0,
          hp: 10000,
        },
      ],
    },
  }
}
