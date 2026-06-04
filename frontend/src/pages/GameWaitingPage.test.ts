import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ROUTE_NAMES } from '@/constants/routes'
import { saveGameWaitingPayload } from '@/services/gameWaitingPayload'

import GameWaitingPage from './GameWaitingPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    gameRoomId: '100',
  },
}))
const routerReplaceMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => ({
    replace: routerReplaceMock,
  }),
}))

describe('GameWaitingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
  })

  it('reads the stored game waiting payload for the current route gameRoomId', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-room-id')).toBe('100')
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('returns to match when payload is missing', async () => {
    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('false')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when route gameRoomId does not match stored payload', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 101,
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/101',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('false')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })
})
