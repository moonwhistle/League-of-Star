<template>
  <!-- eslint-disable vue/html-closing-bracket-newline, vue/html-indent, vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="profile-page"
    :style="{ '--profile-background-image': `url(${backgroundImageUrl})` }"
    :aria-label="t('profile.pageLabel')"
    :data-profile-status="profileStatus"
    :data-profile-error-message="profileErrorMessage"
    :data-rank-status="rankStatus"
    :data-rank-error-message="rankErrorMessage"
    :data-records-status="recordsStatus"
    :data-records-error-message="recordsErrorMessage"
    :data-records-count="profileRecords.length"
    :data-records-page="currentRecordsPage"
    :data-records-total-pages="recordsPageCount"
  >
    <section class="profile-shell" aria-live="polite">
      <header class="profile-header">
        <div>
          <h1>{{ t('profile.title') }}</h1>
        </div>
        <nav class="profile-actions" :aria-label="t('profile.navigation')">
          <button
            class="profile-icon-button"
            type="button"
            :aria-label="t('profile.returnToMatch')"
            :title="t('profile.returnToMatch')"
            @click="returnToMatch"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 6l-6 6 6 6" />
            </svg>
          </button>
        </nav>
      </header>

      <section class="profile-grid">
        <section class="profile-panel profile-panel--account" :aria-label="t('profile.account')">
          <div class="profile-panel-heading">
            <span>{{ t('profile.account') }}</span>
            <strong>{{ displayNickname }}</strong>
          </div>

          <div class="profile-account-body">
            <div class="profile-avatar" aria-hidden="true">{{ avatarInitial }}</div>
            <div class="profile-account-copy">
              <strong v-if="profileStatus === 'loading'">{{ t('profile.profileLoading') }}</strong>
              <strong v-else-if="profileStatus === 'error'">{{ profileErrorMessage }}</strong>
              <template v-else-if="profile !== undefined">
                <span>{{ t('profile.email') }}</span>
                <strong>{{ profile.email }}</strong>
                <span>{{ t('profile.joinedAt') }}</span>
                <strong>{{ formatDateTime(profile.createdAt) }}</strong>
              </template>
            </div>
          </div>
        </section>

        <section class="profile-panel profile-panel--rank" :aria-label="t('profile.rank')">
          <div class="profile-panel-heading">
            <span>{{ t('profile.rank') }}</span>
            <strong>{{ displayRank }}</strong>
          </div>

          <div v-if="rankStatus === 'loading'" class="profile-state">
            {{ t('profile.rankLoading') }}
          </div>
          <div v-else-if="rankStatus === 'error'" class="profile-state profile-state--error">
            {{ rankErrorMessage }}
          </div>
          <div v-else-if="rank !== undefined" class="profile-rank-grid">
            <div>
              <span>{{ t('profile.lp') }}</span>
              <strong>{{ formatLp(rank.lp) }}</strong>
            </div>
            <div>
              <span>{{ t('profile.recordSummary') }}</span>
              <strong>{{ formatWinLossDraw(rank.wins, rank.losses, rank.draws) }}</strong>
            </div>
            <div>
              <span>{{ t('profile.rankUpdatedAt') }}</span>
              <strong>{{ formatDateTime(rank.rankUpdatedAt) }}</strong>
            </div>
          </div>
        </section>

        <section
          class="profile-panel profile-panel--records"
          :aria-label="t('profile.recentRecords')"
        >
          <div class="profile-panel-heading">
            <span>{{ t('profile.recentRecords') }}</span>
          </div>
          <p class="profile-record-notice">{{ t('profile.recordsLimitNotice') }}</p>

          <div v-if="recordsStatus === 'loading'" class="profile-state">
            {{ t('profile.recordsLoading') }}
          </div>
          <div v-else-if="recordsStatus === 'error'" class="profile-state profile-state--error">
            {{ recordsErrorMessage }}
          </div>
          <div v-else-if="profileRecords.length === 0" class="profile-state">
            {{ t('profile.recordsEmpty') }}
          </div>
          <ol v-else class="profile-record-list">
            <li v-for="record in profileRecords" :key="record.gameId" :data-result="record.result">
              <strong>{{ formatResult(record.result) }}</strong>
              <span
                >{{ t('records.me') }} {{ t('records.versus') }} {{ record.opponentNickname }}</span
              >
              <em
                >{{ formatRankChange(record.rankBefore, record.rankAfter) }} ·
                {{ formatLpChange(record.lpChange) }}</em
              >
              <time>{{ formatDateTime(record.playedAt) }}</time>
            </li>
          </ol>

          <nav
            v-if="shouldShowRecordsPagination"
            class="profile-record-pagination"
            :aria-label="t('records.paginationLabel')"
          >
            <button
              v-for="page in recordsPageNumbers"
              :key="page"
              type="button"
              :class="{ 'is-active': currentRecordsPage === page }"
              :aria-current="currentRecordsPage === page ? 'page' : undefined"
              :disabled="recordsStatus === 'loading'"
              @click="changeRecordsPage(page)"
            >
              {{ page }}
            </button>
          </nav>
        </section>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { getMyGameRecords } from '@/services/gameRecordService'
