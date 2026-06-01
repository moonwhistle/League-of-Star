export type GameId = number

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
