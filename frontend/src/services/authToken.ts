const ACCESS_TOKEN_KEY = 'smite.accessToken'
const REFRESH_TOKEN_KEY = 'smite.refreshToken'

function getStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.localStorage
}

export function getAccessToken(): string | null {
  return getStorage()?.getItem(ACCESS_TOKEN_KEY) ?? null
}

export function getRefreshToken(): string | null {
  return getStorage()?.getItem(REFRESH_TOKEN_KEY) ?? null
}

export function setAuthTokens(accessToken: string, refreshToken: string): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(ACCESS_TOKEN_KEY, accessToken)
  storage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearAuthTokens(): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.removeItem(ACCESS_TOKEN_KEY)
  storage.removeItem(REFRESH_TOKEN_KEY)
}
