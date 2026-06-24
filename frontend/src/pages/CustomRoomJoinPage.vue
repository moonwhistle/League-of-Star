<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="custom-room-join-page"
    :style="{ '--custom-room-background-image': `url(${backgroundImageUrl})` }"
    :aria-label="t('customRoomJoin.pageLabel')"
    :data-custom-room-join-status="joinStatus"
    :data-custom-room-join-error-message="joinErrorMessage"
    data-testid="custom-room-join-page"
  >
    <section class="custom-room-join-card">
      <span>{{ t('customRoomJoin.title') }}</span>
      <h1>{{ t('customRoomJoin.heading') }}</h1>
      <p v-if="joinStatus === 'loading'">{{ t('customRoomJoin.loading') }}</p>
      <p v-else-if="joinStatus === 'error'" class="custom-room-join-error" role="alert">
        {{ joinErrorMessage }}
      </p>
      <button type="button" @click="returnToRooms">{{ t('customRoomJoin.returnToRooms') }}</button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { joinCustomRoom } from '@/services/customRoomService'

import backgroundImageUrl from '../../img/background-new-sharp.png'

type JoinStatus = 'idle' | 'loading' | 'success' | 'error'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const joinStatus = ref<JoinStatus>('idle')
const joinErrorMessage = ref('')
const joinAbortController = shallowRef<AbortController>()
const routeInviteCode = computed(() => normalizeInviteCode(route.params.inviteCode))

watch(
  routeInviteCode,
  (inviteCode) => {
    void joinRoom(inviteCode)
  },
  { immediate: true },
)

onUnmounted(() => {
  abortJoinRequest()
})

async function joinRoom(inviteCode: string) {
  joinErrorMessage.value = ''

  if (inviteCode === '') {
    joinStatus.value = 'error'
    joinErrorMessage.value = t('customRoomJoin.inviteCodeMissing')
    return
  }

  abortJoinRequest()
  const controller = new AbortController()
  joinAbortController.value = controller
  joinStatus.value = 'loading'

  try {
    const response = await joinCustomRoom(inviteCode, controller.signal)

    if (joinAbortController.value !== controller) {
      return
    }

    joinStatus.value = 'success'
    await router.push({
      name: ROUTE_NAMES.customRoom,
      params: {
        roomId: String(response.roomId),
      },
    })
  } catch (error) {
    if (controller.signal.aborted) {
      return
    }

    joinStatus.value = 'error'
    joinErrorMessage.value = errorMessage(error, t('customRoomJoin.errorFallback'))
  }
}

function returnToRooms() {
  void router.push({ name: ROUTE_NAMES.customRooms })
}

function abortJoinRequest() {
  joinAbortController.value?.abort()
}

function normalizeInviteCode(value: unknown) {
  if (Array.isArray(value)) {
    return String(value[0] ?? '')
      .trim()
      .toUpperCase()
  }

  return String(value ?? '')
    .trim()
    .toUpperCase()
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && typeof error.message === 'string') {
    return error.message
  }

  if (error instanceof Error && error.message.trim() !== '') {
    return error.message
  }

  return fallback
}
</script>

<style scoped>
.custom-room-join-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  color: #f8fbff;
  background:
    linear-gradient(90deg, rgba(3, 7, 18, 0.8), rgba(5, 10, 25, 0.42)),
    var(--custom-room-background-image) center / cover no-repeat;
}

.custom-room-join-card {
  width: min(520px, 100%);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  padding: 24px;
  background: rgba(5, 10, 22, 0.74);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(16px);
}

.custom-room-join-card span {
  color: rgba(248, 251, 255, 0.68);
  font-size: 13px;
  font-weight: 800;
}

.custom-room-join-card h1 {
  margin: 6px 0 12px;
  font-size: 28px;
  letter-spacing: 0;
}

.custom-room-join-card p {
  margin: 0 0 18px;
  color: rgba(248, 251, 255, 0.76);
}

.custom-room-join-error {
  color: #fecaca;
}

.custom-room-join-card button {
  min-height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  padding: 0 14px;
  color: #f8fbff;
  background: rgba(255, 255, 255, 0.08);
  font: inherit;
  cursor: pointer;
  transition:
    transform 140ms ease,
    border-color 140ms ease,
    background 140ms ease;
}

.custom-room-join-card button:hover {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.8);
  background: rgba(14, 165, 233, 0.24);
}
</style>
