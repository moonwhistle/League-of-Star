<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
  <main
    class="game-waiting-page"
    :style="{ '--game-waiting-background-image': `url(${backgroundImageUrl})` }"
    :data-game-waiting-payload-ready="gameWaitingPayload !== undefined"
    :data-game-room-id="gameWaitingPayload?.game.gameRoomId ?? ''"
    :data-loading-progress="loadingProgress"
    :data-game-socket-status="gameSocketStatus"
    :data-game-socket-last-event="gameSocketLastEvent"
    :data-game-socket-both-ready="gameSocketBothReady"
    :data-game-socket-error-message="gameSocketErrorMessage"
    :data-game-countdown-seconds="visibleCountdownSeconds"
    :data-game-countdown-start-at="gameCountdownStartAt"
  >
    <header class="game-waiting-header" aria-label="Game waiting header">
      <h1>LEAGUE OF SMITE</h1>
      <button class="locale-toggle" type="button" @click="toggleLocale">
        {{ nextLocaleLabel }}
      </button>
    </header>

    <p v-if="payloadErrorMessage !== ''" class="payload-error" role="alert">
      {{ payloadErrorMessage }}
    </p>

    <section
      v-if="visibleCountdownSeconds > 0"
      class="countdown-overlay"
      aria-label="Game start countdown"
      aria-live="polite"
    >
      <span>{{ t('gameWaiting.countdownNumber') }}</span>
      <strong :key="visibleCountdownSeconds">{{ visibleCountdownSeconds }}</strong>
    </section>

    <section class="game-waiting-stage" aria-label="Game waiting details">
      <section class="combatant-panel is-player" aria-label="Player info">
        <span class="panel-label">{{ t('gameWaiting.myInfo') }}</span>
        <div class="combatant-body">
          <div class="combatant-emblem" aria-hidden="true">S</div>
          <h2>Summoner</h2>
          <dl>
            <div>
              <dt>{{ t('gameWaiting.tier') }}</dt>
              <dd>BRONZE IV</dd>
            </div>
            <div>
              <dt>LP</dt>
              <dd>1,248</dd>
            </div>
          </dl>
        </div>
      </section>

      <div class="versus-mark" aria-hidden="true">VS</div>

      <section class="combatant-panel is-opponent" aria-label="Opponent info">
        <span class="panel-label">{{ t('gameWaiting.opponentInfo') }}</span>
        <div class="combatant-body">
          <div class="combatant-emblem" aria-hidden="true">?</div>
          <h2>{{ opponentName }}</h2>
          <dl>
            <div>
              <dt>{{ t('gameWaiting.tier') }}</dt>
              <dd>{{ opponentTier }}</dd>
            </div>
            <div>
              <dt>LP</dt>
              <dd>{{ opponentTierScore }}</dd>
            </div>
          </dl>
        </div>
      </section>
    </section>

    <section class="waiting-status" aria-label="Loading status">
      <div class="status-copy">
        <p>{{ t('gameWaiting.status') }}</p>
        <h2>{{ t('gameWaiting.title') }}</h2>
      </div>

      <div class="socket-status" aria-label="Game WebSocket status">
        <span :class="['socket-status-indicator', `is-${gameSocketStatus}`]" aria-hidden="true" />
        <strong>{{ gameSocketStatusLabel }}</strong>
        <p>{{ gameSocketDetailLabel }}</p>
      </div>

      <div class="loading-meter" aria-label="Payload loading progress">
        <div class="loading-meter-row">
          <strong>{{ loadingProgress }}%</strong>
          <span>{{ loadingStatusLabel }}</span>
        </div>
        <div class="loading-track" aria-hidden="true">
          <span v-for="step in loadingSteps" :key="step.key" :class="{ 'is-filled': step.ready }" />
        </div>
        <ol class="loading-steps">
          <li v-for="step in loadingSteps" :key="step.key" :class="{ 'is-ready': step.ready }">
            <span>{{ step.label }}</span>
            <strong>{{ step.ready ? t('gameWaiting.ready') : t('gameWaiting.pending') }}</strong>
          </li>
        </ol>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { saveGameStartPayloadFromMessage } from '@/services/gameStartPayload'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'
import { calculateGameWaitingProgress } from '@/services/gameWaitingProgress'
import { connectGameWebSocket } from '@/services/realtime/gameWebSocket'
import {
  handoffGameWebSocket,
  takeGameWebSocketHandoff,
} from '@/services/realtime/gameWebSocketHandoff'

import backgroundImageUrl from '../../img/background.png'

