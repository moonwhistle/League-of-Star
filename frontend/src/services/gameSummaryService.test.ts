import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { getGameSummary } from './gameSummaryService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('gameSummaryService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests a game summary by gameId', async () => {
    requestJsonMock.mockResolvedValue({
      summaryStatus: 'PENDING',
      gameId: 100,
      retryAfterMillis: 1000,
    })

    await expect(getGameSummary(100)).resolves.toEqual({
      summaryStatus: 'PENDING',
      gameId: 100,
      retryAfterMillis: 1000,
    })

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/games/100/summary', {
      method: 'GET',
      signal: undefined,
    })
  })

  it('passes an abort signal to the request client', async () => {
    const controller = new AbortController()
    requestJsonMock.mockResolvedValue({
      summaryStatus: 'DONE',
      gameId: 100,
      gameResult: 'PLAYER1_WIN',
      winnerUserId: 1,
      finishedAt: '2026-06-01T00:00:00',
      me: createPlayerSummary({
        result: 'WIN',
      }),
      opponent: createPlayerSummary({
        userId: 2,
        nickname: 'Voidwalker',
        result: 'LOSS',
      }),
    })

    await getGameSummary(100, controller.signal)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/games/100/summary', {
      method: 'GET',
      signal: controller.signal,
    })
  })
})

function createPlayerSummary(
  overrides: Partial<{
    userId: number
    nickname: string
    result: 'WIN' | 'LOSS' | 'DRAW'
  }> = {},
) {
  return {
    userId: overrides.userId ?? 1,
    nickname: overrides.nickname ?? 'Starlord',
    result: overrides.result ?? 'WIN',
    lpBefore: 100,
    lpAfter: 125,
    lpChange: 25,
    rankBefore: 'GOLD_IV',
    rankAfter: 'GOLD_III',
    seriesType: 'RANK',
    rankSeriesId: null,
  }
}
