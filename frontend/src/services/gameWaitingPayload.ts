import type {
  MatchResponseGame,
  MatchResponseOpponent,
  MatchResponseResultNotification,
} from '@/types/match'

const GAME_WAITING_PAYLOAD_KEY_PREFIX = 'league-of-star.gameWaitingPayload:'

export interface GameWaitingPayload {
  matchId: string
  opponent: MatchResponseOpponent | null
  game: MatchResponseGame
  receivedAt: string
}

export function saveGameWaitingPayloadFromMatchResult(
  result: MatchResponseResultNotification,
): GameWaitingPayload | null {
  if (result.action !== 'GO_TO_GAME_WAITING' || !isValidGame(result.game)) {
    return null
  }

  const payload: GameWaitingPayload = {
    matchId: result.matchId,
    opponent: result.opponent,
    game: result.game,
    receivedAt: new Date().toISOString(),
  }

  saveGameWaitingPayload(payload)

  return payload
}

export function saveGameWaitingPayload(payload: GameWaitingPayload): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(buildGameWaitingPayloadKey(payload.game.gameRoomId), JSON.stringify(payload))
}

export function readGameWaitingPayload(gameRoomId: string | number): GameWaitingPayload | null {
  const storage = getStorage()

  if (storage === null) {
    return null
  }

  const normalizedGameRoomId = normalizeGameRoomId(gameRoomId)
  const storedPayload = storage.getItem(buildGameWaitingPayloadKey(normalizedGameRoomId))

  if (storedPayload === null) {
    return null
  }

  try {
    const payload = JSON.parse(storedPayload) as unknown

    if (!isGameWaitingPayload(payload)) {
      return null
    }

    if (normalizeGameRoomId(payload.game.gameRoomId) !== normalizedGameRoomId) {
      return null
    }

    return payload
  } catch {
    return null
  }
}

export function buildGameWaitingPayloadKey(gameRoomId: string | number): string {
  return `${GAME_WAITING_PAYLOAD_KEY_PREFIX}${normalizeGameRoomId(gameRoomId)}`
}

function isGameWaitingPayload(payload: unknown): payload is GameWaitingPayload {
  if (!isRecord(payload)) {
    return false
  }

  return (
    typeof payload.matchId === 'string' &&
    isValidOpponent(payload.opponent) &&
    isValidGame(payload.game) &&
    typeof payload.receivedAt === 'string' &&
    payload.receivedAt.trim() !== ''
  )
}

function isValidOpponent(opponent: unknown): opponent is MatchResponseOpponent | null {
  if (opponent === null) {
    return true
  }

  if (!isRecord(opponent)) {
    return false
  }

  return (
    Number.isFinite(opponent.userId) &&
    typeof opponent.nickname === 'string' &&
    opponent.nickname.trim() !== '' &&
    typeof opponent.tier === 'string' &&
    opponent.tier.trim() !== '' &&
    Number.isFinite(opponent.tierScore)
  )
}

function isValidGame(game: unknown): game is MatchResponseGame {
  if (!isRecord(game)) {
    return false
  }

  return (
    Number.isFinite(game.gameRoomId) &&
    typeof game.webSocketUrl === 'string' &&
    game.webSocketUrl.trim() !== ''
  )
}

function normalizeGameRoomId(gameRoomId: string | number): string {
  return String(gameRoomId).trim()
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function getStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.sessionStorage
}
