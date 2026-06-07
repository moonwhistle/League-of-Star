<template>
  <!-- eslint-disable vue/max-attributes-per-line -->
  <main
    class="game-play-page"
    :data-game-start-payload-ready="gameStartPayload !== null"
    :data-game-room-id="gameStartPayload?.gameRoomId ?? ''"
    :data-game-start-at="gameStartPayload?.startAt ?? ''"
    :data-game-elapsed-ms="elapsedMs"
    :data-game-current-hp="currentHp"
  >
    <header class="game-play-header" aria-label="Game play header">
      <h1>LEAGUE OF SMITE</h1>
      <button class="locale-toggle" type="button" @click="toggleLocale">
        {{ nextLocaleLabel }}
      </button>
    </header>

    <section
      v-if="gameStartPayload !== null"
      class="game-start-status"
      aria-label="Game start data"
    >
      <p>{{ t('gamePlay.status') }}</p>
      <h2>{{ t('gamePlay.title') }}</h2>
      <dl>
        <div>
          <dt>{{ t('gamePlay.gameRoom') }}</dt>
          <dd>{{ gameStartPayload.gameRoomId }}</dd>
        </div>
        <div>
          <dt>{{ t('gamePlay.startAt') }}</dt>
          <dd>{{ gameStartPayload.startAt }}</dd>
        </div>
        <div>
          <dt>{{ t('gamePlay.dragonMaxHp') }}</dt>
          <dd>{{ gameStartPayload.scenario.dragonMaxHp }}</dd>
        </div>
        <div>
          <dt>{{ t('gamePlay.durationMs') }}</dt>
          <dd>{{ gameStartPayload.scenario.durationMs }}</dd>
        </div>
      </dl>
    </section>

    <p v-else class="payload-error" role="alert">
      {{ t('gamePlay.payloadMissing') }}
    </p>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { getHpAtElapsedMs } from '@/game/hpScenario'
import { readGameStartPayload } from '@/services/gameStartPayload'

const route = useRoute()
const router = useRouter()
const { nextLocaleLabel, t, toggleLocale } = useLocale()
const gameStartPayload = shallowRef(readGameStartPayload(readRouteGameRoomId()))

const elapsedMs = computed(() =>
  gameStartPayload.value === null ? 0 : Math.max(0, Date.now() - gameStartPayload.value.startAt),
)
const currentHp = computed(() =>
  gameStartPayload.value === null
    ? 0
    : getHpAtElapsedMs(gameStartPayload.value.scenario, elapsedMs.value),
)

onMounted(() => {
  const gameRoomId = readRouteGameRoomId()

  if (gameRoomId === '') {
    returnToMatch()
    return
  }

  const payload = readGameStartPayload(gameRoomId)

  if (payload === null) {
    returnToMatch()
    return
  }

  gameStartPayload.value = payload
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
