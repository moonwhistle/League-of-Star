export type GameId = number

export type GameRoomId = number

export type GameSummaryStatus = 'PENDING' | 'DONE'

export type GameResult = 'PLAYER1_WIN' | 'PLAYER2_WIN' | 'DRAW'

export type GameRecordResult = 'WIN' | 'LOSS' | 'DRAW'

export type GameRecordSeriesType = 'RANK' | 'PLACEMENT' | 'PROMOTION'

export interface GameSummaryPlayer {
  userId: number
  nickname: string
  result: GameRecordResult
  lpBefore: number
  lpAfter: number
  lpChange: number
  rankBefore: string
  rankAfter: string
  seriesType: GameRecordSeriesType
  rankSeriesId: number | null
}

export interface GameSummaryPendingResponse {
  summaryStatus: 'PENDING'
  gameId: GameId
  retryAfterMillis: number
}

export interface GameSummaryDoneResponse {
  summaryStatus: 'DONE'
  gameId: GameId
  gameResult: GameResult
  winnerUserId: number | null
  finishedAt: string
  me: GameSummaryPlayer
  opponent: GameSummaryPlayer
}

export type GameSummaryResponse = GameSummaryPendingResponse | GameSummaryDoneResponse

export type GameWebSocketClientMessage =
  | {
      type: 'CLIENT_READY'
      payload: Record<string, never>
    }
  | {
      type: 'RTT_PONG'
      payload: {
        seq: number
      }
    }
  | {
      type: 'LIGHTNING'
      payload: null
    }

export type GameWebSocketServerMessage =
  | {
      type: 'PLAYER_JOINED'
      payload: PlayerPayload
    }
  | {
      type: 'PLAYER_READY'
      payload: PlayerReadyPayload
    }
  | {
      type: 'PLAYER_LEFT'
      payload: PlayerPayload
    }
  | {
      type: 'RTT_PING'
      payload: RttPingPayload
    }
  | {
      type: 'GAME_WAITING_TIMEOUT'
      payload: GameWaitingFailurePayload
    }
  | {
      type: 'GAME_START_FAILED'
      payload: GameWaitingFailurePayload
    }
  | {
      type: 'COUNTDOWN'
      payload: CountdownPayload
    }
  | {
      type: 'GAME_START'
      payload: GameStartPayload
    }
  | {
      type: 'GAME_RESULT'
      payload: GameResultPayload
    }
  | {
      type: 'LIGHTNING_APPLIED'
      payload: GameLightningAppliedPayload
    }
  | {
      type: 'ERROR'
      payload: GameWebSocketErrorPayload
    }

export interface PlayerPayload {
  userId: number
}

export interface PlayerReadyPayload {
  userId: number
  bothReady: boolean
}

export interface RttPingPayload {
  seq: number
}

export interface GameWaitingFailurePayload {
  gameRoomId: GameRoomId
  reason: string
  action: string
}

export interface CountdownPayload {
  gameRoomId: GameRoomId
  serverTime: number
  startAt: number
  countdownDisplaySeconds: number
}

export interface GameStartPayload {
  gameRoomId: GameRoomId
  serverTime: number
  startAt: number
  scenario: GameStartScenario
}

export interface GameStartScenario {
  starCoreMaxHp: number
  durationMs: number
  hpTimeline: HpTimelineStep[]
}

export interface HpTimelineStep {
  timeMs: number
  hp: number
}

export interface GameResultPayload {
  gameRoomId: GameRoomId
  result: GameResult
  winnerUserId: number | null
  reason: string
  finishedAt: number
  actions: GameActionSummary[]
}

export interface GameActionSummary {
  userId: number
  serverReceiveTime: number
  lightningTimeMs: number
  starCoreHpAtLightning: number
  damage: number
  afterHp: number
  isKill: boolean
}

export interface GameLightningAppliedPayload extends GameActionSummary {
  gameRoomId: GameRoomId
  cooldownUntil: number
}

export interface GameWebSocketErrorPayload {
  code: string
  reason: string
}
