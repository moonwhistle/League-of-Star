import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { readGameStartPayload } from '@/services/gameStartPayload'
import { saveGameWaitingPayload } from '@/services/gameWaitingPayload'
import { connectGameWebSocket } from '@/services/realtime/gameWebSocket'

import GameWaitingPage from './GameWaitingPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    gameRoomId: '100',
  },
}))
const routerReplaceMock = vi.hoisted(() => vi.fn())
const routerPushMock = vi.hoisted(() => vi.fn())
const routeLeaveGuardsMock = vi.hoisted((): unknown[] => [])
const gameWebSocketHandoffMock = vi.hoisted(() => ({
  handoffGameWebSocket: vi.fn(),
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
      sendLightning: vi.fn(),
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
const { setLocale } = useLocale()

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: unknown) => {
    routeLeaveGuardsMock.push(guard)
  },
  useRoute: () => routeMock,
  useRouter: () => ({
    replace: routerReplaceMock,
    push: routerPushMock,
  }),
}))

vi.mock('@/services/realtime/gameWebSocket', () => ({
  connectGameWebSocket: gameWebSocketMock.connect,
}))

vi.mock('@/services/realtime/gameWebSocketHandoff', () => ({
  handoffGameWebSocket: gameWebSocketHandoffMock.handoffGameWebSocket,
  takeGameWebSocketHandoff: gameWebSocketHandoffMock.takeGameWebSocketHandoff,
}))

const connectGameWebSocketMock = vi.mocked(connectGameWebSocket)
let createdVideoElements: HTMLVideoElement[] = []
let restoreCreateElement = () => {}

interface GameWebSocketTestHandlers {
  onOpen?: (event: Event) => void
  onMessage?: (message: unknown, event: MessageEvent) => void
  onError?: (error: unknown) => void
  onClose?: (event: CloseEvent) => void
}

function getGameWebSocketHandlers() {
  if (gameWebSocketMock.state.handlers === undefined) {
    throw new Error('Game WebSocket handlers were not registered.')
  }

  return gameWebSocketMock.state.handlers as GameWebSocketTestHandlers
}

function getLatestRouteLeaveGuard() {
  const guard = routeLeaveGuardsMock.at(-1)

  if (guard === undefined) {
    throw new Error('Route leave guard was not registered.')
  }

  return guard as (to?: unknown) => unknown
}

function emitLatestVideoPreloadEvent(type: string) {
  const video = createdVideoElements.at(-1)

  if (video === undefined) {
    throw new Error('Video preload element was not created.')
  }

  video.dispatchEvent(new Event(type))
}

