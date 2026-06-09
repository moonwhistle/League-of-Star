import type { GameRoomId, GameStartPayload, GameStartScenario, HpTimelineStep } from '@/types/game'

const GAME_START_PAYLOAD_KEY_PREFIX = 'league-of-star.gameStartPayload:'

export interface StoredGameStartPayload {
  gameRoomId: GameRoomId
  serverTime: number
  startAt: number
  scenario: GameStartScenario
  receivedAt: string
}

export function saveGameStartPayloadFromMessage(
  messagePayload: unknown,
): StoredGameStartPayload | null {
  if (!isGameStartPayload(messagePayload)) {
    return null
  }

  const payload: StoredGameStartPayload = {
    gameRoomId: messagePayload.gameRoomId,
    serverTime: messagePayload.serverTime,
    startAt: messagePayload.startAt,
    scenario: messagePayload.scenario,
    receivedAt: new Date().toISOString(),
  }

  saveGameStartPayload(payload)

  return payload
}

export function saveGameStartPayload(payload: StoredGameStartPayload): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(buildGameStartPayloadKey(payload.gameRoomId), JSON.stringify(payload))
}

export function readGameStartPayload(gameRoomId: string | number): StoredGameStartPayload | null {
  const storage = getStorage()

  if (storage === null) {
    return null
  }

  const normalizedGameRoomId = normalizeGameRoomId(gameRoomId)
  const storedPayload = storage.getItem(buildGameStartPayloadKey(normalizedGameRoomId))

  if (storedPayload === null) {
    return null
  }

  try {
    const payload = JSON.parse(storedPayload) as unknown

    if (!isStoredGameStartPayload(payload)) {
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

export function buildGameStartPayloadKey(gameRoomId: string | number): string {
  return `${GAME_START_PAYLOAD_KEY_PREFIX}${normalizeGameRoomId(gameRoomId)}`
}

function isStoredGameStartPayload(payload: unknown): payload is StoredGameStartPayload {
  if (!isRecord(payload)) {
    return false
  }

  return (
    isGameRoomId(payload.gameRoomId) &&
    Number.isFinite(payload.serverTime) &&
    Number.isFinite(payload.startAt) &&
    isGameStartScenario(payload.scenario) &&
    typeof payload.receivedAt === 'string' &&
    payload.receivedAt.trim() !== ''
  )
}

function isGameStartPayload(payload: unknown): payload is GameStartPayload {
  if (!isRecord(payload)) {
    return false
  }

  return (
    isGameRoomId(payload.gameRoomId) &&
    Number.isFinite(payload.serverTime) &&
    Number.isFinite(payload.startAt) &&
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
