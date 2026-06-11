<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
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
    :data-match-response-command-status="matchResponseCommandStatus"
    :data-match-response-command-pending="isMatchResponseCommandPending"
    :data-can-submit-match-response="canSubmitMatchResponse"
    :data-match-result-action="matchResponseResult?.action ?? ''"
    :data-stream-error-message="streamErrorMessage"
    :data-can-start-match="canStartMatch"
    :data-queue-status="queueStatus"
    :data-queue-error-message="queueErrorMessage"
    :data-logout-pending="isLoggingOut"
    :data-logout-confirm-open="isLogoutConfirmOpen"
  >
    <header class="match-app-bar" aria-label="Match navigation">
      <h1>LEAGUE OF STAR</h1>
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
        <button
          class="icon-button"
          type="button"
          :aria-label="t('match.logout')"
          :disabled="!canLogout"
          @click="openLogoutConfirm"
        >
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
            <strong>Legendary Star</strong>
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

    <div v-if="isMatchFoundModalOpen" class="match-found-backdrop" role="presentation">
      <section
        class="match-found-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="match-found-title"
      >
        <span class="match-found-corner is-top-left" aria-hidden="true" />
        <span class="match-found-corner is-top-right" aria-hidden="true" />
        <span class="match-found-corner is-bottom-left" aria-hidden="true" />
        <span class="match-found-corner is-bottom-right" aria-hidden="true" />

        <h2 id="match-found-title">{{ t('match.foundTitle') }}</h2>
        <p class="match-found-subtitle">{{ t('match.foundSubtitle') }}</p>

        <div
          class="match-found-emblem"
          :style="{ '--match-found-progress': matchFoundCountdownProgress }"
          aria-hidden="true"
        >
          <span class="match-found-ring" />
          <div class="match-found-logo-frame">
            <img :src="logoImageUrl" alt="" data-testid="match-found-logo" />
          </div>
        </div>

        <p class="match-found-countdown" aria-live="polite">
          <span v-if="!isMatchFoundLoading">{{ t('match.responseTime') }}</span>
          <strong>{{ matchFoundStatusLabel }}</strong>
        </p>

        <div class="match-found-actions">
          <button
            class="match-found-accept"
            :class="{
              'is-pending': matchResponseCommandStatus === 'accepting',
              'is-submitted': matchResponseCommandStatus === 'accepted',
            }"
            type="button"
            :disabled="!canSubmitMatchResponse"
            @click="acceptMatchResponse"
          >
            {{ matchAcceptActionLabel }}
          </button>
          <button
            class="match-found-decline"
            :class="{
              'is-pending': matchResponseCommandStatus === 'rejecting',
              'is-submitted': matchResponseCommandStatus === 'rejected',
            }"
            type="button"
            :disabled="!canSubmitMatchResponse"
            @click="rejectMatchResponse"
          >
            {{ matchRejectActionLabel }}
          </button>
        </div>
      </section>
    </div>

    <div
      v-if="isLogoutConfirmOpen"
      class="match-logout-backdrop"
      role="presentation"
      @click="closeLogoutConfirm"
    >
      <section
        class="match-logout-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="match-logout-title"
        @click.stop
      >
        <h2 id="match-logout-title">{{ t('match.logoutConfirmTitle') }}</h2>
        <p>{{ t('match.logoutConfirmMessage') }}</p>
        <div class="match-logout-actions">
          <button class="match-logout-cancel" type="button" @click="closeLogoutConfirm">
            {{ t('match.logoutCancel') }}
          </button>
          <button
            class="match-logout-confirm"
            type="button"
            :disabled="isLoggingOut"
            @click="confirmLogout"
          >
            {{ isLoggingOut ? t('match.loggingOut') : t('match.logoutConfirm') }}
          </button>
        </div>
      </section>
    </div>

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
import { useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { logout } from '@/services/authService'
import { clearAuthTokens, getRefreshToken } from '@/services/authToken'
import { saveGameWaitingPayloadFromMatchResult } from '@/services/gameWaitingPayload'
import { acceptMatch, joinMatchQueue, leaveMatchQueue, rejectMatch } from '@/services/matchService'
import { connectMatchEventSource } from '@/services/realtime/matchEventSource'

import backgroundImageUrl from '../../img/background-new-sharp.png'
import logoImageUrl from '../../img/lightning-spell.png'

const streamStatus = ref('idle')
const { nextLocaleLabel, t, toggleLocale } = useLocale()
const router = useRouter()
const lastHeartbeatAt = ref('')
const connectedEvent = shallowRef()
const matchFound = shallowRef()
const matchResponseResult = shallowRef()
const streamErrorMessage = ref('')
const queueStatus = ref('ready')
const queueErrorMessage = ref('')
const matchWaitingSeconds = ref(0)
const isLoggingOut = ref(false)
const isLogoutConfirmOpen = ref(false)
const isMatchFoundModalOpen = ref(false)
const matchFoundCountdownSeconds = ref(0)
const matchResponseCommandStatus = ref('idle')
const errorModalMessage = ref('')
const isMatchResponseCommandPending = computed(
  () =>
    matchResponseCommandStatus.value === 'accepting' ||
    matchResponseCommandStatus.value === 'rejecting',
)
const hasSubmittedMatchResponseCommand = computed(
  () =>
    matchResponseCommandStatus.value === 'accepted' ||
    matchResponseCommandStatus.value === 'rejected' ||
    matchResponseCommandStatus.value === 'lockWaiting',
)
const currentMatchFoundId = computed(() => String(matchFound.value?.matchId ?? '').trim())
const isMatchFoundLoading = computed(
  () => isMatchFoundModalOpen.value && matchFoundCountdownSeconds.value <= 0,
)
const canSubmitMatchResponse = computed(
  () =>
    isMatchFoundModalOpen.value &&
    currentMatchFoundId.value !== '' &&
    matchFoundCountdownSeconds.value > 0 &&
    streamStatus.value === 'connected' &&
    !isMatchResponseCommandPending.value &&
    !hasSubmittedMatchResponseCommand.value &&
    matchResponseResult.value === undefined,
)
const matchFoundCountdownProgress = computed(() => {
  const totalSeconds = Math.max(1, getMatchFoundAcceptTimeoutSeconds(1))
  const progress = Math.min(1, Math.max(0, matchFoundCountdownSeconds.value / totalSeconds))

  return `${progress}turn`
})
const matchFoundStatusLabel = computed(() => {
  if (matchResponseCommandStatus.value === 'accepting') {
    return t('match.accepting')
  }

  if (matchResponseCommandStatus.value === 'rejecting') {
    return t('match.declining')
  }

  if (matchResponseCommandStatus.value === 'accepted') {
    return t('match.waitingForOpponent')
  }

  if (matchResponseCommandStatus.value === 'rejected') {
    return t('match.waitingForResult')
  }

  if (matchResponseCommandStatus.value === 'lockWaiting') {
    return t('match.processingResponse')
  }

  if (isMatchFoundLoading.value) {
    return t('match.loading')
  }

  return String(matchFoundCountdownSeconds.value)
})
const matchAcceptActionLabel = computed(() => {
  if (matchResponseCommandStatus.value === 'accepting') {
    return t('match.accepting')
  }

  if (matchResponseCommandStatus.value === 'accepted') {
    return t('match.accepted')
  }

  return t('match.accept')
})
const matchRejectActionLabel = computed(() => {
  if (matchResponseCommandStatus.value === 'rejecting') {
    return t('match.declining')
  }

  if (matchResponseCommandStatus.value === 'rejected') {
    return t('match.declined')
  }

  return t('match.decline')
})
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
const canLogout = computed(
  () =>
    !isLoggingOut.value &&
    !hasActiveMatchFoundResponse.value &&
    !isMatchResponseCommandPending.value &&
    !hasSubmittedMatchResponseCommand.value,
)
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
let shouldResetMatchmakingAfterErrorModalClose = false
let hasCompletedMatchResultTransition = false
let joinAbortController = new AbortController()
let leaveAbortController = new AbortController()
let matchResponseAbortController = new AbortController()
let logoutAbortController = new AbortController()

onMounted(() => {
  isActive = true
  streamErrorMessage.value = ''
})

onUnmounted(() => {
  isActive = false
  shouldJoinAfterStreamConnected = false
  abortJoinRequest()
  abortLeaveRequest()
  abortMatchResponseRequest()
  abortLogoutRequest()
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

function openLogoutConfirm() {
  if (!canLogout.value) {
    return
  }

  isLogoutConfirmOpen.value = true
}

function closeLogoutConfirm() {
  if (isLoggingOut.value) {
    return
  }

  isLogoutConfirmOpen.value = false
}

async function confirmLogout() {
  if (!canLogout.value || !isLogoutConfirmOpen.value) {
    return
  }

  isLoggingOut.value = true
  isLogoutConfirmOpen.value = false
  queueErrorMessage.value = ''
  streamErrorMessage.value = ''
  abortJoinRequest()
  abortLeaveRequest()

  try {
    if (hasQueueJoinRequestStarted) {
      queueStatus.value = 'leaving'
      stopMatchWaitingTimer()
      leaveAbortController = new AbortController()
      await leaveMatchQueue(leaveAbortController.signal)
    }
  } catch {
    // Logout is a user-requested session termination. Queue cleanup is best-effort.
  } finally {
    await finalizeLogout()
  }
}

async function finalizeLogout() {
  abortLogoutRequest()
  logoutAbortController = new AbortController()
  const refreshToken = getRefreshToken()

  try {
    if (refreshToken !== null && refreshToken.trim() !== '') {
      await logout(refreshToken, logoutAbortController.signal)
    }
  } catch {
    // Backend revoke failure must not keep the current browser session authenticated.
  } finally {
    clearAuthTokens()
    closeMatchStream()
    resetLocalLogoutState()

    await router.push({ name: ROUTE_NAMES.login })
  }
}

function startMatchmaking() {
  if (queueStatus.value !== 'ready') {
    return
  }

  hasCompletedMatchResultTransition = false
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
        if (isActive && !hasCompletedMatchResultTransition) {
          streamErrorMessage.value = ''
        }
      },
      onConnected: (payload) => {
        if (!isActive || hasCompletedMatchResultTransition) {
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
        if (isActive && !hasCompletedMatchResultTransition) {
          lastHeartbeatAt.value = payload.sentAt
        }
      },
      onMatchFound: (payload) => {
        if (isActive && !hasCompletedMatchResultTransition) {
          matchFound.value = payload
          matchResponseResult.value = undefined
          openMatchFoundModal()
        }
      },
      onMatchResponseResult: (payload) => {
        if (isActive && !hasCompletedMatchResultTransition) {
          matchResponseResult.value = payload
          handleMatchResponseResult()
        }
      },
      onError: () => {
        if (isActive && !hasCompletedMatchResultTransition) {
          handleStreamError()
        }
      },
    })
    closeMatchEventSource = connection.close
  } catch {
    handleStreamError()
  }
}

