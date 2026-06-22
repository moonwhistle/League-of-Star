import type {
  GameRoomId,
  GameStartScenario,
  HpTimelineStep,
  PracticeGameStartResponse,
} from '@/types/game'

const PRACTICE_GAME_START_PAYLOAD_KEY_PREFIX = 'league-of-star.practiceGameStartPayload:'

export interface StoredPracticeGameStartPayload extends PracticeGameStartResponse {
  receivedAt: string
}

export function savePracticeGameStartPayloadFromResponse(
  responsePayload: unknown,
): StoredPracticeGameStartPayload | null {
  if (!isPracticeGameStartResponse(responsePayload)) {
    return null
  }

  const payload: StoredPracticeGameStartPayload = {
    gameRoomId: responsePayload.gameRoomId,
    serverTime: responsePayload.serverTime,
    startAt: responsePayload.startAt,
    webSocketUrl: responsePayload.webSocketUrl,
    scenario: responsePayload.scenario,
    receivedAt: new Date().toISOString(),
  }

  savePracticeGameStartPayload(payload)

  return payload
}

export function savePracticeGameStartPayload(payload: StoredPracticeGameStartPayload): void {
  const storage = getStorage()

  if (storage === null) {
    return
  }

  storage.setItem(buildPracticeGameStartPayloadKey(payload.gameRoomId), JSON.stringify(payload))
}

export function readPracticeGameStartPayload(
  gameRoomId: string | number,
): StoredPracticeGameStartPayload | null {
  const storage = getStorage()

  if (storage === null) {
    return null
  }

  const normalizedGameRoomId = normalizeGameRoomId(gameRoomId)
  const storedPayload = storage.getItem(buildPracticeGameStartPayloadKey(normalizedGameRoomId))

  if (storedPayload === null) {
    return null
  }

  try {
    const payload = JSON.parse(storedPayload) as unknown

    if (!isStoredPracticeGameStartPayload(payload)) {
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

export function buildPracticeGameStartPayloadKey(gameRoomId: string | number): string {
  return `${PRACTICE_GAME_START_PAYLOAD_KEY_PREFIX}${normalizeGameRoomId(gameRoomId)}`
}

function isStoredPracticeGameStartPayload(
  payload: unknown,
): payload is StoredPracticeGameStartPayload {
  return (
    isRecord(payload) &&
    isPracticeGameStartResponse(payload) &&
    typeof payload.receivedAt === 'string' &&
    payload.receivedAt.trim() !== ''
  )
}

function isPracticeGameStartResponse(payload: unknown): payload is PracticeGameStartResponse {
  if (!isRecord(payload)) {
    return false
  }

  return (
    isGameRoomId(payload.gameRoomId) &&
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
