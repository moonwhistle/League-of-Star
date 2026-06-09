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

/**
 * Final backend-settled match response result.
 *
 * HTTP accept/reject responses are command acknowledgements only. Page transition
 * must be driven by this SSE payload's action.
 */
export interface MatchResponseOpponent {
  userId: number
  nickname: string
  tier: string
  tierScore: number
}

/**
 * Required only when action is GO_TO_GAME_WAITING.
 * Failed outcomes can legally send game as null.
 */
export interface MatchResponseGame {
  gameRoomId: number
  webSocketUrl: string
}

/**
 * Action policy:
 * - GO_TO_GAME_WAITING: close match SSE and move to game waiting with game payload.
 * - GO_TO_MATCH_START: close match SSE and return to ready state.
 * - RETURN_TO_MATCHING: backend already restored this user to queue; keep match SSE
 *   open and do not call join/leave.
 */
export interface MatchResponseResultNotification {
  matchId: MatchId
  outcome: MatchResponseOutcome
  reason: MatchResponseReason
  action: MatchResponseAction
  opponent: MatchResponseOpponent | null
  game: MatchResponseGame | null
}
