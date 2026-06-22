<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/html-self-closing -->
  <main
    class="game-play-page"
    :data-game-start-payload-ready="gameStartPayload !== null"
    :data-game-waiting-payload-ready="gameWaitingPayload !== null"
    :data-game-practice-payload-ready="practiceGameStartPayload !== null"
    :data-game-play-state-ready="playState !== null"
    :data-game-mode="playState?.mode ?? ''"
    :data-game-room-id="playState?.gameRoomId ?? ''"
    :data-game-start-at="playState?.startAt ?? ''"
    :data-game-duration-ms="playState?.durationMs ?? ''"
    :data-game-elapsed-ms="elapsedMs"
    :data-game-current-hp="currentHp"
    :data-game-websocket-url="playState?.webSocketUrl ?? ''"
    :data-game-started="hasGameStarted"
    :data-game-countdown-seconds="countdownSeconds"
    :data-game-hp-percent="hpPercent"
    :data-game-natural-death-waiting="isNaturalDeathWaiting"
    :data-game-socket-status="gameSocketStatus"
    :data-game-socket-last-event="gameSocketLastEvent"
    :data-game-socket-error-message="gameSocketErrorMessage"
    :data-game-result-received="gameResultReceived"
    :data-game-socket-lightning-ready="canSendGameSocketLightning"
    :data-game-star-targeted="isStarTargeted"
    :data-game-lightning-ready="canCastLightningSpell"
    :data-game-lightning-hit-ready="canSendLightningCommand"
    :data-game-lightning-sent="appliedLightningActions.length > 0"
    :data-game-my-lightning-cooldown-ms="myLightningCooldownRemainingMs"
    :data-game-three-ready="isThreeSceneReady"
    :data-game-practice-result-visible="isPracticeResultVisible"
    :data-game-practice-result="practiceResultOutcome"
    :data-game-practice-restart-status="practiceRestartStatus"
  >
    <section v-if="playState !== null" class="game-arena" aria-label="Game play arena">
      <canvas
        ref="threeCanvas"
        class="three-scene"
        data-testid="three-scene"
        aria-label="Galaxy space background"
      />
      <div class="space-vignette" aria-hidden="true" />
      <div
        v-if="isCountdownOverlayVisible"
        class="countdown-overlay"
        data-testid="countdown-overlay"
        aria-live="polite"
      >
        <span :key="countdownSeconds">{{ countdownSeconds }}</span>
      </div>
      <div
        v-if="lightningImpactId > 0"
        :key="lightningImpactId"
        class="lightning-impact"
        :data-lightning-impact-owner="lightningImpactOwner"
        :style="lightningImpactStyle"
        aria-hidden="true"
      >
        <span class="lightning-impact__ring lightning-impact__ring--outer" />
        <span class="lightning-impact__ring lightning-impact__ring--inner" />
        <span class="lightning-impact__core" />
        <span class="lightning-impact__particle lightning-impact__particle--a" />
        <span class="lightning-impact__particle lightning-impact__particle--b" />
        <span class="lightning-impact__particle lightning-impact__particle--c" />
        <span class="lightning-impact__particle lightning-impact__particle--d" />
      </div>
      <div
        class="lightning-hud lightning-hud--mine"
        :data-lightning-hud-status="myLightningHudStatus"
        data-testid="lightning-hud"
        :aria-label="myLightningSpellAriaLabel"
      >
        <div class="lightning-spell" :style="myLightningCooldownStyle">
          <div class="lightning-spell__damage" data-testid="lightning-damage">
            {{ lightningDamageLabel }}
          </div>
          <div class="lightning-spell__icon-wrap" aria-hidden="true">
            <img
              class="lightning-spell__icon"
              :src="lightningSpellImageUrl"
              :alt="t('gamePlay.lightningButton')"
            />
            <span v-if="myLightningCooldownRemainingMs > 0" class="lightning-spell__cooldown" />
          </div>
          <div class="lightning-spell__keys" aria-hidden="true">
            <kbd>D</kbd>
            <kbd>F</kbd>
          </div>
        </div>
      </div>
      <section
        v-if="isPracticeResultVisible"
        class="practice-result-overlay"
        data-testid="practice-result-overlay"
        aria-live="polite"
      >
        <div class="practice-result-panel">
          <p>{{ t('gamePlay.practiceResultEyebrow') }}</p>
          <h1>{{ practiceResultTitle }}</h1>
          <div class="practice-result-actions">
            <button
              type="button"
              :disabled="practiceRestartStatus === 'loading'"
              @click="restartPractice"
            >
              {{
                practiceRestartStatus === 'loading'
                  ? t('gamePlay.practiceRestarting')
                  : t('gamePlay.practiceRestart')
              }}
            </button>
            <button type="button" @click="returnToMatch">
              {{ t('gamePlay.practiceReturnToMatch') }}
            </button>
          </div>
          <p v-if="practiceRestartErrorMessage !== ''" class="practice-result-error" role="alert">
            {{ practiceRestartErrorMessage }}
          </p>
        </div>
      </section>
    </section>

    <p v-else class="payload-error" role="alert">
      {{ t('gamePlay.payloadMissing') }}
    </p>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, shallowRef } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { getHpAtElapsedMs, getScenarioMaxHp } from '@/game/hpScenario'
