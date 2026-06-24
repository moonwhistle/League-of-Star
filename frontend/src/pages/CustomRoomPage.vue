<template>
  <!-- eslint-disable vue/html-closing-bracket-newline, vue/html-indent, vue/html-self-closing, vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="custom-room-page"
    :style="{ '--custom-room-background-image': `url(${backgroundImageUrl})` }"
    :aria-label="t('customRoom.pageLabel')"
    :data-custom-room-status="roomStatus"
    :data-custom-room-error-message="roomErrorMessage"
    :data-custom-room-id="room?.roomId ?? ''"
    :data-custom-room-participant-count="room?.participants.length ?? 0"
    :data-custom-room-copy-status="copyStatus"
    :data-custom-room-copy-message="copyMessage"
    :data-custom-room-socket-status="socketStatus"
    :data-custom-room-socket-error-message="socketErrorMessage"
    :data-custom-room-leave-status="leaveStatus"
    :data-custom-room-leave-error-message="leaveErrorMessage"
    data-testid="custom-room-page"
  >
    <section class="custom-room-shell">
      <header class="custom-room-header">
        <div>
          <span>{{ t('customRoom.title') }}</span>
          <h1>{{ room?.roomName ?? t('customRoom.title') }}</h1>
        </div>
        <nav class="custom-room-actions" :aria-label="t('customRoom.pageLabel')">
          <button type="button" @click="returnToRooms">{{ t('customRoom.returnToRooms') }}</button>
          <button type="button" :disabled="roomStatus === 'loading'" @click="loadRoom">
            {{ t('customRoom.refresh') }}
          </button>
          <button
            v-if="roomStatus === 'success'"
            type="button"
            data-testid="custom-room-leave-button"
            :disabled="leaveStatus === 'loading'"
            @click="leaveRoom"
          >
            {{ leaveStatus === 'loading' ? t('customRoom.leaving') : t('customRoom.leave') }}
          </button>
        </nav>
      </header>

      <section v-if="roomStatus === 'loading'" class="custom-room-state">
        {{ t('customRoom.loading') }}
      </section>
      <section
        v-else-if="roomStatus === 'closed'"
        class="custom-room-state custom-room-state--closed"
      >
        <div>
          <strong>{{ t('customRoom.closedTitle') }}</strong>
          <p>{{ t('customRoom.closedDescription') }}</p>
          <button type="button" @click="returnToMatch">{{ t('customRoom.returnToMatch') }}</button>
        </div>
      </section>
      <section
        v-else-if="roomStatus === 'error'"
        class="custom-room-state custom-room-state--error"
      >
        {{ roomErrorMessage }}
      </section>
      <template v-else-if="room !== undefined">
        <section class="custom-room-grid">
          <section class="custom-room-panel" :aria-label="t('customRoom.roomInfo')">
            <div class="custom-room-panel-heading">
              <span>{{ t('customRoom.roomInfo') }}</span>
            </div>

            <dl class="custom-room-info-list">
              <div>
                <dt>{{ t('customRoom.currentPlayers') }}</dt>
                <dd>{{ room.participants.length }}/{{ room.maxParticipants }}</dd>
              </div>
              <div>
                <dt>{{ t('customRoom.inviteCode') }}</dt>
                <dd>{{ room.inviteCode }}</dd>
              </div>
            </dl>
          </section>

          <section
            class="custom-room-panel custom-room-invite-panel"
            :aria-label="t('customRoom.inviteLink')"
          >
            <div class="custom-room-panel-heading">
              <span>{{ t('customRoom.inviteLink') }}</span>
            </div>
            <div class="custom-room-invite-copy">
              <p class="custom-room-invite-link" data-testid="custom-room-invite-link">
                {{ inviteLink }}
              </p>
              <button type="button" data-testid="custom-room-copy-button" @click="copyInviteLink">
                {{ t('customRoom.copyInvite') }}
              </button>
            </div>
            <p v-if="copyMessage !== ''" class="custom-room-copy-message" role="status">
              {{ copyMessage }}
            </p>
            <p v-if="socketErrorMessage !== ''" class="custom-room-alert" role="alert">
              {{ socketErrorMessage }}
            </p>
            <p v-if="leaveErrorMessage !== ''" class="custom-room-alert" role="alert">
              {{ leaveErrorMessage }}
            </p>
          </section>
        </section>

        <section
          class="custom-room-panel custom-room-participants"
          :aria-label="t('customRoom.participants')"
        >
          <div class="custom-room-panel-heading">
            <span>{{ t('customRoom.participants') }}</span>
            <strong>{{ room.participants.length }}/{{ room.maxParticipants }}</strong>
          </div>
          <ol>
            <li v-for="participant in room.participants" :key="participant.userId">
              <span>{{ participant.nickname }}</span>
              <strong>{{
                participant.role === 'OWNER' ? t('customRoom.owner') : t('customRoom.player')
              }}</strong>
            </li>
          </ol>
        </section>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { getCustomRoom, leaveCustomRoom } from '@/services/customRoomService'
