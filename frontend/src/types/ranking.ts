export interface RankingResponse {
  summary: RankingSummaryResponse
  entries: RankingEntryResponse[]
  currentUser: RankingEntryResponse
}

export interface RankingSummaryResponse {
  myRankPosition: number
  topPercent: number
  totalRankers: number
}

export interface RankingEntryResponse {
  rankPosition: number
  userId: number
  nickname: string
  tier: string
  division: string | null
  rank: string
  lp: number
  tierScore: number
  wins: number
  losses: number
  draws: number
  isCurrentUser: boolean
}
