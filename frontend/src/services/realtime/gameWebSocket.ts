import { WS_BASE_URL } from '@/constants/env'
import type {
  GameRoomId,
  GameWebSocketClientMessage,
  GameWebSocketServerMessage,
} from '@/types/game'

import { getAccessToken } from '../authToken'

export interface GameWebSocketHandlers {
  onOpen?: (event: Event) => void
  onMessage?: (message: GameWebSocketServerMessage, event: MessageEvent<string>) => void
  onError?: (event: Event) => void
  onClose?: (event: CloseEvent) => void
}

export interface GameWebSocketConnection {
  socket: WebSocket
  sendClientReady: () => void
  sendRttPong: (seq: number) => void
  sendSmite: () => void
  close: (code?: number, reason?: string) => void
}

export function connectGameWebSocket(
  gameRoomId: GameRoomId,
  handlers: GameWebSocketHandlers = {},
): GameWebSocketConnection {
  const socket = new WebSocket(buildGameWebSocketUrl(gameRoomId))

  socket.addEventListener('open', (event) => handlers.onOpen?.(event))
  socket.addEventListener('message', (event: MessageEvent<string>) => {
    handlers.onMessage?.(JSON.parse(event.data) as GameWebSocketServerMessage, event)
  })
  socket.addEventListener('error', (event) => handlers.onError?.(event))
  socket.addEventListener('close', (event) => handlers.onClose?.(event))

  return {
    socket,
    sendClientReady: () => sendMessage(socket, { type: 'CLIENT_READY', payload: null }),
    sendRttPong: (seq) => sendMessage(socket, { type: 'RTT_PONG', payload: { seq } }),
    sendSmite: () => sendMessage(socket, { type: 'SMITE', payload: null }),
    close: (code, reason) => socket.close(code, reason),
  }
}

function buildGameWebSocketUrl(gameRoomId: GameRoomId): string {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new Error('Game WebSocket connection requires an access token.')
  }

  const baseUrl = WS_BASE_URL.replace(/\/$/, '')
  const token = encodeURIComponent(accessToken)

  return `${baseUrl}/ws/game/${gameRoomId}?token=${token}`
}

function sendMessage(socket: WebSocket, message: GameWebSocketClientMessage): void {
  socket.send(JSON.stringify(message))
}
