<template>
  <main
    :data-stream-status="streamStatus"
    :data-connected-user-id="connectedEvent?.userId ?? ''"
    :data-last-heartbeat-at="lastHeartbeatAt"
    :data-match-found-id="matchFound?.matchId ?? ''"
    :data-match-result-action="matchResponseResult?.action ?? ''"
  >
    Match
  </main>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef } from 'vue'

import { connectMatchEventSource } from '@/services/realtime/matchEventSource'

const streamStatus = ref('idle')
const connectedEvent = shallowRef()
const lastHeartbeatAt = ref('')
const matchFound = shallowRef()
const matchResponseResult = shallowRef()

let closeMatchEventSource = () => {}
let isActive = false

onMounted(() => {
  isActive = true
  streamStatus.value = 'connecting'

  try {
    const connection = connectMatchEventSource({
      onOpen: () => {
        if (isActive) {
          streamStatus.value = 'open'
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
          streamStatus.value = 'error'
        }
      },
    })
    closeMatchEventSource = connection.close
  } catch {
    streamStatus.value = 'error'
  }
})

onUnmounted(() => {
  isActive = false
  closeMatchEventSource()
  closeMatchEventSource = () => {}
})
</script>