import { getMyProfile } from '@/services/profileService'
import { getMyRank } from '@/services/rankService'
import type { GameRecordResult } from '@/types/game'
import type { GameRecordEntryResponse, GameRecordListResponse } from '@/types/gameRecord'
import type { UserProfileResponse, UserRankResponse } from '@/types/user'

import backgroundImageUrl from '../../img/background-new-sharp.png'

const PROFILE_FIRST_RECORDS_PAGE = 1
const PROFILE_MAX_RECORD_PAGES = 3

type LoadStatus = 'idle' | 'loading' | 'success' | 'error'

const router = useRouter()
const { locale, t } = useLocale()
const profileStatus = ref<LoadStatus>('idle')
const rankStatus = ref<LoadStatus>('idle')
const recordsStatus = ref<LoadStatus>('idle')
const profileErrorMessage = ref('')
const rankErrorMessage = ref('')
const recordsErrorMessage = ref('')
const profile = shallowRef<UserProfileResponse>()
const rank = shallowRef<UserRankResponse>()
const recordsResponse = shallowRef<GameRecordListResponse>()
const profileRecords = shallowRef<GameRecordEntryResponse[]>([])
const currentRecordsPage = ref(PROFILE_FIRST_RECORDS_PAGE)
const profileAbortController = shallowRef<AbortController>()
const rankAbortController = shallowRef<AbortController>()
const recordsAbortController = shallowRef<AbortController>()
const displayNickname = computed(() => {
  if (profileStatus.value === 'loading') {
    return t('profile.profileLoading')
  }

  const nickname = profile.value?.nickname.trim()

  return nickname === undefined || nickname === '' ? t('profile.profileUnavailable') : nickname
})
const avatarInitial = computed(() => displayNickname.value.trim().charAt(0).toUpperCase() || 'S')
const displayRank = computed(() => {
  if (rankStatus.value === 'loading') {
    return t('profile.rankLoading')
  }

  return rank.value?.rank ?? t('profile.rankUnavailable')
})
const recordsPageCount = computed(() =>
  Math.min(
    PROFILE_MAX_RECORD_PAGES,
    Math.max(
      PROFILE_FIRST_RECORDS_PAGE,
      recordsResponse.value?.totalPages ?? PROFILE_FIRST_RECORDS_PAGE,
    ),
  ),
)
const recordsPageNumbers = computed(() =>
  Array.from({ length: recordsPageCount.value }, (_, index) => PROFILE_FIRST_RECORDS_PAGE + index),
)
const shouldShowRecordsPagination = computed(
  () => recordsResponse.value !== undefined && recordsResponse.value.totalElements > 0,
)
onMounted(() => {
  void loadProfile()
  void loadRank()
  void loadRecords(PROFILE_FIRST_RECORDS_PAGE)
})

onUnmounted(() => {
  abortProfileRequest()
  abortRankRequest()
  abortRecordsRequest()
})

async function loadProfile() {
  abortProfileRequest()
  profileStatus.value = 'loading'
  profileErrorMessage.value = ''

  const controller = new AbortController()
  profileAbortController.value = controller

  try {
    const response = await getMyProfile(controller.signal)

    if (profileAbortController.value !== controller) {
      return
    }

    profile.value = response
    profileStatus.value = 'success'
  } catch (error) {
    if (shouldIgnoreRequestError(error, profileAbortController.value, controller)) {
      return
    }

    profile.value = undefined
    profileStatus.value = 'error'
    profileErrorMessage.value = getRequestErrorMessage(error, '프로필 정보를 불러올 수 없습니다.')
  } finally {
    if (profileAbortController.value === controller) {
      profileAbortController.value = undefined
    }
  }
}

async function loadRank() {
  abortRankRequest()
  rankStatus.value = 'loading'
  rankErrorMessage.value = ''

  const controller = new AbortController()
  rankAbortController.value = controller

  try {
    const response = await getMyRank(controller.signal)

    if (rankAbortController.value !== controller) {
      return
    }

    rank.value = response
    rankStatus.value = 'success'
  } catch (error) {
    if (shouldIgnoreRequestError(error, rankAbortController.value, controller)) {
      return
    }

    rank.value = undefined
    rankStatus.value = 'error'
    rankErrorMessage.value = getRequestErrorMessage(error, '랭크 정보를 불러올 수 없습니다.')
  } finally {
    if (rankAbortController.value === controller) {
      rankAbortController.value = undefined
    }
  }
}

