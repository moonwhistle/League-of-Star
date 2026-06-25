<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="game-result-page"
    :data-game-result-payload-ready="gameResultPayload !== null"
    :data-game-waiting-payload-ready="gameWaitingPayload !== null"
    :data-game-room-id="gameRoomId ?? ''"
    :data-game-result="gameSummaryPayload?.gameResult ?? gameResultPayload?.result ?? ''"
    :data-game-winner-user-id="
      gameSummaryPayload?.winnerUserId ?? gameResultPayload?.winnerUserId ?? ''
    "
    :data-game-result-reason="gameResultPayload?.reason ?? ''"
    :data-game-result-mode="gameResultPayload?.gameMode ?? ''"
    :data-game-result-custom="isCustomGameResult"
    :data-game-result-outcome="resultOutcome"
    :data-game-summary-status="summaryStatus"
    :data-game-summary-error-status="summaryErrorStatus"
  >
    <section class="game-result-summary" aria-live="polite">
      <p class="game-result-summary__eyebrow">
        {{ t('gameResult.eyebrow') }}
      </p>
      <h1 class="game-result-summary__title">
        {{ resultTitle }}
      </h1>

      <p v-if="summaryStatus === 'loading'" class="game-result-summary__status">
        {{ t('gameResult.summaryLoading') }}
      </p>

      <p v-else-if="summaryStatus === 'pending'" class="game-result-summary__status">
        {{ t('gameResult.summaryPending') }}
      </p>

      <p v-else-if="summaryStatus === 'error'" class="game-result-summary__error" role="alert">
        {{ summaryErrorMessage }}
      </p>

      <section
        v-if="summaryStatus === 'done' && gameSummaryPayload !== undefined"
        class="summary-panel"
        :aria-label="t('gameResult.summarySection')"
      >
        <div class="summary-players">
          <article class="summary-player summary-player--me" data-result-player="me">
            <p class="summary-player__label">{{ t('gameResult.me') }}</p>
            <h2>{{ gameSummaryPayload.me.nickname }}</h2>
            <dl>
              <div>
                <dt>{{ t('gameResult.playerResult') }}</dt>
                <dd>{{ formatPlayerResult(gameSummaryPayload.me.result) }}</dd>
              </div>
              <div v-if="!isCustomGameResult">
                <dt>{{ t('gameResult.rank') }}</dt>
                <dd>
                  {{
                    formatRankChange(
                      gameSummaryPayload.me.rankBefore,
                      gameSummaryPayload.me.rankAfter,
                    )
                  }}
                </dd>
              </div>
              <div v-if="!isCustomGameResult">
                <dt>{{ t('gameResult.lp') }}</dt>
                <dd>
                  {{ gameSummaryPayload.me.lpBefore }} -> {{ gameSummaryPayload.me.lpAfter }} ({{
                    formatLpChange(gameSummaryPayload.me.lpChange)
                  }})
                </dd>
              </div>
            </dl>
          </article>

          <article class="summary-player summary-player--opponent" data-result-player="opponent">
            <p class="summary-player__label">{{ t('gameResult.opponent') }}</p>
            <h2>{{ gameSummaryPayload.opponent.nickname }}</h2>
            <dl>
              <div>
                <dt>{{ t('gameResult.playerResult') }}</dt>
                <dd>{{ formatPlayerResult(gameSummaryPayload.opponent.result) }}</dd>
              </div>
              <div v-if="!isCustomGameResult">
                <dt>{{ t('gameResult.rank') }}</dt>
                <dd>
                  {{
                    formatRankChange(
                      gameSummaryPayload.opponent.rankBefore,
                      gameSummaryPayload.opponent.rankAfter,
                    )
                  }}
                </dd>
              </div>
              <div v-if="!isCustomGameResult">
                <dt>{{ t('gameResult.lp') }}</dt>
                <dd>
                  {{ gameSummaryPayload.opponent.lpBefore }} ->
                  {{ gameSummaryPayload.opponent.lpAfter }}
                  ({{ formatLpChange(gameSummaryPayload.opponent.lpChange) }})
                </dd>
              </div>
            </dl>
          </article>
        </div>
      </section>

      <button class="match-return-button" type="button" @click="returnToMatch">
        {{ t('gameResult.returnToMatch') }}
      </button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { readCustomGameStartPayload } from '@/services/customGameStartPayload'
