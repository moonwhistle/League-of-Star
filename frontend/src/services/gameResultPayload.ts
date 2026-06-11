import type { GameActionSummary, GameResult, GameResultPayload, GameRoomId } from '@/types/game'

const GAME_RESULT_PAYLOAD_KEY_PREFIX = 'league-of-star.gameResultPayload:'

export interface StoredGameResultPayload extends GameResultPayload {
  receivedAt: string
}

export function saveGameResultPayloadFromMessage(
  messagePayload: unknown,
): StoredGameResultPayload | null {
  if (!isGameResultPayload(messagePayload)) {
    return null
  }

  const payload: StoredGameResultPayload = {
    gameRoomId: messagePayload.gameRoomId,
    result: messagePayload.result,
    winnerUserId: messagePayload.winnerUserId,
    reason: messagePayload.reason,
    finishedAt: messagePayload.finishedAt,
    actions: messagePayload.actions,
    receivedAt: new Date().toISOString(),
  }

  saveGameResultPayload(payload)

  return payload
}

export function saveGameResultPayload(payload: StoredGameResultPayload): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(buildGameResultPayloadKey(payload.gameRoomId), JSON.stringify(payload))
}

export function readGameResultPayload(gameRoomId: string | number): StoredGameResultPayload | null {
  const storage = getStorage()

  if (storage === null) {
    return null
  }

  const normalizedGameRoomId = normalizeGameRoomId(gameRoomId)
  const storedPayload = storage.getItem(buildGameResultPayloadKey(normalizedGameRoomId))

  if (storedPayload === null) {
    return null
  }

  try {
    const payload = JSON.parse(storedPayload) as unknown

    if (!isStoredGameResultPayload(payload)) {
      return null
    }

    if (normalizeGameRoomId(payload.gameRoomId) !== normalizedGameRoomId) {
      return null
    }

    return payload
  } catch {
    return null
  }
}

export function buildGameResultPayloadKey(gameRoomId: string | number): string {
  return `${GAME_RESULT_PAYLOAD_KEY_PREFIX}${normalizeGameRoomId(gameRoomId)}`
}

function isStoredGameResultPayload(payload: unknown): payload is StoredGameResultPayload {
  return (
    isRecord(payload) &&
    isGameResultPayload(payload) &&
    typeof payload.receivedAt === 'string' &&
    payload.receivedAt.trim() !== ''
  )
}

function isGameResultPayload(payload: unknown): payload is GameResultPayload {
  if (!isRecord(payload)) {
    return false
  }

  return (
    isGameRoomId(payload.gameRoomId) &&
    isGameResult(payload.result) &&
    isWinnerUserId(payload.winnerUserId) &&
    typeof payload.reason === 'string' &&
    payload.reason.trim() !== '' &&
    Number.isFinite(payload.finishedAt) &&
    Array.isArray(payload.actions) &&
    payload.actions.every(isGameActionSummary)
  )
}

function isGameActionSummary(action: unknown): action is GameActionSummary {
  if (!isRecord(action)) {
    return false
  }

  return (
    Number.isFinite(action.userId) &&
    Number.isFinite(action.serverReceiveTime) &&
    Number.isFinite(action.lightningTimeMs) &&
    Number.isFinite(action.starCoreHpAtLightning) &&
    Number.isFinite(action.damage) &&
    Number.isFinite(action.afterHp) &&
    typeof action.isKill === 'boolean'
  )
}

function isGameResult(value: unknown): value is GameResult {
  return value === 'PLAYER1_WIN' || value === 'PLAYER2_WIN' || value === 'DRAW'
}

function isWinnerUserId(value: unknown): value is number | null {
  return value === null || Number.isFinite(value)
}

function isGameRoomId(gameRoomId: unknown): gameRoomId is GameRoomId {
  return Number.isFinite(gameRoomId)
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
