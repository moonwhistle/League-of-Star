<template>
  <!-- eslint-disable vue/html-closing-bracket-newline, vue/html-indent, vue/html-self-closing, vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="custom-rooms-page"
    :style="{ '--custom-room-background-image': `url(${backgroundImageUrl})` }"
    :aria-label="t('customRooms.pageLabel')"
    :data-custom-rooms-status="roomsStatus"
    :data-custom-rooms-error-message="roomsErrorMessage"
    :data-custom-rooms-count="rooms.length"
    :data-custom-room-create-status="createStatus"
    :data-custom-room-create-error-message="createErrorMessage"
    :data-custom-room-invite-status="inviteStatus"
    :data-custom-room-invite-error-message="inviteErrorMessage"
  >
    <section class="custom-rooms-shell">
      <header class="custom-rooms-header">
        <div>
          <h1>{{ t('customRooms.title') }}</h1>
        </div>
        <nav class="custom-rooms-actions" :aria-label="t('customRooms.pageLabel')">
          <button type="button" @click="returnToMatch">{{ t('customRooms.returnToMatch') }}</button>
          <button type="button" :disabled="roomsStatus === 'loading'" @click="loadRooms">
            {{ t('customRooms.refresh') }}
          </button>
        </nav>
      </header>

      <section class="custom-rooms-toolbar" :aria-label="t('customRooms.toolbarLabel')">
        <button
          class="custom-rooms-create"
          type="button"
          data-testid="custom-room-create-button"
          :disabled="createStatus === 'loading'"
          @click="createRoom"
        >
          {{
            createStatus === 'loading' ? t('customRooms.creatingRoom') : t('customRooms.createRoom')
          }}
        </button>

        <form class="custom-rooms-invite" @submit.prevent="findInviteRoom">
          <label for="custom-room-invite-code">{{ t('customRooms.inviteCode') }}</label>
          <input
            id="custom-room-invite-code"
            v-model="inviteCodeInput"
            data-testid="custom-room-invite-input"
            type="text"
            :placeholder="t('customRooms.invitePlaceholder')"
            autocomplete="off"
          />
          <button
            type="submit"
            data-testid="custom-room-invite-submit"
            :disabled="inviteStatus === 'loading'"
          >
            {{
              inviteStatus === 'loading'
                ? t('customRooms.findingInvite')
                : t('customRooms.findInvite')
            }}
          </button>
        </form>
      </section>

      <p v-if="createErrorMessage !== ''" class="custom-rooms-alert" role="alert">
        {{ createErrorMessage }}
      </p>
      <p v-if="inviteErrorMessage !== ''" class="custom-rooms-alert" role="alert">
        {{ inviteErrorMessage }}
      </p>

      <section class="custom-rooms-panel" :aria-label="t('customRooms.listTitle')">
        <header class="custom-rooms-panel-heading">
          <h2>{{ t('customRooms.listTitle') }}</h2>
          <span>{{ rooms.length }}</span>
        </header>

        <div v-if="roomsStatus === 'loading'" class="custom-rooms-state">
          {{ t('customRooms.loading') }}
        </div>
        <div
          v-else-if="roomsStatus === 'error'"
          class="custom-rooms-state custom-rooms-state--error"
        >
          {{ roomsErrorMessage }}
        </div>
        <div v-else-if="rooms.length === 0" class="custom-rooms-state">
          {{ t('customRooms.empty') }}
        </div>
        <ol v-else class="custom-rooms-list">
          <li v-for="room in rooms" :key="room.roomId">
            <div class="custom-room-row-copy">
              <strong>{{ room.roomName }}</strong>
              <span>{{ t('customRooms.owner') }} #{{ room.ownerUserId }}</span>
            </div>
            <div class="custom-room-row-meta">
              <span
                >{{ t('customRooms.currentPlayers') }} {{ room.currentParticipants }}/{{
                  room.maxParticipants
                }}</span
              >
              <span>{{ t('customRooms.status') }} {{ room.status }}</span>
            </div>
            <button type="button" @click="openRoom(room.roomId)">
              {{ t('customRooms.openRoom') }}
            </button>
          </li>
        </ol>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import {
  createCustomRoom,
  getCustomRoomInvitePreview,
  getCustomRooms,
} from '@/services/customRoomService'
import type { CustomRoomListItem } from '@/types/customRoom'