function handleMatchResponseResult() {
  const payload = matchResponseResult.value

  if (payload.action === 'GO_TO_GAME_WAITING') {
    transitionToGameWaiting()
    return
  }

  if (payload.action === 'RETURN_TO_MATCHING') {
    returnToMatchingQueue()
    return
  }

  returnToMatchStart()
}

function transitionToGameWaiting() {
  const game = matchResponseResult.value?.game

  if (!isValidGamePayload(game)) {
    failMatchmaking(t('match.resultFailed'))
    return
  }

  hasCompletedMatchResultTransition = true
  resetMatchFoundModalState()
  stopMatchWaitingTimer()
  saveGameWaitingPayloadFromMatchResult(matchResponseResult.value)
  closeMatchStream()
  queueStatus.value = 'ready'
  queueErrorMessage.value = ''
  matchWaitingSeconds.value = 0
  streamStatus.value = 'idle'
  streamErrorMessage.value = ''
  shouldJoinAfterStreamConnected = false
  hasQueueJoinRequestStarted = false

  void Promise.resolve(
    router.push({
      name: ROUTE_NAMES.gameWaiting,
      params: {
        gameRoomId: String(game.gameRoomId),
      },
    }),
  ).catch(() => {
    if (isActive) {
      failMatchmaking(t('match.resultFailed'))
    }
  })
}

