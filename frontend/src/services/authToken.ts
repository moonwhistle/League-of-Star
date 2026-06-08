const ACCESS_TOKEN_KEY = 'league-of-star.accessToken'
const REFRESH_TOKEN_KEY = 'league-of-star.refreshToken'
const LEGACY_ACCESS_TOKEN_KEY = 'smite.accessToken'
const LEGACY_REFRESH_TOKEN_KEY = 'smite.refreshToken'

function getStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.localStorage
}

export function getAccessToken(): string | null {
  return getStoredValue(ACCESS_TOKEN_KEY, LEGACY_ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return getStoredValue(REFRESH_TOKEN_KEY, LEGACY_REFRESH_TOKEN_KEY)
}

export function setAuthTokens(accessToken: string, refreshToken: string): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(ACCESS_TOKEN_KEY, accessToken)
  storage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  storage.removeItem(LEGACY_ACCESS_TOKEN_KEY)
  storage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
}

export function clearAuthTokens(): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.removeItem(ACCESS_TOKEN_KEY)
  storage.removeItem(REFRESH_TOKEN_KEY)
  storage.removeItem(LEGACY_ACCESS_TOKEN_KEY)
  storage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
}

function getStoredValue(primaryKey: string, legacyKey: string): string | null {
  const storage = getStorage()

  if (storage === null) {
    return null
  }

  return storage.getItem(primaryKey) ?? storage.getItem(legacyKey)
}
