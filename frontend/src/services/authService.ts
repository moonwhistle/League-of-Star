import type { LoginRequest, LoginResponse } from '@/types/auth'

import { requestJson } from './apiClient'

export function login(request: LoginRequest, signal?: AbortSignal): Promise<LoginResponse> {
  return requestJson<LoginResponse, LoginRequest>('/api/v1/auth/login', {
    method: 'POST',
    body: request,
    signal,
    auth: false,
  })
}