import {
  connectCustomRoomWebSocket,
  type CustomRoomWebSocketConnection,
} from '@/services/realtime/customRoomWebSocket'
import type { CustomRoomResponse, CustomRoomWebSocketServerMessage } from '@/types/customRoom'

import backgroundImageUrl from '../../img/background-new-sharp.png'

type LoadStatus = 'idle' | 'loading' | 'success' | 'error' | 'closed'
type CopyStatus = 'idle' | 'success' | 'error'
type LeaveStatus = 'idle' | 'loading' | 'success' | 'error'
type SocketStatus = 'idle' | 'connecting' | 'open' | 'error' | 'closed'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const roomStatus = ref<LoadStatus>('idle')
const copyStatus = ref<CopyStatus>('idle')
const leaveStatus = ref<LeaveStatus>('idle')
const socketStatus = ref<SocketStatus>('idle')
const roomErrorMessage = ref('')
const copyMessage = ref('')
const leaveErrorMessage = ref('')
const socketErrorMessage = ref('')
const room = shallowRef<CustomRoomResponse>()
const roomAbortController = shallowRef<AbortController>()
const leaveAbortController = shallowRef<AbortController>()
const roomSocketConnection = shallowRef<CustomRoomWebSocketConnection>()
const routeRoomId = computed(() => String(route.params.roomId ?? '').trim())
const inviteLink = computed(() => {
  const inviteCode = room.value?.inviteCode.trim() ?? ''

  if (inviteCode === '') {
    return ''
  }

  return `${window.location.origin}/custom-games/join/${encodeURIComponent(inviteCode)}`
})

watch(
  routeRoomId,
  () => {
    void loadRoom()
  },
  { immediate: true },
)

onUnmounted(() => {
  abortRoomRequest()
  abortLeaveRequest()
  closeRoomSocket()
})

async function loadRoom() {
  copyStatus.value = 'idle'
  copyMessage.value = ''
  leaveErrorMessage.value = ''
  socketErrorMessage.value = ''
  roomErrorMessage.value = ''

  if (routeRoomId.value === '') {
    room.value = undefined
    roomStatus.value = 'error'
    roomErrorMessage.value = t('customRoom.roomIdMissing')
    closeRoomSocket(false)
    return
  }

  abortRoomRequest()
  closeRoomSocket()
  const controller = new AbortController()
  roomAbortController.value = controller
  roomStatus.value = 'loading'

  try {
    const response = await getCustomRoom(routeRoomId.value, controller.signal)

    if (roomAbortController.value !== controller) {
      return
    }

    room.value = response
    roomStatus.value = 'success'
    connectRoomSocket(response.roomId)
  } catch (error) {
    if (controller.signal.aborted) {
      return
    }

    room.value = undefined
    roomStatus.value = 'error'
    roomErrorMessage.value = errorMessage(error, t('customRoom.errorFallback'))
    closeRoomSocket()
  }
}

async function leaveRoom() {
  if (room.value === undefined || leaveStatus.value === 'loading') {
    return
  }

  abortLeaveRequest()
  const controller = new AbortController()
  leaveAbortController.value = controller
  leaveStatus.value = 'loading'
  leaveErrorMessage.value = ''

  try {
    await leaveCustomRoom(room.value.roomId, controller.signal)

    if (leaveAbortController.value !== controller) {
      return
    }

    leaveStatus.value = 'success'
    closeRoomSocket()
    await router.push({ name: ROUTE_NAMES.customRooms })
  } catch (error) {
    if (controller.signal.aborted) {
      return
    }

    leaveStatus.value = 'error'
    leaveErrorMessage.value = errorMessage(error, t('customRoom.leaveFailed'))
  }
}

async function copyInviteLink() {
  copyStatus.value = 'idle'
  copyMessage.value = ''

  try {
    if (inviteLink.value === '' || navigator.clipboard === undefined) {
      throw new Error(t('customRoom.copyFailed'))
    }

    await navigator.clipboard.writeText(inviteLink.value)
    copyStatus.value = 'success'
    copyMessage.value = t('customRoom.copySuccess')
  } catch {
    copyStatus.value = 'error'
    copyMessage.value = t('customRoom.copyFailed')
  }
}

function returnToRooms() {
  void router.push({ name: ROUTE_NAMES.customRooms })
}

function returnToMatch() {
  void router.push({ name: ROUTE_NAMES.match })
}

function abortRoomRequest() {
  roomAbortController.value?.abort()
}

function abortLeaveRequest() {
  leaveAbortController.value?.abort()
}

function connectRoomSocket(roomId: number) {
  closeRoomSocket()
  socketStatus.value = 'connecting'
  socketErrorMessage.value = ''

  try {
    let connection: CustomRoomWebSocketConnection | undefined
    connection = connectCustomRoomWebSocket(roomId, {
      onOpen: () => {
        if (connection === undefined || roomSocketConnection.value?.socket !== connection.socket) {
          return
        }

        socketStatus.value = 'open'
      },
      onMessage: (message) => {
        if (connection === undefined || roomSocketConnection.value?.socket !== connection.socket) {
          return
        }

        handleRoomSocketMessage(message)
      },
      onError: (error) => {
        if (connection === undefined || roomSocketConnection.value?.socket !== connection.socket) {
          return
        }

        socketStatus.value = 'error'
        socketErrorMessage.value = socketErrorMessageFrom(error)
      },
      onClose: (event) => {
        if (roomSocketConnection.value?.socket !== event.target) {
          return
        }

        socketStatus.value = 'closed'
      },
    })

    roomSocketConnection.value = connection
  } catch (error) {
    socketStatus.value = 'error'
    socketErrorMessage.value = socketErrorMessageFrom(error)
  }
}