async function loadRecords(page: number) {
  abortRecordsRequest()
  recordsStatus.value = 'loading'
  recordsErrorMessage.value = ''
  currentRecordsPage.value = page

  const controller = new AbortController()
  recordsAbortController.value = controller

  try {
    const response = await getMyGameRecords(page, controller.signal)

    if (recordsAbortController.value !== controller) {
      return
    }

    recordsResponse.value = response
    profileRecords.value = response.records
    recordsStatus.value = 'success'
  } catch (error) {
    if (shouldIgnoreRequestError(error, recordsAbortController.value, controller)) {
      return
    }

    recordsResponse.value = undefined
    profileRecords.value = []
    recordsStatus.value = 'error'
    recordsErrorMessage.value = getRequestErrorMessage(error, '최근 전적을 불러올 수 없습니다.')
  } finally {
    if (recordsAbortController.value === controller) {
      recordsAbortController.value = undefined
    }
  }
}

function changeRecordsPage(page: number) {
  if (page === currentRecordsPage.value || recordsStatus.value === 'loading') {
    return
  }

  void loadRecords(page)
}

function shouldIgnoreRequestError(
  error: unknown,
  currentController: AbortController | undefined,
  requestController: AbortController,
) {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    currentController !== requestController
  )
}

function getRequestErrorMessage(error: unknown, fallbackMessage: string) {
  if (error instanceof ApiClientError || error instanceof Error) {
    return error.message
  }

  return fallbackMessage
}

function abortProfileRequest() {
  profileAbortController.value?.abort()
  profileAbortController.value = undefined
}

function abortRankRequest() {
  rankAbortController.value?.abort()
  rankAbortController.value = undefined
}

function abortRecordsRequest() {
  recordsAbortController.value?.abort()
  recordsAbortController.value = undefined
}

function formatResult(result: GameRecordResult) {
  switch (result) {
    case 'DRAW':
      return t('records.draw')
    case 'LOSS':
      return t('records.loss')
    case 'WIN':
      return t('records.win')
    default:
      return result
  }
}

function formatRankChange(rankBefore: string, rankAfter: string) {
  return rankBefore === rankAfter ? rankAfter : `${rankBefore} -> ${rankAfter}`
}

function formatLp(lp: number) {
  return `${lp.toLocaleString(locale.value === 'ko' ? 'ko-KR' : 'en-US')} LP`
}

function formatLpChange(lpChange: number) {
  const prefix = lpChange > 0 ? '+' : ''

  return `${prefix}${lpChange} LP`
}

function formatWinLossDraw(wins: number, losses: number, draws: number) {
  return `${wins}${t('match.wins')} ${losses}${t('match.losses')} ${draws}${t('match.draws')}`
}

function formatDateTime(value: string) {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat(locale.value === 'ko' ? 'ko-KR' : 'en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function returnToMatch() {
  void router.push({ name: ROUTE_NAMES.match })
}
</script>

<style scoped>
.profile-page {
  position: relative;
  min-height: 100vh;
  padding: 32px;
  color: #f8fbff;
  background:
    linear-gradient(90deg, rgba(4, 8, 22, 0.64), rgba(4, 8, 22, 0.18) 58%),
    linear-gradient(0deg, rgba(4, 8, 22, 0.62), rgba(4, 8, 22, 0.08) 48%),
    var(--profile-background-image);
  background-color: #030610;
  background-repeat: no-repeat;
  background-position: center;
  background-size:
    100% 100%,
    100% 100%,
    contain;
}

.profile-shell {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 22px;
  width: min(1080px, 100%);
  margin: 0 auto;
}

.profile-header {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.profile-header > div {
  display: grid;
  gap: 8px;
}

.profile-header span,
.profile-panel-heading span,
.profile-account-copy span,
.profile-rank-grid span {
  color: rgba(248, 251, 255, 0.68);
  font-size: 13px;
  font-weight: 800;
}

.profile-header h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1.16;
}

.profile-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.profile-icon-button {
  min-height: 44px;
  border: 1px solid rgba(142, 238, 255, 0.42);
  border-radius: 8px;
  color: #f8fbff;
  background: rgba(8, 19, 36, 0.78);
  transition:
    transform 140ms ease,
    border-color 140ms ease,
    background-color 140ms ease,
    box-shadow 140ms ease;
}

.profile-icon-button:hover,
.profile-icon-button:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(142, 238, 255, 0.86);
  background: rgba(16, 42, 72, 0.92);
  box-shadow:
    0 0 0 3px rgba(142, 238, 255, 0.12),
    0 12px 34px rgba(0, 0, 0, 0.34);
  outline: none;
}

