<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="match-page"
    :style="{ '--match-background-image': `url(${backgroundImageUrl})` }"
    :data-stream-status="streamStatus"
    :data-connected-user-id="connectedEvent?.userId ?? ''"
    :data-last-heartbeat-at="lastHeartbeatAt"
    :data-match-found-id="matchFound?.matchId ?? ''"
    :data-match-found-modal-open="isMatchFoundModalOpen"
    :data-match-found-countdown-seconds="matchFoundCountdownSeconds"
    :data-match-found-loading="isMatchFoundLoading"
    :data-match-result-action="matchResponseResult?.action ?? ''"
    :data-stream-error-message="streamErrorMessage"
    :data-can-start-match="canStartMatch"
    :data-queue-status="queueStatus"
    :data-queue-error-message="queueErrorMessage"
  >
    <header class="match-app-bar" aria-label="Match navigation">
      <h1>LEAGUE OF SMITE</h1>
      <button class="locale-toggle match-locale-toggle" type="button" @click="toggleLocale">
        {{ nextLocaleLabel }}
      </button>
      <nav class="match-actions" aria-label="Account actions">
        <button class="icon-button" type="button" :aria-label="t('match.records')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 5h14" />
            <path d="M5 12h14" />
            <path d="M5 19h14" />
            <path d="M8 5v14" />
            <path d="M16 5v14" />
          </svg>
        </button>
        <button class="icon-button" type="button" :aria-label="t('match.logout')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M10 6H6v12h4" />
            <path d="M14 8l4 4-4 4" />
            <path d="M8 12h10" />
          </svg>
        </button>
      </nav>
    </header>

    <section class="match-layout" aria-label="Match lobby">
      <aside class="ranking-panel" aria-label="Ranking overview">
        <section class="profile-panel" aria-label="Player profile">
          <div class="avatar-frame" aria-hidden="true">S</div>
          <div class="profile-copy">
            <strong>Summoner</strong>
          </div>
        </section>

        <section class="ranking-summary" aria-label="Ranking summary">
          <h2>{{ t('match.ranking') }}</h2>
          <div class="summary-grid">
            <div>
              <span>{{ t('match.myRank') }}</span>
              <strong>#128</strong>
            </div>
            <div>
              <span>{{ t('match.top') }}</span>
              <strong>7%</strong>
            </div>
            <div>
              <span>{{ t('match.seasonBest') }}</span>
              <strong>#94</strong>
            </div>
          </div>
        </section>

        <ol class="ranking-list" aria-label="Top ranking">
          <li>
            <span>1</span>
            <strong>Legendary Dragon</strong>
            <em>3,492 LP</em>
          </li>
          <li>
            <span>2</span>
            <strong>ShadowWalker</strong>
            <em>3,218 LP</em>
          </li>
          <li>
            <span>3</span>
            <strong>K-God Z</strong>
            <em>3,105 LP</em>
          </li>
          <li class="is-current">
            <span>128</span>
            <strong>Summoner</strong>
            <em>1,248 LP</em>
          </li>
          <li>
            <span>129</span>
            <strong>SoloQueueKing</strong>
            <em>1,240 LP</em>
          </li>
        </ol>
      </aside>

      <section class="match-cta-panel" aria-label="Match controls">
        <section class="rank-panel" aria-label="Current rank">
          <div>
            <span>{{ t('match.currentRank') }}</span>
            <strong>BRONZE IV</strong>
          </div>
          <div class="rank-progress">
            <strong>1,248 LP</strong>
            <span>{{ t('match.toNextRank') }}</span>
          </div>
          <div class="progress-track" aria-hidden="true">
            <span />
          </div>
        </section>

        <button
          class="primary-match-button"
          :class="{ 'is-waiting': queueStatus === 'queued' }"
          data-testid="match-start-button"
          type="button"
          :disabled="!canUsePrimaryMatchAction"
          @click="handlePrimaryMatchAction"
        >
          <span v-if="queueStatus !== 'queued'" aria-hidden="true">▶</span>
          {{ primaryMatchActionLabel }}
        </button>

        <div class="secondary-actions">
          <button type="button">{{ t('match.practice') }}</button>
          <button type="button">{{ t('match.custom') }}</button>
        </div>
      </section>
    </section>

    <div
      v-if="errorModalMessage !== ''"
      class="match-error-backdrop"
      role="presentation"
      @click="closeErrorModal"
    >
      <section
        class="match-error-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="match-error-title"
        @click.stop
      >
        <h2 id="match-error-title">{{ t('match.errorTitle') }}</h2>
        <p>{{ errorModalMessage }}</p>
        <button type="button" @click="closeErrorModal">{{ t('match.errorConfirm') }}</button>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'