const GAME_WAITING_CLIENT_WATCHDOG_MS = 30000
const route = useRoute()
const router = useRouter()
const { nextLocaleLabel, t, toggleLocale } = useLocale()
const gameWaitingPayload = shallowRef()
const payloadErrorMessage = ref('')
const gameSocketStatus = ref('idle')
const gameSocketLastEvent = ref('')
const gameSocketErrorMessage = ref('')
const gameSocketBothReady = ref(false)
const gameSocketPlayerLeftUserId = ref('')
const gameCountdownStartAt = ref(0)
const gameCountdownDisplaySeconds = ref(0)
const gameCountdownRemainingSeconds = ref(0)
const videoPreloadElement = shallowRef()
const gameWebSocketConnection = shallowRef()
let cleanupVideoPreloadListeners = () => {}
let gameWaitingWatchdogId = 0
let gameCountdownTimerId = 0
let closeGameWebSocket = () => {}
let sendGameSocketClientReady = () => {}
let sendGameSocketRttPong = (seq = 0) => {
  void seq
}
let isActive = false
let hasFinalGameSocketFailure = false
let hasCompletedGameStartTransition = false
let hasSentClientReady = false

const opponentName = computed(
  () => gameWaitingPayload.value?.opponent?.nickname ?? t('gameWaiting.unknownOpponent'),
)
const opponentTier = computed(() => gameWaitingPayload.value?.opponent?.tier ?? '-')
const opponentTierScore = computed(() =>
  String(gameWaitingPayload.value?.opponent?.tierScore ?? '-'),
)
const loadingProgressState = computed(() => calculateGameWaitingProgress(gameWaitingPayload.value))
const loadingSteps = computed(() =>
  loadingProgressState.value.steps.map((step) => ({
    ...step,
    label: getLoadingStepLabel(step.key),
  })),
)
const loadingProgress = computed(() => loadingProgressState.value.progress)
const loadingStatusLabel = computed(() =>
  loadingProgress.value >= 100 ? t('gameWaiting.ready') : t('gameWaiting.pending'),
)
const visibleCountdownSeconds = computed(() =>
  gameCountdownRemainingSeconds.value > 0 &&
  gameCountdownRemainingSeconds.value <= gameCountdownDisplaySeconds.value
    ? gameCountdownRemainingSeconds.value
    : 0,
)
const gameSocketStatusLabel = computed(() => {
  if (gameSocketStatus.value === 'connecting') {
    return t('gameWaiting.socketConnecting')
  }

  if (gameSocketStatus.value === 'connected') {
    return t('gameWaiting.socketConnected')
  }

  if (gameSocketStatus.value === 'preloading') {
    return t('gameWaiting.videoPreloading')
  }

  if (gameSocketStatus.value === 'readySent') {
    return t('gameWaiting.readySent')
  }

  if (gameSocketStatus.value === 'waitingOpponent') {
    return t('gameWaiting.waitingOpponentReady')
  }

  if (gameSocketStatus.value === 'bothReady') {
    return t('gameWaiting.bothReady')
  }

  if (gameSocketStatus.value === 'rttMeasuring') {
    return t('gameWaiting.rttMeasuring')
  }

  if (gameSocketStatus.value === 'countdown') {
    return t('gameWaiting.countdown')
  }

  if (gameSocketStatus.value === 'starting') {
    return t('gameWaiting.gameStarting')
  }

  if (gameSocketStatus.value === 'failed') {
    return t('gameWaiting.websocketFailed')
  }

  return t('gameWaiting.socketPending')
})
const gameSocketDetailLabel = computed(() => {
  if (gameSocketLastEvent.value === 'PLAYER_LEFT') {
    return `${t('gameWaiting.playerLeft')} ${gameSocketPlayerLeftUserId.value}`.trim()
  }

  if (gameSocketErrorMessage.value !== '') {
    return `${gameSocketErrorMessage.value} ${t('gameWaiting.returningToMatch')}`.trim()
  }

  if (gameSocketStatus.value === 'connecting') {
    return t('gameWaiting.socketConnectingDetail')
  }

  if (gameSocketStatus.value === 'connected') {
    return t('gameWaiting.socketConnectedDetail')
  }

  if (gameSocketStatus.value === 'preloading') {
    return t('gameWaiting.videoPreloadingDetail')
  }

  if (gameSocketStatus.value === 'readySent') {
    return t('gameWaiting.readySentDetail')
  }

  if (gameSocketStatus.value === 'waitingOpponent') {
    return t('gameWaiting.waitingOpponentReadyDetail')
  }

  if (gameSocketStatus.value === 'bothReady') {
    return t('gameWaiting.bothReadyDetail')
  }

  if (gameSocketStatus.value === 'rttMeasuring') {
    return t('gameWaiting.rttMeasuringDetail')
  }

  if (gameSocketStatus.value === 'countdown') {
    return t('gameWaiting.countdownDetail')
  }

  if (gameSocketStatus.value === 'starting') {
    return t('gameWaiting.gameStartingDetail')
  }

  return t('gameWaiting.socketDetail')
})