import { readGameResultPayload } from '@/services/gameResultPayload'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'
import { getGameSummary } from '@/services/gameSummaryService'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const gameRoomId = shallowRef()
const gameResultPayload = shallowRef(readGameResultPayload('__missing__'))
const gameWaitingPayload = shallowRef(readGameWaitingPayload('__missing__'))
const customGameStartPayload = shallowRef(readCustomGameStartPayload('__missing__'))
const gameSummaryPayload = shallowRef()
const summaryStatus = shallowRef('idle')
const summaryErrorMessage = shallowRef('')
const summaryErrorStatus = shallowRef('')
let summaryPollingTimerId = 0
const summaryAbortController = shallowRef()

const resultOutcome = computed(() => {
  if (gameSummaryPayload.value !== undefined) {
    return resolveSummaryOutcome(gameSummaryPayload.value)
  }

  if (gameResultPayload.value === null) {
    return ''
  }

  if (gameResultPayload.value.winnerUserId === null || gameResultPayload.value.result === 'DRAW') {
    return 'draw'
  }

  const customMyUserId = customGameStartPayload.value?.myUserId

  if (gameResultPayload.value.gameMode === 'CUSTOM' && Number.isFinite(customMyUserId)) {
    return gameResultPayload.value.winnerUserId === customMyUserId ? 'win' : 'lose'
  }

  const opponentUserId = gameWaitingPayload.value?.opponent?.userId

  if (!Number.isFinite(opponentUserId)) {
    return ''
  }

  return gameResultPayload.value.winnerUserId === opponentUserId ? 'lose' : 'win'
})
const isCustomGameResult = computed(() => gameResultPayload.value?.gameMode === 'CUSTOM')

const resultTitle = computed(() => {
  switch (resultOutcome.value) {
    case 'draw':
      return 'DRAW'
    case 'lose':
      return 'YOU LOSE'
    case 'win':
      return 'YOU WIN'
    default:
      return t('gameResult.summaryPendingTitle')
  }
})

onMounted(() => {
  const routeGameRoomId = readRouteGameRoomId()

  if (routeGameRoomId === null) {
    returnToMatch()
    return
  }

  gameRoomId.value = routeGameRoomId
  gameResultPayload.value = readGameResultPayload(routeGameRoomId)
  gameWaitingPayload.value = readGameWaitingPayload(routeGameRoomId)
  customGameStartPayload.value = readCustomGameStartPayload(routeGameRoomId)
  void fetchGameSummary(routeGameRoomId)
})

onUnmounted(() => {
  clearSummaryPollingTimer()
  abortSummaryRequest()
})

async function fetchGameSummary(nextGameRoomId = gameRoomId.value) {
  const normalizedGameRoomId = Number(nextGameRoomId)

  if (!Number.isFinite(normalizedGameRoomId)) {
    return
  }

  clearSummaryPollingTimer()
  abortSummaryRequest()
  const controller = new AbortController()
  summaryAbortController.value = controller

  if (summaryStatus.value !== 'pending') {
    summaryStatus.value = 'loading'
  }

  summaryErrorMessage.value = ''
  summaryErrorStatus.value = ''

  try {
    const response = await getGameSummary(normalizedGameRoomId, controller.signal)

    if (summaryAbortController.value !== controller) {
      return
    }

    if (response.summaryStatus === 'PENDING') {
      summaryStatus.value = 'pending'
      scheduleSummaryPolling(response.retryAfterMillis)
      return
    }

    gameSummaryPayload.value = response
    summaryStatus.value = 'done'
  } catch (error) {
    if (
      (error instanceof DOMException && error.name === 'AbortError') ||
      summaryAbortController.value !== controller
    ) {
      return
    }

    summaryStatus.value = 'error'
    summaryErrorStatus.value = error instanceof ApiClientError ? String(error.status) : ''
    summaryErrorMessage.value =
      error instanceof ApiClientError ? error.message : t('gameResult.summaryErrorFallback')
  } finally {
    if (summaryAbortController.value === controller) {
      summaryAbortController.value = undefined
    }
  }
}