import { useLocale } from '@/composables/useLocale'
import { joinMatchQueue, leaveMatchQueue } from '@/services/matchService'
import { connectMatchEventSource } from '@/services/realtime/matchEventSource'

import backgroundImageUrl from '../../img/background.png'

const streamStatus = ref('idle')
const { nextLocaleLabel, t, toggleLocale } = useLocale()
const lastHeartbeatAt = ref('')
const connectedEvent = shallowRef()
const matchFound = shallowRef()
const matchResponseResult = shallowRef()
const streamErrorMessage = ref('')
const queueStatus = ref('ready')
const queueErrorMessage = ref('')
const matchWaitingSeconds = ref(0)
const isMatchFoundModalOpen = ref(false)
const matchFoundCountdownSeconds = ref(0)
const errorModalMessage = ref('')
const isMatchFoundLoading = computed(
  () => isMatchFoundModalOpen.value && matchFoundCountdownSeconds.value <= 0,
)
const hasActiveMatchFoundResponse = computed(
  () =>
    isMatchFoundModalOpen.value ||
    (matchFound.value !== undefined && matchResponseResult.value === undefined),
)
const canStartMatch = computed(
  () => queueStatus.value === 'ready' && streamStatus.value !== 'connecting',
)
const canUsePrimaryMatchAction = computed(() => {
  if (hasActiveMatchFoundResponse.value || streamStatus.value === 'error') {
    return false
  }

  if (queueStatus.value === 'ready') {
    return streamStatus.value !== 'connecting'
  }

  return queueStatus.value === 'queued'
})
const primaryMatchActionLabel = computed(() => {
  if (streamStatus.value === 'connecting' && queueStatus.value === 'ready') {
    return t('match.connecting')
  }

  if (queueStatus.value === 'joining') {
    return t('match.joining')
  }

  if (queueStatus.value === 'queued') {
    return String(matchWaitingSeconds.value)
  }

  if (queueStatus.value === 'leaving') {
    return t('match.leaving')
  }

  return t('match.start')
})

const MATCH_STREAM_ERROR_MESSAGE = 'Match event stream is currently unavailable.'
const MATCH_QUEUE_ERROR_MESSAGE = 'Match queue request failed. Please try again.'

let closeMatchEventSource = () => {}
// Guards against late stream callbacks that arrive after route unmount.
let isActive = false
let matchWaitingTimerId = 0
let matchFoundCountdownTimerId = 0
let matchFoundCountdownDeadline = 0
let shouldJoinAfterStreamConnected = false
let hasQueueJoinRequestStarted = false
let joinAbortController = new AbortController()
let leaveAbortController = new AbortController()

onMounted(() => {
  isActive = true
  streamErrorMessage.value = ''
})

onUnmounted(() => {
  isActive = false
  shouldJoinAfterStreamConnected = false
  abortJoinRequest()
  abortLeaveRequest()
  stopMatchWaitingTimer()
  stopMatchFoundCountdown()
  closeMatchStream()
})

function handlePrimaryMatchAction() {
  if (hasActiveMatchFoundResponse.value || streamStatus.value === 'error') {
    return
  }

  if (queueStatus.value === 'ready') {
    startMatchmaking()
    return
  }

  if (queueStatus.value === 'queued') {
    void cancelMatchmaking()
  }
}

function startMatchmaking() {
  if (queueStatus.value !== 'ready') {
    return
  }

  queueErrorMessage.value = ''
  streamErrorMessage.value = ''
  errorModalMessage.value = ''
  shouldJoinAfterStreamConnected = true
  hasQueueJoinRequestStarted = false
  streamStatus.value = 'connecting'
  resetStreamPayloads()
  closeMatchStream()
  startMatchWaiting()

  try {
    const connection = connectMatchEventSource({
      onOpen: () => {
        if (isActive) {
          streamErrorMessage.value = ''
        }
      },
      onConnected: (payload) => {
        if (!isActive) {
          return
        }

        connectedEvent.value = payload
        streamStatus.value = 'connected'

        if (shouldJoinAfterStreamConnected && queueStatus.value === 'queued') {
          shouldJoinAfterStreamConnected = false
          void joinQueueAfterStreamConnected()
        }
      },
      onHeartbeat: (payload) => {
        if (isActive) {
          lastHeartbeatAt.value = payload.sentAt
        }
      },
      onMatchFound: (payload) => {
        if (isActive) {
          matchFound.value = payload
          openMatchFoundModal()
        }
      },
      onMatchResponseResult: (payload) => {
        if (isActive) {
          matchResponseResult.value = payload
        }
      },
      onError: () => {
        if (isActive) {
          handleStreamError()
        }
      },
    })
    closeMatchEventSource = connection.close
  } catch {
    handleStreamError()
  }
}

