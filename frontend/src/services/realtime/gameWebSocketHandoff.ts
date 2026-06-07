import type { GameRoomId } from '@/types/game'

import type { GameWebSocketConnection } from './gameWebSocket'

const gameWebSocketHandoffs = new Map<string, GameWebSocketConnection>()

export function handoffGameWebSocket(
  gameRoomId: string | GameRoomId,
  connection: GameWebSocketConnection,
): void {
  const key = normalizeGameRoomId(gameRoomId)
  const previousConnection = gameWebSocketHandoffs.get(key)

  if (previousConnection !== undefined && previousConnection !== connection) {
    previousConnection.close()
  }

  gameWebSocketHandoffs.set(key, connection)
}

export function takeGameWebSocketHandoff(
  gameRoomId: string | GameRoomId,
): GameWebSocketConnection | null {
  const key = normalizeGameRoomId(gameRoomId)
  const connection = gameWebSocketHandoffs.get(key)

  if (connection === undefined) {
    return null
  }

  gameWebSocketHandoffs.delete(key)

  return connection
}

export function clearGameWebSocketHandoff(gameRoomId?: string | GameRoomId): void {
  if (gameRoomId !== undefined) {
    const key = normalizeGameRoomId(gameRoomId)
    const connection = gameWebSocketHandoffs.get(key)
    connection?.close()
    gameWebSocketHandoffs.delete(key)
    return
  }

  for (const connection of gameWebSocketHandoffs.values()) {
    connection.close()
  }

  gameWebSocketHandoffs.clear()
}

function normalizeGameRoomId(gameRoomId: string | GameRoomId): string {
  return String(gameRoomId).trim()
}
