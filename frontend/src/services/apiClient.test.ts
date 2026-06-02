import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getAccessToken } from './authToken'
import { requestVoid } from './apiClient'

vi.mock('./authToken', () => ({
  getAccessToken: vi.fn(),
}))

const getAccessTokenMock = vi.mocked(getAccessToken)
const fetchMock = vi.fn()

function getFetchInit(): RequestInit {
  const call = fetchMock.mock.calls[0]

  if (call === undefined || call[1] === undefined) {
    throw new Error('fetch was not called with init.')
  }

  return call[1] as RequestInit
}

describe('apiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAccessTokenMock.mockReturnValue('access-token')
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
})
