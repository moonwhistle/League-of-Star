import { API_BASE_URL } from '@/constants/env'
import type { ApiErrorBody, ApiRequestOptions } from '@/types/api'

import { getAccessToken } from './authToken'

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
  const response = await fetch(buildApiUrl(path), {
    method: options.method ?? 'GET',
    headers: buildHeaders(options),
    body: serializeBody(options.body),
    signal: options.signal,
  })

  const responseBody = await readResponseBody(response)

  if (!response.ok) {
    throw new ApiClientError(response.status, responseBody)
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