import {
  createNoopThreeGalaxyBackgroundSceneController,
  createThreeGalaxyBackgroundScene,
} from '@/game/threeGalaxyBackgroundScene'
import {
  createGameResultPayloadFromMessage,
  saveGameResultPayload,
} from '@/services/gameResultPayload'
import { readGameStartPayload } from '@/services/gameStartPayload'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'
import {
  readPracticeGameStartPayload,
  savePracticeGameStartPayloadFromResponse,
} from '@/services/practiceGameStartPayload'
import { startPractice } from '@/services/practiceService'
import { connectGameWebSocket } from '@/services/realtime/gameWebSocket'
import { takeGameWebSocketHandoff } from '@/services/realtime/gameWebSocketHandoff'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const lightningSpellImageUrl = new URL('../../img/lightning-spell.png', import.meta.url).href
const threeCanvas = shallowRef(null)
const gameStartPayload = shallowRef(readGameStartPayload('__missing__'))
const gameWaitingPayload = shallowRef(readGameWaitingPayload('__missing__'))
const practiceGameStartPayload = shallowRef(readPracticeGameStartPayload('__missing__'))
const nowMs = shallowRef(Date.now())
const gameSocketStatus = shallowRef('idle')
const gameSocketLastEvent = shallowRef('')
const gameSocketErrorMessage = shallowRef('')
const gameResultReceived = shallowRef(false)
const gameResultPayload = shallowRef()
const isResultRouteTransitioning = shallowRef(false)
const playGameWebSocketConnection = shallowRef()
const practiceRestartStatus = shallowRef('idle')
const practiceRestartErrorMessage = shallowRef('')
const appliedLightningActions = shallowRef([{}].slice(1))
const myLightningCooldownUntil = shallowRef(0)
const isThreeSceneReady = shallowRef(false)
const isStarTargeted = shallowRef(false)
const displayedHp = shallowRef()
const lightningImpactId = shallowRef(0)
const lightningImpactOwner = shallowRef('mine')
const hasPointerScreenPosition = shallowRef(false)
const pointerScreenPosition = shallowRef({ x: 0, y: 0 })
const lightningImpactScreenPosition = shallowRef({ x: 0, y: 0 })
const lightningTargetScreenPosition = shallowRef({ x: 0, y: 0 })
let animationFrameId = 0
let closePlayWebSocket = () => {}
let threeSceneController = createNoopThreeGalaxyBackgroundSceneController()
let hasThreeSceneController = false

const LIGHTNING_DAMAGE = 1200
const LIGHTNING_COOLDOWN_MS = 2000
const lightningDamageLabel = computed(() => LIGHTNING_DAMAGE.toLocaleString('en-US'))

const playState = computed(() => {
  if (gameStartPayload.value !== null && gameWaitingPayload.value !== null) {
    return {
      mode: 'MATCH',
      gameRoomId: gameStartPayload.value.gameRoomId,
      startAt: gameStartPayload.value.startAt,
      durationMs: gameStartPayload.value.scenario.durationMs,
      scenario: gameStartPayload.value.scenario,
      webSocketUrl: gameWaitingPayload.value.game.webSocketUrl,
    }
  }

  if (practiceGameStartPayload.value !== null) {
    return {
      mode: 'PRACTICE',
      gameRoomId: practiceGameStartPayload.value.gameRoomId,
      startAt: practiceGameStartPayload.value.startAt,
      durationMs: practiceGameStartPayload.value.scenario.durationMs,
      scenario: practiceGameStartPayload.value.scenario,
      webSocketUrl: practiceGameStartPayload.value.webSocketUrl,
    }
  }

  return null
})

const rawElapsedMs = computed(() =>
  playState.value === null ? 0 : nowMs.value - playState.value.startAt,
)
const elapsedMs = computed(() => Math.max(0, rawElapsedMs.value))
const hasGameStarted = computed(() => rawElapsedMs.value >= 0)
const countdownSeconds = computed(() =>
  playState.value === null || hasGameStarted.value
    ? 0
    : Math.max(1, Math.ceil((playState.value.startAt - nowMs.value) / 1000)),
)
const scenarioHp = computed(() =>
  playState.value === null ? 0 : getHpAtElapsedMs(playState.value.scenario, elapsedMs.value),
)
const confirmedLightningDamage = computed(() => {
  const actions = Reflect.get(Object(gameResultPayload.value), 'actions')

  return (
    (Array.isArray(actions) ? actions.length : appliedLightningActions.value.length) *
    LIGHTNING_DAMAGE
  )
})
const effectiveHp = computed(() => Math.max(0, scenarioHp.value - confirmedLightningDamage.value))
const currentHp = computed(() =>
  typeof displayedHp.value === 'number' ? displayedHp.value : effectiveHp.value,
)
const hpPercent = computed(() => {
  if (playState.value === null || getScenarioMaxHp(playState.value.scenario) <= 0) {
    return 0
  }

  return Math.min(
    100,
    Math.max(0, Math.round((currentHp.value / getScenarioMaxHp(playState.value.scenario)) * 100)),
  )
})
const lightningImpactStyle = computed(() => {
  const impactOwner = lightningImpactOwner.value
  const isOpponentImpact = impactOwner === 'opponent'
  const isMissImpact = impactOwner === 'miss'
  const colorSet = isOpponentImpact
    ? {
        core: '#fff3f4',
        mid: '#ff405a',
        edge: '#ff9aa8',
        glow: 'rgba(255, 64, 90, 0.78)',
        flare: 'rgba(255, 138, 151, 0.54)',
      }
    : isMissImpact
      ? {
          core: '#ffffff',
          mid: '#f7fbff',
          edge: '#dfe8ff',
          glow: 'rgba(255, 255, 255, 0.82)',
          flare: 'rgba(224, 235, 255, 0.56)',
        }
      : {
          core: '#f4fdff',
          mid: '#58dfff',
          edge: '#8ff8ff',
          glow: 'rgba(87, 222, 255, 0.78)',
          flare: 'rgba(107, 240, 255, 0.48)',
        }

  return {
    '--lightning-impact-x': `${lightningImpactScreenPosition.value.x}px`,
    '--lightning-impact-y': `${lightningImpactScreenPosition.value.y}px`,
    '--lightning-impact-core': colorSet.core,
    '--lightning-impact-mid': colorSet.mid,
    '--lightning-impact-edge': colorSet.edge,
    '--lightning-impact-glow': colorSet.glow,
    '--lightning-impact-flare': colorSet.flare,
  }
})

