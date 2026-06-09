import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAuthTokens } from '../authToken'
import { connectGameWebSocket } from './gameWebSocket'

class MockWebSocket extends EventTarget {
  static instances: MockWebSocket[] = []

  readonly sentMessages: string[] = []
  readonly url: string
  close = vi.fn((code?: number, reason?: string) => {
    this.dispatchEvent(new CloseEvent('close', { code, reason }))
  })

  constructor(url: string) {
    super()
    this.url = url
    MockWebSocket.instances.push(this)
  }

  send(message: string): void {
    this.sentMessages.push(message)
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

describe('connectGameWebSocket', () => {
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

  it('connects relative webSocketUrl with the configured websocket base URL and token', () => {
    connectGameWebSocket('/ws/game/100')

    expect(MockWebSocket.instances[0]?.url).toBe(
      'ws://localhost:8080/ws/game/100?token=access+token%2Fwith+space',
    )
  })

  it('keeps absolute websocket URLs and appends token to existing query parameters', () => {
    connectGameWebSocket('wss://game.example.test/ws/game/100?room=blue')

    expect(MockWebSocket.instances[0]?.url).toBe(
      'wss://game.example.test/ws/game/100?room=blue&token=access+token%2Fwith+space',
    )
  })

  it('keeps the previous gameRoomId input path for compatibility', () => {
    connectGameWebSocket(100)

    expect(MockWebSocket.instances[0]?.url).toBe(
      'ws://localhost:8080/ws/game/100?token=access+token%2Fwith+space',
    )
  })

  it('does not connect without an access token', () => {
    window.localStorage.clear()

    expect(() => connectGameWebSocket('/ws/game/100')).toThrow(
      'Game WebSocket connection requires an access token.',
    )
    expect(MockWebSocket.instances).toHaveLength(0)
  })

  it('dispatches parsed server messages and reports invalid JSON through onError', () => {
    const onMessage = vi.fn()
    const onError = vi.fn()

    connectGameWebSocket('/ws/game/100', { onMessage, onError })
    const socket = MockWebSocket.instances[0]

    socket?.emitMessage('{"type":"RTT_PING","payload":{"seq":3}}')
    socket?.emitMessage('{')

    expect(onMessage).toHaveBeenCalledWith(
      {
        type: 'RTT_PING',
        payload: {
          seq: 3,
        },
      },
      expect.any(MessageEvent),
    )
    expect(onError).toHaveBeenCalledWith(expect.any(SyntaxError))
  })

  it('can replace handlers on an existing connection for route handoff', () => {
    const waitingOnMessage = vi.fn()
    const playOnMessage = vi.fn()
    const connection = connectGameWebSocket('/ws/game/100', {
      onMessage: waitingOnMessage,
    })
    const socket = MockWebSocket.instances[0]

    socket?.emitMessage('{"type":"PLAYER_READY","payload":{"userId":2,"bothReady":true}}')
    connection.setHandlers({
      onMessage: playOnMessage,
    })
    socket?.emitMessage('{"type":"GAME_RESULT","payload":{"gameRoomId":100}}')

    expect(waitingOnMessage).toHaveBeenCalledTimes(1)
    expect(playOnMessage).toHaveBeenCalledTimes(1)
    expect(playOnMessage).toHaveBeenCalledWith(
      {
        type: 'GAME_RESULT',
        payload: {
          gameRoomId: 100,
        },
      },
      expect.any(MessageEvent),
    )
  })

  it('sends game websocket client messages using the backend envelope', () => {
    const connection = connectGameWebSocket('/ws/game/100')
    const socket = MockWebSocket.instances[0]

    connection.sendClientReady()
    connection.sendRttPong(7)
    connection.sendLightning()

    expect(socket?.sentMessages).toEqual([
      '{"type":"CLIENT_READY","payload":{}}',
      '{"type":"RTT_PONG","payload":{"seq":7}}',
      '{"type":"LIGHTNING","payload":null}',
    ])
  })

  it('closes the socket once even if close is called repeatedly', () => {
    const connection = connectGameWebSocket('/ws/game/100')
    const socket = MockWebSocket.instances[0]

    connection.close(1000, 'done')
    connection.close(1000, 'done')

    expect(socket?.close).toHaveBeenCalledTimes(1)
    expect(socket?.close).toHaveBeenCalledWith(1000, 'done')
  })
})
