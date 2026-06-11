import { API_BASE_URL } from '@/constants/env'
import type { ApiErrorBody, ApiRequestOptions } from '@/types/api'
import type { TokenRefreshRequest, TokenRefreshResponse } from '@/types/auth'

import { clearAuthTokens, getAccessToken, getRefreshToken, setAuthTokens } from './authToken'

let refreshRequestPromise: Promise<boolean> | null = null

export class ApiClientError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, body: unknown) {
    super(getErrorMessage(status, body))
    this.name = 'ApiClientError'
    this.status = status
    this.body = body
  }
}

export async function requestJson<TResponse, TBody = unknown>(
  path: string,
  options: ApiRequestOptions<TBody> = {},
): Promise<TResponse> {
  const response = await sendRequest(path, options)

  const responseBody = await readResponseBody(response)

  if (!response.ok) {
    const error = new ApiClientError(response.status, responseBody)

    if (!isRefreshEligible(response.status, options)) {
      throw error
    }

    const isRefreshSuccessful = await refreshAuthSession()

    if (!isRefreshSuccessful) {
      throw error
    }

    const retryResponse = await sendRequest(path, {
      ...options,
      skipAuthRefresh: true,
    })
    const retryResponseBody = await readResponseBody(retryResponse)

    if (!retryResponse.ok) {
      throw new ApiClientError(retryResponse.status, retryResponseBody)
    }

    return retryResponseBody as TResponse
  }

  return responseBody as TResponse
}

export async function requestVoid<TBody = unknown>(
  path: string,
  options: ApiRequestOptions<TBody> = {},
): Promise<void> {
  await requestJson<void, TBody>(path, options)
}

function buildApiUrl(path: string): string {
  const baseUrl = API_BASE_URL.replace(/\/$/, '')
  const normalizedPath = path.replace(/^\//, '')

  return `${baseUrl}/${normalizedPath}`
}

function sendRequest<TBody>(path: string, options: ApiRequestOptions<TBody>): Promise<Response> {
  return fetch(buildApiUrl(path), {
    method: options.method ?? 'GET',
    headers: buildHeaders(options),
    body: serializeBody(options.body),
    signal: options.signal,
  })
}

function buildHeaders<TBody>(options: ApiRequestOptions<TBody>): Headers {
  const headers = new Headers(options.headers)

  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (options.auth !== false) {
    const accessToken = getAccessToken()

    if (accessToken !== null) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }
  }

  return headers
}

function serializeBody(body: unknown): BodyInit | undefined {
  return body === undefined ? undefined : JSON.stringify(body)
}

function isRefreshEligible<TBody>(status: number, options: ApiRequestOptions<TBody>): boolean {
  return status === 401 && options.auth !== false && options.skipAuthRefresh !== true
}

async function refreshAuthSession(): Promise<boolean> {
  refreshRequestPromise ??= performRefreshAuthSession().finally(() => {
    refreshRequestPromise = null
  })

  return refreshRequestPromise
}

async function performRefreshAuthSession(): Promise<boolean> {
  const refreshToken = getRefreshToken()

  if (refreshToken === null || refreshToken.trim() === '') {
    clearAuthTokens()
    return false
  }

  try {
    const response = await fetch(buildApiUrl('/api/v1/auth/refresh'), {
      method: 'POST',
      headers: buildRefreshHeaders(),
      body: serializeBody({
        refreshToken,
      } satisfies TokenRefreshRequest),
    })
    const responseBody = await readResponseBody(response)

    if (!response.ok || !isTokenRefreshResponse(responseBody)) {
      clearAuthTokens()
      return false
    }

    setAuthTokens(responseBody.accessToken, responseBody.refreshToken)
    return true
  } catch {
    clearAuthTokens()
    return false
  }
}

function buildRefreshHeaders(): Headers {
  const headers = new Headers()

  headers.set('Content-Type', 'application/json')

  return headers
}

function isTokenRefreshResponse(body: unknown): body is TokenRefreshResponse {
  if (typeof body !== 'object' || body === null) {
    return false
  }

  const response = body as Partial<TokenRefreshResponse>

  return typeof response.accessToken === 'string' && typeof response.refreshToken === 'string'
}

async function readResponseBody(response: Response): Promise<unknown> {
  const text = await response.text()

  if (text.trim() === '') {
    return undefined
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

function getErrorMessage(status: number, body: unknown): string {
  if (isApiErrorBody(body) && typeof body.message === 'string') {
    return body.message
  }

  return `API request failed with status ${status}`
}

function isApiErrorBody(body: unknown): body is ApiErrorBody {
  return typeof body === 'object' && body !== null
}