onMounted(() => {
  isActive = true
  window.addEventListener('beforeunload', handleBeforeUnload)
  const gameRoomId = readRouteGameRoomId()

  if (gameRoomId === '') {
    returnToMatchWithPayloadError()
    return
  }

  const payload = readGameWaitingPayload(gameRoomId)

  if (payload === null) {
    returnToMatchWithPayloadError()
    return
  }

  gameWaitingPayload.value = payload
  connectWaitingWebSocket(payload)
})

onUnmounted(() => {
  isActive = false
  window.removeEventListener('beforeunload', handleBeforeUnload)
  closeWaitingWebSocket()
})

onBeforeRouteLeave((to) => {
  if (isAllowedGameStartRouteLeave(to)) {
    return true
  }

  if (!shouldWarnBeforeLeaving()) {
    return true
  }

  return window.confirm(t('gameWaiting.leaveWarning'))
})

function handleBeforeUnload() {
  if (!shouldWarnBeforeLeaving()) {
    return
  }

  const event = arguments[0]
  event.preventDefault()
  event.returnValue = ''
}

function shouldWarnBeforeLeaving() {
  return (
    gameWaitingPayload.value !== undefined &&
    !hasFinalGameSocketFailure &&
    !hasCompletedGameStartTransition
  )
}

function isAllowedGameStartRouteLeave(to = {}) {
  return hasCompletedGameStartTransition && Reflect.get(Object(to), 'name') === ROUTE_NAMES.gamePlay
}

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match }).catch((error) => {
    if (!isActive) {
      return
    }

    const routeErrorMessage =
      error instanceof Error && error.message.trim() !== '' ? ` ${error.message}` : ''
    gameSocketStatus.value = 'failed'
    gameSocketErrorMessage.value =
      `${gameSocketErrorMessage.value || t('gameWaiting.websocketFailed')} ${t('gameWaiting.returnFailed')}${routeErrorMessage}`.trim()
  })
}

function returnToMatchWithPayloadError() {
  payloadErrorMessage.value = t('gameWaiting.payloadMissing')
  returnToMatch()
}

function connectWaitingWebSocket(payload = gameWaitingPayload.value) {
  gameSocketStatus.value = 'connecting'
  gameSocketErrorMessage.value = ''
  gameSocketLastEvent.value = ''
  gameSocketBothReady.value = false
  gameSocketPlayerLeftUserId.value = ''
  resetGameCountdown()
  hasSentClientReady = false
  hasFinalGameSocketFailure = false
  hasCompletedGameStartTransition = false
  closeWaitingWebSocket()
  startGameWaitingWatchdog()

  try {
    const connection = connectGameWebSocket(payload.game.webSocketUrl, {
      onOpen: () => {
        if (!canHandleGameSocketCallback()) {
          return
        }

        startGameVideoPreload(payload.game.videoUrl)
      },
      onMessage: (message) => {
        if (!canHandleGameSocketCallback()) {
          return
        }

        handleGameSocketMessage(message)
      },
      onError: (error) => {
        if (!canHandleGameSocketCallback()) {
          return
        }

        failGameSocketAndReturnToMatch(
          error instanceof Error ? error.message : t('gameWaiting.websocketFailed'),
        )
      },
      onClose: () => {
        if (!canHandleGameSocketCallback()) {
          return
        }

        failGameSocketAndReturnToMatch(t('gameWaiting.websocketClosed'))
      },
    })

    gameWebSocketConnection.value = connection
    closeGameWebSocket = connection.close
    sendGameSocketClientReady = connection.sendClientReady
    sendGameSocketRttPong = (seq = 0) => connection.sendRttPong(seq)
  } catch (error) {
    failGameSocketAndReturnToMatch(
      error instanceof Error ? error.message : t('gameWaiting.websocketFailed'),
    )
  }
}

function startGameWaitingWatchdog() {
  clearGameWaitingWatchdog()
  gameWaitingWatchdogId = window.setTimeout(() => {
    if (!canHandleGameSocketCallback()) {
      return
    }

    failGameSocketAndReturnToMatch(t('gameWaiting.watchdogTimeout'))
  }, GAME_WAITING_CLIENT_WATCHDOG_MS)
}

