import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAuthTokens } from '../authToken'
import { connectCustomRoomWebSocket } from './customRoomWebSocket'

class MockWebSocket extends EventTarget {
  static instances: MockWebSocket[] = []

  readonly url: string
  close = vi.fn((code?: number, reason?: string) => {
    this.dispatchEvent(new CloseEvent('close', { code, reason }))
  })

  constructor(url: string) {
    super()
    this.url = url
    MockWebSocket.instances.push(this)
  }

  emitMessage(data: string): void {
    this.dispatchEvent(new MessageEvent('message', { data }))
  }

  emitOpen(): void {
    this.dispatchEvent(new Event('open'))
  }

  emitError(): void {
    this.dispatchEvent(new Event('error'))
  }
}

describe('connectCustomRoomWebSocket', () => {
  const OriginalWebSocket = globalThis.WebSocket

  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    MockWebSocket.instances = []
    globalThis.WebSocket = MockWebSocket as unknown as typeof WebSocket
    setAuthTokens('access token/with space', 'refresh-token')
  })

  afterEach(() => {
    globalThis.WebSocket = OriginalWebSocket
  })

  it('connects custom room websocket with the configured websocket base URL and token', () => {
    connectCustomRoomWebSocket(100)

    expect(MockWebSocket.instances[0]?.url).toBe(
      'ws://localhost:8080/ws/custom-games/rooms/100?token=access+token%2Fwith+space',
    )
  })

  it('encodes roomId when building the websocket URL', () => {
    connectCustomRoomWebSocket('room/with space')

    expect(MockWebSocket.instances[0]?.url).toBe(
      'ws://localhost:8080/ws/custom-games/rooms/room%2Fwith%20space?token=access+token%2Fwith+space',
    )
  })

  it('does not connect without an access token', () => {
    window.localStorage.clear()

    expect(() => connectCustomRoomWebSocket(100)).toThrow(
      'Custom Room WebSocket connection requires an access token.',
    )
    expect(MockWebSocket.instances).toHaveLength(0)
  })

  it('does not connect without a roomId', () => {
    expect(() => connectCustomRoomWebSocket(' ')).toThrow(
      'Custom Room WebSocket roomId is required.',
    )
    expect(MockWebSocket.instances).toHaveLength(0)
  })

  it('dispatches parsed server messages and reports invalid JSON through onError', () => {
    const onMessage = vi.fn()
    const onError = vi.fn()

    connectCustomRoomWebSocket(100, { onMessage, onError })
    const socket = MockWebSocket.instances[0]

    socket?.emitMessage('{"type":"ROOM_UPDATED","payload":{"roomId":100,"participants":[]}}')
    socket?.emitMessage('{')

    expect(onMessage).toHaveBeenCalledWith(
      {
        type: 'ROOM_UPDATED',
        payload: {
          roomId: 100,
          participants: [],
        },
      },
      expect.any(MessageEvent),
    )
    expect(onError).toHaveBeenCalledWith(expect.any(SyntaxError))
  })

  it('dispatches ROOM_STARTED messages with the custom game start payload', () => {
    const onMessage = vi.fn()

    connectCustomRoomWebSocket(100, { onMessage })
    const socket = MockWebSocket.instances[0]

    socket?.emitMessage(
      JSON.stringify({
        type: 'ROOM_STARTED',
        payload: {
          roomId: 100,
          gameRoomId: 200,
          gameMode: 'CUSTOM',
          serverTime: 10_000,
          startAt: 13_000,
          webSocketUrl: '/ws/game/200',
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 12000,
            hpTimeline: [{ timeMs: 0, hp: 10000 }],
          },
        },
      }),
    )

    expect(onMessage).toHaveBeenCalledWith(
      {
        type: 'ROOM_STARTED',
        payload: {
          roomId: 100,
          gameRoomId: 200,
          gameMode: 'CUSTOM',
          serverTime: 10_000,
          startAt: 13_000,
          webSocketUrl: '/ws/game/200',
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 12000,
            hpTimeline: [{ timeMs: 0, hp: 10000 }],
          },
        },
      },
      expect.any(MessageEvent),
    )
  })

  it('can replace handlers on an existing connection', () => {
    const firstOnMessage = vi.fn()
    const secondOnMessage = vi.fn()
    const connection = connectCustomRoomWebSocket(100, {
      onMessage: firstOnMessage,
    })
    const socket = MockWebSocket.instances[0]

    socket?.emitMessage('{"type":"ROOM_UPDATED","payload":{"roomId":100}}')
    connection.setHandlers({
      onMessage: secondOnMessage,
    })
    socket?.emitMessage('{"type":"ROOM_CLOSED","payload":{"roomId":100}}')

    expect(firstOnMessage).toHaveBeenCalledTimes(1)
    expect(secondOnMessage).toHaveBeenCalledWith(
      {
        type: 'ROOM_CLOSED',
        payload: {
          roomId: 100,
        },
      },
      expect.any(MessageEvent),
    )
  })

  it('closes the socket once even if close is called repeatedly', () => {
    const connection = connectCustomRoomWebSocket(100)
    const socket = MockWebSocket.instances[0]

    connection.close(1000, 'done')
    connection.close(1000, 'done')

    expect(socket?.close).toHaveBeenCalledTimes(1)
    expect(socket?.close).toHaveBeenCalledWith(1000, 'done')
  })
})