function isValidGamePayload(game = {}) {
  if (typeof game !== 'object' || game === null) {
    return false
  }

  const gameRoomId = Reflect.get(game, 'gameRoomId')
  const webSocketUrl = Reflect.get(game, 'webSocketUrl')

  return Number.isFinite(gameRoomId) && String(webSocketUrl).trim() !== ''
}

function returnToMatchStart() {
  resetMatchmakingState()
  hasCompletedMatchResultTransition = true
}

function returnToMatchingQueue() {
  resetMatchFoundModalState()
  matchFound.value = undefined
  matchResponseResult.value = undefined
  queueStatus.value = 'queued'
  queueErrorMessage.value = ''
  streamErrorMessage.value = ''
  shouldJoinAfterStreamConnected = false
  hasQueueJoinRequestStarted = true
  startMatchWaiting()
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

async function acceptMatchResponse() {
  await submitMatchResponseCommand('accept')
}

async function rejectMatchResponse() {
  await submitMatchResponseCommand('reject')
}

async function submitMatchResponseCommand(command = 'accept') {
  if (!canSubmitMatchResponse.value) {
    return
  }

  const matchId = currentMatchFoundId.value
  abortMatchResponseRequest()
  const activeMatchResponseAbortController = matchResponseAbortController
  matchResponseCommandStatus.value = command === 'accept' ? 'accepting' : 'rejecting'

  try {
    if (command === 'accept') {
      await acceptMatch(matchId, activeMatchResponseAbortController.signal)
    } else {
      await rejectMatch(matchId, activeMatchResponseAbortController.signal)
    }

    if (!isActive || activeMatchResponseAbortController.signal.aborted) {
      return
    }

    matchResponseCommandStatus.value = command === 'accept' ? 'accepted' : 'rejected'
  } catch (error) {
    if (!isActive || (error instanceof Error && error.name === 'AbortError')) {
      return
    }

    if (error instanceof ApiClientError && isMatchResponseLockFailure(error)) {
      matchResponseCommandStatus.value = 'lockWaiting'
      return
    }

    const message =
      error instanceof Error && error.message.trim() !== ''
        ? error.message
        : t('match.responseFailed')
    failMatchmaking(message, message)
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
  resetMatchResponseCommandState()
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
  const fallbackSeconds = getMatchFoundAcceptTimeoutSeconds(0)
  const eventCreatedAt = Date.parse(String(matchFound.value?.eventCreatedAt ?? ''))

  if (Number.isNaN(eventCreatedAt)) {
    return Date.now() + fallbackSeconds * 1000
  }

  return eventCreatedAt + fallbackSeconds * 1000
}

function getMatchFoundAcceptTimeoutSeconds(defaultSeconds = 0) {
  const seconds = Number(matchFound.value?.acceptTimeoutSeconds ?? defaultSeconds)

  if (!Number.isFinite(seconds) || seconds < 0) {
    return defaultSeconds
  }

  return seconds
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
  resetMatchResponseCommandState()
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
    shouldResetMatchmakingAfterErrorModalClose = true
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
  shouldResetMatchmakingAfterErrorModalClose = false
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

function abortMatchResponseRequest() {
  matchResponseAbortController.abort()
  matchResponseAbortController = new AbortController()
}

function abortLogoutRequest() {
  logoutAbortController.abort()
}

function resetLocalLogoutState() {
  resetMatchFoundModalState()
  queueStatus.value = 'ready'
  queueErrorMessage.value = ''
  matchWaitingSeconds.value = 0
  streamStatus.value = 'idle'
  streamErrorMessage.value = ''
  shouldJoinAfterStreamConnected = false
  hasQueueJoinRequestStarted = false
  shouldResetMatchmakingAfterErrorModalClose = false
  isLoggingOut.value = false
  isLogoutConfirmOpen.value = false
  stopMatchWaitingTimer()
  resetStreamPayloads()
}

function isMatchResponseLockFailure(error = new ApiClientError(0, undefined)) {
  const errorCode = getApiErrorCode(error)

  return errorCode === 'MATCH_012' || errorCode === 'MATCH_RESPONSE_LOCK_FAILED'
}

function getApiErrorCode(error = new ApiClientError(0, undefined)) {
  if (typeof error.body !== 'object' || error.body === null) {
    return ''
  }

  const code = Reflect.get(error.body, 'code')

  return typeof code === 'string' ? code : ''
}

function resetMatchResponseCommandState() {
  abortMatchResponseRequest()
  matchResponseCommandStatus.value = 'idle'
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

  if (shouldResetMatchmakingAfterErrorModalClose) {
    shouldResetMatchmakingAfterErrorModalClose = false
    resetMatchmakingState()
  }
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
    linear-gradient(90deg, rgba(4, 8, 22, 0.54), rgba(4, 8, 22, 0.08) 58%),
    linear-gradient(0deg, rgba(4, 8, 22, 0.5), rgba(4, 8, 22, 0.04) 48%),
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
  background: rgba(2, 6, 16, 0.06);
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
  width: min(318px, 100%);
  min-width: 0;
  margin-bottom: clamp(8px, 3vh, 28px);
}

.rank-panel {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  min-width: 0;
  padding: 12px 14px;
  margin-bottom: 10px;
  border-right: 3px solid rgba(100, 242, 232, 0.42);
  background: rgba(18, 27, 48, 0.66);
}

.rank-panel strong {
  font-size: 1.12rem;
}

.rank-progress {
  align-items: flex-end;
  text-align: right;
}

.rank-progress strong {
  font-size: 0.84rem;
  color: var(--match-accent);
}

.progress-track {
  grid-column: 1 / -1;
  min-width: 0;
  height: 6px;
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
  min-height: 56px;
  padding: 0 18px;
  font-size: 1.08rem;
  font-weight: 900;
  line-height: 1.15;
  color: #f8fbff;
  background: #162a42;
  border: 1px solid rgba(99, 242, 232, 0.48);
  border-radius: 4px;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.28);
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
  margin-right: 8px;
  font-size: 0.86rem;
}

.primary-match-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  filter: grayscale(0.35);
}

.secondary-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  min-width: 0;
  margin-top: 10px;
}