function clearGameWaitingWatchdog() {
  if (gameWaitingWatchdogId === 0) {
    return
  }

  window.clearTimeout(gameWaitingWatchdogId)
  gameWaitingWatchdogId = 0
}

function startGameVideoPreload(videoUrl = '') {
  cleanupGameVideoPreload()
  gameSocketStatus.value = 'preloading'

  const video = document.createElement('video')
  videoPreloadElement.value = video
  video.preload = 'auto'
  video.muted = true
  video.playsInline = true

  const completePreload = () => {
    if (videoPreloadElement.value !== video || !canHandleGameSocketCallback()) {
      return
    }

    cleanupGameVideoPreload()
    sendClientReadyOnce()
  }
  const failPreload = () => {
    if (videoPreloadElement.value !== video || !canHandleGameSocketCallback()) {
      return
    }

    failGameSocketAndReturnToMatch(t('gameWaiting.videoPreloadFailed'))
  }

  video.addEventListener('loadeddata', completePreload)
  video.addEventListener('canplaythrough', completePreload)
  video.addEventListener('error', failPreload)
  cleanupVideoPreloadListeners = () => {
    video.removeEventListener('loadeddata', completePreload)
    video.removeEventListener('canplaythrough', completePreload)
    video.removeEventListener('error', failPreload)
  }

  video.src = videoUrl
  video.load()
}

function sendClientReadyOnce() {
  if (hasSentClientReady || !canHandleGameSocketCallback()) {
    return
  }

  try {
    hasSentClientReady = true
    sendGameSocketClientReady()
    gameSocketStatus.value = 'readySent'
  } catch (error) {
    failGameSocketAndReturnToMatch(
      error instanceof Error ? error.message : t('gameWaiting.websocketFailed'),
    )
  }
}

function handleGameSocketMessage(message = {}) {
  const messageType = String(Reflect.get(message, 'type') ?? '')
  const payload = Reflect.get(message, 'payload')
  gameSocketLastEvent.value = messageType

  if (messageType === 'PLAYER_JOINED') {
    gameSocketStatus.value = 'connected'
    return
  }

  if (messageType === 'PLAYER_READY') {
    const bothReady = Reflect.get(Object(payload), 'bothReady') === true
    gameSocketBothReady.value = bothReady
    gameSocketStatus.value = bothReady ? 'bothReady' : 'waitingOpponent'

    if (bothReady) {
      clearGameWaitingWatchdog()
    }

    return
  }

  if (messageType === 'PLAYER_LEFT') {
    gameSocketStatus.value = 'waitingOpponent'
    gameSocketPlayerLeftUserId.value = String(Reflect.get(Object(payload), 'userId') ?? '').trim()
    return
  }

  if (messageType === 'RTT_PING') {
    const seq = Reflect.get(Object(payload), 'seq')
    clearGameWaitingWatchdog()
    gameSocketStatus.value = 'rttMeasuring'

    if (Number.isFinite(seq)) {
      sendGameSocketRttPong(Number(seq))
    }

    return
  }

  if (messageType === 'COUNTDOWN') {
    handleCountdownMessage(payload)
    return
  }

  if (messageType === 'GAME_START') {
    handleGameStartMessage(payload)
    return
  }

  if (
    messageType === 'GAME_WAITING_TIMEOUT' ||
    messageType === 'GAME_START_FAILED' ||
    messageType === 'ERROR'
  ) {
    failGameSocketAndReturnToMatch(resolveGameSocketFailureMessage(payload))
  }
}

function resolveGameSocketFailureMessage(payload = {}) {
  const reason = Reflect.get(Object(payload), 'reason')

  return typeof reason === 'string' && reason.trim() !== ''
    ? reason
    : t('gameWaiting.websocketFailed')
}

function handleCountdownMessage(payload = {}) {
  const gameRoomId = Reflect.get(Object(payload), 'gameRoomId')
  const serverTime = Reflect.get(Object(payload), 'serverTime')
  const startAt = Reflect.get(Object(payload), 'startAt')
  const countdownDisplaySeconds = Reflect.get(Object(payload), 'countdownDisplaySeconds')

  if (
    !Number.isFinite(gameRoomId) ||
    !Number.isFinite(serverTime) ||
    !Number.isFinite(startAt) ||
    !Number.isFinite(countdownDisplaySeconds) ||
    countdownDisplaySeconds <= 0 ||
    normalizeGameRoomId(gameRoomId) !== readRouteGameRoomId()
  ) {
    failGameSocketAndReturnToMatch(t('gameWaiting.startPayloadInvalid'))
    return
  }

  clearGameWaitingWatchdog()
  gameSocketStatus.value = 'countdown'
  gameCountdownStartAt.value = Number(startAt)
  gameCountdownDisplaySeconds.value = Number(countdownDisplaySeconds)
  startGameCountdownTimer()
}