describe('GameWaitingPage', () => {
  beforeEach(() => {
    restoreCreateElement()
    vi.clearAllMocks()
    window.sessionStorage.clear()
    createdVideoElements = []
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
    routerPushMock.mockResolvedValue(undefined)
    gameWebSocketMock.state.handlers = undefined
    gameWebSocketMock.state.connection.setHandlers.mockClear()
    gameWebSocketMock.state.connection.sendClientReady.mockClear()
    gameWebSocketMock.state.connection.sendRttPong.mockClear()
    gameWebSocketMock.state.connection.sendLightning.mockClear()
    gameWebSocketMock.state.connection.close.mockClear()
    gameWebSocketHandoffMock.handoffGameWebSocket.mockClear()
    gameWebSocketHandoffMock.takeGameWebSocketHandoff.mockReset()
    routeLeaveGuardsMock.length = 0
    const originalCreateElement = document.createElement.bind(document)
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation(((
      tagName: string,
      options?: ElementCreationOptions,
    ) => {
      const element = originalCreateElement(tagName, options)

      if (tagName.toLowerCase() === 'video') {
        createdVideoElements.push(element as HTMLVideoElement)
        Object.defineProperty(element, 'load', {
          configurable: true,
          value: vi.fn(),
        })
      }

      return element
    }) as typeof document.createElement)
    restoreCreateElement = () => createElementSpy.mockRestore()
    setLocale('ko')
  })

  afterEach(() => {
    restoreCreateElement()
    restoreCreateElement = () => {}
    vi.useRealTimers()
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
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-waiting-payload-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-room-id')).toBe('100')
    expect(wrapper.get('main').attributes('data-loading-progress')).toBe('100')
    expect(wrapper.get('h1').text()).toBe('LEAGUE OF STAR')
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
    expect(connectGameWebSocketMock).toHaveBeenCalledWith('/ws/game/100', expect.any(Object))
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('tracks game websocket waiting states from server messages', async () => {
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
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('preloading')
    expect(wrapper.text()).toContain('전장 데이터 확인 중')
    expect(wrapper.text()).toContain('게임 시작 전에 필요한 MP4 데이터를 미리 불러오는 중입니다.')

    handlers.onMessage?.(
      {
        type: 'PLAYER_READY',
        payload: {
          userId: 1,
          bothReady: false,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('waitingOpponent')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('PLAYER_READY')
    expect(wrapper.get('main').attributes('data-game-socket-both-ready')).toBe('false')
    expect(wrapper.text()).toContain('상대 준비 대기')
    expect(wrapper.text()).toContain('상대방의 준비 신호를 기다리는 중입니다.')

    handlers.onMessage?.(
      {
        type: 'PLAYER_LEFT',
        payload: {
          userId: 2,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('waitingOpponent')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('PLAYER_LEFT')
    expect(wrapper.text()).toContain('상대 연결 이탈 2')

    handlers.onMessage?.(
      {
        type: 'PLAYER_READY',
        payload: {
          userId: 2,
          bothReady: true,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('bothReady')
    expect(wrapper.get('main').attributes('data-game-socket-both-ready')).toBe('true')
    expect(wrapper.text()).toContain('양쪽 준비 완료')
    expect(wrapper.text()).toContain('양쪽 준비가 끝났고 연결 품질 확인을 기다리는 중입니다.')
  })

  it('preloads the game video after websocket open and sends CLIENT_READY once', async () => {
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
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('preloading')
    expect(createdVideoElements).toHaveLength(1)
    expect(createdVideoElements[0]?.getAttribute('src')).toBe('/assets/game/star-core-view.mp4')
    expect(createdVideoElements[0]?.load).toHaveBeenCalledTimes(1)

    emitLatestVideoPreloadEvent('loadeddata')
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendClientReady).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('readySent')
    expect(wrapper.text()).toContain('준비 신호 전송됨')
    expect(wrapper.text()).toContain('내 준비 신호를 보냈고 상대 준비를 기다리는 중입니다.')

    emitLatestVideoPreloadEvent('canplaythrough')
    emitLatestVideoPreloadEvent('loadeddata')
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendClientReady).toHaveBeenCalledTimes(1)
  })

  it('closes the websocket and returns to match when video preload fails', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/missing.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    emitLatestVideoPreloadEvent('error')
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '전장 데이터를 불러오지 못했습니다.',
    )
    expect(wrapper.text()).toContain('매칭 화면으로 돌아갑니다.')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
    expect(gameWebSocketMock.state.connection.sendClientReady).not.toHaveBeenCalled()
  })

  it('does not send CLIENT_READY when preload resolves after unmount', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()
    wrapper.unmount()
    emitLatestVideoPreloadEvent('loadeddata')
    await flushPromises()

    expect(gameWebSocketMock.state.connection.sendClientReady).not.toHaveBeenCalled()
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
  })

  it('warns before browser refresh while waiting payload is active', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const event = new Event('beforeunload', { cancelable: true })

    window.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)

    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener')
    wrapper.unmount()

    expect(removeEventListenerSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
  })

  it('confirms route leave while waiting and follows the user choice', async () => {
    const confirmSpy = vi
      .spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    mount(GameWaitingPage)
    await flushPromises()
    const guard = getLatestRouteLeaveGuard()

    expect(guard({ name: ROUTE_NAMES.match })).toBe(false)
    expect(guard({ name: ROUTE_NAMES.match })).toBe(true)
    expect(confirmSpy).toHaveBeenCalledWith(
      '게임 대기 중에 이동하면 현재 연결이 끊길 수 있습니다. 그래도 이동하시겠습니까?',
    )
  })

  it('responds to RTT_PING and renders COUNTDOWN before game start', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'RTT_PING',
        payload: {
          seq: 7,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendRttPong).toHaveBeenCalledWith(7)
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('rttMeasuring')
    expect(wrapper.text()).toContain('연결 품질 확인 중')
    expect(wrapper.text()).toContain('서버와 왕복 지연 시간을 확인하는 중입니다.')

    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('countdown')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('COUNTDOWN')
    expect(wrapper.get('main').attributes('data-game-countdown-seconds')).toBe('3')
    expect(wrapper.get('main').attributes('data-game-countdown-start-at')).toBe(
      String(Date.now() + 2500),
    )
    expect(wrapper.text()).toContain('게임 시작 예고')
    expect(wrapper.text()).toContain('서버가 확정한 시작 시각까지 대기하는 중입니다.')
    expect(wrapper.text()).toContain('시작까지')

    expect(routerReplaceMock).not.toHaveBeenCalled()
    expect(routerPushMock).not.toHaveBeenCalled()
  })

  it('can start the countdown from 2 when COUNTDOWN is received late', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 1200,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-countdown-seconds')).toBe('2')

    vi.advanceTimersByTime(1000)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-countdown-seconds')).toBe('1')
  })

  it('returns to match when COUNTDOWN belongs to a different gameRoomId', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 101,
          serverTime: Date.now(),
          startAt: Date.now() + 3000,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '게임 시작 정보가 올바르지 않습니다.',
    )
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('stores GAME_START payload and moves to play after a matching COUNTDOWN', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))
    const startAt = Date.now() + 2500
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [
              {
                timeMs: 0,
                hp: 10000,
              },
            ],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    const storedPayload = readGameStartPayload(100)

    expect(storedPayload).not.toBeNull()
    expect(storedPayload?.gameRoomId).toBe(100)
    expect(storedPayload?.serverTime).toBe(Date.now())
    expect(storedPayload?.startAt).toBe(startAt)
    expect(storedPayload?.scenario.starCoreMaxHp).toBe(10000)
    expect(storedPayload?.receivedAt).toBe('2026-06-01T00:00:00.000Z')
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('starting')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('GAME_START')
    expect(wrapper.get('main').attributes('data-game-countdown-seconds')).toBe('0')
    expect(wrapper.text()).toContain('게임 시작 정보 저장됨')
    expect(wrapper.text()).toContain('서버가 확정한 시작 데이터로 전장 화면으로 이동하는 중입니다.')
    expect(gameWebSocketMock.state.connection.setHandlers).toHaveBeenCalledWith()
    expect(gameWebSocketHandoffMock.handoffGameWebSocket).toHaveBeenCalledWith(
      '100',
      gameWebSocketMock.state.connection,
    )
    expect(gameWebSocketMock.state.connection.close).not.toHaveBeenCalled()
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.gamePlay,
      params: {
        gameRoomId: '100',
      },
    })
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('allows the normal transition to play without route leave confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(getLatestRouteLeaveGuard()({ name: ROUTE_NAMES.gamePlay })).toBe(true)
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('uses GAME_START as the source of truth when COUNTDOWN was not received', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(readGameStartPayload(100)).not.toBeNull()
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.gamePlay,
      params: {
        gameRoomId: '100',
      },
    })
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('returns to match when GAME_START belongs to a different gameRoomId', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 101,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '게임 시작 정보가 올바르지 않습니다.',
    )
    expect(readGameStartPayload(100)).toBeNull()
    expect(routerPushMock).not.toHaveBeenCalled()
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when COUNTDOWN and GAME_START startAt values differ', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 4000,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '게임 시작 정보가 올바르지 않습니다.',
    )
    expect(readGameStartPayload(100)).toBeNull()
    expect(routerPushMock).not.toHaveBeenCalled()
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('keeps the game start transition final when late websocket callbacks arrive', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00.000Z'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    handlers.onClose?.(new CloseEvent('close'))
    handlers.onError?.(new Error('late error'))
    handlers.onMessage?.(
      {
        type: 'ERROR',
        payload: {
          reason: 'late server error',
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('starting')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('GAME_START')
    expect(routerPushMock).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('keeps a failed state when moving to play route fails', async () => {
    gameWebSocketHandoffMock.takeGameWebSocketHandoff.mockReturnValueOnce(
      gameWebSocketMock.state.connection,
    )
    routerPushMock.mockRejectedValueOnce(new Error('NAVIGATION_BLOCKED'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 2500,
          scenario: {
            starCoreMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toContain(
      '게임 화면 이동에 실패했습니다.',
    )
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toContain(
      'NAVIGATION_BLOCKED',
    )
    expect(gameWebSocketHandoffMock.handoffGameWebSocket).toHaveBeenCalledWith(
      '100',
      gameWebSocketMock.state.connection,
    )
    expect(gameWebSocketHandoffMock.takeGameWebSocketHandoff).toHaveBeenCalledWith('100')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('returns to match when the client watchdog expires', async () => {
    vi.useFakeTimers()
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    vi.advanceTimersByTime(29999)
    await flushPromises()

    expect(routerReplaceMock).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '게임 대기 시간이 초과되었습니다.',
    )
    expect(wrapper.text()).toContain('매칭 화면으로 돌아갑니다.')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('clears the client watchdog when both players are ready', async () => {
    vi.useFakeTimers()
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'PLAYER_READY',
        payload: {
          userId: 2,
          bothReady: true,
        },
      },
      new MessageEvent('message'),
    )
    vi.advanceTimersByTime(30000)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('bothReady')
    expect(routerReplaceMock).not.toHaveBeenCalled()
    expect(gameWebSocketMock.state.connection.close).not.toHaveBeenCalled()
  })

  it('clears the client watchdog when RTT starts', async () => {
    vi.useFakeTimers()
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'RTT_PING',
        payload: {
          seq: 8,
        },
      },
      new MessageEvent('message'),
    )
    vi.advanceTimersByTime(30000)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('rttMeasuring')
    expect(gameWebSocketMock.state.connection.sendRttPong).toHaveBeenCalledWith(8)
    expect(routerReplaceMock).not.toHaveBeenCalled()
    expect(gameWebSocketMock.state.connection.close).not.toHaveBeenCalled()
  })

  it('returns to match on websocket error or close', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const errorWrapper = mount(GameWaitingPage)
    await flushPromises()
    getGameWebSocketHandlers().onError?.(new Error('SOCKET_ERROR'))
    await flushPromises()

    expect(errorWrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(errorWrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      'SOCKET_ERROR',
    )
    expect(errorWrapper.text()).toContain('매칭 화면으로 돌아갑니다.')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })

    errorWrapper.unmount()
    vi.clearAllMocks()
    gameWebSocketMock.state.connection.close.mockClear()

    const closeWrapper = mount(GameWaitingPage)
    await flushPromises()
    getGameWebSocketHandlers().onClose?.(new CloseEvent('close'))
    await flushPromises()

    expect(closeWrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(closeWrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '게임 대기 연결이 종료되었습니다.',
    )
    expect(closeWrapper.text()).toContain('매칭 화면으로 돌아갑니다.')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when the websocket connection cannot be created', async () => {
    connectGameWebSocketMock.mockImplementationOnce(() => {
      throw new Error('HANDSHAKE_FAILED')
    })
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      'HANDSHAKE_FAILED',
    )
    expect(wrapper.text()).toContain('매칭 화면으로 돌아갑니다.')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when GAME_WAITING_TIMEOUT is received', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_WAITING_TIMEOUT',
        payload: {
          gameRoomId: 100,
          reason: 'WAITING_TIMEOUT',
          action: 'GO_TO_MATCH_START',
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe('WAITING_TIMEOUT')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when ERROR is received', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'ERROR',
        payload: {
          code: 'ROOM_ABORTED',
          reason: 'ROOM_ABORTED',
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe('ROOM_ABORTED')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('keeps a failed state when returning to match route fails', async () => {
    routerReplaceMock.mockRejectedValueOnce(new Error('NAVIGATION_BLOCKED'))
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'ERROR',
        payload: {
          code: 'ROOM_ABORTED',
          reason: 'ROOM_ABORTED',
        },
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toContain(
      'ROOM_ABORTED',
    )
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toContain(
      '매칭 화면 복귀에 실패했습니다.',
    )
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toContain(
      'NAVIGATION_BLOCKED',
    )
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('marks final websocket failures and ignores late callbacks', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_START_FAILED',
        payload: {
          gameRoomId: 100,
          reason: 'RTT_FAILED',
          action: 'GO_TO_MATCH_START',
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe('RTT_FAILED')
    expect(wrapper.text()).toContain('게임 대기 연결 실패')
    expect(wrapper.text()).toContain('매칭 화면으로 돌아갑니다.')
    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })

    handlers.onOpen?.(new Event('open'))
    handlers.onMessage?.(
      {
        type: 'PLAYER_READY',
        payload: {
          userId: 2,
          bothReady: true,
        },
      },
      new MessageEvent('message'),
    )
    handlers.onError?.(new Error('late error'))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('failed')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('GAME_START_FAILED')
    expect(wrapper.get('main').attributes('data-game-socket-both-ready')).toBe('false')
  })

  it('closes the game websocket on unmount', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    wrapper.unmount()

    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
  })

  it('cleans websocket, watchdog, countdown timer, and preload listeners on unmount', async () => {
    vi.useFakeTimers()
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 100,
          serverTime: Date.now(),
          startAt: Date.now() + 3000,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()
    wrapper.unmount()
    vi.advanceTimersByTime(30000)
    emitLatestVideoPreloadEvent('loadeddata')
    await flushPromises()

    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
    expect(gameWebSocketMock.state.connection.sendClientReady).not.toHaveBeenCalled()
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('toggles game waiting copy between Korean and English', async () => {
    saveGameWaitingPayload({
      matchId: 'match-1',
      opponent: null,
      game: {
        gameRoomId: 100,
        videoUrl: '/assets/game/star-core-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    expect(wrapper.text()).toContain('게임 준비 중')
    expect(wrapper.text()).toContain('상대 정보 대기')
    expect(wrapper.text()).toContain('수신 대기')
    expect(wrapper.text()).toContain('대기방 연결 중')
    expect(wrapper.text()).toContain('게임 대기방에 접속하는 중입니다.')
    expect(wrapper.get('main').attributes('data-loading-progress')).toBe('80')

    await wrapper.get('.locale-toggle').trigger('click')

    expect(wrapper.text()).toContain('Preparing Game')
    expect(wrapper.text()).toContain('Waiting for opponent')
    expect(wrapper.text()).toContain('Waiting')
    expect(wrapper.text()).toContain('Connecting room')
    expect(wrapper.text()).toContain('Connecting to the game waiting room.')
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
        videoUrl: '/assets/game/star-core-view.mp4',
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
