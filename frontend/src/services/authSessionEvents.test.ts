import { describe, expect, it, vi } from 'vitest'

import { addAuthSessionExpiredListener, notifyAuthSessionExpired } from './authSessionEvents'

describe('authSessionEvents', () => {
  it('notifies registered auth session expired listeners', () => {
    const listener = vi.fn()
    const removeListener = addAuthSessionExpiredListener(listener)

    notifyAuthSessionExpired()

    expect(listener).toHaveBeenCalledTimes(1)

    removeListener()
    notifyAuthSessionExpired()

    expect(listener).toHaveBeenCalledTimes(1)
  })
})
