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
const gameWebSocketHandoffMock = vi.hoisted(() => ({
  takeGameWebSocketHandoff: vi.fn(),
}))
const gameWebSocketMock = vi.hoisted(() => {
  const state = {
    handlers: undefined,
    connection: {
      socket: {},
      setHandlers: vi.fn(),
      sendClientReady: vi.fn(),
      sendRttPong: vi.fn(),
      sendSmite: vi.fn(),
      close: vi.fn(),
    },
  }
  const connect = vi.fn((url = '', handlers = {}) => {
    void url
    state.handlers = handlers

    return state.connection
  })

  return {
    connect,
    state,
  }
})

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: unknown) => {
    routeLeaveGuardsMock.push(guard)
  },
  useRoute: () => routeMock,
  useRouter: () => ({
    replace: routerReplaceMock,
  }),
}))

vi.mock('@/services/realtime/gameWebSocket', () => ({
  connectGameWebSocket: gameWebSocketMock.connect,
}))

vi.mock('@/services/realtime/gameWebSocketHandoff', () => ({
  takeGameWebSocketHandoff: gameWebSocketHandoffMock.takeGameWebSocketHandoff,
}))

const { setLocale } = useLocale()

interface GameWebSocketTestHandlers {
  onOpen?: (event: Event) => void
  onMessage?: (message: unknown, event: MessageEvent) => void
  onError?: (error: unknown) => void
  onClose?: (event: CloseEvent) => void
}

function getLatestRouteLeaveGuard() {
  const guard = routeLeaveGuardsMock.at(-1)

  if (guard === undefined) {
    throw new Error('Route leave guard was not registered.')
  }

  return guard as (to?: unknown) => unknown
}

function getGameWebSocketHandlers() {
  if (gameWebSocketMock.state.handlers === undefined) {
    throw new Error('Game WebSocket handlers were not registered.')
  }

  return gameWebSocketMock.state.handlers as GameWebSocketTestHandlers
}

describe('GamePlayPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
    window.sessionStorage.clear()
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
    routeLeaveGuardsMock.length = 0
    gameWebSocketMock.state.handlers = undefined
    gameWebSocketMock.connect.mockClear()
    gameWebSocketMock.state.connection.setHandlers.mockClear()
    gameWebSocketMock.state.connection.close.mockClear()
    gameWebSocketHandoffMock.takeGameWebSocketHandoff.mockReset()
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
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('connecting')
    expect(wrapper.get('main').attributes('data-game-socket-smite-ready')).toBe('true')
    expect(wrapper.text()).toContain('전장 시작 데이터 확인됨')
    expect(wrapper.text()).toContain('서버 시작 시각 기준으로 대기 중')
    expect(wrapper.text()).toContain('게임룸')
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('드래곤 최대 HP')
    expect(wrapper.text()).toContain('10000')
    expect(wrapper.text()).toContain('진행 시간')
    expect(wrapper.text()).toContain('15000')
    expect(wrapper.text()).toContain('전장 연결 중')
    expect(wrapper.find('[data-testid="smite-button"]').exists()).toBe(false)
    expect(gameWebSocketMock.connect).toHaveBeenCalledWith('/ws/game/100', expect.any(Object))
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('uses a handed off game websocket before reconnecting from storage', async () => {
    gameWebSocketHandoffMock.takeGameWebSocketHandoff.mockReturnValueOnce(
      gameWebSocketMock.state.connection,
    )
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(gameWebSocketHandoffMock.takeGameWebSocketHandoff).toHaveBeenCalledWith('100')
    expect(gameWebSocketMock.state.connection.setHandlers).toHaveBeenCalledWith(expect.any(Object))
    expect(gameWebSocketMock.connect).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('handoff')
    expect(wrapper.get('main').attributes('data-game-socket-smite-ready')).toBe('true')
    expect(wrapper.text()).toContain('대기방 연결 인계됨')
  })

  it('handles play websocket ERROR and GAME_RESULT without moving to match', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('connected')

    handlers.onMessage?.(
      {
        type: 'ERROR',
        payload: {
          code: 'GAME_ERROR',
          reason: 'SERVER_SIDE_ERROR',
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-game-socket-smite-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('ERROR')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      'SERVER_SIDE_ERROR',
    )
    expect(routerReplaceMock).not.toHaveBeenCalled()

    handlers.onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: {
          gameRoomId: 100,
          result: 'PLAYER1_WIN',
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('resultReceived')
    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('true')
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('renders reconnect failures in place instead of returning to match', async () => {
    gameWebSocketMock.connect.mockImplementationOnce(() => {
      throw new Error('RECONNECT_FAILED')
    })
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-game-socket-smite-ready')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      'RECONNECT_FAILED',
    )
    expect(wrapper.text()).toContain('RECONNECT_FAILED')
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
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
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
