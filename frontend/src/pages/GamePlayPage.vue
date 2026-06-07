<template>
  <!-- eslint-disable vue/max-attributes-per-line -->
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
    :data-game-video-url="playState?.videoUrl ?? ''"
    :data-game-websocket-url="playState?.webSocketUrl ?? ''"
  >
    <header class="game-play-header" aria-label="Game play header">
      <h1>LEAGUE OF SMITE</h1>
      <button class="locale-toggle" type="button" @click="toggleLocale">
        {{ nextLocaleLabel }}
      </button>
    </header>

    <section v-if="playState !== null" class="game-start-status" aria-label="Game start data">
      <p>{{ t('gamePlay.status') }}</p>
      <h2>{{ t('gamePlay.title') }}</h2>
      <dl>
        <div>
          <dt>{{ t('gamePlay.gameRoom') }}</dt>
          <dd>{{ playState.gameRoomId }}</dd>
        </div>
        <div>
          <dt>{{ t('gamePlay.startAt') }}</dt>
          <dd>{{ playState.startAt }}</dd>
        </div>
        <div>
          <dt>{{ t('gamePlay.dragonMaxHp') }}</dt>
          <dd>{{ playState.scenario.dragonMaxHp }}</dd>
        </div>
        <div>
          <dt>{{ t('gamePlay.durationMs') }}</dt>
          <dd>{{ playState.durationMs }}</dd>
        </div>
      </dl>
    </section>

    <p v-else class="payload-error" role="alert">
      {{ t('gamePlay.payloadMissing') }}
    </p>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { getHpAtElapsedMs } from '@/game/hpScenario'
import { readGameStartPayload } from '@/services/gameStartPayload'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'

const route = useRoute()
const router = useRouter()
const { nextLocaleLabel, t, toggleLocale } = useLocale()
const gameStartPayload = shallowRef(readGameStartPayload('__missing__'))
const gameWaitingPayload = shallowRef(readGameWaitingPayload('__missing__'))
const nowMs = shallowRef(Date.now())
let elapsedTimerId = 0

const playState = computed(() => {
  if (gameStartPayload.value === null || gameWaitingPayload.value === null) {
    return null
  }

  return {
    gameRoomId: gameStartPayload.value.gameRoomId,
    startAt: gameStartPayload.value.startAt,
    durationMs: gameStartPayload.value.scenario.durationMs,
    scenario: gameStartPayload.value.scenario,
    videoUrl: gameWaitingPayload.value.game.videoUrl,
    webSocketUrl: gameWaitingPayload.value.game.webSocketUrl,
  }
})

const elapsedMs = computed(() =>
  playState.value === null ? 0 : Math.max(0, nowMs.value - playState.value.startAt),
)
const currentHp = computed(() =>
  playState.value === null ? 0 : getHpAtElapsedMs(playState.value.scenario, elapsedMs.value),
)

onMounted(() => {
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
  nowMs.value = Date.now()
  elapsedTimerId = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 250)
})

onUnmounted(() => {
  if (elapsedTimerId !== 0) {
    window.clearInterval(elapsedTimerId)
    elapsedTimerId = 0
  }
})

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match })
}

function readRouteGameRoomId() {
  const routeGameRoomIdParam = route.params.gameRoomId

  return Array.isArray(routeGameRoomIdParam)
    ? String(routeGameRoomIdParam[0] ?? '').trim()
    : String(routeGameRoomIdParam ?? '').trim()
}
</script>

<style scoped>
.game-play-page {
  --game-play-panel: rgba(6, 12, 30, 0.84);
  --game-play-line: rgba(102, 240, 232, 0.22);
  --game-play-cyan: #62f4ed;
  --game-play-pink: #ffb6b2;
  --game-play-text: #f5f8ff;
  --game-play-muted: rgba(221, 230, 246, 0.68);

  display: grid;
  grid-template-rows: auto 1fr;
  width: 100%;
  min-height: 100dvh;
  overflow: hidden;
  font-family: var(--font-sans);
  color: var(--game-play-text);
  background:
    linear-gradient(180deg, rgba(5, 10, 24, 0.86), rgba(5, 10, 24, 0.95)),
    url('../../img/background.png') center / cover no-repeat;
}

.game-play-page,
.game-play-page * {
  box-sizing: border-box;
}

.game-play-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
  padding: 0 clamp(20px, 4vw, 42px);
  border-bottom: 1px solid rgba(206, 224, 255, 0.1);
}

.game-play-header h1 {
  margin: 0;
  overflow: hidden;
  font-size: 1.42rem;
  font-weight: 900;
  color: #f0d7ff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.locale-toggle {
  width: 42px;
  height: 32px;
  color: var(--game-play-muted);
  background: rgba(8, 15, 34, 0.7);
  border: 1px solid rgba(206, 224, 255, 0.18);
  border-radius: 4px;
  font-size: 0.76rem;
  font-weight: 900;
}

.game-start-status {
  align-self: center;
  width: min(760px, calc(100% - 40px));
  margin: 0 auto;
  padding: clamp(22px, 4vw, 34px);
  background: var(--game-play-panel);
  border: 1px solid var(--game-play-line);
}

.game-start-status p,
.game-start-status h2 {
  margin: 0;
}

.game-start-status p {
  font-size: 0.78rem;
  font-weight: 900;
  color: var(--game-play-cyan);
  text-transform: uppercase;
}

.game-start-status h2 {
  margin-top: 8px;
  font-size: clamp(1.4rem, 4vw, 2.3rem);
  font-weight: 900;
  line-height: 1.18;
  color: #f6f8ff;
}

.game-start-status dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 24px 0 0;
}

.game-start-status div {
  min-width: 0;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.04);
}

.game-start-status dt {
  margin-bottom: 4px;
  font-size: 0.68rem;
  font-weight: 900;
  color: var(--game-play-muted);
  text-transform: uppercase;
}

.game-start-status dd {
  margin: 0;
  overflow: hidden;
  font-size: 0.94rem;
  font-weight: 900;
  color: #f6f8ff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payload-error {
  align-self: center;
  width: min(620px, calc(100% - 40px));
  padding: 12px 16px;
  margin: 0 auto;
  color: #ffd9d6;
  background: rgba(36, 10, 18, 0.82);
  border: 1px solid rgba(255, 182, 178, 0.28);
}

@media (max-width: 640px) {
  .game-start-status dl {
    grid-template-columns: 1fr;
  }
}
</style>