.secondary-actions button {
  min-width: 0;
  min-height: 42px;
  padding: 0 10px;
  color: var(--match-text);
  font-size: 0.86rem;
  line-height: 1.15;
  white-space: normal;
  background: rgba(24, 31, 52, 0.76);
  border: 1px solid rgba(206, 224, 255, 0.12);
  border-radius: 4px;
}

.match-found-backdrop {
  position: fixed;
  inset: 0;
  z-index: 4;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 38%, rgba(71, 229, 220, 0.1), transparent 28%),
    rgba(1, 5, 14, 0.74);
  backdrop-filter: blur(5px);
}

.match-found-dialog {
  position: relative;
  width: min(380px, 100%);
  min-width: 0;
  max-height: calc(100svh - 48px);
  padding: clamp(28px, 5vw, 42px) clamp(24px, 5vw, 38px) 36px;
  overflow: hidden;
  color: var(--match-text);
  text-align: center;
  background:
    linear-gradient(180deg, rgba(12, 20, 40, 0.98), rgba(4, 9, 22, 0.98)), rgba(5, 10, 24, 0.98);
  border: 1px solid rgba(99, 242, 232, 0.3);
  box-shadow:
    0 26px 76px rgba(0, 0, 0, 0.58),
    inset 0 0 42px rgba(99, 242, 232, 0.04);
}

