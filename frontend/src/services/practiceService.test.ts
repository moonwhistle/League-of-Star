import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { startPractice } from './practiceService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('practiceService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('starts practice with the authenticated backend contract', async () => {
    const abortController = new AbortController()
    requestJsonMock.mockResolvedValue({
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

    await expect(startPractice(abortController.signal)).resolves.toEqual({
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

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/games/practice', {
      method: 'POST',
      signal: abortController.signal,
    })
  })
})