async function joinQueueAfterStreamConnected() {
  queueErrorMessage.value = ''
  abortJoinRequest()
  joinAbortController = new AbortController()
  const activeJoinAbortController = joinAbortController
  hasQueueJoinRequestStarted = true

  try {
    await joinMatchQueue(activeJoinAbortController.signal)

    if (!isActive || activeJoinAbortController.signal.aborted) {
      return
    }

    queueErrorMessage.value = ''
  } catch (error) {
    if (!isActive || (error instanceof Error && error.name === 'AbortError')) {
      return
    }

    queueErrorMessage.value =
      error instanceof Error && error.message.trim() !== ''
        ? error.message
        : MATCH_QUEUE_ERROR_MESSAGE
    closeMatchStream()
    failMatchmaking(queueErrorMessage.value, queueErrorMessage.value)
  }
}

function startMatchWaiting() {
  queueStatus.value = 'queued'
  queueErrorMessage.value = ''
  matchWaitingSeconds.value = 1
  stopMatchWaitingTimer()
  matchWaitingTimerId = window.setInterval(() => {
    matchWaitingSeconds.value += 1
  }, 1000)
}

async function cancelMatchmaking() {
  if (!hasQueueJoinRequestStarted) {
    resetMatchmakingState()
    return
  }

  queueStatus.value = 'leaving'
  queueErrorMessage.value = ''
  abortJoinRequest()
  abortLeaveRequest()
  leaveAbortController = new AbortController()
  const activeLeaveAbortController = leaveAbortController

  try {
    await leaveMatchQueue(activeLeaveAbortController.signal)

    if (!isActive || activeLeaveAbortController.signal.aborted) {
      return
    }

    resetMatchmakingState()
  } catch (error) {
    if (!isActive || (error instanceof Error && error.name === 'AbortError')) {
      return
    }

    queueStatus.value = 'queued'
    queueErrorMessage.value =
      error instanceof Error && error.message.trim() !== ''
        ? error.message
        : MATCH_QUEUE_ERROR_MESSAGE
  }
}

function stopMatchWaitingTimer() {
  if (matchWaitingTimerId === 0) {
    return
  }

  window.clearInterval(matchWaitingTimerId)
  matchWaitingTimerId = 0
}

function openMatchFoundModal() {
  isMatchFoundModalOpen.value = true
  stopMatchWaitingTimer()
  startMatchFoundCountdown()
}

function startMatchFoundCountdown() {
  stopMatchFoundCountdown()
  matchFoundCountdownDeadline = resolveMatchFoundDeadline()
  updateMatchFoundCountdown()

  if (matchFoundCountdownSeconds.value <= 0) {
    return
  }

  matchFoundCountdownTimerId = window.setInterval(() => {
    updateMatchFoundCountdown()

    if (matchFoundCountdownSeconds.value <= 0) {
      stopMatchFoundCountdown()
    }
  }, 1000)
}

function updateMatchFoundCountdown() {
  matchFoundCountdownSeconds.value = calculateMatchFoundRemainingSeconds()
}

function resolveMatchFoundDeadline() {
  const fallbackSeconds = Math.max(0, Number(matchFound.value?.acceptTimeoutSeconds ?? 0))
  const eventCreatedAt = Date.parse(String(matchFound.value?.eventCreatedAt ?? ''))

  if (Number.isNaN(eventCreatedAt)) {
    return Date.now() + fallbackSeconds * 1000
  }

  return eventCreatedAt + fallbackSeconds * 1000
}

function calculateMatchFoundRemainingSeconds() {
  const remainingMilliseconds = matchFoundCountdownDeadline - Date.now()

  return Math.max(0, Math.ceil(remainingMilliseconds / 1000))
}

function stopMatchFoundCountdown() {
  if (matchFoundCountdownTimerId === 0) {
    return
  }

  window.clearInterval(matchFoundCountdownTimerId)
  matchFoundCountdownTimerId = 0
}

