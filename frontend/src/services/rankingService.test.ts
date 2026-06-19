import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { getRankings } from './rankingService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('rankingService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests rankings with the authenticated backend contract', async () => {
    const abortController = new AbortController()
    const response = {
      summary: {
        myRankPosition: 128,
        topPercent: 7,
        totalRankers: 1840,
      },
      entries: [
        {
          rankPosition: 1,
          userId: 10,
          nickname: 'Legendary Star',
          tier: 'CHALLENGER',
          division: null,
          rank: 'CHALLENGER',
          lp: 3492,
          tierScore: 40,
          wins: 122,
          losses: 44,
          draws: 3,
          isCurrentUser: false,
        },
      ],
      currentUser: {
        rankPosition: 128,
        userId: 1,
        nickname: 'MoonStar',
        tier: 'GOLD',
        division: 'IV',
        rank: 'GOLD_IV',
        lp: 40,
        tierScore: 13,
        wins: 12,
        losses: 8,
        draws: 1,
        isCurrentUser: true,
      },
    }
    requestJsonMock.mockResolvedValue(response)

    await expect(getRankings(abortController.signal)).resolves.toEqual(response)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/rankings?limit=5', {
      method: 'GET',
      signal: abortController.signal,
    })
  })
})
