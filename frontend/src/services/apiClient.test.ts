import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAuthTokens, getAccessToken, getRefreshToken, setAuthTokens } from './authToken'
import { addAuthSessionExpiredListener } from './authSessionEvents'
import { ApiClientError, requestJson, requestVoid } from './apiClient'

vi.mock('./authToken', () => ({
  getAccessToken: vi.fn(),
  getRefreshToken: vi.fn(),
  setAuthTokens: vi.fn(),
  clearAuthTokens: vi.fn(),
}))

const getAccessTokenMock = vi.mocked(getAccessToken)
const getRefreshTokenMock = vi.mocked(getRefreshToken)
const setAuthTokensMock = vi.mocked(setAuthTokens)
const clearAuthTokensMock = vi.mocked(clearAuthTokens)
const fetchMock = vi.fn()

function getFetchInit(index = 0): RequestInit {
  const call = fetchMock.mock.calls[index]

  if (call === undefined || call[1] === undefined) {
    throw new Error('fetch was not called with init.')
  }

  return call[1] as RequestInit
}

describe('apiClient', () => {
  let currentAccessToken = 'access-token'

  beforeEach(() => {
    vi.clearAllMocks()
    currentAccessToken = 'access-token'
    getAccessTokenMock.mockImplementation(() => currentAccessToken)
    getRefreshTokenMock.mockReturnValue('refresh-token')
    setAuthTokensMock.mockImplementation((accessToken) => {
      currentAccessToken = accessToken
    })
    fetchMock.mockResolvedValue(new Response('', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends authenticated void requests without a request body by default', async () => {
    await requestVoid('/api/v1/match/join', {
      method: 'POST',
    })

    const [url] = fetchMock.mock.calls[0] ?? []
    const init = getFetchInit()
    const headers = init.headers as Headers

    expect(url).toBe('http://localhost:8080/api/v1/match/join')
    expect(init.method).toBe('POST')
    expect(init.body).toBeUndefined()
    expect(headers.get('Authorization')).toBe('Bearer access-token')
    expect(headers.has('Content-Type')).toBe(false)
  })

  it('refreshes tokens and retries the original request once after an authenticated 401', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: 'new-access-token',
            refreshToken: 'new-refresh-token',
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }))

    await expect(requestJson('/api/v1/profile')).resolves.toEqual({ ok: true })

    const initialHeaders = getFetchInit(0).headers as Headers
    const refreshInit = getFetchInit(1)
    const refreshHeaders = refreshInit.headers as Headers
    const retryHeaders = getFetchInit(2).headers as Headers

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/v1/auth/refresh')
    expect(refreshInit.method).toBe('POST')
    expect(refreshInit.body).toBe(JSON.stringify({ refreshToken: 'refresh-token' }))
    expect(refreshHeaders.get('Content-Type')).toBe('application/json')
    expect(refreshHeaders.has('Authorization')).toBe(false)
    expect(initialHeaders.get('Authorization')).toBe('Bearer access-token')
    expect(retryHeaders.get('Authorization')).toBe('Bearer new-access-token')
    expect(setAuthTokensMock).toHaveBeenCalledWith('new-access-token', 'new-refresh-token')
    expect(clearAuthTokensMock).not.toHaveBeenCalled()
  })

  it('clears tokens and keeps the original 401 when refresh token is missing', async () => {
    const authExpiredListener = vi.fn()
    const removeListener = addAuthSessionExpiredListener(authExpiredListener)
    getRefreshTokenMock.mockReturnValue(null)
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'expired' }), { status: 401 }),
    )

    try {
      await expect(requestVoid('/api/v1/match/join')).rejects.toBeInstanceOf(ApiClientError)
    } finally {
      removeListener()
    }

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(authExpiredListener).toHaveBeenCalledTimes(1)
    expect(setAuthTokensMock).not.toHaveBeenCalled()
  })

  it('clears tokens and does not retry the original request when refresh fails', async () => {
    const authExpiredListener = vi.fn()
    const removeListener = addAuthSessionExpiredListener(authExpiredListener)
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: 'invalid refresh' }), { status: 401 }),
      )

    try {
      await expect(requestVoid('/api/v1/match/join')).rejects.toBeInstanceOf(ApiClientError)
    } finally {
      removeListener()
    }

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/v1/auth/refresh')
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(authExpiredListener).toHaveBeenCalledTimes(1)
    expect(setAuthTokensMock).not.toHaveBeenCalled()
  })

  it('shares one refresh request across concurrent authenticated 401 responses', async () => {
    let protectedRequestCount = 0

    fetchMock.mockImplementation((url: string) => {
      if (url === 'http://localhost:8080/api/v1/auth/refresh') {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              accessToken: 'shared-access-token',
              refreshToken: 'shared-refresh-token',
            }),
            { status: 200 },
          ),
        )
      }

      protectedRequestCount += 1

      if (protectedRequestCount <= 2) {
        return Promise.resolve(
          new Response(JSON.stringify({ message: 'expired' }), { status: 401 }),
        )
      }

      return Promise.resolve(new Response('', { status: 200 }))
    })

    await Promise.all([
      requestVoid('/api/v1/match/join', { method: 'POST' }),
      requestVoid('/api/v1/match/leave', { method: 'DELETE' }),
    ])

    const refreshCalls = fetchMock.mock.calls.filter(
      ([url]) => url === 'http://localhost:8080/api/v1/auth/refresh',
    )

    expect(fetchMock).toHaveBeenCalledTimes(5)
    expect(refreshCalls).toHaveLength(1)
    expect(setAuthTokensMock).toHaveBeenCalledTimes(1)
  })

  it('does not repeat refresh when the retried request also returns 401', async () => {
    const authExpiredListener = vi.fn()
    const removeListener = addAuthSessionExpiredListener(authExpiredListener)
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: 'new-access-token',
            refreshToken: 'new-refresh-token',
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: 'still expired' }), { status: 401 }),
      )

    try {
      await expect(requestVoid('/api/v1/match/join')).rejects.toBeInstanceOf(ApiClientError)
    } finally {
      removeListener()
    }

    const refreshCalls = fetchMock.mock.calls.filter(
      ([url]) => url === 'http://localhost:8080/api/v1/auth/refresh',
    )

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(refreshCalls).toHaveLength(1)
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(authExpiredListener).toHaveBeenCalledTimes(1)
  })

  it('does not refresh public requests', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'invalid login' }), { status: 401 }),
    )

    await expect(
      requestVoid('/api/v1/auth/login', {
        method: 'POST',
        auth: false,
      }),
    ).rejects.toBeInstanceOf(ApiClientError)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(setAuthTokensMock).not.toHaveBeenCalled()
  })

  it('does not refresh requests that explicitly skip auth refresh', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'logout expired' }), { status: 401 }),
    )

    await expect(
      requestVoid('/api/v1/auth/logout', {
        method: 'POST',
        skipAuthRefresh: true,
      }),
    ).rejects.toBeInstanceOf(ApiClientError)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(setAuthTokensMock).not.toHaveBeenCalled()
  })
})
