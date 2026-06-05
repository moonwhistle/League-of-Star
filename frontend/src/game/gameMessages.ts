import type { GameWebSocketClientMessage, GameWebSocketServerMessage } from '@/types/game'

export type { GameWebSocketClientMessage, GameWebSocketServerMessage }

export function createClientReadyMessage(): GameWebSocketClientMessage {
  return {
    type: 'CLIENT_READY',
    payload: {},
  }
}

export function createRttPongMessage(seq: number): GameWebSocketClientMessage {
  return {
    type: 'RTT_PONG',
    payload: {
      seq,
    },
  }
}

export function createSmiteMessage(): GameWebSocketClientMessage {
  return {
    type: 'SMITE',
    payload: null,
  }
}
