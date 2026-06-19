import type { GameId, GameRecordResult } from './game'

export interface GameRecordListResponse {
  page: number
  size: number
  totalPages: number
  totalElements: number
  hasNext: boolean
  records: GameRecordEntryResponse[]
}

export interface GameRecordEntryResponse {
  gameId: GameId
  result: GameRecordResult
  opponentUserId: number
  opponentNickname: string
  rankBefore: string
  rankAfter: string
  lpBefore: number
  lpAfter: number
  lpChange: number
  playedAt: string
}