const canSendGameSocketLightning = computed(() => playGameWebSocketConnection.value !== undefined)
const isGameSocketReadyForLightning = computed(
  () => gameSocketStatus.value === 'connected' || gameSocketStatus.value === 'handoff',
)
const myLightningCooldownRemainingMs = computed(() =>
  Math.max(0, myLightningCooldownUntil.value - nowMs.value),
)
const canCastLightningSpell = computed(
  () =>
    hasGameStarted.value && myLightningCooldownRemainingMs.value <= 0 && !gameResultReceived.value,
)
const canSendLightningCommand = computed(
  () =>
    canCastLightningSpell.value &&
    canSendGameSocketLightning.value &&
    isGameSocketReadyForLightning.value &&
    isStarTargeted.value,
)
const myLightningHudStatus = computed(() => {
  if (gameResultReceived.value) {
    return 'result'
  }
  if (myLightningCooldownRemainingMs.value > 0) {
    return 'cooldown'
  }
  if (gameSocketStatus.value === 'error') {
    return 'error'
  }
  if (!isGameSocketReadyForLightning.value) {
    return 'offline'
  }

  return 'active'
})
const myLightningSpellAriaLabel = computed(() => {
  switch (myLightningHudStatus.value) {
    case 'cooldown':
      return t('gamePlay.lightningCooldown')
    case 'result':
      return t('gamePlay.resultReceived')
    case 'error':
      return t('gamePlay.socketError')
    case 'offline':
      return t('gamePlay.socketConnecting')
    default:
      return t('gamePlay.lightningButton')
  }
})
const isPracticeResultVisible = computed(
  () => playState.value?.mode === 'PRACTICE' && gameResultReceived.value,
)
const practiceResultOutcome = computed(() => {
  if (!isPracticeResultVisible.value) {
    return ''
  }

  const practiceResult = Reflect.get(Object(gameResultPayload.value), 'practiceResult')

  return practiceResult === 'SUCCESS' || practiceResult === 'FAILED' ? practiceResult : ''
})
const practiceResultTitle = computed(() =>
  practiceResultOutcome.value === 'SUCCESS'
    ? t('gamePlay.practiceSuccessTitle')
    : t('gamePlay.practiceFailedTitle'),
)
const isCountdownOverlayVisible = computed(
  () => playState.value !== null && !hasGameStarted.value && countdownSeconds.value > 0,
)
const myLightningCooldownStyle = computed(() => ({
  '--lightning-cooldown-progress': `${Math.min(
    100,
    (myLightningCooldownRemainingMs.value / LIGHTNING_COOLDOWN_MS) * 100,
  )}%`,
}))
const isNaturalDeathWaiting = computed(
  () =>
    playState.value !== null &&
    elapsedMs.value >= playState.value.durationMs &&
    !gameResultReceived.value,
)
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('keydown', handleLightningKeyDown)
  window.addEventListener('pointermove', handlePointerMove)
  const gameRoomId = readRouteGameRoomId()

  if (gameRoomId === '') {
    returnToMatch()
    return
  }

  if (!initializePlayFromStorage(gameRoomId)) {
    returnToMatch()
  }
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('keydown', handleLightningKeyDown)
  window.removeEventListener('pointermove', handlePointerMove)
  closePlayWebSocket()
  playGameWebSocketConnection.value = undefined
  closePlayWebSocket = () => {}

  stopFrameLoop()
  disposeThreeScene()
})

onBeforeRouteLeave(() => {
  if (isResultRouteTransitioning.value) {
    return true
  }

  if (!shouldWarnBeforeLeaving()) {
    return true
  }

  return window.confirm(t('gamePlay.leaveWarning'))
})

function handleBeforeUnload() {
  if (!shouldWarnBeforeLeaving()) {
    return
  }

  const event = arguments[0]
  event.preventDefault()
  event.returnValue = ''
}

function handleLightningKeyDown() {
  const event = arguments[0]

  if (event.repeat || !isLightningKey(event) || !canCastLightningSpell.value) {
    return
  }

  event.preventDefault()
  const shouldSendLightning = canSendLightningCommand.value
  myLightningCooldownUntil.value = Date.now() + LIGHTNING_COOLDOWN_MS
  triggerLightningImpact(shouldSendLightning ? 'mine' : 'miss', resolveLightningCastPosition())

  if (!shouldSendLightning) {
    return
  }

  try {
    playGameWebSocketConnection.value?.sendLightning()
    gameSocketErrorMessage.value = ''
  } catch (error) {
    gameSocketStatus.value = 'error'
    gameSocketErrorMessage.value =
      error instanceof Error ? error.message : t('gamePlay.socketErrorDetail')
  }
}

