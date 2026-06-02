<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    :data-stream-status="streamStatus"
    :data-connected-user-id="connectedEvent?.userId ?? ''"
    :data-last-heartbeat-at="lastHeartbeatAt"
    :data-match-found-id="matchFound?.matchId ?? ''"
    :data-match-result-action="matchResponseResult?.action ?? ''"
    :data-stream-error-message="streamErrorMessage"
    :data-can-start-match="canStartMatch"
    :data-queue-status="queueStatus"
    :data-queue-error-message="queueErrorMessage"
  >
    Match
    <button type="button" :disabled="!canStartMatch">Start Matching</button>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'

import { connectMatchEventSource } from '@/services/realtime/matchEventSource'

const streamStatus = ref('connecting')
const lastHeartbeatAt = ref('')
const connectedEvent = shallowRef()
const matchFound = shallowRef()
const matchResponseResult = shallowRef()
const streamErrorMessage = ref('')
const queueStatus = ref('ready')
const queueErrorMessage = ref('')
const canStartMatch = computed(
  () => streamStatus.value === 'connected' && queueStatus.value === 'ready',
)

const MATCH_STREAM_ERROR_MESSAGE = 'Match event stream is currently unavailable.'

let closeMatchEventSource = () => {}
// Guards against late stream callbacks that arrive after route unmount.
let isActive = false

onMounted(() => {
  isActive = true
  streamStatus.value = 'connecting'
  streamErrorMessage.value = ''

  try {
    const connection = connectMatchEventSource({
      onOpen: () => {
        if (isActive) {
          streamErrorMessage.value = ''
        }
      },
      onConnected: (payload) => {
        if (isActive) {
          connectedEvent.value = payload
          streamStatus.value = 'connected'
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
        }
      },
      onMatchResponseResult: (payload) => {
        if (isActive) {
          matchResponseResult.value = payload
        }
      },
      onError: () => {
        if (isActive) {
          setStreamError()
        }
      },
    })
    closeMatchEventSource = connection.close
  } catch {
    setStreamError()
  }
})

onUnmounted(() => {
  isActive = false
  closeMatchEventSource()
  closeMatchEventSource = () => {}
})

function setStreamError() {
  streamStatus.value = 'error'
  streamErrorMessage.value = MATCH_STREAM_ERROR_MESSAGE
}
</script>
