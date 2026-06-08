import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { GameWebSocketConnection } from './gameWebSocket'
import {
  clearGameWebSocketHandoff,
  handoffGameWebSocket,
  takeGameWebSocketHandoff,
} from './gameWebSocketHandoff'

describe('gameWebSocketHandoff', () => {
  beforeEach(() => {
    clearGameWebSocketHandoff()
  })

  it('stores and consumes a handed off connection once by gameRoomId', () => {
    const connection = createConnection()

    handoffGameWebSocket(100, connection)

    expect(takeGameWebSocketHandoff('100')).toBe(connection)
    expect(takeGameWebSocketHandoff('100')).toBeNull()
    expect(connection.close).not.toHaveBeenCalled()
  })

  it('closes the previous connection when replacing a handoff for the same room', () => {
    const previousConnection = createConnection()
    const nextConnection = createConnection()

    handoffGameWebSocket(100, previousConnection)
    handoffGameWebSocket(100, nextConnection)

    expect(previousConnection.close).toHaveBeenCalledTimes(1)
    expect(takeGameWebSocketHandoff(100)).toBe(nextConnection)
  })

  it('can clear one handoff or every stored handoff', () => {
    const firstConnection = createConnection()
    const secondConnection = createConnection()

    handoffGameWebSocket(100, firstConnection)
    handoffGameWebSocket(101, secondConnection)
    clearGameWebSocketHandoff(100)

    expect(firstConnection.close).toHaveBeenCalledTimes(1)
    expect(takeGameWebSocketHandoff(100)).toBeNull()
    expect(takeGameWebSocketHandoff(101)).toBe(secondConnection)

    handoffGameWebSocket(101, secondConnection)
    clearGameWebSocketHandoff()

    expect(secondConnection.close).toHaveBeenCalledTimes(1)
    expect(takeGameWebSocketHandoff(101)).toBeNull()
  })
})

function createConnection(): GameWebSocketConnection {
  return {
    socket: {} as WebSocket,
    setHandlers: vi.fn(),
    sendClientReady: vi.fn(),
    sendRttPong: vi.fn(),
    sendLightning: vi.fn(),
    close: vi.fn(),
  }
}