function handleGameStartMessage(payload = {}) {
  const gameRoomId = Reflect.get(Object(payload), 'gameRoomId')
  const startAt = Reflect.get(Object(payload), 'startAt')
  const routeGameRoomId = readRouteGameRoomId()

  if (
    !Number.isFinite(gameRoomId) ||
    !Number.isFinite(startAt) ||
    normalizeGameRoomId(gameRoomId) !== routeGameRoomId ||
    (gameCountdownStartAt.value !== 0 && Number(startAt) !== gameCountdownStartAt.value)
  ) {
    failGameSocketAndReturnToMatch(t('gameWaiting.startPayloadInvalid'))
    return
  }

  const storedPayload = saveGameStartPayloadFromMessage(payload)

  if (storedPayload === null) {
    failGameSocketAndReturnToMatch(t('gameWaiting.startPayloadInvalid'))
    return
  }

  clearGameWaitingWatchdog()
  gameSocketStatus.value = 'starting'
  gameSocketErrorMessage.value = ''
  hasCompletedGameStartTransition = true
  handoffWaitingWebSocket(routeGameRoomId)

  void router
    .push({
      name: ROUTE_NAMES.gamePlay,
      params: {
        gameRoomId: routeGameRoomId,
      },
    })
    .catch((error) => {
      if (!isActive) {
        return
      }

      const routeErrorMessage =
        error instanceof Error && error.message.trim() !== '' ? ` ${error.message}` : ''
      takeGameWebSocketHandoff(routeGameRoomId)?.close()
      hasCompletedGameStartTransition = false
      failGameSocket(`${t('gameWaiting.gameStartTransitionFailed')}${routeErrorMessage}`.trim())
    })
}

function startGameCountdownTimer() {
  clearGameCountdownTimer()
  updateGameCountdownRemainingSeconds()
  gameCountdownTimerId = window.setInterval(updateGameCountdownRemainingSeconds, 250)
}

function updateGameCountdownRemainingSeconds() {
  gameCountdownRemainingSeconds.value = Math.max(
    0,
    Math.ceil((gameCountdownStartAt.value - Date.now()) / 1000),
  )

  if (gameCountdownRemainingSeconds.value <= 0) {
    clearGameCountdownTimer()
  }
}

function clearGameCountdownTimer() {
  if (gameCountdownTimerId === 0) {
    return
  }

  window.clearInterval(gameCountdownTimerId)
  gameCountdownTimerId = 0
}

function resetGameCountdown() {
  clearGameCountdownTimer()
  gameCountdownStartAt.value = 0
  gameCountdownDisplaySeconds.value = 0
  gameCountdownRemainingSeconds.value = 0
}

function failGameSocket(message = t('gameWaiting.websocketFailed')) {
  hasFinalGameSocketFailure = true
  clearGameWaitingWatchdog()
  resetGameCountdown()
  cleanupGameVideoPreload()
  gameSocketStatus.value = 'failed'
  gameSocketErrorMessage.value = message
}

function failGameSocketAndReturnToMatch(message = t('gameWaiting.websocketFailed')) {
  failGameSocket(message)
  closeWaitingWebSocket()
  returnToMatch()
}

function canHandleGameSocketCallback() {
  return isActive && !hasFinalGameSocketFailure && !hasCompletedGameStartTransition
}

function closeWaitingWebSocket() {
  clearGameWaitingWatchdog()
  resetGameCountdown()
  cleanupGameVideoPreload()
  closeGameWebSocket()
  gameWebSocketConnection.value = undefined
  closeGameWebSocket = () => {}
  sendGameSocketClientReady = () => {}
  sendGameSocketRttPong = (seq = 0) => {
    void seq
  }
}

function handoffWaitingWebSocket(gameRoomId = '') {
  clearGameWaitingWatchdog()
  resetGameCountdown()
  cleanupGameVideoPreload()

  if (gameWebSocketConnection.value !== undefined) {
    gameWebSocketConnection.value.setHandlers()
    handoffGameWebSocket(gameRoomId, gameWebSocketConnection.value)
  }

  gameWebSocketConnection.value = undefined
  closeGameWebSocket = () => {}
  sendGameSocketClientReady = () => {}
  sendGameSocketRttPong = (seq = 0) => {
    void seq
  }
}

