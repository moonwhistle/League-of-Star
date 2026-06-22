<template>
  <!-- eslint-disable vue/max-attributes-per-line -->
  <main
    class="profile-page"
    aria-label="My profile"
    :data-profile-status="profileStatus"
    :data-profile-error-message="profileErrorMessage"
    :data-rank-status="rankStatus"
    :data-rank-error-message="rankErrorMessage"
    :data-records-status="recordsStatus"
    :data-records-error-message="recordsErrorMessage"
    :data-records-count="recordsResponse?.records.length ?? 0"
  >
    <h1>내 정보</h1>

    <section aria-label="Account profile">
      <strong v-if="profileStatus === 'loading'">프로필 불러오는 중</strong>
      <strong v-else-if="profileStatus === 'error'">{{ profileErrorMessage }}</strong>
      <template v-else-if="profile !== undefined">
        <strong>{{ profile.nickname }}</strong>
        <span>{{ profile.email }}</span>
        <span>{{ profile.createdAt }}</span>
      </template>
    </section>

    <section aria-label="Current rank">
      <strong v-if="rankStatus === 'loading'">랭크 불러오는 중</strong>
      <strong v-else-if="rankStatus === 'error'">{{ rankErrorMessage }}</strong>
      <template v-else-if="rank !== undefined">
        <strong>{{ rank.rank }}</strong>
        <span>{{ rank.lp }} LP</span>
        <span>{{ rank.wins }}승 {{ rank.losses }}패 {{ rank.draws }}무</span>
        <span>{{ rank.rankUpdatedAt }}</span>
      </template>
    </section>

    <section aria-label="Recent records">
      <strong v-if="recordsStatus === 'loading'">최근 전적 불러오는 중</strong>
      <strong v-else-if="recordsStatus === 'error'">{{ recordsErrorMessage }}</strong>
      <strong v-else-if="recordsResponse?.records.length === 0">최근 전적 없음</strong>
      <ol v-else-if="recordsResponse !== undefined">
        <li v-for="record in recordsResponse.records" :key="record.gameId">
          {{ record.result }} {{ record.opponentNickname }} {{ record.rankAfter }}
        </li>
      </ol>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef } from 'vue'

import { ApiClientError } from '@/services/apiClient'
import { getMyGameRecords } from '@/services/gameRecordService'
import { getMyProfile } from '@/services/profileService'
import { getMyRank } from '@/services/rankService'
import type { GameRecordListResponse } from '@/types/gameRecord'
import type { UserProfileResponse, UserRankResponse } from '@/types/user'

const PROFILE_RECORDS_PAGE = 1

type LoadStatus = 'idle' | 'loading' | 'success' | 'error'

const profileStatus = ref<LoadStatus>('idle')
const rankStatus = ref<LoadStatus>('idle')
const recordsStatus = ref<LoadStatus>('idle')
const profileErrorMessage = ref('')
const rankErrorMessage = ref('')
const recordsErrorMessage = ref('')
const profile = shallowRef<UserProfileResponse>()
const rank = shallowRef<UserRankResponse>()
const recordsResponse = shallowRef<GameRecordListResponse>()
const profileAbortController = shallowRef<AbortController>()
const rankAbortController = shallowRef<AbortController>()
const recordsAbortController = shallowRef<AbortController>()

onMounted(() => {
  void loadProfile()
  void loadRank()
  void loadRecords()
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

async function loadRecords() {
  abortRecordsRequest()
  recordsStatus.value = 'loading'
  recordsErrorMessage.value = ''

  const controller = new AbortController()
  recordsAbortController.value = controller

  try {
    const response = await getMyGameRecords(PROFILE_RECORDS_PAGE, controller.signal)

    if (recordsAbortController.value !== controller) {
      return
    }

    recordsResponse.value = response
    recordsStatus.value = 'success'
  } catch (error) {
    if (shouldIgnoreRequestError(error, recordsAbortController.value, controller)) {
      return
    }

    recordsResponse.value = undefined
    recordsStatus.value = 'error'
    recordsErrorMessage.value = getRequestErrorMessage(error, '최근 전적을 불러올 수 없습니다.')
  } finally {
    if (recordsAbortController.value === controller) {
      recordsAbortController.value = undefined
    }
  }
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
</script>