function resetMatchFoundModalState() {
  stopMatchFoundCountdown()
  isMatchFoundModalOpen.value = false
  matchFoundCountdownSeconds.value = 0
  matchFoundCountdownDeadline = 0
}

function setStreamError() {
  streamStatus.value = 'error'
  streamErrorMessage.value = MATCH_STREAM_ERROR_MESSAGE
}

function handleStreamError() {
  shouldJoinAfterStreamConnected = false
  setStreamError()
  closeMatchStream()

  if (hasActiveMatchFoundResponse.value) {
    resetMatchFoundModalState()
    showErrorModal(t('match.streamFailed'))
    return
  }

  if (
    (queueStatus.value === 'joining' || queueStatus.value === 'queued') &&
    hasQueueJoinRequestStarted
  ) {
    void leaveQueueAfterStreamError()
    return
  }

  failMatchmaking(t('match.streamFailed'), '', MATCH_STREAM_ERROR_MESSAGE)
}

async function leaveQueueAfterStreamError() {
  queueStatus.value = 'leaving'
  queueErrorMessage.value = ''
  abortJoinRequest()
  abortLeaveRequest()
  stopMatchWaitingTimer()
  leaveAbortController = new AbortController()
  const activeLeaveAbortController = leaveAbortController

  try {
    await leaveMatchQueue(activeLeaveAbortController.signal)

    if (!isActive || activeLeaveAbortController.signal.aborted) {
      return
    }

    resetMatchmakingState()
    showErrorModal(t('match.streamFailed'))
  } catch (error) {
    if (!isActive || (error instanceof Error && error.name === 'AbortError')) {
      return
    }

    queueErrorMessage.value =
      error instanceof Error && error.message.trim() !== ''
        ? error.message
        : MATCH_QUEUE_ERROR_MESSAGE
    streamErrorMessage.value = MATCH_STREAM_ERROR_MESSAGE
    failMatchmaking(queueErrorMessage.value, queueErrorMessage.value, MATCH_STREAM_ERROR_MESSAGE)
  }
}

function closeMatchStream() {
  closeMatchEventSource()
  closeMatchEventSource = () => {}
}

function resetMatchmakingState() {
  queueStatus.value = 'ready'
  queueErrorMessage.value = ''
  matchWaitingSeconds.value = 0
  stopMatchWaitingTimer()
  closeMatchStream()
  streamStatus.value = 'idle'
  streamErrorMessage.value = ''
  shouldJoinAfterStreamConnected = false
  hasQueueJoinRequestStarted = false
  resetStreamPayloads()
}

function resetStreamPayloads() {
  connectedEvent.value = undefined
  lastHeartbeatAt.value = ''
  matchFound.value = undefined
  matchResponseResult.value = undefined
  resetMatchFoundModalState()
}

function abortJoinRequest() {
  joinAbortController.abort()
}

function abortLeaveRequest() {
  leaveAbortController.abort()
}

function failMatchmaking(message = '', nextQueueErrorMessage = '', nextStreamErrorMessage = '') {
  resetMatchmakingState()
  queueErrorMessage.value = nextQueueErrorMessage
  streamErrorMessage.value = nextStreamErrorMessage
  showErrorModal(message)
}

function showErrorModal(message = '') {
  errorModalMessage.value = message
}

function closeErrorModal() {
  errorModalMessage.value = ''
}
</script>

<style scoped>
.match-page {
  --match-panel: rgba(7, 13, 31, 0.84);
  --match-panel-strong: rgba(6, 10, 24, 0.94);
  --match-line: rgba(102, 240, 232, 0.22);
  --match-accent: #63f2e8;
  --match-accent-strong: #a56bff;
  --match-text: #f8fbff;
  --match-muted: rgba(219, 232, 244, 0.72);

  position: relative;
  width: 100%;
  height: 100dvh;
  min-height: 620px;
  overflow: hidden;
  font-family: var(--font-sans);
  color: var(--match-text);
  background:
    linear-gradient(90deg, rgba(4, 8, 22, 0.82), rgba(4, 8, 22, 0.22) 58%),
    linear-gradient(0deg, rgba(4, 8, 22, 0.82), rgba(4, 8, 22, 0.1) 48%),
    var(--match-background-image) center / cover no-repeat;
}

.match-page,
.match-page * {
  box-sizing: border-box;
}

.match-page::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background: rgba(2, 6, 16, 0.16);
}

.match-app-bar,
.match-layout {
  position: relative;
  z-index: 1;
}