.match-found-dialog::before {
  position: absolute;
  inset: 12px;
  pointer-events: none;
  content: '';
  border: 1px solid rgba(99, 242, 232, 0.08);
}

.match-found-corner {
  position: absolute;
  width: 34px;
  height: 34px;
  pointer-events: none;
  border-color: rgba(99, 242, 232, 0.58);
}

.match-found-corner.is-top-left {
  top: -1px;
  left: -1px;
  border-top: 2px solid;
  border-left: 2px solid;
}

.match-found-corner.is-top-right {
  top: -1px;
  right: -1px;
  border-top: 2px solid;
  border-right: 2px solid;
}

.match-found-corner.is-bottom-left {
  bottom: -1px;
  left: -1px;
  border-bottom: 2px solid;
  border-left: 2px solid;
}

.match-found-corner.is-bottom-right {
  right: -1px;
  bottom: -1px;
  border-right: 2px solid;
  border-bottom: 2px solid;
}

.match-found-dialog h2 {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: clamp(1.55rem, 5vw, 2rem);
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  color: var(--match-accent);
  text-transform: uppercase;
  text-shadow: 0 0 18px rgba(99, 242, 232, 0.72);
}

.match-found-subtitle {
  position: relative;
  z-index: 1;
  margin: 10px 0 0;
  font-size: 0.78rem;
  font-weight: 800;
  color: var(--match-muted);
  text-transform: uppercase;
}

.match-found-emblem {
  position: relative;
  z-index: 1;
  display: grid;
  width: clamp(138px, 40vw, 178px);
  height: clamp(138px, 40vw, 178px);
  margin: clamp(26px, 5vh, 36px) auto 22px;
  place-items: center;
}