function handleRoomSocketMessage(message: CustomRoomWebSocketServerMessage) {
  if (message.type === 'ROOM_UPDATED') {
    room.value = message.payload
    roomStatus.value = 'success'
    socketErrorMessage.value = ''
    return
  }

  if (message.type === 'ROOM_CLOSED') {
    room.value = message.payload
    roomStatus.value = 'closed'
    socketStatus.value = 'closed'
    socketErrorMessage.value = ''
    closeRoomSocket()
    return
  }

  socketStatus.value = 'error'
  socketErrorMessage.value = message.payload.reason || t('customRoom.socketError')
}

function closeRoomSocket(resetStatus = true) {
  const connection = roomSocketConnection.value
  roomSocketConnection.value = undefined
  if (resetStatus) {
    socketStatus.value = 'idle'
  }
  connection?.close(1000, 'custom room page closed')
}

function socketErrorMessageFrom(error: unknown) {
  if (error instanceof Error && error.message.trim() !== '') {
    return error.message
  }

  return t('customRoom.socketError')
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
.custom-room-page {
  min-height: 100vh;
  padding: 32px;
  color: #f8fbff;
  background:
    linear-gradient(90deg, rgba(3, 7, 18, 0.78), rgba(5, 10, 25, 0.42)),
    var(--custom-room-background-image) center / cover no-repeat;
}

.custom-room-shell {
  width: min(1040px, 100%);
  margin: 0 auto;
}

.custom-room-header,
.custom-room-panel,
.custom-room-state {
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  background: rgba(5, 10, 22, 0.72);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(16px);
}

.custom-room-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.custom-room-header span,
.custom-room-panel-heading span {
  color: rgba(248, 251, 255, 0.68);
  font-size: 13px;
  font-weight: 700;
}

.custom-room-header h1 {
  overflow: hidden;
  margin: 4px 0 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-room-actions,
.custom-room-grid,
.custom-room-invite-copy {
  display: flex;
  align-items: center;
  gap: 10px;
}

.custom-room-actions button,
.custom-room-invite-copy button {
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

.custom-room-actions button:hover:not(:disabled),
.custom-room-invite-copy button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(125, 211, 252, 0.8);
  background: rgba(14, 165, 233, 0.24);
}

.custom-room-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.custom-room-state {
  min-height: 220px;
  display: grid;
  margin-top: 18px;
  place-items: center;
  color: rgba(248, 251, 255, 0.72);
}

.custom-room-state--error {
  color: #fecaca;
}

.custom-room-state--closed {
  text-align: center;
}

.custom-room-state--closed strong {
  display: block;
  margin-bottom: 8px;
  color: #f8fbff;
  font-size: 20px;
}

.custom-room-state--closed p {
  margin: 0 0 16px;
}

.custom-room-state--closed button {
  min-height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  padding: 0 14px;
  color: #f8fbff;
  background: rgba(255, 255, 255, 0.08);
  font: inherit;
  cursor: pointer;
}

.custom-room-grid {
  align-items: stretch;
  margin-top: 18px;
}

.custom-room-panel {
  flex: 1;
  min-width: 0;
  padding: 18px;
}

.custom-room-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.custom-room-panel-heading strong {
  color: #93c5fd;
}

.custom-room-info-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.custom-room-info-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 10px;
}

.custom-room-info-list dt {
  color: rgba(248, 251, 255, 0.68);
}

.custom-room-info-list dd {
  overflow: hidden;
  margin: 0;
  font-weight: 800;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-room-invite-link {
  min-width: 0;
  flex: 1;
  min-height: 40px;
  margin: 0;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  padding: 10px 12px;
  color: #f8fbff;
  background: rgba(0, 0, 0, 0.26);
  font: inherit;
  overflow-wrap: anywhere;
}

.custom-room-copy-message {
  margin: 12px 0 0;
  color: rgba(248, 251, 255, 0.72);
}

.custom-room-alert {
  margin: 12px 0 0;
  color: #fecaca;
}

.custom-room-participants {
  margin-top: 18px;
}

.custom-room-participants ol {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.custom-room-participants li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.06);
}

.custom-room-participants li span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-room-participants li strong {
  color: #bfdbfe;
}

@media (max-width: 720px) {
  .custom-room-page {
    padding: 18px;
  }

  .custom-room-header,
  .custom-room-actions,
  .custom-room-grid,
  .custom-room-invite-copy {
    align-items: stretch;
    flex-direction: column;
  }

  .custom-room-actions button,
  .custom-room-invite-copy button,
  .custom-room-invite-link {
    width: 100%;
  }
}
</style>