function readRouteGameRoomId() {
  const routeGameRoomIdParam = route.params.gameRoomId

  return Array.isArray(routeGameRoomIdParam)
    ? String(routeGameRoomIdParam[0] ?? '').trim()
    : String(routeGameRoomIdParam ?? '').trim()
}

function normalizeGameRoomId(gameRoomId = '') {
  return String(gameRoomId).trim()
}

function cleanupGameVideoPreload() {
  cleanupVideoPreloadListeners()
  cleanupVideoPreloadListeners = () => {}
  videoPreloadElement.value = undefined
}

function getLoadingStepLabel(key = '') {
  if (key === 'matchId') {
    return t('gameWaiting.matchData')
  }

  if (key === 'opponent') {
    return t('gameWaiting.opponentInfo')
  }

  if (key === 'gameRoomId') {
    return t('gameWaiting.gameSetup')
  }

  if (key === 'videoUrl') {
    return t('gameWaiting.video')
  }

  return t('gameWaiting.socket')
}
</script>

<style scoped>
.game-waiting-page {
  --waiting-panel: rgba(6, 12, 30, 0.78);
  --waiting-panel-strong: rgba(5, 9, 22, 0.92);
  --waiting-line: rgba(102, 240, 232, 0.22);
  --waiting-cyan: #62f4ed;
  --waiting-pink: #ffb6b2;
  --waiting-violet: #d7b9ff;
  --waiting-text: #f5f8ff;
  --waiting-muted: rgba(221, 230, 246, 0.68);

  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  width: 100%;
  min-height: 100dvh;
  overflow: hidden;
  font-family: var(--font-sans);
  color: var(--waiting-text);
  background:
    linear-gradient(180deg, rgba(5, 10, 24, 0.78), rgba(5, 10, 24, 0.92)),
    linear-gradient(90deg, rgba(4, 9, 23, 0.88), rgba(4, 9, 23, 0.3) 50%, rgba(4, 9, 23, 0.88)),
    var(--game-waiting-background-image) center / cover no-repeat;
}

.game-waiting-page,
.game-waiting-page * {
  box-sizing: border-box;
}

.game-waiting-page::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background:
    linear-gradient(rgba(98, 244, 237, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(98, 244, 237, 0.035) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: linear-gradient(180deg, transparent, #000 18%, #000 82%, transparent);
}

.game-waiting-header,
.game-waiting-stage,
.waiting-status {
  position: relative;
  z-index: 1;
}

.game-waiting-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
  padding: 0 clamp(20px, 4vw, 42px);
  border-bottom: 1px solid rgba(206, 224, 255, 0.1);
}

.game-waiting-header h1 {
  margin: 0;
  overflow: hidden;
  font-size: 1.42rem;
  font-weight: 900;
  letter-spacing: 0;
  color: #f0d7ff;
  text-shadow: 0 0 18px rgba(188, 107, 255, 0.64);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.locale-toggle {
  width: 42px;
  height: 32px;
  color: var(--waiting-muted);
  background: rgba(8, 15, 34, 0.7);
  border: 1px solid rgba(206, 224, 255, 0.18);
  border-radius: 4px;
  font-size: 0.76rem;
  font-weight: 900;
}

.payload-error {
  position: relative;
  z-index: 1;
  width: min(620px, calc(100% - 40px));
  padding: 12px 16px;
  margin: 16px auto 0;
  color: #ffd9d6;
  background: rgba(36, 10, 18, 0.8);
  border: 1px solid rgba(255, 182, 178, 0.28);
}

.game-waiting-stage {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(88px, 140px) minmax(240px, 1fr);
  gap: clamp(16px, 3vw, 34px);
  align-items: center;
  width: min(1120px, calc(100% - 40px));
  margin: 0 auto;
  padding: clamp(22px, 5vh, 56px) 0;
}

.combatant-panel {
  min-width: 0;
}

.panel-label {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 14px;
  font-size: 0.76rem;
  font-weight: 900;
  color: var(--waiting-cyan);
  text-transform: uppercase;
}

.is-opponent .panel-label {
  color: var(--waiting-pink);
}

.panel-label::before,
.panel-label::after {
  width: 4px;
  height: 16px;
  content: '';
  background: currentcolor;
}

.combatant-body {
  position: relative;
  min-height: 210px;
  padding: 30px clamp(20px, 4vw, 44px);
  overflow: hidden;
  background: linear-gradient(180deg, rgba(7, 14, 34, 0.86), rgba(6, 11, 26, 0.74));
  border: 1px solid var(--waiting-line);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.combatant-body::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  content: '';
  background: linear-gradient(90deg, var(--waiting-cyan), transparent);
}

.is-opponent .combatant-body::after {
  background: linear-gradient(90deg, transparent, var(--waiting-pink));
}

.combatant-emblem {
  display: grid;
  width: 54px;
  height: 54px;
  margin: 0 auto 18px;
  place-items: center;
  font-weight: 900;
  color: #06101c;
  background: var(--waiting-cyan);
  border: 2px solid rgba(210, 255, 251, 0.72);
  border-radius: 50%;
  box-shadow: 0 0 22px rgba(98, 244, 237, 0.34);
}

.is-opponent .combatant-emblem {
  background: var(--waiting-pink);
  border-color: rgba(255, 224, 220, 0.72);
  box-shadow: 0 0 22px rgba(255, 182, 178, 0.28);
}

.combatant-body h2 {
  margin: 0 0 18px;
  overflow-wrap: anywhere;
  font-size: clamp(1.45rem, 3vw, 2.25rem);
  font-weight: 900;
  line-height: 1.18;
  text-align: center;
  color: #edf1ff;
}

.combatant-body dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  margin: 0;
}

.combatant-body div {
  min-width: 0;
}

.combatant-body dl > div {
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.04);
}

