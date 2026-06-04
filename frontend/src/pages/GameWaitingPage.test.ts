import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { saveGameWaitingPayload } from '@/services/gameWaitingPayload'

import GameWaitingPage from './GameWaitingPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    gameRoomId: '100',
  },
}))
const routerReplaceMock = vi.hoisted(() => vi.fn())
const { setLocale } = useLocale()

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
    setLocale('ko')
  })

  it('reads the stored game waiting payload for the current route gameRoomId', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: {
        userId: 2,
        nickname: 'Voidwalker',
        tier: 'Gold IV',
        tierScore: 13,
      },
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
    expect(wrapper.get('main').attributes('data-loading-progress')).toBe('100')
    expect(wrapper.get('h1').text()).toBe('LEAGUE OF SMITE')
    expect(wrapper.text()).toContain('게임 준비 중')
    expect(wrapper.text()).toContain('전투 데이터를 동기화하는 중')
    expect(wrapper.text()).toContain('Voidwalker')
    expect(wrapper.text()).toContain('Gold IV')
    expect(wrapper.text()).toContain('매칭 정보')
    expect(wrapper.text()).toContain('게임 준비')
    expect(wrapper.text()).toContain('수신 완료')
    expect(wrapper.text()).not.toContain('준비 완료')
    expect(wrapper.text()).not.toContain('#100')
    expect(wrapper.text()).not.toContain('match-1')
    expect(wrapper.text()).not.toContain('게임룸')
    expect(wrapper.text()).not.toContain('매치 ID')
    expect(wrapper.findAll('.loading-steps .is-ready')).toHaveLength(5)
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('toggles game waiting copy between Korean and English', async () => {
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

    expect(wrapper.text()).toContain('게임 준비 중')
    expect(wrapper.text()).toContain('상대 정보 대기')
    expect(wrapper.text()).toContain('수신 대기')
    expect(wrapper.get('main').attributes('data-loading-progress')).toBe('80')

    await wrapper.get('.locale-toggle').trigger('click')

    expect(wrapper.text()).toContain('Preparing Game')
    expect(wrapper.text()).toContain('Waiting for opponent')
    expect(wrapper.text()).toContain('Waiting')
    expect(wrapper.text()).not.toContain('Ready')
  })

  it('returns to match when payload is missing', async () => {
    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('false')
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '게임 준비 정보를 찾을 수 없습니다. 매칭 화면으로 돌아갑니다.',
    )
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
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '게임 준비 정보를 찾을 수 없습니다. 매칭 화면으로 돌아갑니다.',
    )
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })
})
