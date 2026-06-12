import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { getMyProfile } from './profileService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('profileService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests the current user profile with the authenticated backend contract', async () => {
    const abortController = new AbortController()
    requestJsonMock.mockResolvedValue({
      userId: 1,
      email: 'moon@example.com',
      nickname: 'MoonStar',
      createdAt: '2026-06-12T10:00:00',
    })

    await expect(getMyProfile(abortController.signal)).resolves.toEqual({
      userId: 1,
      email: 'moon@example.com',
      nickname: 'MoonStar',
      createdAt: '2026-06-12T10:00:00',
    })

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/users/me/profile', {
      method: 'GET',
      signal: abortController.signal,
    })
  })
})