import backgroundImageUrl from '../../img/background-new-sharp.png'

type LoadStatus = 'idle' | 'loading' | 'success' | 'error'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const roomsStatus = ref<LoadStatus>('idle')
const createStatus = ref<LoadStatus>('idle')
const inviteStatus = ref<LoadStatus>('idle')
const roomsErrorMessage = ref('')
const createErrorMessage = ref('')
const inviteErrorMessage = ref('')
const rooms = shallowRef<CustomRoomListItem[]>([])
const inviteCodeInput = ref('')
const normalizedInviteCode = computed(() => inviteCodeInput.value.trim().toUpperCase())
const routeInviteCode = computed(() => normalizeInviteCode(route.params.inviteCode))
const roomsAbortController = shallowRef<AbortController>()
const createAbortController = shallowRef<AbortController>()
const inviteAbortController = shallowRef<AbortController>()

onMounted(() => {
  void loadRooms()
})

watch(
  routeInviteCode,
  (inviteCode) => {
    if (inviteCode === '') {
      return
    }

    void findInviteRoomByCode(inviteCode)
  },
  { immediate: true },
)

onUnmounted(() => {
  abortRoomsRequest()
  abortCreateRequest()
  abortInviteRequest()
})

async function loadRooms() {
  abortRoomsRequest()
  const controller = new AbortController()
  roomsAbortController.value = controller
  roomsStatus.value = 'loading'
  roomsErrorMessage.value = ''

  try {
    const response = await getCustomRooms(controller.signal)

    if (roomsAbortController.value !== controller) {
      return
    }

    rooms.value = response.rooms
    roomsStatus.value = 'success'
  } catch (error) {
    if (controller.signal.aborted) {
      return
    }

    rooms.value = []
    roomsStatus.value = 'error'
    roomsErrorMessage.value = errorMessage(error, t('customRooms.errorFallback'))
  }
}

async function createRoom() {
  abortCreateRequest()
  const controller = new AbortController()
  createAbortController.value = controller
  createStatus.value = 'loading'
  createErrorMessage.value = ''

  try {
    const response = await createCustomRoom(controller.signal)

    if (createAbortController.value !== controller) {
      return
    }

    createStatus.value = 'success'
    await openRoom(response.roomId)
  } catch (error) {
    if (controller.signal.aborted) {
      return
    }

    createStatus.value = 'error'
    createErrorMessage.value = errorMessage(error, t('customRooms.createFailed'))
  }
}

async function findInviteRoom() {
  inviteErrorMessage.value = ''

  if (normalizedInviteCode.value === '') {
    inviteStatus.value = 'error'
    inviteErrorMessage.value = t('customRooms.inviteRequired')
    return
  }

  await findInviteRoomByCode(normalizedInviteCode.value)
}