.match-app-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 64px;
  padding: 0 clamp(20px, 4vw, 40px);
  border-bottom: 1px solid rgba(206, 224, 255, 0.12);
  background: rgba(5, 10, 24, 0.56);
}

.match-app-bar h1 {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 1.55rem;
  font-weight: 900;
  letter-spacing: 0;
  color: #f0d7ff;
  text-shadow: 0 0 16px rgba(188, 107, 255, 0.72);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.match-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 12px;
  min-width: 0;
}

.locale-toggle {
  min-width: 42px;
  height: 32px;
  color: var(--match-muted);
  background: rgba(8, 15, 34, 0.7);
  border: 1px solid rgba(206, 224, 255, 0.18);
  border-radius: 4px;
  font-size: 0.76rem;
  font-weight: 900;
}

.match-locale-toggle {
  flex: 0 0 auto;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: var(--match-muted);
  background: rgba(8, 15, 34, 0.7);
  border: 1px solid rgba(206, 224, 255, 0.18);
  border-radius: 6px;
}

.icon-button svg {
  width: 20px;
  height: 20px;
  stroke: currentcolor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}

.match-layout {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(320px, 1fr);
  gap: clamp(24px, 5vw, 72px);
  min-width: 0;
  height: calc(100dvh - 64px);
  min-height: 556px;
  padding: 16px clamp(20px, 4vw, 40px) 32px;
}

.ranking-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(206, 224, 255, 0.12);
  background: var(--match-panel-strong);
  box-shadow: 20px 0 42px rgba(0, 0, 0, 0.22);
}

.profile-panel {
  position: relative;
  display: flex;
  gap: 14px;
  align-items: center;
  min-height: 86px;
  padding: 16px 18px;
  margin: 0 0 14px;
  border-left: 3px solid var(--match-accent-strong);
  border-bottom: 1px solid rgba(206, 224, 255, 0.1);
  background: linear-gradient(90deg, rgba(118, 74, 202, 0.28), transparent);
}

.profile-panel::after {
  position: absolute;
  right: 18px;
  bottom: -8px;
  left: 18px;
  height: 1px;
  content: '';
  background: rgba(99, 242, 232, 0.16);
}

.avatar-frame {
  display: grid;
  flex: 0 0 auto;
  width: 52px;
  height: 52px;
  place-items: center;
  font-weight: 900;
  color: #06101c;
  background: var(--match-accent);
  border: 2px solid rgba(210, 255, 251, 0.72);
  border-radius: 50%;
}

.profile-copy,
.rank-panel div {
  display: flex;
  flex-direction: column;
}

.profile-copy strong {
  font-size: 1rem;
  line-height: 1.2;
}

.ranking-summary span,
.rank-panel span {
  font-size: 0.78rem;
  color: var(--match-muted);
}

.ranking-summary {
  margin-top: 4px;
  border-top: 1px solid rgba(206, 224, 255, 0.1);
  border-bottom: 1px solid rgba(206, 224, 255, 0.1);
}

.ranking-summary h2 {
  margin: 0;
  padding: 16px 18px;
  font-size: 1rem;
  font-weight: 900;
  color: var(--match-accent);
  text-transform: uppercase;
  background:
    linear-gradient(90deg, rgba(99, 242, 232, 0.14), transparent 72%), rgba(20, 30, 55, 0.92);
  border-left: 3px solid var(--match-accent);
  box-shadow: inset 0 -1px 0 rgba(206, 224, 255, 0.08);
  text-shadow: 0 0 12px rgba(99, 242, 232, 0.32);
}

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.summary-grid div {
  min-height: 72px;
  padding: 16px 18px;
  border-right: 1px solid rgba(206, 224, 255, 0.08);
  border-bottom: 1px solid rgba(206, 224, 255, 0.08);
}

.summary-grid div:last-child {
  grid-column: 1 / -1;
  border-bottom: 0;
}

.summary-grid strong {
  display: block;
  margin-top: 8px;
  color: var(--match-accent);
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  padding: 14px 0;
  margin: 0;
  overflow-y: auto;
  list-style: none;
}

.ranking-list li {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 0 18px;
  color: var(--match-muted);
}

