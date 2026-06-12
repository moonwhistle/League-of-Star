import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { getMyRank } from './rankService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('rankService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests the current user rank with the authenticated backend contract', async () => {
    const abortController = new AbortController()
    requestJsonMock.mockResolvedValue({
      userId: 1,
      tier: 'GOLD',
      division: 'IV',
      rank: 'GOLD_IV',
      lp: 40,
      tierScore: 13,
      wins: 12,
      losses: 8,
      draws: 1,
      rankUpdatedAt: '2026-06-12T10:00:00',
    })

    await expect(getMyRank(abortController.signal)).resolves.toEqual({
      userId: 1,
      tier: 'GOLD',
      division: 'IV',
      rank: 'GOLD_IV',
      lp: 40,
      tierScore: 13,
      wins: 12,
      losses: 8,
      draws: 1,
      rankUpdatedAt: '2026-06-12T10:00:00',
    })

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/users/me/rank', {
      method: 'GET',
      signal: abortController.signal,
    })
  })
})