.match-found-ring {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle, transparent 56%, #07101d 57%, #07101d 65%, transparent 66%),
    conic-gradient(var(--match-accent) var(--match-found-progress), rgba(99, 242, 232, 0.16) 0);
  border-radius: 50%;
  box-shadow: 0 0 22px rgba(99, 242, 232, 0.22);
}

.match-found-logo-frame {
  position: relative;
  display: grid;
  width: 72%;
  height: 72%;
  place-items: center;
  background: rgba(18, 34, 49, 0.92);
  border: 1px solid rgba(99, 242, 232, 0.2);
  border-radius: 50%;
  box-shadow: inset 0 0 28px rgba(0, 0, 0, 0.38);
}

.match-found-logo-frame img {
  width: 58%;
  height: 58%;
  object-fit: contain;
}

.match-found-countdown {
  position: relative;
  z-index: 1;
  min-height: 42px;
  margin: 0 0 18px;
  color: var(--match-muted);
  font-size: 0.74rem;
  font-weight: 800;
  text-transform: uppercase;
}

.match-found-countdown span,
.match-found-countdown strong {
  display: block;
}

.match-found-countdown strong {
  margin-top: 5px;
  color: var(--match-accent);
  font-size: 1.28rem;
  line-height: 1;
  text-shadow: 0 0 12px rgba(99, 242, 232, 0.48);
}

.match-found-actions {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 12px;
}

.match-found-actions button {
  width: 100%;
  min-height: 48px;
  padding: 0 20px;
  overflow: hidden;
  font-size: 0.82rem;
  font-weight: 900;
  letter-spacing: 0;
  text-transform: uppercase;
  clip-path: polygon(10% 0, 90% 0, 100% 50%, 90% 100%, 10% 100%, 0 50%);
}

.match-found-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.match-found-actions button.is-pending,
.match-found-actions button.is-submitted {
  opacity: 0.82;
}

.match-found-accept {
  color: #06101c;
  background: var(--match-accent);
  border: 1px solid rgba(209, 255, 251, 0.72);
  box-shadow: 0 0 22px rgba(99, 242, 232, 0.22);
}

.match-found-decline {
  color: rgba(240, 249, 255, 0.82);
  background: rgba(4, 9, 22, 0.74);
  border: 1px solid rgba(206, 224, 255, 0.22);
}

.match-found-accept.is-submitted {
  color: #06101c;
  background: #b7fff7;
}

.match-found-decline.is-submitted {
  color: #f7fbff;
  background: rgba(119, 80, 156, 0.74);
  border-color: rgba(214, 174, 255, 0.38);
}

.match-logout-backdrop,
.match-error-backdrop {
  position: fixed;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(1, 5, 14, 0.68);
}

.match-logout-dialog,
.match-error-dialog {
  width: min(360px, 100%);
  padding: 22px;
  color: var(--match-text);
  background: rgba(7, 13, 31, 0.96);
  border: 1px solid rgba(99, 242, 232, 0.28);
  border-radius: 6px;
  box-shadow: 0 18px 54px rgba(0, 0, 0, 0.42);
}

.match-logout-dialog h2,
.match-error-dialog h2 {
  margin: 0 0 10px;
  font-size: 1.05rem;
  line-height: 1.2;
}

.match-logout-dialog p,
.match-error-dialog p {
  margin: 0 0 18px;
  color: var(--match-muted);
  line-height: 1.5;
}

.match-logout-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.match-logout-actions button,
.match-error-dialog button {
  width: 100%;
  min-height: 44px;
  font-weight: 900;
  border: 0;
  border-radius: 4px;
}

.match-error-dialog button {
  color: #06101c;
  background: var(--match-accent);
}

.match-logout-cancel {
  color: rgba(219, 232, 244, 0.78);
  background: rgba(8, 15, 34, 0.72);
  border: 1px solid rgba(206, 224, 255, 0.14);
}

.match-logout-confirm {
  color: #06101c;
  background: var(--match-accent);
}

.match-logout-confirm:disabled {
  cursor: wait;
  opacity: 0.62;
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
    min-height: 52px;
    font-size: 1rem;
  }

  .secondary-actions {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .match-found-backdrop {
    padding: 18px;
  }

  .match-found-dialog {
    width: min(342px, 100%);
    padding: 28px 22px 30px;
  }

  .match-found-emblem {
    margin: 24px auto 18px;
  }

  .match-found-actions button {
    min-height: 46px;
  }
}
</style>