.combatant-body dt {
  margin-bottom: 4px;
  font-size: 0.68rem;
  font-weight: 900;
  color: var(--waiting-muted);
  text-transform: uppercase;
}

.combatant-body dd {
  margin: 0;
  overflow: hidden;
  font-size: 0.98rem;
  font-weight: 900;
  color: #f6f8ff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.versus-mark {
  display: grid;
  min-width: 0;
  place-items: center;
  font-size: clamp(3rem, 7vw, 5.6rem);
  font-style: italic;
  font-weight: 900;
  color: #e4fbff;
  text-shadow:
    0 0 16px rgba(98, 244, 237, 0.86),
    0 0 34px rgba(188, 107, 255, 0.42);
}

.waiting-status {
  width: min(980px, calc(100% - 40px));
  margin: 0 auto clamp(24px, 5vh, 44px);
}

.status-copy {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.status-copy p,
.status-copy h2 {
  margin: 0;
}

.status-copy p {
  font-size: 0.78rem;
  font-weight: 900;
  color: var(--waiting-cyan);
  text-transform: uppercase;
}

.status-copy h2 {
  font-size: clamp(1.25rem, 3vw, 2rem);
  font-weight: 900;
  color: var(--waiting-violet);
}

.socket-status {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 10px;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(206, 224, 255, 0.08);
}

.socket-status-indicator {
  width: 9px;
  height: 9px;
  background: rgba(221, 230, 246, 0.5);
  border-radius: 50%;
}

.socket-status-indicator.is-connected,
.socket-status-indicator.is-readySent,
.socket-status-indicator.is-waitingOpponent,
.socket-status-indicator.is-bothReady {
  background: var(--waiting-cyan);
  box-shadow: 0 0 12px rgba(98, 244, 237, 0.54);
}

.socket-status-indicator.is-rttMeasuring,
.socket-status-indicator.is-countdown,
.socket-status-indicator.is-starting,
.socket-status-indicator.is-preloading,
.socket-status-indicator.is-connecting {
  background: var(--waiting-violet);
  box-shadow: 0 0 12px rgba(215, 185, 255, 0.44);
}

.socket-status-indicator.is-failed {
  background: var(--waiting-pink);
  box-shadow: 0 0 12px rgba(255, 182, 178, 0.46);
}

.socket-status strong {
  min-width: 0;
  overflow: hidden;
  font-size: 0.76rem;
  font-weight: 900;
  color: #f6f8ff;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.socket-status p {
  grid-column: 2;
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 0.68rem;
  font-weight: 800;
  color: var(--waiting-muted);
  white-space: normal;
}

.countdown-overlay {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  grid-template-rows: auto auto;
  gap: clamp(10px, 2vh, 18px);
  place-content: center;
  place-items: center;
  pointer-events: none;
  background: rgba(3, 7, 18, 0.44);
  backdrop-filter: blur(4px);
}

.countdown-overlay span {
  max-width: min(520px, calc(100vw - 40px));
  overflow: hidden;
  font-size: clamp(0.82rem, 2vw, 1.05rem);
  font-weight: 900;
  color: rgba(245, 248, 255, 0.78);
  text-align: center;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.countdown-overlay strong {
  display: block;
  min-width: 0;
  font-size: clamp(7rem, 26vw, 18rem);
  font-weight: 900;
  line-height: 1.18;
  color: rgba(245, 248, 255, 0.82);
  text-align: center;
  text-shadow:
    0 0 30px rgba(98, 244, 237, 0.64),
    0 0 80px rgba(215, 185, 255, 0.36);
  animation: countdown-pulse 920ms ease-out both;
}

@keyframes countdown-pulse {
  0% {
    opacity: 0;
    transform: scale(0.82);
  }

  28% {
    opacity: 1;
  }

  100% {
    opacity: 0.68;
    transform: scale(1.08);
  }
}

.loading-meter-row {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.loading-meter-row strong {
  font-size: clamp(1.5rem, 4vw, 2.4rem);
  line-height: 1.15;
  color: var(--waiting-cyan);
  text-shadow: 0 0 16px rgba(98, 244, 237, 0.52);
}

.loading-meter-row span {
  font-size: 0.78rem;
  font-weight: 900;
  color: var(--waiting-muted);
  text-transform: uppercase;
}

.loading-track {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  height: 12px;
  padding: 2px;
  background: rgba(206, 224, 255, 0.1);
  border: 1px solid rgba(206, 224, 255, 0.14);
}

.loading-track span {
  min-width: 0;
  background: rgba(206, 224, 255, 0.16);
}

.loading-track span.is-filled {
  background: linear-gradient(90deg, var(--waiting-violet), var(--waiting-cyan));
  box-shadow: 0 0 18px rgba(98, 244, 237, 0.34);
}

.loading-steps {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  padding: 0;
  margin: 10px 0 0;
  list-style: none;
}

.loading-steps li {
  min-width: 0;
  padding: 10px 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(206, 224, 255, 0.08);
}

.loading-steps span,
.loading-steps strong {
  display: block;
  overflow: hidden;
  font-size: 0.68rem;
  font-weight: 900;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.loading-steps span {
  color: var(--waiting-muted);
}

.loading-steps strong {
  margin-top: 4px;
  color: rgba(221, 230, 246, 0.55);
}

.loading-steps .is-ready strong {
  color: var(--waiting-cyan);
}

@media (max-width: 760px) {
  .game-waiting-page {
    min-height: 100dvh;
    overflow-y: hidden;
  }

  .game-waiting-header {
    min-height: 54px;
    padding: 0 16px;
  }

  .game-waiting-header h1 {
    font-size: 1.05rem;
  }

  .game-waiting-stage {
    grid-template-columns: minmax(0, 1fr) 42px minmax(0, 1fr);
    gap: 8px;
    width: min(100% - 24px, 420px);
    padding: 12px 0 14px;
  }

  .versus-mark {
    font-size: 1.85rem;
  }

  .panel-label {
    margin-bottom: 8px;
    font-size: 0.62rem;
  }

  .panel-label::before,
  .panel-label::after {
    width: 3px;
    height: 11px;
  }

  .combatant-body {
    min-height: 0;
    padding: 12px 8px;
  }

  .combatant-emblem {
    width: 34px;
    height: 34px;
    margin-bottom: 10px;
    font-size: 0.72rem;
  }

  .combatant-body h2 {
    margin-bottom: 10px;
    font-size: 0.92rem;
    line-height: 1.2;
  }

  .combatant-body dl > div {
    padding: 7px 5px;
  }

  .combatant-body dt {
    font-size: 0.56rem;
  }

  .combatant-body dd {
    overflow-wrap: anywhere;
    font-size: 0.7rem;
    white-space: normal;
  }

  .waiting-status {
    width: min(100% - 24px, 420px);
    margin-bottom: 16px;
  }

  .status-copy,
  .loading-meter-row {
    align-items: start;
    flex-direction: column;
    gap: 6px;
  }

  .status-copy {
    margin-bottom: 10px;
  }

  .socket-status {
    padding: 7px 8px;
    margin-bottom: 8px;
  }

  .status-copy p,
  .loading-meter-row span,
  .socket-status p {
    font-size: 0.64rem;
  }

  .socket-status strong {
    font-size: 0.66rem;
  }

  .status-copy h2 {
    font-size: 1.18rem;
  }

  .loading-meter-row {
    margin-bottom: 6px;
  }

  .loading-meter-row strong {
    font-size: 1.42rem;
  }

  .loading-track {
    gap: 4px;
    height: 10px;
  }

  .loading-steps {
    grid-template-columns: 1fr;
    gap: 4px;
    margin-top: 8px;
  }

  .loading-steps li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 6px 8px;
  }

  .loading-steps span,
  .loading-steps strong {
    min-width: 0;
    font-size: 0.62rem;
  }

  .loading-steps strong {
    margin-top: 0;
    text-align: right;
  }
}
</style>
