import { WS_BASE_URL } from '@/constants/env'
import type { CustomRoomWebSocketServerMessage } from '@/types/customRoom'

import { getAccessToken } from '../authToken'

export interface CustomRoomWebSocketHandlers {
  onOpen?: (event: Event) => void
  onMessage?: (message: CustomRoomWebSocketServerMessage, event: MessageEvent<string>) => void
  onError?: (error: Event | unknown) => void
  onClose?: (event: CloseEvent) => void
}

export interface CustomRoomWebSocketConnection {
  socket: WebSocket
  setHandlers: (handlers?: CustomRoomWebSocketHandlers) => void
  close: (code?: number, reason?: string) => void
}

export function connectCustomRoomWebSocket(
  roomId: number | string,
  handlers: CustomRoomWebSocketHandlers = {},
): CustomRoomWebSocketConnection {
  const socket = new WebSocket(buildCustomRoomWebSocketUrl(roomId))
  let currentHandlers = handlers
  let isClosed = false

  socket.addEventListener('open', (event) => currentHandlers.onOpen?.(event))
  socket.addEventListener('message', (event: MessageEvent<string>) => {
    try {
      currentHandlers.onMessage?.(JSON.parse(event.data) as CustomRoomWebSocketServerMessage, event)
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
    close: (code, reason) => {
      if (isClosed) {
        return
      }

      isClosed = true
      socket.close(code, reason)
    },
  }
}

function buildCustomRoomWebSocketUrl(roomId: number | string): string {
  const accessToken = getAccessToken()

  if (accessToken === null || accessToken.trim() === '') {
    throw new Error('Custom Room WebSocket connection requires an access token.')
  }

  const normalizedRoomId = String(roomId).trim()

  if (normalizedRoomId === '') {
    throw new Error('Custom Room WebSocket roomId is required.')
  }

  const url = resolveCustomRoomWebSocketUrl(
    `/ws/custom-games/rooms/${encodeURIComponent(normalizedRoomId)}`,
  )
  url.searchParams.set('token', accessToken)

  return url.toString()
}

function resolveCustomRoomWebSocketUrl(webSocketUrl: string): URL {
  const baseUrl = WS_BASE_URL.replace(/\/$/, '')

  return new URL(`${baseUrl}${webSocketUrl}`)
}