function isLightningKey(event = {}) {
  const normalizedKey = String(Reflect.get(Object(event), 'key') ?? '')
    .trim()
    .toLowerCase()
  const normalizedCode = String(Reflect.get(Object(event), 'code') ?? '').trim()

  return (
    normalizedKey === 'd' ||
    normalizedKey === 'f' ||
    normalizedCode === 'KeyD' ||
    normalizedCode === 'KeyF'
  )
}

function handlePointerMove() {
  const event = arguments[0]

  if (!Number.isFinite(event.clientX) || !Number.isFinite(event.clientY)) {
    return
  }

  pointerScreenPosition.value = {
    x: event.clientX,
    y: event.clientY,
  }
  hasPointerScreenPosition.value = true
}

function shouldWarnBeforeLeaving() {
  return playState.value !== null && !gameResultReceived.value
}

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match })
}

async function restartPractice() {
  if (practiceRestartStatus.value === 'loading') {
    return
  }

  practiceRestartStatus.value = 'loading'
  practiceRestartErrorMessage.value = ''

  try {
    const response = await startPractice()
    const payload = savePracticeGameStartPayloadFromResponse(response)

    if (payload === null) {
      failPracticeRestart(t('gamePlay.practiceRestartFailed'))
      return
    }

    resetPlayRuntimeForPractice()
    practiceGameStartPayload.value = payload
    await router.replace({
      name: ROUTE_NAMES.gamePlay,
      params: {
        gameRoomId: String(payload.gameRoomId),
      },
    })
    startPlayRuntime(String(payload.gameRoomId), payload.webSocketUrl)
  } catch (error) {
    const message =
      error instanceof Error && error.message.trim() !== ''
        ? error.message
        : t('gamePlay.practiceRestartFailed')
    failPracticeRestart(message)
  }
}

function failPracticeRestart(message = '') {
  practiceRestartStatus.value = 'error'
  practiceRestartErrorMessage.value = message
}

function resetPlayRuntimeForPractice() {
  closePlayWebSocket()
  playGameWebSocketConnection.value = undefined
  closePlayWebSocket = () => {}
  stopFrameLoop()
  disposeThreeScene()
  gameStartPayload.value = null
  gameWaitingPayload.value = null
  gameSocketStatus.value = 'idle'
  gameSocketLastEvent.value = ''
  gameSocketErrorMessage.value = ''
  gameResultReceived.value = false
  gameResultPayload.value = undefined
  isResultRouteTransitioning.value = false
  appliedLightningActions.value = []
  myLightningCooldownUntil.value = 0
  displayedHp.value = undefined
  lightningImpactId.value = 0
  isStarTargeted.value = false
  isThreeSceneReady.value = false
  nowMs.value = Date.now()
  practiceRestartStatus.value = 'idle'
  practiceRestartErrorMessage.value = ''
}

function initializePlayFromStorage(gameRoomId = '') {
  const payload = readGameStartPayload(gameRoomId)
  const waitingPayload = readGameWaitingPayload(gameRoomId)

  if (payload !== null && waitingPayload !== null) {
    gameStartPayload.value = payload
    gameWaitingPayload.value = waitingPayload
    practiceGameStartPayload.value = null
    startPlayRuntime(gameRoomId, waitingPayload.game.webSocketUrl)
    return true
  }

  const practicePayload = readPracticeGameStartPayload(gameRoomId)

  if (practicePayload !== null) {
    gameStartPayload.value = null
    gameWaitingPayload.value = null
    practiceGameStartPayload.value = practicePayload
    startPlayRuntime(gameRoomId, practicePayload.webSocketUrl)
    return true
  }

  return false
}

function startPlayRuntime(gameRoomId = '', webSocketUrl = '') {
  void nextTick(() => {
    initializeThreeScene()
    startFrameLoop()
  })
  connectPlayWebSocket(gameRoomId, webSocketUrl)
}

function connectPlayWebSocket(gameRoomId = '', webSocketUrl = '') {
  const handlers = {
    onOpen: () => {
      gameSocketStatus.value = 'connected'
      gameSocketErrorMessage.value = ''
    },
    onMessage: (message = {}) => {
      handlePlayWebSocketMessage(message)
    },
    onError: handlePlayWebSocketError,
    onClose: () => {
      if (gameSocketStatus.value === 'resultReceived') {
        return
      }

      gameSocketStatus.value = 'error'
      gameSocketErrorMessage.value = t('gamePlay.socketClosed')
    },
  }
  const handedOffConnection = takeGameWebSocketHandoff(gameRoomId)

  if (handedOffConnection != null) {
    handedOffConnection.setHandlers(handlers)
    playGameWebSocketConnection.value = handedOffConnection
    closePlayWebSocket = handedOffConnection.close
    gameSocketStatus.value = 'handoff'
    gameSocketErrorMessage.value = ''
    return
  }

  try {
    gameSocketStatus.value = 'connecting'
    const connection = connectGameWebSocket(webSocketUrl, handlers)
    playGameWebSocketConnection.value = connection
    closePlayWebSocket = connection.close
  } catch (error) {
    playGameWebSocketConnection.value = undefined
    gameSocketStatus.value = 'error'
    gameSocketErrorMessage.value =
      error instanceof Error ? error.message : t('gamePlay.socketErrorDetail')
  }
}

