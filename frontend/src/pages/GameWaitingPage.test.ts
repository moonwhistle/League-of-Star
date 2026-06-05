import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { saveGameWaitingPayload } from '@/services/gameWaitingPayload'
import { connectGameWebSocket } from '@/services/realtime/gameWebSocket'

import GameWaitingPage from './GameWaitingPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    gameRoomId: '100',
  },
}))
const routerReplaceMock = vi.hoisted(() => vi.fn())
const gameWebSocketMock = vi.hoisted(() => {
  const state = {
    handlers: undefined,
    connection: {
      socket: {},
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
const { setLocale } = useLocale()

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => ({
    replace: routerReplaceMock,
  }),
}))

vi.mock('@/services/realtime/gameWebSocket', () => ({
  connectGameWebSocket: gameWebSocketMock.connect,
}))

const connectGameWebSocketMock = vi.mocked(connectGameWebSocket)

interface GameWebSocketTestHandlers {
  onOpen?: (event: Event) => void
  onMessage?: (message: unknown, event: MessageEvent) => void
  onError?: (error: unknown) => void
}

function getGameWebSocketHandlers() {
  if (gameWebSocketMock.state.handlers === undefined) {
    throw new Error('Game WebSocket handlers were not registered.')
  }

  return gameWebSocketMock.state.handlers as GameWebSocketTestHandlers
}

describe('GameWaitingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
    gameWebSocketMock.state.handlers = undefined
    gameWebSocketMock.state.connection.sendClientReady.mockClear()
    gameWebSocketMock.state.connection.sendRttPong.mockClear()
    gameWebSocketMock.state.connection.sendSmite.mockClear()
    gameWebSocketMock.state.connection.close.mockClear()
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
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('connected')
    expect(wrapper.text()).toContain('대기방 연결됨')

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
  })

  it('responds to RTT_PING and keeps COUNTDOWN/GAME_START as safe waiting events', async () => {
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

    handlers.onMessage?.(
      {
        type: 'COUNTDOWN',
        payload: {
          gameRoomId: 100,
          serverTime: 1,
          startAt: 2,
          countdownDisplaySeconds: 3,
        },
      },
      new MessageEvent('message'),
    )
    handlers.onMessage?.(
      {
        type: 'GAME_START',
        payload: {
          gameRoomId: 100,
          serverTime: 1,
          startAt: 2,
          scenario: {
            dragonMaxHp: 10000,
            durationMs: 15000,
            hpTimeline: [],
          },
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('rttMeasuring')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('GAME_START')
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('marks final websocket failures and ignores late callbacks', async () => {
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
        videoUrl: '/assets/game/dragon-view.mp4',
        webSocketUrl: '/ws/game/100',
      },
      receivedAt: '2026-06-01T00:00:00.000Z',
    })

    const wrapper = mount(GameWaitingPage)
    await flushPromises()

    wrapper.unmount()

    expect(gameWebSocketMock.state.connection.close).toHaveBeenCalledTimes(1)
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
