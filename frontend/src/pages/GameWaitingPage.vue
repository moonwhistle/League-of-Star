<template>
  <main
    :data-game-waiting-payload-ready="gameWaitingPayload !== undefined"
    :data-game-room-id="gameWaitingPayload?.game.gameRoomId ?? ''"
  >
    Game Waiting
  </main>
</template>

<script setup lang="ts">
import { onMounted, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ROUTE_NAMES } from '@/constants/routes'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'

const route = useRoute()
const router = useRouter()
const gameWaitingPayload = shallowRef()

onMounted(() => {
  const routeGameRoomIdParam = route.params.gameRoomId
  const gameRoomId = Array.isArray(routeGameRoomIdParam)
    ? String(routeGameRoomIdParam[0] ?? '').trim()
    : String(routeGameRoomIdParam ?? '').trim()

  if (gameRoomId === '') {
    returnToMatch()
    return
  }

  const payload = readGameWaitingPayload(gameRoomId)

  if (payload === null) {
    returnToMatch()
    return
  }

  gameWaitingPayload.value = payload
})

function returnToMatch() {
  void router.replace({ name: ROUTE_NAMES.match })
}
</script>
