import type {
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  SignupRequest,
  SignupResponse,
  TokenRefreshRequest,
  TokenRefreshResponse,
} from '@/types/auth'

import { requestJson } from './apiClient'

export function login(request: LoginRequest, signal?: AbortSignal): Promise<LoginResponse> {
  return requestJson<LoginResponse, LoginRequest>('/api/v1/auth/login', {
    method: 'POST',
    body: request,
    signal,
    auth: false,
  })
}

export function signup(request: SignupRequest, signal?: AbortSignal): Promise<SignupResponse> {
  return requestJson<SignupResponse, SignupRequest>('/api/v1/auth/signUp', {
    method: 'POST',
    body: request,
    signal,
    auth: false,
  })
}

export function logout(refreshToken: string, signal?: AbortSignal): Promise<void> {
  return requestJson<void, LogoutRequest>('/api/v1/auth/logout', {
    method: 'POST',
    body: {
      refreshToken,
    },
    signal,
    skipAuthRefresh: true,
  })
}

export function refresh(refreshToken: string, signal?: AbortSignal): Promise<TokenRefreshResponse> {
  return requestJson<TokenRefreshResponse, TokenRefreshRequest>('/api/v1/auth/refresh', {
    method: 'POST',
    body: {
      refreshToken,
    },
    signal,
    auth: false,
  })
}
