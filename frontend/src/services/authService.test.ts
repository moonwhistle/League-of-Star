import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { login, logout, signup } from './authService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('authService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestJsonMock.mockResolvedValue(undefined)
  })

  it('logs in with the public backend login contract', async () => {
    const abortController = new AbortController()

    await login(
      {
        email: 'test@example.com',
        password: 'password123',
      },
      abortController.signal,
    )

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/login', {
      method: 'POST',
      body: {
        email: 'test@example.com',
        password: 'password123',
      },
      signal: abortController.signal,
      auth: false,
    })
  })

  it('signs up with the public backend signUp contract', async () => {
    const abortController = new AbortController()

    await signup(
      {
        email: 'new@example.com',
        password: 'password123',
        nickname: 'StarUser',
      },
      abortController.signal,
    )

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/signUp', {
      method: 'POST',
      body: {
        email: 'new@example.com',
        password: 'password123',
        nickname: 'StarUser',
      },
      signal: abortController.signal,
      auth: false,
    })
  })

  it('logs out with the authenticated backend logout contract', async () => {
    const abortController = new AbortController()

    await logout('refresh-token', abortController.signal)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/logout', {
      method: 'POST',
      body: {
        refreshToken: 'refresh-token',
      },
      signal: abortController.signal,
    })
  })
})
