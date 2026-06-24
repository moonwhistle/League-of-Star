const CURRENT_CUSTOM_ROOM_ID_KEY = 'league-of-star.currentCustomRoomId'

function getStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.localStorage
}

export function getCurrentCustomRoomId(): string | null {
  const value = getStorage()?.getItem(CURRENT_CUSTOM_ROOM_ID_KEY)?.trim() ?? ''

  return value === '' ? null : value
}

export function rememberCurrentCustomRoom(roomId: number | string): void {
  const normalizedRoomId = String(roomId).trim()

  if (normalizedRoomId === '') {
    return
  }

  getStorage()?.setItem(CURRENT_CUSTOM_ROOM_ID_KEY, normalizedRoomId)
}

export function clearCurrentCustomRoom(): void {
  getStorage()?.removeItem(CURRENT_CUSTOM_ROOM_ID_KEY)
}
