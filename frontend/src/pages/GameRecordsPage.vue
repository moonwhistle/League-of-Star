<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="game-records-page"
    :data-records-status="recordsStatus"
    :data-records-page="currentPage"
    :data-records-total-pages="recordsResponse?.totalPages ?? 0"
    :data-records-count="recordsResponse?.records.length ?? 0"
  >
    <section class="records-shell" aria-live="polite">
      <header class="records-header">
        <div>
          <p class="records-eyebrow">{{ t('records.eyebrow') }}</p>
          <h1>{{ t('records.title') }}</h1>
        </div>
        <button class="records-return-button" type="button" @click="returnToMatch">
          {{ t('records.returnToMatch') }}
        </button>
      </header>

      <section v-if="recordsStatus === 'loading'" class="records-state">
        <strong>{{ t('records.loadingTitle') }}</strong>
        <span>{{ t('records.loadingDetail') }}</span>
      </section>

      <section v-else-if="recordsStatus === 'error'" class="records-state records-state--error">
        <strong>{{ t('records.errorTitle') }}</strong>
        <span>{{ recordsErrorMessage }}</span>
        <button type="button" @click="retryRecords">{{ t('records.retry') }}</button>
      </section>

      <section v-else-if="isRecordsEmpty" class="records-state">
        <strong>{{ t('records.emptyTitle') }}</strong>
        <span>{{ t('records.emptyDetail') }}</span>
      </section>

      <section v-else class="records-content" :aria-label="t('records.sectionLabel')">
        <div class="records-summary">
          <span>{{ t('records.summaryLabel') }}</span>
          <strong>{{ formatRecordCount(recordsResponse?.totalElements ?? 0) }}</strong>
        </div>

        <ol class="records-list">
          <li
            v-for="record in recordsResponse?.records ?? []"
            :key="record.gameId"
            class="record-card"
            :data-record-result="record.result"
          >
            <div class="record-result" :data-result="record.result">
              {{ formatResult(record.result) }}
            </div>

            <div class="record-matchup">
              <div class="record-player record-player--me">
                <strong>{{ t('records.me') }}</strong>
              </div>
              <span class="record-versus">{{ t('records.versus') }}</span>
              <div class="record-player record-player--opponent">
                <strong>{{ record.opponentNickname }}</strong>
                <span>{{ formatPlayedAt(record.playedAt) }}</span>
              </div>
            </div>

            <div class="record-rank">
              <span>{{ formatRankChange(record.rankBefore, record.rankAfter) }}</span>
              <strong>
                {{ record.lpBefore }} -> {{ record.lpAfter }}
                <em>{{ formatLpChange(record.lpChange) }}</em>
              </strong>
            </div>
          </li>
        </ol>

        <nav class="records-pagination" :aria-label="t('records.paginationLabel')">
          <button type="button" :disabled="!canGoPrevious" @click="goToPreviousPage">
            {{ t('records.previous') }}
          </button>
          <button
            v-for="pageNumber in pageNumbers"
            :key="pageNumber"
            type="button"
            :class="{ 'is-current': pageNumber === currentPage }"
            :aria-current="pageNumber === currentPage ? 'page' : undefined"
            @click="goToPage(pageNumber)"
          >
            {{ pageNumber }}
          </button>
          <button type="button" :disabled="!canGoNext" @click="goToNextPage">
            {{ t('records.next') }}
          </button>
        </nav>
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
import type { GameRecordResult } from '@/types/game'
import type { GameRecordListResponse } from '@/types/gameRecord'

const FIRST_PAGE = 1
const MAX_PAGE = 3

type RecordsStatus = 'idle' | 'loading' | 'success' | 'error'

const router = useRouter()
const { locale, t } = useLocale()
const recordsStatus = ref<RecordsStatus>('idle')
const recordsErrorMessage = ref('')
const requestedPage = ref(FIRST_PAGE)
const recordsResponse = shallowRef<GameRecordListResponse>()
const recordsAbortController = shallowRef<AbortController>()

