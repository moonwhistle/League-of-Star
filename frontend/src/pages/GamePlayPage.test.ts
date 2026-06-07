import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { buildGameStartPayloadKey, saveGameStartPayload } from '@/services/gameStartPayload'
import { buildGameWaitingPayloadKey, saveGameWaitingPayload } from '@/services/gameWaitingPayload'

import GamePlayPage from './GamePlayPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    gameRoomId: '100',
  },
}))
const routerReplaceMock = vi.hoisted(() => vi.fn())
const routeLeaveGuardsMock = vi.hoisted((): unknown[] => [])

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: unknown) => {
    routeLeaveGuardsMock.push(guard)
  },
  useRoute: () => routeMock,
  useRouter: () => ({
    replace: routerReplaceMock,
  }),
}))

const { setLocale } = useLocale()

function getLatestRouteLeaveGuard() {
  const guard = routeLeaveGuardsMock.at(-1)

  if (guard === undefined) {
    throw new Error('Route leave guard was not registered.')
  }

  return guard as (to?: unknown) => unknown
}

describe('GamePlayPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
    window.sessionStorage.clear()
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
    routeLeaveGuardsMock.length = 0
    setLocale('ko')
  })

  it('reads the stored GAME_START payload for the current route gameRoomId', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:05.000Z'))
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-start-payload-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-play-state-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-room-id')).toBe('100')
    expect(wrapper.get('main').attributes('data-game-start-at')).toBe(String(Date.now() - 2000))
    expect(wrapper.get('main').attributes('data-game-duration-ms')).toBe('15000')
    expect(wrapper.get('main').attributes('data-game-elapsed-ms')).toBe('2000')
    expect(wrapper.get('main').attributes('data-game-current-hp')).toBe('8667')
    expect(wrapper.get('main').attributes('data-game-video-url')).toBe(
      '/assets/game/dragon-view.mp4',
    )
    expect(wrapper.get('main').attributes('data-game-websocket-url')).toBe('/ws/game/100')
    expect(wrapper.text()).toContain('전장 시작 데이터 확인됨')
    expect(wrapper.text()).toContain('서버 시작 시각 기준으로 대기 중')
    expect(wrapper.text()).toContain('게임룸')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('드래곤 최대 HP')
    expect(wrapper.text()).toContain('10000')
    expect(wrapper.text()).toContain('진행 시간')
    expect(wrapper.text()).toContain('15000')
    expect(wrapper.find('[data-testid="smite-button"]').exists()).toBe(false)
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('updates elapsed time from the stored GAME_START startAt', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:05.000Z'))
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-elapsed-ms')).toBe('2000')
    expect(wrapper.get('main').attributes('data-game-current-hp')).toBe('8667')

    vi.advanceTimersByTime(1000)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-elapsed-ms')).toBe('3000')
    expect(wrapper.get('main').attributes('data-game-current-hp')).toBe('8000')
  })

  it('warns before browser refresh while valid play state is active', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    const event = new Event('beforeunload', { cancelable: true })

    window.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)

    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener')
    wrapper.unmount()

    expect(removeEventListenerSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
  })

  it('confirms route leave while valid play state is active', async () => {
    const confirmSpy = vi
      .spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)
    saveValidPlayPayloads()

    mount(GamePlayPage)
    await flushPromises()
    const guard = getLatestRouteLeaveGuard()

    expect(guard({ name: ROUTE_NAMES.match })).toBe(false)
    expect(guard({ name: ROUTE_NAMES.match })).toBe(true)
    expect(confirmSpy).toHaveBeenCalledWith(
      '게임 진행 중에 이동하면 화면 복구가 필요할 수 있습니다. 그래도 이동하시겠습니까?',
    )
  })

  it('returns to match when the GAME_START payload is missing', async () => {
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

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-start-payload-ready')).toBe('false')
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '게임 시작 정보를 찾을 수 없습니다. 매칭 화면으로 돌아갑니다.',
    )
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when the game waiting payload is missing', async () => {
    saveGameStartPayload({
      gameRoomId: 100,
      serverTime: 1,
      startAt: 2,
      scenario: {
        dragonMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-start-payload-ready')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-play-state-ready')).toBe('false')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when the route gameRoomId does not match the stored payload', async () => {
    window.sessionStorage.setItem(
      buildGameStartPayloadKey(100),
      JSON.stringify({
        gameRoomId: 101,
        serverTime: 1,
        startAt: 2,
        scenario: {
          dragonMaxHp: 10000,
          durationMs: 15000,
          hpTimeline: [],
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )
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

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-start-payload-ready')).toBe('false')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when the route gameRoomId does not match the stored waiting payload', async () => {
    saveGameStartPayload({
      gameRoomId: 100,
      serverTime: 1,
      startAt: 2,
      scenario: {
        dragonMaxHp: 10000,
        durationMs: 15000,
        hpTimeline: [],
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })
    window.sessionStorage.setItem(
      buildGameWaitingPayloadKey(100),
      JSON.stringify({
        matchId: 'match-1',
        opponent: null,
        game: {
          gameRoomId: 101,
          videoUrl: '/assets/game/dragon-view.mp4',
          webSocketUrl: '/ws/game/101',
        },
        receivedAt: '2026-06-01T00:00:00.000Z',
      }),
    )

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-play-state-ready')).toBe('false')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('toggles game play copy between Korean and English', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.text()).toContain('전장 시작 데이터 확인됨')
    expect(wrapper.text()).toContain('게임룸')

    await wrapper.get('.locale-toggle').trigger('click')

    expect(wrapper.text()).toContain('Battle start data confirmed')
    expect(wrapper.text()).toContain('Game room')
    expect(wrapper.text()).toContain('Dragon max HP')
  })
})

function saveValidPlayPayloads(): void {
  saveGameStartPayload({
    gameRoomId: 100,
    serverTime: Date.now() - 5000,
    startAt: Date.now() - 2000,
    scenario: {
      dragonMaxHp: 10000,
      durationMs: 15000,
      hpTimeline: [
        {
          timeMs: 0,
          hp: 10000,
        },
        {
          timeMs: 15000,
          hp: 0,
        },
      ],
    },
    receivedAt: '2026-06-01T00:00:00.000Z',
  })
  saveGameWaitingPayload({
    matchId: 'match-1',
    opponent: {
      userId: 2,
      nickname: 'opponent',
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
}