function startFrameLoop() {
  stopFrameLoop()

  const updateFrame = () => {
    nowMs.value = Date.now()
    updateDisplayedHp()
    updateThreeScene()
    animationFrameId = window.requestAnimationFrame(updateFrame)
  }

  updateFrame()
}

function stopFrameLoop() {
  if (animationFrameId !== 0) {
    window.cancelAnimationFrame(animationFrameId)
    animationFrameId = 0
  }
}

function updateDisplayedHp() {
  const nextHp = effectiveHp.value
  displayedHp.value =
    typeof displayedHp.value === 'number' ? Math.min(displayedHp.value, nextHp) : nextHp
}

function handlePlayWebSocketError() {
  if (gameResultReceived.value) {
    return
  }

  const error = arguments[0]
  gameSocketStatus.value = 'error'
  gameSocketErrorMessage.value =
    error instanceof Error ? error.message : t('gamePlay.socketErrorDetail')
}

function handlePlayWebSocketMessage(message = {}) {
  const messageType = String(Reflect.get(message, 'type') ?? '')
  const payload = Reflect.get(message, 'payload')
  gameSocketLastEvent.value = messageType

  if (messageType === 'ERROR') {
    const reason = Reflect.get(Object(payload), 'reason')
    gameSocketStatus.value = 'error'
    gameSocketErrorMessage.value =
      typeof reason === 'string' && reason.trim() !== '' ? reason : t('gamePlay.socketErrorDetail')
    return
  }

  if (messageType === 'GAME_RESULT') {
    handleGameResult(payload)
  }

  if (messageType === 'LIGHTNING_APPLIED') {
    handleLightningApplied(payload)
  }
}

function handleGameResult(payload = {}) {
  if (gameResultReceived.value || isResultRouteTransitioning.value) {
    return
  }

  const storedPayload = createGameResultPayloadFromMessage(payload)

  if (storedPayload === null) {
    gameSocketStatus.value = 'error'
    gameSocketErrorMessage.value = t('gamePlay.resultPayloadInvalid')
    return
  }

  if (isPracticePlayResult(storedPayload)) {
    handlePracticeGameResult(storedPayload)
    return
  }

  saveGameResultPayload(storedPayload)

  gameResultReceived.value = true
  gameResultPayload.value = storedPayload
  syncAppliedActionsFromGameResult(storedPayload)
  gameSocketStatus.value = 'resultReceived'
  gameSocketErrorMessage.value = ''
  isResultRouteTransitioning.value = true
  updateDisplayedHp()

  void router
    .replace({
      name: ROUTE_NAMES.gameResult,
      params: {
        gameRoomId: String(storedPayload.gameRoomId),
      },
    })
    .catch(() => {
      isResultRouteTransitioning.value = false
      gameSocketStatus.value = 'error'
      gameSocketErrorMessage.value = t('gamePlay.resultTransitionFailed')
    })
}

function handlePracticeGameResult(storedPayload = {}) {
  if (!isValidPracticeResultPayload(storedPayload)) {
    gameSocketStatus.value = 'error'
    gameSocketErrorMessage.value = t('gamePlay.resultPayloadInvalid')
    return
  }

  gameResultReceived.value = true
  gameResultPayload.value = storedPayload
  syncAppliedActionsFromGameResult(storedPayload)
  gameSocketStatus.value = 'resultReceived'
  gameSocketErrorMessage.value = ''
  practiceRestartStatus.value = 'idle'
  practiceRestartErrorMessage.value = ''
  updateDisplayedHp()
}

function isPracticePlayResult(storedPayload = {}) {
  return (
    playState.value?.mode === 'PRACTICE' ||
    Reflect.get(Object(storedPayload), 'gameMode') === 'PRACTICE'
  )
}

function isValidPracticeResultPayload(storedPayload = {}) {
  const practiceResult = Reflect.get(Object(storedPayload), 'practiceResult')

  return (
    Reflect.get(Object(storedPayload), 'gameMode') === 'PRACTICE' &&
    (practiceResult === 'SUCCESS' || practiceResult === 'FAILED')
  )
}

function handleLightningApplied(payload = {}) {
  const action = Object(payload)
  const serverReceiveTime = Number(Reflect.get(action, 'serverReceiveTime'))
  const userId = Number(Reflect.get(action, 'userId'))
  const cooldownUntil = Number(Reflect.get(action, 'cooldownUntil'))

  if (!Number.isFinite(serverReceiveTime) || !Number.isFinite(userId)) {
    return
  }

  if (
    !appliedLightningActions.value.some(
      (existingAction) =>
        Number(Reflect.get(Object(existingAction), 'serverReceiveTime')) === serverReceiveTime &&
        Number(Reflect.get(Object(existingAction), 'userId')) === userId,
    )
  ) {
    appliedLightningActions.value = [...appliedLightningActions.value, payload]
  }

  if (isOpponentUserId(userId)) {
    triggerLightningImpact('opponent', lightningTargetScreenPosition.value)
  } else {
    myLightningCooldownUntil.value = Number.isFinite(cooldownUntil) ? cooldownUntil : Date.now()
  }

  updateDisplayedHp()
}

