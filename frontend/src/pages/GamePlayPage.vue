<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/html-self-closing -->
  <main
    class="game-play-page"
    :data-game-start-payload-ready="gameStartPayload !== null"
    :data-game-waiting-payload-ready="gameWaitingPayload !== null"
    :data-game-play-state-ready="playState !== null"
    :data-game-room-id="gameStartPayload?.gameRoomId ?? ''"
    :data-game-start-at="gameStartPayload?.startAt ?? ''"
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
    :data-game-lightning-ready="canSendLightningCommand"
    :data-game-lightning-sent="lightningSent"
    :data-game-three-ready="isThreeSceneReady"
  >
    <section v-if="playState !== null" class="game-arena" aria-label="Game play arena">
      <canvas
        ref="threeCanvas"
        class="three-scene"
        data-testid="three-scene"
        aria-label="Galaxy space background"
      />
      <div class="space-vignette" aria-hidden="true" />
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
import { readGameStartPayload } from '@/services/gameStartPayload'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'
import { connectGameWebSocket } from '@/services/realtime/gameWebSocket'
import { takeGameWebSocketHandoff } from '@/services/realtime/gameWebSocketHandoff'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const threeCanvas = shallowRef(null)
const gameStartPayload = shallowRef(readGameStartPayload('__missing__'))
const gameWaitingPayload = shallowRef(readGameWaitingPayload('__missing__'))
const nowMs = shallowRef(Date.now())
const gameSocketStatus = shallowRef('idle')
const gameSocketLastEvent = shallowRef('')
const gameSocketErrorMessage = shallowRef('')
const gameResultReceived = shallowRef(false)
const playGameWebSocketConnection = shallowRef()
const lightningSent = shallowRef(false)
const isThreeSceneReady = shallowRef(false)
const isStarTargeted = shallowRef(false)
let animationFrameId = 0
let closePlayWebSocket = () => {}
let threeSceneController = createNoopThreeGalaxyBackgroundSceneController()
let hasThreeSceneController = false

const LIGHTNING_SENT_STORAGE_PREFIX = 'league-of-star.gamePlayLightningSent'

const playState = computed(() => {
  if (gameStartPayload.value === null || gameWaitingPayload.value === null) {
    return null
  }

  return {
    gameRoomId: gameStartPayload.value.gameRoomId,
    startAt: gameStartPayload.value.startAt,
    durationMs: gameStartPayload.value.scenario.durationMs,
    scenario: gameStartPayload.value.scenario,
    webSocketUrl: gameWaitingPayload.value.game.webSocketUrl,
  }
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
const currentHp = computed(() =>
  playState.value === null ? 0 : getHpAtElapsedMs(playState.value.scenario, elapsedMs.value),
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

const canSendGameSocketLightning = computed(() => playGameWebSocketConnection.value !== undefined)
const isGameSocketReadyForLightning = computed(
  () => gameSocketStatus.value === 'connected' || gameSocketStatus.value === 'handoff',
)
const canSendLightningCommand = computed(
  () =>
    canSendGameSocketLightning.value &&
    isGameSocketReadyForLightning.value &&
    isStarTargeted.value &&
    !lightningSent.value &&
    !gameResultReceived.value,
)
const isNaturalDeathWaiting = computed(
  () =>
    playState.value !== null &&
    elapsedMs.value >= playState.value.durationMs &&
    !gameResultReceived.value,
)
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('keydown', handleLightningKeyDown)
  const gameRoomId = readRouteGameRoomId()

  if (gameRoomId === '') {
    returnToMatch()
    return
  }

  const payload = readGameStartPayload(gameRoomId)
  const waitingPayload = readGameWaitingPayload(gameRoomId)

  if (payload === null || waitingPayload === null) {
    returnToMatch()
    return
  }

  gameStartPayload.value = payload
  gameWaitingPayload.value = waitingPayload
  lightningSent.value = readLightningSent(gameRoomId)
  void nextTick(() => {
    initializeThreeScene()
    startFrameLoop()
  })
  connectPlayWebSocket(gameRoomId, waitingPayload.game.webSocketUrl)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('keydown', handleLightningKeyDown)
  closePlayWebSocket()
  playGameWebSocketConnection.value = undefined
  closePlayWebSocket = () => {}

  stopFrameLoop()
  disposeThreeScene()
})

onBeforeRouteLeave(() => {
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

  if (event.repeat || !isLightningKey(event.key) || !canSendLightningCommand.value) {
    return
  }

  event.preventDefault()

  try {
    playGameWebSocketConnection.value?.sendLightning()
    lightningSent.value = true
    gameSocketErrorMessage.value = ''
  } catch (error) {
    lightningSent.value = false
    gameSocketStatus.value = 'error'
    gameSocketErrorMessage.value =
      error instanceof Error ? error.message : t('gamePlay.socketErrorDetail')
  }
}

function isLightningKey(key = '') {
  const normalizedKey = key.trim().toLowerCase()

  return normalizedKey === 'd' || normalizedKey === 'f'
}

function shouldWarnBeforeLeaving() {
  return playState.value !== null
}

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match })
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

function handlePlayWebSocketError() {
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
    gameResultReceived.value = true
    gameSocketStatus.value = 'resultReceived'
    gameSocketErrorMessage.value = ''
  }
}

function readRouteGameRoomId() {
  const routeGameRoomIdParam = route.params.gameRoomId

  return Array.isArray(routeGameRoomIdParam)
    ? String(routeGameRoomIdParam[0] ?? '').trim()
    : String(routeGameRoomIdParam ?? '').trim()
}

function buildLightningSentStorageKey(gameRoomId = '') {
  return `${LIGHTNING_SENT_STORAGE_PREFIX}:${gameRoomId}`
}

function readLightningSent(gameRoomId = '') {
  try {
    return window.sessionStorage.getItem(buildLightningSentStorageKey(gameRoomId)) === 'true'
  } catch {
    return false
  }
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
</style>
