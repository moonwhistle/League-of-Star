export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userId: number
  nickname: string
}

export interface SignupRequest {
  email: string
  password: string
  nickname: string
}

export interface SignupResponse {
  id: number
  email: string
  nickname: string
}

export interface LogoutRequest {
  refreshToken: string
}

export interface PasswordResetRequest {
  email: string
}

export interface PasswordResetSubmitRequest {
  token: string
  newPassword: string
}

export interface TokenRefreshRequest {
  refreshToken: string
}

export interface TokenRefreshResponse {
  accessToken: string
  refreshToken: string
}
