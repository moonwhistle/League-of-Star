<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
  <main
    class="game-waiting-page"
    :style="{ '--game-waiting-background-image': `url(${backgroundImageUrl})` }"
    :data-game-waiting-payload-ready="gameWaitingPayload !== undefined"
    :data-game-room-id="gameWaitingPayload?.game.gameRoomId ?? ''"
    :data-loading-progress="loadingProgress"
  >
    <header class="game-waiting-header" aria-label="Game waiting header">
      <h1>LEAGUE OF SMITE</h1>
      <button class="locale-toggle" type="button" @click="toggleLocale">
        {{ nextLocaleLabel }}
      </button>
    </header>

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
import { computed, onMounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'

import backgroundImageUrl from '../../img/background.png'

const route = useRoute()
const router = useRouter()
const { nextLocaleLabel, t, toggleLocale } = useLocale()
const gameWaitingPayload = shallowRef()

const opponentName = computed(
  () => gameWaitingPayload.value?.opponent?.nickname ?? t('gameWaiting.unknownOpponent'),
)
const opponentTier = computed(() => gameWaitingPayload.value?.opponent?.tier ?? '-')
const opponentTierScore = computed(() =>
  String(gameWaitingPayload.value?.opponent?.tierScore ?? '-'),
)
const loadingSteps = computed(() => [
  {
    key: 'matchId',
    label: t('gameWaiting.matchData'),
    ready: String(gameWaitingPayload.value?.matchId ?? '').trim() !== '',
  },
  {
    key: 'opponent',
    label: t('gameWaiting.opponentInfo'),
    ready:
      gameWaitingPayload.value?.opponent !== null &&
      gameWaitingPayload.value?.opponent !== undefined,
  },
  {
    key: 'gameRoomId',
    label: t('gameWaiting.gameSetup'),
    ready: Number.isFinite(gameWaitingPayload.value?.game.gameRoomId),
  },
  {
    key: 'videoUrl',
    label: t('gameWaiting.video'),
    ready: String(gameWaitingPayload.value?.game.videoUrl ?? '').trim() !== '',
  },
  {
    key: 'webSocketUrl',
    label: t('gameWaiting.socket'),
    ready: String(gameWaitingPayload.value?.game.webSocketUrl ?? '').trim() !== '',
  },
])
const loadingProgress = computed(() => {
  const readyCount = loadingSteps.value.filter((step) => step.ready).length

  return readyCount * 20
})
const loadingStatusLabel = computed(() =>
  loadingProgress.value >= 100 ? t('gameWaiting.ready') : t('gameWaiting.pending'),
)

onMounted(() => {
  const routeGameRoomIdParam = route.params.gameRoomId
  const gameRoomId = Array.isArray(routeGameRoomIdParam)
    ? String(routeGameRoomIdParam[0] ?? '').trim()
    : String(routeGameRoomIdParam ?? '').trim()

  if (gameRoomId === '') {
    returnToMatch()
    return
  }

  const payload = readGameWaitingPayload(gameRoomId)

  if (payload === null) {
    returnToMatch()
    return
  }

  gameWaitingPayload.value = payload
})

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match })
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
  line-height: 1.05;
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

.loading-meter-row {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.loading-meter-row strong {
  font-size: clamp(1.5rem, 4vw, 2.4rem);
  line-height: 1;
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
    overflow-y: auto;
  }

  .game-waiting-stage {
    grid-template-columns: 1fr;
    padding: 20px 0 26px;
  }

  .versus-mark {
    font-size: 2.6rem;
  }

  .combatant-body {
    min-height: 0;
    padding: 22px 18px;
  }

  .status-copy,
  .loading-meter-row {
    align-items: start;
    flex-direction: column;
  }

  .loading-steps {
    grid-template-columns: 1fr;
  }
}
</style>