const currentPage = computed(() => recordsResponse.value?.page ?? requestedPage.value)
const isRecordsEmpty = computed(
  () => recordsStatus.value === 'success' && (recordsResponse.value?.records.length ?? 0) === 0,
)
const pageNumbers = computed(() => {
  const totalPages = recordsResponse.value?.totalPages ?? 0

  return Array.from({ length: Math.min(totalPages, MAX_PAGE) }, (_, index) => index + FIRST_PAGE)
})
const canGoPrevious = computed(
  () => recordsStatus.value === 'success' && currentPage.value > FIRST_PAGE,
)
const canGoNext = computed(
  () =>
    recordsStatus.value === 'success' &&
    Boolean(recordsResponse.value?.hasNext) &&
    currentPage.value < MAX_PAGE,
)

onMounted(() => {
  void fetchRecords(FIRST_PAGE)
})

onUnmounted(() => {
  abortRecordsRequest()
})

async function fetchRecords(page: number) {
  const nextPage = normalizePage(page)

  abortRecordsRequest()
  requestedPage.value = nextPage
  recordsStatus.value = 'loading'
  recordsErrorMessage.value = ''

  const controller = new AbortController()
  recordsAbortController.value = controller

  try {
    const response = await getMyGameRecords(nextPage, controller.signal)

    if (recordsAbortController.value !== controller) {
      return
    }

    recordsResponse.value = response
    recordsStatus.value = 'success'
  } catch (error) {
    if (
      (error instanceof DOMException && error.name === 'AbortError') ||
      recordsAbortController.value !== controller
    ) {
      return
    }

    recordsStatus.value = 'error'
    recordsErrorMessage.value =
      error instanceof ApiClientError ? error.message : t('records.errorFallback')
  } finally {
    if (recordsAbortController.value === controller) {
      recordsAbortController.value = undefined
    }
  }
}

function retryRecords() {
  void fetchRecords(requestedPage.value)
}

function goToPage(page: number) {
  if (page === currentPage.value || page < FIRST_PAGE || page > MAX_PAGE) {
    return
  }

  void fetchRecords(page)
}

function goToPreviousPage() {
  if (!canGoPrevious.value) {
    return
  }

  void fetchRecords(currentPage.value - 1)
}

function goToNextPage() {
  if (!canGoNext.value) {
    return
  }

  void fetchRecords(currentPage.value + 1)
}

function abortRecordsRequest() {
  const controller = recordsAbortController.value

  if (controller === undefined) {
    return
  }

  controller.abort()
  recordsAbortController.value = undefined
}

function normalizePage(page: number) {
  if (!Number.isFinite(page)) {
    return FIRST_PAGE
  }

  return Math.min(MAX_PAGE, Math.max(FIRST_PAGE, Math.trunc(page)))
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

function formatLpChange(lpChange: number) {
  return lpChange > 0 ? `+${lpChange}` : String(lpChange)
}

function formatPlayedAt(playedAt: string) {
  const date = new Date(playedAt)

  if (Number.isNaN(date.getTime())) {
    return playedAt
  }

  return new Intl.DateTimeFormat(locale.value === 'ko' ? 'ko-KR' : 'en-US', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function formatRecordCount(totalElements: number) {
  return `${totalElements} ${t('records.countUnit')}`
}

function returnToMatch() {
  void router.push({ name: ROUTE_NAMES.match })
}
</script>

<style scoped>
.game-records-page {
  min-height: 100vh;
  padding: 32px;
  color: #f8fbff;
  background:
    radial-gradient(circle at 72% 24%, rgba(255, 220, 117, 0.18), transparent 28%),
    radial-gradient(circle at 24% 72%, rgba(82, 195, 255, 0.18), transparent 32%), #030610;
}

.records-shell {
  display: grid;
  gap: 22px;
  width: min(980px, 100%);
  margin: 0 auto;
}

.records-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.records-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 900;
  color: #8eeeff;
}

.records-header h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1;
}

.records-return-button,
.records-state button,
.records-pagination button {
  min-height: 42px;
  border: 1px solid rgba(142, 238, 255, 0.42);
  border-radius: 8px;
  color: #f8fbff;
  background: rgba(8, 19, 36, 0.78);
}

.records-return-button {
  padding: 0 18px;
  font-weight: 800;
}

.records-state,
.records-content {
  display: grid;
  gap: 18px;
  padding: 22px;
  border: 1px solid rgba(142, 238, 255, 0.2);
  border-radius: 8px;
  background: rgba(5, 12, 24, 0.72);
  box-shadow: 0 20px 80px rgba(0, 0, 0, 0.32);
}

.records-state {
  min-height: 220px;
  place-content: center;
  text-align: center;
}

.records-state strong {
  font-size: 22px;
}

.records-state span {
  color: rgba(248, 251, 255, 0.74);
}

.records-state button {
  justify-self: center;
  padding: 0 18px;
  font-weight: 800;
}

.records-state--error strong {
  color: #ffb8bf;
}

.records-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: rgba(248, 251, 255, 0.72);
}

