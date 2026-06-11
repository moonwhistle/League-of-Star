export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface ApiRequestOptions<TBody = unknown> {
  method?: HttpMethod
  body?: TBody
  headers?: HeadersInit
  signal?: AbortSignal
  auth?: boolean
  skipAuthRefresh?: boolean
}

export interface ApiErrorBody {
  message?: string
  code?: string
  [key: string]: unknown
}