function scheduleSummaryPolling(retryAfterMillis = 1000) {
  const delayMs =
    Number.isFinite(retryAfterMillis) && retryAfterMillis > 0 ? retryAfterMillis : 1000

  summaryPollingTimerId = window.setTimeout(() => {
    void fetchGameSummary()
  }, delayMs)
}

function clearSummaryPollingTimer() {
  if (summaryPollingTimerId === 0) {
    return
  }

  window.clearTimeout(summaryPollingTimerId)
  summaryPollingTimerId = 0
}

function abortSummaryRequest() {
  const controller = summaryAbortController.value

  if (controller === undefined) {
    return
  }

  controller.abort()
  summaryAbortController.value = undefined
}

function resolveSummaryOutcome(summaryValue = {}) {
  const summary = Object(summaryValue)
  const me = Object(Reflect.get(summary, 'me'))
  const gameResult = Reflect.get(summary, 'gameResult')
  const winnerUserId = Reflect.get(summary, 'winnerUserId')
  const meResult = Reflect.get(me, 'result')

  if (gameResult === 'DRAW' || winnerUserId === null || meResult === 'DRAW') {
    return 'draw'
  }

  return meResult === 'WIN' ? 'win' : 'lose'
}

function readRouteGameRoomId() {
  const routeGameRoomId = route.params.gameRoomId
  const normalizedGameRoomId = Array.isArray(routeGameRoomId)
    ? (routeGameRoomId[0]?.trim() ?? '')
    : String(routeGameRoomId).trim()
  const numericGameRoomId = Number(normalizedGameRoomId)

  return Number.isFinite(numericGameRoomId) && normalizedGameRoomId !== ''
    ? numericGameRoomId
    : null
}

function formatPlayerResult(resultValue = '') {
  const result = String(resultValue)

  switch (result) {
    case 'DRAW':
      return t('gameResult.draw')
    case 'LOSS':
      return t('gameResult.loss')
    case 'WIN':
      return t('gameResult.win')
    default:
      return String(result)
  }
}

function formatLpChange(lpChange = 0) {
  return lpChange > 0 ? `+${lpChange}` : String(lpChange)
}

function formatRankChange(rankBefore = '', rankAfter = '') {
  return rankBefore === rankAfter ? rankAfter : `${rankBefore} -> ${rankAfter}`
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
  width: min(880px, 100%);
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

.game-result-summary__status,
.game-result-summary__error,
.summary-panel__finished-at,
.summary-panel__custom-note {
  margin: 0;
  color: rgba(223, 239, 255, 0.82);
  font-size: 15px;
  font-weight: 800;
}

.summary-panel__custom-note {
  color: #8eeeff;
}

.game-result-summary__error {
  color: #ffc0c0;
}

.summary-panel {
  display: grid;
  gap: 18px;
}

.summary-players {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.summary-player {
  min-width: 0;
  padding: 18px;
  border: 1px solid rgba(178, 230, 255, 0.22);
  background: rgba(3, 8, 20, 0.62);
  text-align: left;
}

.summary-player--me {
  border-color: rgba(100, 208, 255, 0.48);
}

.summary-player__label {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 900;
  color: #8eeeff;
  text-transform: uppercase;
}

.summary-player h2 {
  margin: 0 0 14px;
  overflow-wrap: anywhere;
  font-size: 24px;
  line-height: 1.1;
}

.summary-player dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

.summary-player dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.summary-player dt {
  flex: 0 0 auto;
  color: rgba(192, 223, 244, 0.72);
  font-size: 12px;
  font-weight: 800;
}

.summary-player dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  font-weight: 900;
  text-align: right;
}

.match-return-button {
  justify-self: center;
  min-width: 144px;
  padding: 12px 18px;
  border: 1px solid rgba(142, 238, 255, 0.5);
  color: #f8fbff;
  background: rgba(26, 110, 150, 0.36);
  font: inherit;
  font-size: 14px;
  font-weight: 900;
  cursor: pointer;
}

.match-return-button:hover {
  background: rgba(36, 147, 198, 0.5);
}

@media (max-width: 700px) {
  .game-result-page {
    padding: 20px;
  }

  .summary-players {
    grid-template-columns: 1fr;
  }

  .summary-player dl div {
    display: grid;
    gap: 4px;
  }

  .summary-player dd {
    text-align: left;
  }
}
</style>
