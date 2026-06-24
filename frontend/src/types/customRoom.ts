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