.records-summary strong {
  color: #f8fbff;
}

.records-list {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.record-card {
  position: relative;
  display: grid;
  grid-template-columns: 86px minmax(280px, 1fr) minmax(230px, 0.78fr);
  gap: 14px;
  align-items: center;
  min-height: 76px;
  padding: 10px 14px;
  overflow: hidden;
  border: 1px solid rgba(142, 238, 255, 0.16);
  border-left: 4px solid #8eeeff;
  border-radius: 8px;
  background:
    linear-gradient(90deg, rgba(142, 238, 255, 0.12), transparent 34%), rgba(255, 255, 255, 0.055);
}

.record-card[data-record-result='LOSS'] {
  border-left-color: #ff8f9d;
  background:
    linear-gradient(90deg, rgba(255, 143, 157, 0.13), transparent 34%), rgba(255, 255, 255, 0.055);
}

.record-card[data-record-result='DRAW'] {
  border-left-color: #d7deea;
  background:
    linear-gradient(90deg, rgba(215, 222, 234, 0.1), transparent 34%), rgba(255, 255, 255, 0.055);
}

.record-result {
  display: grid;
  min-height: 44px;
  place-items: center;
  border-radius: 8px;
  font-weight: 900;
  color: #07111f;
  background: #8eeeff;
}

.record-result[data-result='LOSS'] {
  background: #ff8f9d;
}

.record-result[data-result='DRAW'] {
  background: #d7deea;
}

.record-matchup,
.record-rank {
  min-width: 0;
}

.record-matchup {
  display: grid;
  grid-template-columns: minmax(76px, 0.58fr) 44px minmax(0, 1fr);
  align-items: center;
}

.record-player {
  display: grid;
  min-width: 0;
  min-height: 48px;
  align-content: center;
  padding: 0 14px;
  border: 1px solid rgba(142, 238, 255, 0.14);
  background: rgba(3, 6, 16, 0.38);
}

.record-player--me {
  border-radius: 8px 0 0 8px;
}

.record-player--opponent {
  border-radius: 0 8px 8px 0;
}

.record-versus {
  display: grid;
  min-height: 48px;
  place-items: center;
  font-size: 12px;
  font-weight: 950;
  color: #8eeeff;
  background:
    linear-gradient(90deg, rgba(142, 238, 255, 0.34), rgba(142, 238, 255, 0.08)),
    rgba(3, 6, 16, 0.54);
}

.record-player strong,
.record-rank span,
.record-rank strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-player span,
.record-rank span {
  color: rgba(248, 251, 255, 0.66);
}

.record-rank {
  display: grid;
  gap: 6px;
  text-align: right;
}

.record-rank em {
  margin-left: 8px;
  font-style: normal;
  color: #ffd86f;
}

.records-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.records-pagination button {
  min-width: 44px;
  padding: 0 14px;
  font-weight: 800;
}

.records-pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.records-pagination .is-current {
  color: #07111f;
  background: #8eeeff;
}

@media (max-width: 700px) {
  .game-records-page {
    padding: 20px;
  }

  .records-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .records-header h1 {
    font-size: 34px;
  }

  .records-state,
  .records-content {
    padding: 16px;
  }

  .record-card {
    grid-template-columns: 76px minmax(0, 1fr);
    gap: 10px;
  }

  .record-result {
    min-height: 64px;
  }

  .record-matchup {
    grid-template-columns: minmax(58px, 0.54fr) 36px minmax(0, 1fr);
  }

  .record-player {
    min-height: 44px;
    padding: 0 10px;
  }

  .record-versus {
    min-height: 44px;
  }

  .record-rank {
    grid-column: 1 / -1;
    text-align: left;
  }
}
</style>
