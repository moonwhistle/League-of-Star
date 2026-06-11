type AuthSessionExpiredListener = () => void

const authSessionExpiredListeners = new Set<AuthSessionExpiredListener>()

export function addAuthSessionExpiredListener(listener: AuthSessionExpiredListener): () => void {
  authSessionExpiredListeners.add(listener)

  return () => {
    authSessionExpiredListeners.delete(listener)
  }
}

export function notifyAuthSessionExpired(): void {
  for (const listener of authSessionExpiredListeners) {
    listener()
  }
}
