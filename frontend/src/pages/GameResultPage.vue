<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="game-result-page"
    :data-game-result-payload-ready="gameResultPayload !== null"
    :data-game-waiting-payload-ready="gameWaitingPayload !== null"
    :data-game-room-id="gameResultPayload?.gameRoomId ?? ''"
    :data-game-result="gameResultPayload?.result ?? ''"
    :data-game-winner-user-id="gameResultPayload?.winnerUserId ?? ''"
    :data-game-result-reason="gameResultPayload?.reason ?? ''"
    :data-game-result-outcome="resultOutcome"
  >
    <section v-if="gameResultPayload !== null" class="game-result-summary" aria-live="polite">
      <p class="game-result-summary__eyebrow">
        {{ t('gameResult.eyebrow') }}
      </p>
      <h1 class="game-result-summary__title">
        {{ resultTitle }}
      </h1>
      <dl class="game-result-summary__meta">
        <div>
          <dt>{{ t('gameResult.reason') }}</dt>
          <dd>{{ gameResultPayload.reason }}</dd>
        </div>
        <div>
          <dt>{{ t('gameResult.gameRoom') }}</dt>
          <dd>{{ gameResultPayload.gameRoomId }}</dd>
        </div>
      </dl>
    </section>

    <p v-else class="payload-error" role="alert">
      {{ t('gameResult.payloadMissing') }}
    </p>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { readGameResultPayload } from '@/services/gameResultPayload'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const gameResultPayload = shallowRef(readGameResultPayload('__missing__'))
const gameWaitingPayload = shallowRef(readGameWaitingPayload('__missing__'))

const resultOutcome = computed(() => {
  if (gameResultPayload.value === null) {
    return ''
  }

  if (gameResultPayload.value.winnerUserId === null || gameResultPayload.value.result === 'DRAW') {
    return 'draw'
  }

  const opponentUserId = gameWaitingPayload.value?.opponent?.userId

  if (!Number.isFinite(opponentUserId)) {
    return ''
  }

  return gameResultPayload.value.winnerUserId === opponentUserId ? 'lose' : 'win'
})

const resultTitle = computed(() => {
  switch (resultOutcome.value) {
    case 'draw':
      return 'DRAW'
    case 'lose':
      return 'YOU LOSE'
    case 'win':
      return 'YOU WIN'
    default:
      return ''
  }
})

onMounted(() => {
  const gameRoomId = readRouteGameRoomId()

  if (gameRoomId === '') {
    returnToMatch()
    return
  }

  const resultPayload = readGameResultPayload(gameRoomId)
  const waitingPayload = readGameWaitingPayload(gameRoomId)

  if (resultPayload === null || !canResolveResultOutcome(resultPayload, waitingPayload)) {
    returnToMatch()
    return
  }

  gameResultPayload.value = resultPayload
  gameWaitingPayload.value = waitingPayload
})

function canResolveResultOutcome(
  resultPayload = gameResultPayload.value,
  waitingPayload = gameWaitingPayload.value,
) {
  if (resultPayload === null) {
    return false
  }

  if (resultPayload.winnerUserId === null || resultPayload.result === 'DRAW') {
    return true
  }

  return Number.isFinite(waitingPayload?.opponent?.userId)
}

function readRouteGameRoomId() {
  const routeGameRoomId = route.params.gameRoomId

  return Array.isArray(routeGameRoomId)
    ? (routeGameRoomId[0]?.trim() ?? '')
    : String(routeGameRoomId)
}

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match })
}
</script>

<style scoped>
.game-result-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px;
  color: #f8fbff;
  background:
    radial-gradient(circle at 52% 42%, rgba(89, 223, 255, 0.24), transparent 30%),
    radial-gradient(circle at 48% 62%, rgba(255, 216, 112, 0.16), transparent 34%), #030610;
}

.game-result-summary {
  display: grid;
  gap: 18px;
  width: min(520px, 100%);
  text-align: center;
}

.game-result-summary__eyebrow {
  margin: 0;
  font-size: 13px;
  font-weight: 800;
  color: #8eeeff;
  text-transform: uppercase;
}

.game-result-summary__title {
  margin: 0;
  font-size: clamp(48px, 10vw, 96px);
  font-weight: 950;
  line-height: 0.92;
  color: #fff;
  text-shadow:
    0 0 18px rgba(88, 223, 255, 0.55),
    0 0 42px rgba(255, 212, 101, 0.28);
}

.game-result-summary__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.game-result-summary__meta div {
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(178, 230, 255, 0.22);
  background: rgba(3, 8, 20, 0.62);
}

.game-result-summary__meta dt {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  color: rgba(192, 223, 244, 0.72);
  text-transform: uppercase;
}

.game-result-summary__meta dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 800;
}

.payload-error {
  max-width: 420px;
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  text-align: center;
}

@media (max-width: 480px) {
  .game-result-page {
    padding: 20px;
  }

  .game-result-summary__meta {
    grid-template-columns: 1fr;
  }
}
</style>
