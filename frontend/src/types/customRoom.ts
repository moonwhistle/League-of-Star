import type { GameRoomId, GameStartScenario } from './game'

export type CustomRoomStatus = 'WAITING' | 'STARTED' | 'CLOSED'

export type CustomRoomParticipantRole = 'OWNER' | 'PLAYER'

export interface CustomRoomParticipant {
  userId: number
  nickname: string
  role: CustomRoomParticipantRole
}

export interface CustomRoomResponse {
  roomId: number
  roomName: string
  inviteCode: string
  ownerUserId: number
  status: CustomRoomStatus
  maxParticipants: number
  participants: CustomRoomParticipant[]
}

export interface CustomRoomListItem {
  roomId: number
  roomName: string
  inviteCode: string
  ownerUserId: number
  status: CustomRoomStatus
  maxParticipants: number
  currentParticipants: number
}

export interface CustomRoomListResponse {
  rooms: CustomRoomListItem[]
}

export interface CustomGameStartResponse {
  roomId: number
  gameRoomId: GameRoomId
  gameMode: 'CUSTOM'
  serverTime: number
  startAt: number
  webSocketUrl: string
  scenario: GameStartScenario
}

export type CustomRoomWebSocketServerMessage =
  | {
      type: 'ROOM_UPDATED'
      payload: CustomRoomResponse
    }
  | {
      type: 'ROOM_CLOSED'
      payload: CustomRoomResponse
    }
  | {
      type: 'ROOM_STARTED'
      payload: CustomGameStartResponse
    }
  | {
      type: 'ERROR'
      payload: {
        code: string
        reason: string
      }
    }