async function findInviteRoomByCode(inviteCode: string) {
  inviteCodeInput.value = inviteCode
  inviteErrorMessage.value = ''
  abortInviteRequest()
  const controller = new AbortController()
  inviteAbortController.value = controller
  inviteStatus.value = 'loading'

  try {
    const response = await getCustomRoomInvitePreview(inviteCode, controller.signal)

    if (inviteAbortController.value !== controller) {
      return
    }

    inviteStatus.value = 'success'
    await openRoom(response.roomId)
  } catch (error) {
    if (controller.signal.aborted) {
      return
    }

    inviteStatus.value = 'error'
    inviteErrorMessage.value = errorMessage(error, t('customRooms.inviteFailed'))
  }
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

function returnToMatch() {
  void router.push({ name: ROUTE_NAMES.match })
}

function openRoom(roomId: number | string) {
  return router.push({
    name: ROUTE_NAMES.customRoom,
    params: {
      roomId: String(roomId),
    },
  })
}

function abortRoomsRequest() {
  roomsAbortController.value?.abort()
}

function abortCreateRequest() {
  createAbortController.value?.abort()
}

function abortInviteRequest() {
  inviteAbortController.value?.abort()
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
.custom-rooms-page {
  min-height: 100vh;
  padding: 32px;
  color: #f8fbff;
  background:
    linear-gradient(90deg, rgba(3, 7, 18, 0.78), rgba(5, 10, 25, 0.42)),
    var(--custom-room-background-image) center / cover no-repeat;
}

.custom-rooms-shell {
  width: min(1120px, 100%);
  margin: 0 auto;
}

.custom-rooms-header,
.custom-rooms-toolbar,
.custom-rooms-panel {
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  background: rgba(5, 10, 22, 0.72);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(16px);
}

.custom-rooms-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.custom-rooms-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0;
}

.custom-rooms-actions,
.custom-rooms-toolbar,
.custom-rooms-invite,
.custom-room-row-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.custom-rooms-actions button,
.custom-rooms-toolbar button,
.custom-rooms-list button {
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

.custom-rooms-actions button:hover:not(:disabled),
.custom-rooms-toolbar button:hover:not(:disabled),
.custom-rooms-list button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.8);
  background: rgba(14, 165, 233, 0.24);
}

.custom-rooms-actions button:disabled,
.custom-rooms-toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.custom-rooms-toolbar {
  justify-content: space-between;
  margin-top: 18px;
  padding: 16px;
}

.custom-rooms-create {
  font-weight: 800;
}

.custom-rooms-invite label {
  font-size: 13px;
  color: rgba(248, 251, 255, 0.72);
}

.custom-rooms-invite input {
  width: min(260px, 42vw);
  min-height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  padding: 0 12px;
  color: #f8fbff;
  background: rgba(0, 0, 0, 0.26);
  font: inherit;
  text-transform: uppercase;
}

.custom-rooms-alert {
  margin: 12px 0 0;
  border: 1px solid rgba(248, 113, 113, 0.45);
  border-radius: 8px;
  padding: 10px 12px;
  color: #fecaca;
  background: rgba(127, 29, 29, 0.34);
}

.custom-rooms-panel {
  margin-top: 18px;
  padding: 18px;
}

.custom-rooms-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.custom-rooms-panel-heading h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.custom-rooms-panel-heading span {
  color: #93c5fd;
  font-weight: 800;
}

.custom-rooms-state {
  min-height: 160px;
  display: grid;
  place-items: center;
  color: rgba(248, 251, 255, 0.72);
}

.custom-rooms-state--error {
  color: #fecaca;
}

.custom-rooms-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.custom-rooms-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.06);
}

.custom-room-row-copy {
  min-width: 0;
}

.custom-room-row-copy strong,
.custom-room-row-copy span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-room-row-copy span,
.custom-room-row-meta {
  color: rgba(248, 251, 255, 0.68);
  font-size: 13px;
}

@media (max-width: 720px) {
  .custom-rooms-page {
    padding: 18px;
  }

  .custom-rooms-header,
  .custom-rooms-toolbar,
  .custom-rooms-invite,
  .custom-room-row-meta {
    align-items: stretch;
    flex-direction: column;
  }

  .custom-rooms-actions {
    justify-content: stretch;
  }

  .custom-rooms-actions button,
  .custom-rooms-toolbar button,
  .custom-rooms-invite input {
    width: 100%;
  }

  .custom-rooms-list li {
    grid-template-columns: 1fr;
  }
}
</style>
