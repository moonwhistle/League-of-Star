export interface UserProfileResponse {
  userId: number
  email: string
  nickname: string
  createdAt: string
}

export interface UserRankResponse {
  userId: number
  tier: string
  division: string | null
  rank: string
  lp: number
  tierScore: number
  wins: number
  losses: number
  draws: number
  rankUpdatedAt: string
}