function triggerLightningImpact(owner = 'mine', position = resolveLightningCastPosition()) {
  lightningImpactOwner.value = ['mine', 'opponent', 'miss'].includes(owner) ? owner : 'mine'
  lightningImpactScreenPosition.value = position
  lightningImpactId.value += 1
}

function resolveLightningCastPosition() {
  if (hasPointerScreenPosition.value) {
    return pointerScreenPosition.value
  }

  if (lightningTargetScreenPosition.value.x > 0 || lightningTargetScreenPosition.value.y > 0) {
    return lightningTargetScreenPosition.value
  }

  return {
    x: typeof window === 'undefined' ? 0 : window.innerWidth / 2,
    y: typeof window === 'undefined' ? 0 : window.innerHeight / 2,
  }
}

function syncAppliedActionsFromGameResult(payload = {}) {
  const actions = Reflect.get(Object(payload), 'actions')

  if (Array.isArray(actions)) {
    appliedLightningActions.value = actions
  }
}

function isOpponentUserId(userId = 0) {
  const opponentUserId = Reflect.get(Object(gameWaitingPayload.value?.opponent), 'userId')

  return Number(opponentUserId) === Number(userId)
}

function readRouteGameRoomId() {
  const routeGameRoomIdParam = route.params.gameRoomId

  return Array.isArray(routeGameRoomIdParam)
    ? String(routeGameRoomIdParam[0] ?? '').trim()
    : String(routeGameRoomIdParam ?? '').trim()
}

function initializeThreeScene() {
  const canvas = threeCanvas.value

  if (canvas === null) {
    isThreeSceneReady.value = false
    return
  }

  disposeThreeScene()

  const nextThreeSceneController = createThreeGalaxyBackgroundScene(canvas, {
    onReadyChange: (nextIsReady) => {
      isThreeSceneReady.value = nextIsReady
    },
    onTargetHoverChange: (nextIsStarTargeted) => {
      isStarTargeted.value = nextIsStarTargeted
    },
    onTargetScreenPositionChange: (nextPosition) => {
      lightningTargetScreenPosition.value = nextPosition
    },
  })
  threeSceneController =
    nextThreeSceneController ?? createNoopThreeGalaxyBackgroundSceneController()
  hasThreeSceneController = nextThreeSceneController !== null
  updateThreeScene()
}

function updateThreeScene() {
  if (!hasThreeSceneController) {
    return
  }

  threeSceneController.update(elapsedMs.value, hpPercent.value, currentHp.value)
}

function disposeThreeScene() {
  isStarTargeted.value = false

  if (!hasThreeSceneController) {
    isThreeSceneReady.value = false
    return
  }

  threeSceneController.dispose()
  threeSceneController = createNoopThreeGalaxyBackgroundSceneController()
  hasThreeSceneController = false
}
</script>

<style scoped>
.game-play-page {
  width: 100%;
  min-height: 100dvh;
  overflow: hidden;
  font-family: var(--font-sans);
  color: #f8f5ff;
  background: linear-gradient(90deg, #3a2427 0%, #261520 36%, #120b18 68%, #050713 100%);
}

.game-play-page,
.game-play-page * {
  box-sizing: border-box;
}

.game-arena {
  position: relative;
  width: 100%;
  min-height: 100dvh;
  overflow: hidden;
  container-type: size;
}

.three-scene {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  cursor: default;
  user-select: none;
}

.space-vignette {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 46%, rgba(255, 225, 188, 0.14), transparent 32%),
    radial-gradient(circle at 44% 42%, rgba(255, 65, 205, 0.1), transparent 35%),
    linear-gradient(90deg, rgba(255, 219, 190, 0.1), transparent 34%, rgba(3, 5, 18, 0.5)),
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 44%, rgba(3, 4, 12, 0.32));
}

.countdown-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  pointer-events: none;
  background:
    radial-gradient(circle at 50% 48%, rgba(95, 224, 255, 0.16), transparent 30%),
    rgba(1, 4, 13, 0.26);
}

.countdown-overlay span {
  display: grid;
  width: min(34vw, 220px);
  aspect-ratio: 1;
  place-items: center;
  font-size: clamp(5rem, 20vw, 12rem);
  font-weight: 900;
  line-height: 1;
  color: #f8fdff;
  text-shadow:
    0 0 22px rgba(91, 222, 255, 0.76),
    0 0 52px rgba(165, 107, 255, 0.42),
    0 6px 18px rgba(0, 0, 0, 0.72);
  animation: countdown-pop 820ms ease-out both;
}

