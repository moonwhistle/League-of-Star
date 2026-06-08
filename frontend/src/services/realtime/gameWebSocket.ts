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
  onError?: (error: Event | unknown) => void
  onClose?: (event: CloseEvent) => void
}

export interface GameWebSocketConnection {
  socket: WebSocket
  setHandlers: (handlers?: GameWebSocketHandlers) => void
  sendClientReady: () => void
  sendRttPong: (seq: number) => void
  sendLightning: () => void
  close: (code?: number, reason?: string) => void
}

export function connectGameWebSocket(
  webSocketUrlOrGameRoomId: string | GameRoomId,
  handlers: GameWebSocketHandlers = {},
): GameWebSocketConnection {
  const socket = new WebSocket(buildGameWebSocketUrl(webSocketUrlOrGameRoomId))
  let currentHandlers = handlers
  let isClosed = false

  socket.addEventListener('open', (event) => currentHandlers.onOpen?.(event))
  socket.addEventListener('message', (event: MessageEvent<string>) => {
    try {
      currentHandlers.onMessage?.(JSON.parse(event.data) as GameWebSocketServerMessage, event)
    } catch (error) {
      currentHandlers.onError?.(error)
    }
  })
  socket.addEventListener('error', (event) => currentHandlers.onError?.(event))
  socket.addEventListener('close', (event) => {
    isClosed = true
    currentHandlers.onClose?.(event)
  })

  return {
    socket,
    setHandlers: (nextHandlers = {}) => {
      currentHandlers = nextHandlers
    },
    sendClientReady: () => sendMessage(socket, { type: 'CLIENT_READY', payload: {} }),
    sendRttPong: (seq) => sendMessage(socket, { type: 'RTT_PONG', payload: { seq } }),
    sendLightning: () => sendMessage(socket, { type: 'LIGHTNING', payload: null }),
    close: (code, reason) => {
      if (isClosed) {
        return
      }

      isClosed = true
      socket.close(code, reason)
    },
  }
}

function buildGameWebSocketUrl(webSocketUrlOrGameRoomId: string | GameRoomId): string {
  const accessToken = getAccessToken()

  if (accessToken === null || accessToken.trim() === '') {
    throw new Error('Game WebSocket connection requires an access token.')
  }

  const webSocketUrl =
    typeof webSocketUrlOrGameRoomId === 'number'
      ? `/ws/game/${webSocketUrlOrGameRoomId}`
      : webSocketUrlOrGameRoomId
  const url = resolveGameWebSocketUrl(webSocketUrl)
  url.searchParams.set('token', accessToken)

  return url.toString()
}

function resolveGameWebSocketUrl(webSocketUrl: string): URL {
  const trimmedWebSocketUrl = webSocketUrl.trim()

  if (trimmedWebSocketUrl === '') {
    throw new Error('Game WebSocket URL is required.')
  }

  if (trimmedWebSocketUrl.startsWith('ws://') || trimmedWebSocketUrl.startsWith('wss://')) {
    return new URL(trimmedWebSocketUrl)
  }

  const baseUrl = WS_BASE_URL.replace(/\/$/, '')
  const relativePath = trimmedWebSocketUrl.startsWith('/')
    ? trimmedWebSocketUrl
    : `/${trimmedWebSocketUrl}`

  return new URL(`${baseUrl}${relativePath}`)
}

function sendMessage(socket: WebSocket, message: GameWebSocketClientMessage): void {
  socket.send(JSON.stringify(message))
}
