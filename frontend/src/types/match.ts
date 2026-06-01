export type MatchId = string

export type MatchNotificationEventName =
  | 'connected'
  | 'heartbeat'
  | 'match_found'
  | 'match_response_result'

export interface MatchSseConnectedEvent {
  userId: number
  connectedAt: string
}

export interface MatchSseHeartbeatEvent {
  sentAt: string
}

export interface MatchFoundNotification {
  matchId: MatchId
  userId: number
  opponentUserId: number
  acceptTimeoutSeconds: number
  eventCreatedAt: string
}

export type MatchResponseOutcome = 'MATCHED' | 'FAILED'

export type MatchResponseReason =
  | 'BOTH_ACCEPTED'
  | 'MY_REJECTED'
  | 'OPPONENT_REJECTED'
  | 'MY_TIMEOUT'
  | 'OPPONENT_TIMEOUT'
  | 'BOTH_TIMEOUT'
  | 'GAME_SETUP_FAILED'

export type MatchResponseAction = 'GO_TO_GAME_WAITING' | 'GO_TO_MATCH_START' | 'RETURN_TO_MATCHING'

export interface MatchResponseOpponent {
  userId: number
  nickname: string
  tier: string
  tierScore: number
}

export interface MatchResponseGame {
  gameRoomId: number
  videoUrl: string
  webSocketUrl: string
}

export interface MatchResponseResultNotification {
  matchId: MatchId
  outcome: MatchResponseOutcome
  reason: MatchResponseReason
  action: MatchResponseAction
  opponent: MatchResponseOpponent | null
  game: MatchResponseGame | null
}