.lightning-impact {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.lightning-impact__ring,
.lightning-impact__core,
.lightning-impact__particle {
  position: absolute;
  top: var(--lightning-impact-y);
  left: var(--lightning-impact-x);
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.lightning-impact__ring {
  width: 180px;
  height: 180px;
  border: 2px solid var(--lightning-impact-mid);
  box-shadow:
    0 0 22px var(--lightning-impact-glow),
    inset 0 0 24px var(--lightning-impact-flare);
  opacity: 0;
  animation: lightning-impact-ring 760ms cubic-bezier(0.16, 1, 0.3, 1) both;
}

.lightning-impact__ring--inner {
  width: 96px;
  height: 96px;
  border-width: 3px;
  animation-delay: 70ms;
}

.lightning-impact__core {
  width: 112px;
  height: 112px;
  background:
    radial-gradient(
      circle,
      rgba(255, 255, 255, 0.95) 0 10%,
      var(--lightning-impact-core) 11% 22%,
      var(--lightning-impact-mid) 23% 42%,
      var(--lightning-impact-glow) 43% 58%,
      transparent 72%
    ),
    radial-gradient(circle, var(--lightning-impact-flare), transparent 68%);
  filter: blur(0.1px) drop-shadow(0 0 30px var(--lightning-impact-glow));
  opacity: 0;
  animation: lightning-impact-core 760ms ease-out both;
}

.lightning-impact__particle {
  width: 14px;
  height: 14px;
  background: var(--lightning-impact-core);
  box-shadow: 0 0 16px var(--lightning-impact-glow);
  opacity: 0;
  animation: lightning-impact-particle 760ms ease-out both;
}

.lightning-impact__particle--a {
  --lightning-impact-particle-x: -86px;
  --lightning-impact-particle-y: -42px;
}

.lightning-impact__particle--b {
  --lightning-impact-particle-x: 78px;
  --lightning-impact-particle-y: -36px;
  animation-delay: 30ms;
}

.lightning-impact__particle--c {
  --lightning-impact-particle-x: -62px;
  --lightning-impact-particle-y: 66px;
  animation-delay: 60ms;
}

.lightning-impact__particle--d {
  --lightning-impact-particle-x: 84px;
  --lightning-impact-particle-y: 58px;
  animation-delay: 90ms;
}

.lightning-hud {
  position: absolute;
  bottom: max(18px, env(safe-area-inset-bottom));
  z-index: 4;
  width: 128px;
  max-width: calc(100% - 32px);
  color: #fff9df;
  pointer-events: none;
}

.lightning-hud--mine {
  left: 50%;
  transform: translateX(-50%);
}

@keyframes countdown-pop {
  0% {
    opacity: 0;
    transform: scale(0.72);
  }

  18% {
    opacity: 1;
    transform: scale(1.04);
  }

  100% {
    opacity: 0.72;
    transform: scale(1);
  }
}

@keyframes lightning-impact-ring {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.18);
  }

  18% {
    opacity: 0.92;
    transform: translate(-50%, -50%) scale(0.48);
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.48);
  }
}

@keyframes lightning-impact-core {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.12);
  }

  14% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(0.88);
  }

  42% {
    opacity: 0.88;
    transform: translate(-50%, -50%) scale(1.08);
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.34);
  }
}

@keyframes lightning-impact-particle {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.4);
  }

  18% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }

  100% {
    opacity: 0;
    transform: translate(
        calc(-50% + var(--lightning-impact-particle-x)),
        calc(-50% + var(--lightning-impact-particle-y))
      )
      scale(0.1);
  }
}

.lightning-spell {
  display: grid;
  grid-template-rows: 20px 76px 30px;
  gap: 5px;
  place-items: center;
  width: 100%;
}

.lightning-spell__damage {
  min-width: 72px;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
  color: #fff6ba;
  text-align: center;
  text-shadow:
    0 1px 2px rgba(0, 0, 0, 0.9),
    0 0 10px rgba(255, 204, 76, 0.48);
  background: rgba(8, 6, 14, 0.62);
  border: 1px solid rgba(255, 223, 126, 0.34);
}

.lightning-hud[data-lightning-hud-status='cooldown'],
.lightning-hud[data-lightning-hud-status='result'] {
  color: rgba(222, 230, 240, 0.68);
}

.lightning-hud[data-lightning-hud-status='cooldown'] .lightning-spell__damage,
.lightning-hud[data-lightning-hud-status='result'] .lightning-spell__damage {
  color: rgba(219, 227, 238, 0.56);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.88);
  border-color: rgba(193, 208, 224, 0.2);
}

.lightning-hud[data-lightning-hud-status='error'] {
  color: #ffe0dc;
}

.lightning-spell__icon-wrap {
  position: relative;
  display: grid;
  width: 76px;
  height: 76px;
  place-items: center;
  background:
    radial-gradient(circle at 50% 32%, rgba(255, 248, 178, 0.22), transparent 42%),
    linear-gradient(180deg, rgba(255, 216, 98, 0.2), rgba(25, 12, 28, 0.72));
  border: 1px solid rgba(255, 228, 142, 0.58);
  box-shadow:
    0 0 20px rgba(255, 204, 76, 0.28),
    inset 0 0 18px rgba(255, 246, 166, 0.16);
  overflow: hidden;
}

.lightning-spell__icon-wrap::after {
  position: absolute;
  inset: 0;
  content: '';
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.16), transparent 42%);
  mix-blend-mode: screen;
}

.lightning-spell__icon {
  display: block;
  width: 64px;
  height: 64px;
  object-fit: contain;
  filter: drop-shadow(0 0 12px rgba(255, 221, 94, 0.72));
}

.lightning-hud[data-lightning-hud-status='cooldown'] .lightning-spell__icon,
.lightning-hud[data-lightning-hud-status='result'] .lightning-spell__icon {
  opacity: 0.62;
  filter: grayscale(0.48) brightness(0.68) drop-shadow(0 0 5px rgba(120, 150, 170, 0.28));
}