.ranking-list strong,
.ranking-list em {
  overflow: hidden;
  font-size: 0.78rem;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-list em {
  color: #eac78c;
}

.ranking-list .is-current {
  color: var(--match-text);
  background: rgba(74, 232, 221, 0.14);
}

.ranking-list .is-current em {
  color: var(--match-accent);
}

.match-cta-panel {
  align-self: end;
  justify-self: end;
  width: min(380px, 100%);
  min-width: 0;
  margin-bottom: clamp(20px, 6vh, 64px);
}

.rank-panel {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  min-width: 0;
  padding: 18px;
  margin-bottom: 16px;
  border-right: 4px solid rgba(100, 242, 232, 0.42);
  background: rgba(18, 27, 48, 0.74);
}

.rank-panel strong {
  font-size: 1.45rem;
}

.rank-progress {
  align-items: flex-end;
  text-align: right;
}

.rank-progress strong {
  font-size: 1rem;
  color: var(--match-accent);
}

.progress-track {
  grid-column: 1 / -1;
  min-width: 0;
  height: 8px;
  overflow: hidden;
  background: rgba(142, 174, 196, 0.28);
}

.progress-track span {
  display: block;
  width: 54%;
  height: 100%;
  background: linear-gradient(90deg, var(--match-accent), #6dc8ff);
}

.primary-match-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 76px;
  padding: 0 24px;
  font-size: 1.35rem;
  font-weight: 900;
  line-height: 1.15;
  color: #f8fbff;
  background: #162a42;
  border: 1px solid rgba(99, 242, 232, 0.48);
  border-radius: 4px;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.32);
}

.primary-match-button:hover:not(:disabled) {
  background: #1b3854;
  border-color: rgba(99, 242, 232, 0.72);
}

.primary-match-button.is-waiting {
  color: #63f2e8;
  background: #0b1727;
  border-color: rgba(165, 107, 255, 0.72);
  box-shadow:
    inset 0 4px 12px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(99, 242, 232, 0.12);
  transform: translateY(1px);
}

.primary-match-button span {
  margin-right: 12px;
  font-size: 1rem;
}

.primary-match-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  filter: grayscale(0.35);
}

.secondary-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-width: 0;
  margin-top: 14px;
}

.secondary-actions button {
  min-width: 0;
  min-height: 56px;
  padding: 0 12px;
  color: var(--match-text);
  line-height: 1.15;
  white-space: normal;
  background: rgba(24, 31, 52, 0.86);
  border: 1px solid rgba(206, 224, 255, 0.12);
  border-radius: 4px;
}

.match-error-backdrop {
  position: fixed;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(1, 5, 14, 0.68);
}

.match-error-dialog {
  width: min(360px, 100%);
  padding: 22px;
  color: var(--match-text);
  background: rgba(7, 13, 31, 0.96);
  border: 1px solid rgba(99, 242, 232, 0.28);
  border-radius: 6px;
  box-shadow: 0 18px 54px rgba(0, 0, 0, 0.42);
}

.match-error-dialog h2 {
  margin: 0 0 10px;
  font-size: 1.05rem;
  line-height: 1.2;
}

.match-error-dialog p {
  margin: 0 0 18px;
  color: var(--match-muted);
  line-height: 1.5;
}

.match-error-dialog button {
  width: 100%;
  min-height: 44px;
  color: #06101c;
  font-weight: 900;
  background: var(--match-accent);
  border: 0;
  border-radius: 4px;
}

@media (max-width: 760px) {
  .match-page {
    width: 100%;
    height: auto;
    min-height: 100svh;
    overflow-x: hidden;
    overflow-y: auto;
  }

  .match-app-bar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 38px 70px;
    gap: 8px;
    min-height: 58px;
    padding: 0 12px;
  }

  .match-app-bar h1 {
    max-width: 152px;
    font-size: 1rem;
  }

  .match-locale-toggle {
    width: 38px;
    min-width: 38px;
  }

  .match-actions {
    gap: 6px;
    justify-content: flex-end;
  }

  .icon-button {
    width: 32px;
    height: 32px;
  }

  .match-layout {
    display: flex;
    flex-direction: column-reverse;
    gap: 18px;
    width: 100%;
    max-width: 100%;
    height: auto;
    min-height: auto;
    padding: 18px 16px 28px;
  }

  .match-cta-panel {
    align-self: stretch;
    width: 100%;
    max-width: 100%;
    margin-bottom: 0;
  }

  .ranking-panel {
    height: auto;
    overflow: visible;
    min-height: auto;
  }

  .ranking-list {
    overflow-y: visible;
  }

  .rank-panel {
    grid-template-columns: 1fr;
  }

  .rank-progress {
    align-items: flex-start;
    text-align: left;
  }

  .primary-match-button {
    min-height: 64px;
    font-size: 1.12rem;
  }

  .secondary-actions {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}
</style>