.profile-icon-button:active {
  transform: translateY(0);
}

.profile-icon-button {
  display: grid;
  width: 46px;
  padding: 0;
  place-items: center;
}

.profile-icon-button svg {
  width: 23px;
  height: 23px;
  fill: none;
  stroke: #fff6c7;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.6;
  filter: drop-shadow(0 0 10px rgba(255, 216, 111, 0.5));
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(320px, 1.1fr);
  gap: 16px;
}

.profile-panel {
  min-width: 0;
  padding: 20px;
  border: 1px solid rgba(142, 238, 255, 0.2);
  border-radius: 8px;
  background: rgba(5, 12, 24, 0.72);
  box-shadow: 0 20px 80px rgba(0, 0, 0, 0.32);
}

.profile-panel--records {
  grid-column: 1 / -1;
}

.profile-panel-heading {
  display: grid;
  gap: 8px;
  margin-bottom: 18px;
}

.profile-panel-heading strong {
  overflow: hidden;
  font-size: 26px;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-account-body {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.profile-avatar {
  display: grid;
  width: 78px;
  height: 78px;
  place-items: center;
  border: 2px solid rgba(255, 216, 111, 0.76);
  border-radius: 50%;
  color: #07111f;
  background: #ffd86f;
  box-shadow: 0 0 28px rgba(255, 216, 111, 0.34);
  font-size: 30px;
  font-weight: 950;
}

.profile-account-copy,
.profile-rank-grid {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.profile-account-copy strong,
.profile-rank-grid strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-rank-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.profile-rank-grid div {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  border: 1px solid rgba(142, 238, 255, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.055);
}

.profile-state {
  display: grid;
  min-height: 110px;
  place-items: center;
  color: rgba(248, 251, 255, 0.72);
  text-align: center;
}

.profile-state--error {
  color: #ffb8bf;
}

.profile-record-list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.profile-record-notice {
  margin: -8px 0 16px;
  color: rgba(248, 251, 255, 0.62);
  font-size: 12px;
  font-weight: 700;
}

.profile-record-list li {
  display: grid;
  grid-template-columns: 82px minmax(180px, 1fr) minmax(180px, 0.8fr) minmax(150px, 0.7fr);
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid rgba(142, 238, 255, 0.16);
  border-left: 4px solid #8eeeff;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.055);
}

.profile-record-list li[data-result='LOSS'] {
  border-left-color: #ff8f9d;
}

.profile-record-list li[data-result='DRAW'] {
  border-left-color: #d7deea;
}

.profile-record-list span,
.profile-record-list em,
.profile-record-list time {
  min-width: 0;
  overflow: hidden;
  color: rgba(248, 251, 255, 0.72);
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-record-list strong {
  color: #8eeeff;
}

.profile-record-list li[data-result='LOSS'] strong {
  color: #ff8f9d;
}

.profile-record-list li[data-result='DRAW'] strong {
  color: #d7deea;
}

.profile-record-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 14px;
}

.profile-record-pagination button {
  min-width: 38px;
  min-height: 36px;
  border: 1px solid rgba(142, 238, 255, 0.24);
  border-radius: 8px;
  color: rgba(248, 251, 255, 0.78);
  background: rgba(255, 255, 255, 0.06);
  font-weight: 900;
  transition:
    transform 140ms ease,
    border-color 140ms ease,
    background-color 140ms ease,
    color 140ms ease;
}

.profile-record-pagination button:hover:not(:disabled),
.profile-record-pagination button:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(142, 238, 255, 0.72);
  color: #f8fbff;
  background: rgba(142, 238, 255, 0.14);
  outline: none;
}

.profile-record-pagination button.is-active {
  border-color: rgba(255, 216, 111, 0.78);
  color: #07111f;
  background: #ffd86f;
}

.profile-record-pagination button:disabled {
  cursor: wait;
  opacity: 0.62;
}

@media (max-width: 760px) {
  .profile-page {
    padding: 22px 16px;
  }

  .profile-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-header h1 {
    font-size: 34px;
  }

  .profile-grid,
  .profile-rank-grid,
  .profile-record-list li {
    grid-template-columns: 1fr;
  }

  .profile-account-body {
    grid-template-columns: 1fr;
  }

  .profile-avatar {
    width: 64px;
    height: 64px;
    font-size: 25px;
  }
}
</style>
