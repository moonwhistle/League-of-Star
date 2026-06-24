import type { CustomGameStartResponse } from '@/types/customRoom'
import type { GameRoomId, GameStartScenario, HpTimelineStep } from '@/types/game'

const CUSTOM_GAME_START_PAYLOAD_KEY_PREFIX = 'league-of-star.customGameStartPayload:'

export interface StoredCustomGameStartPayload extends CustomGameStartResponse {
  myUserId: number
  opponentUserId: number
  receivedAt: string
}

export function saveCustomGameStartPayloadFromResponse(
  responsePayload: unknown,
  myUserId: number,
  opponentUserId: number,
): StoredCustomGameStartPayload | null {
  if (
    !isCustomGameStartResponse(responsePayload) ||
    !Number.isFinite(myUserId) ||
    !Number.isFinite(opponentUserId) ||
    myUserId === opponentUserId
  ) {
    return null
  }

  const payload: StoredCustomGameStartPayload = {
    roomId: responsePayload.roomId,
    gameRoomId: responsePayload.gameRoomId,
    gameMode: responsePayload.gameMode,
    serverTime: responsePayload.serverTime,
    startAt: responsePayload.startAt,
    webSocketUrl: responsePayload.webSocketUrl,
    scenario: responsePayload.scenario,
    myUserId,
    opponentUserId,
    receivedAt: new Date().toISOString(),
  }

  saveCustomGameStartPayload(payload)

  return payload
}

export function saveCustomGameStartPayload(payload: StoredCustomGameStartPayload): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(buildCustomGameStartPayloadKey(payload.gameRoomId), JSON.stringify(payload))
}

export function readCustomGameStartPayload(
  gameRoomId: string | number,
): StoredCustomGameStartPayload | null {
  const storage = getStorage()

  if (storage === null) {
    return null
  }

  const normalizedGameRoomId = normalizeGameRoomId(gameRoomId)
  const storedPayload = storage.getItem(buildCustomGameStartPayloadKey(normalizedGameRoomId))

  if (storedPayload === null) {
    return null
  }

  try {
    const payload = JSON.parse(storedPayload) as unknown

    if (!isStoredCustomGameStartPayload(payload)) {
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

export function buildCustomGameStartPayloadKey(gameRoomId: string | number): string {
  return `${CUSTOM_GAME_START_PAYLOAD_KEY_PREFIX}${normalizeGameRoomId(gameRoomId)}`
}

function isStoredCustomGameStartPayload(payload: unknown): payload is StoredCustomGameStartPayload {
  return (
    isRecord(payload) &&
    isCustomGameStartResponse(payload) &&
    Number.isFinite(payload.myUserId) &&
    Number.isFinite(payload.opponentUserId) &&
    payload.myUserId !== payload.opponentUserId &&
    typeof payload.receivedAt === 'string' &&
    payload.receivedAt.trim() !== ''
  )
}

function isCustomGameStartResponse(payload: unknown): payload is CustomGameStartResponse {
  if (!isRecord(payload)) {
    return false
  }

  return (
    Number.isFinite(payload.roomId) &&
    isGameRoomId(payload.gameRoomId) &&
    payload.gameMode === 'CUSTOM' &&
    Number.isFinite(payload.serverTime) &&
    Number.isFinite(payload.startAt) &&
    typeof payload.webSocketUrl === 'string' &&
    payload.webSocketUrl.trim() !== '' &&
    isGameStartScenario(payload.scenario)
  )
}

function isGameStartScenario(scenario: unknown): scenario is GameStartScenario {
  if (!isRecord(scenario)) {
    return false
  }

  return (
    Number.isFinite(scenario.starCoreMaxHp) &&
    Number.isFinite(scenario.durationMs) &&
    Array.isArray(scenario.hpTimeline) &&
    scenario.hpTimeline.every(isHpTimelineStep)
  )
}

function isHpTimelineStep(step: unknown): step is HpTimelineStep {
  if (!isRecord(step)) {
    return false
  }

  return Number.isFinite(step.timeMs) && Number.isFinite(step.hp)
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