.lightning-hud[data-lightning-hud-status='cooldown'] .lightning-spell__icon-wrap,
.lightning-hud[data-lightning-hud-status='result'] .lightning-spell__icon-wrap {
  background:
    radial-gradient(circle at 50% 32%, rgba(138, 156, 171, 0.14), transparent 44%),
    linear-gradient(180deg, rgba(80, 89, 104, 0.2), rgba(10, 10, 16, 0.78));
  border-color: rgba(156, 169, 184, 0.34);
  box-shadow:
    0 0 12px rgba(92, 124, 146, 0.12),
    inset 0 0 18px rgba(0, 0, 0, 0.28);
}

.lightning-hud[data-lightning-hud-status='offline'] .lightning-spell,
.lightning-hud[data-lightning-hud-status='error'] .lightning-spell {
  opacity: 0.72;
}

.lightning-spell__cooldown {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: conic-gradient(
    rgba(6, 8, 16, 0.72) var(--lightning-cooldown-progress),
    rgba(6, 8, 16, 0.18) 0
  );
  border: 1px solid rgba(220, 235, 255, 0.18);
  animation: lightning-cooldown-spin 2s linear infinite;
}

.lightning-spell__keys {
  display: grid;
  grid-template-columns: repeat(2, 34px);
  gap: 8px;
}

.lightning-spell__keys kbd {
  display: grid;
  width: 34px;
  height: 30px;
  place-items: center;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 900;
  color: #201305;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.52), transparent 36%),
    linear-gradient(180deg, #fff4af, #ffb733);
  border: 1px solid rgba(255, 255, 255, 0.68);
  box-shadow:
    0 2px 0 rgba(83, 40, 0, 0.86),
    0 0 12px rgba(255, 204, 74, 0.24);
}

.lightning-hud[data-lightning-hud-status='cooldown'] .lightning-spell__keys kbd,
.lightning-hud[data-lightning-hud-status='result'] .lightning-spell__keys kbd {
  color: rgba(220, 227, 237, 0.46);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), transparent 38%),
    linear-gradient(180deg, #4c515b, #171a22);
  border-color: rgba(201, 218, 235, 0.18);
  box-shadow:
    0 2px 0 rgba(0, 0, 0, 0.74),
    inset 0 0 10px rgba(0, 0, 0, 0.24);
}

.practice-result-overlay {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 44%, rgba(93, 228, 255, 0.16), transparent 34%),
    rgba(3, 5, 14, 0.74);
  backdrop-filter: blur(4px);
}

.practice-result-panel {
  display: grid;
  gap: 18px;
  width: min(430px, 100%);
  padding: 28px;
  text-align: center;
  background: rgba(8, 12, 28, 0.88);
  border: 1px solid rgba(146, 237, 255, 0.28);
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.44),
    inset 0 0 30px rgba(94, 219, 255, 0.08);
}

.practice-result-panel p,
.practice-result-panel h1 {
  margin: 0;
}

.practice-result-panel > p:first-child {
  font-size: 0.78rem;
  font-weight: 900;
  color: rgba(218, 242, 255, 0.72);
  text-transform: uppercase;
}

.practice-result-panel h1 {
  font-size: clamp(2rem, 6vw, 3.4rem);
  font-weight: 900;
  line-height: 0.98;
  color: #effcff;
  text-shadow:
    0 0 18px rgba(87, 222, 255, 0.48),
    0 0 32px rgba(165, 107, 255, 0.28);
}

.practice-result-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.practice-result-actions button {
  min-width: 0;
  min-height: 44px;
  padding: 0 12px;
  color: #f8fbff;
  font-weight: 900;
  background: rgba(20, 35, 58, 0.86);
  border: 1px solid rgba(99, 242, 232, 0.34);
  border-radius: 4px;
  transition:
    transform 140ms ease,
    border-color 140ms ease,
    background-color 140ms ease,
    box-shadow 140ms ease,
    opacity 140ms ease;
}

.practice-result-actions button:hover:not(:disabled),
.practice-result-actions button:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(99, 242, 232, 0.72);
  background: rgba(29, 55, 84, 0.92);
  box-shadow:
    0 0 0 3px rgba(99, 242, 232, 0.1),
    0 14px 34px rgba(0, 0, 0, 0.32);
}

.practice-result-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.practice-result-error {
  color: #ffd9d6;
}

@keyframes lightning-cooldown-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.payload-error {
  position: relative;
  z-index: 3;
  align-self: center;
  width: min(620px, calc(100% - 40px));
  padding: 12px 16px;
  margin: 0 auto;
  color: #ffd9d6;
  background: rgba(36, 10, 18, 0.82);
  border: 1px solid rgba(255, 182, 178, 0.28);
}

@media (max-width: 520px) {
  .practice-result-panel {
    padding: 22px;
  }

  .practice-result-actions {
    grid-template-columns: 1fr;
  }

  .lightning-hud {
    bottom: max(12px, env(safe-area-inset-bottom));
    width: 116px;
  }

  .lightning-hud--mine {
    left: 50%;
  }

  .lightning-spell {
    grid-template-rows: 18px 68px 28px;
  }

  .lightning-spell__damage {
    min-width: 68px;
    padding: 2px 7px;
    font-size: 11px;
  }

  .lightning-spell__icon-wrap {
    width: 68px;
    height: 68px;
  }

  .lightning-spell__icon {
    width: 58px;
    height: 58px;
  }

  .lightning-spell__keys {
    grid-template-columns: repeat(2, 32px);
    gap: 7px;
  }

  .lightning-spell__keys kbd {
    width: 32px;
    height: 28px;
  }
}
</style>
