import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { readGameResultPayload } from '@/services/gameResultPayload'
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
const threeSceneMock = vi.hoisted(() => ({
  state: {
    callbacks: undefined,
  },
  controller: {
    dispose: vi.fn(),
    triggerLightningImpact: vi.fn(),
    update: vi.fn(),
  },
  createThreeGalaxyBackgroundScene: vi.fn((canvas = {}, callbacks = {}) => {
    void canvas
    threeSceneMock.state.callbacks = callbacks

    return threeSceneMock.controller
  }),
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

vi.mock('@/game/threeGalaxyBackgroundScene', () => ({
  createNoopThreeGalaxyBackgroundSceneController: () => ({
    dispose: vi.fn(),
    triggerLightningImpact: vi.fn(),
    update: vi.fn(),
  }),
  createThreeGalaxyBackgroundScene: threeSceneMock.createThreeGalaxyBackgroundScene,
}))

const { setLocale } = useLocale()

enableAutoUnmount(afterEach)

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

function setStarTargeted(isTargeted: boolean) {
  const callbacks = threeSceneMock.state.callbacks as
    | {
        onTargetHoverChange?: (isTargetHovered: boolean) => void
      }
    | undefined

  if (callbacks?.onTargetHoverChange === undefined) {
    throw new Error('Three scene hover callback was not registered.')
  }

  callbacks.onTargetHoverChange(isTargeted)
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
    threeSceneMock.state.callbacks = undefined
    threeSceneMock.controller.dispose.mockClear()
    threeSceneMock.controller.triggerLightningImpact.mockClear()
    threeSceneMock.controller.update.mockClear()
    threeSceneMock.createThreeGalaxyBackgroundScene.mockClear()
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
    expect(wrapper.get('main').attributes('data-game-hp-percent')).toBe('87')
    expect(wrapper.get('main').attributes('data-game-video-url')).toBeUndefined()
    expect(wrapper.get('main').attributes('data-game-started')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-countdown-seconds')).toBe('0')
    expect(wrapper.get('main').attributes('data-game-websocket-url')).toBe('/ws/game/100')
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('connecting')
    expect(wrapper.get('main').attributes('data-game-socket-lightning-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-lightning-sent')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-three-ready')).toBe('false')
    expect(wrapper.find('video').exists()).toBe(false)
    expect(wrapper.find('[data-testid="three-scene"]').exists()).toBe(true)
    expect(wrapper.find('.space-vignette').exists()).toBe(true)
    expect(wrapper.find('.target-reticle').exists()).toBe(false)
    expect(wrapper.find('.star-core-hp-slot').exists()).toBe(false)
    expect(wrapper.find('[data-testid="lightning-button"]').exists()).toBe(false)
    expect(wrapper.get('.lightning-spell__icon').attributes('alt')).toBe('LIGHTNING')
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('offline')
    expect(gameWebSocketMock.connect).toHaveBeenCalledWith('/ws/game/100', expect.any(Object))
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('keeps the galaxy background only before the server startAt', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:05.000Z'))
    saveValidPlayPayloads({
      startAt: Date.now() + 2800,
    })

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-started')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-countdown-seconds')).toBe('3')
    expect(wrapper.get('main').attributes('data-game-elapsed-ms')).toBe('0')
    expect(wrapper.get('main').attributes('data-game-current-hp')).toBe('10000')
    expect(wrapper.find('[data-testid="three-scene"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="lightning-button"]').exists()).toBe(false)
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('false')
    expect(wrapper.find('[data-testid="lightning-hud"]').exists()).toBe(true)
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
    expect(wrapper.get('main').attributes('data-game-socket-lightning-ready')).toBe('true')
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('active')
  })

  it('handles play websocket ERROR and invalid GAME_RESULT without moving routes', async () => {
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
    expect(wrapper.get('main').attributes('data-game-socket-lightning-ready')).toBe('true')
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

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      '게임 결과 정보가 올바르지 않습니다.',
    )
    expect(readGameResultPayload(100)).toBeNull()
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('stores valid GAME_RESULT and moves to the result route', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    handlers.onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: createGameResultPayload({
          winnerUserId: 1,
          result: 'PLAYER1_WIN',
        }),
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('resultReceived')
    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('true')
    expect(readGameResultPayload(100)?.winnerUserId).toBe(1)
    expect(routerReplaceMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.gameResult,
      params: {
        gameRoomId: '100',
      },
    })
  })

  it('moves to result only once when duplicate GAME_RESULT messages are received', async () => {
    saveValidPlayPayloads()

    mount(GamePlayPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()
    const message = {
      type: 'GAME_RESULT',
      payload: createGameResultPayload({
        winnerUserId: null,
        result: 'DRAW',
        reason: 'NATURAL_DEATH_DRAW',
      }),
    }

    handlers.onMessage?.(message, new MessageEvent('message'))
    handlers.onMessage?.(message, new MessageEvent('message'))
    await flushPromises()

    expect(routerReplaceMock).toHaveBeenCalledTimes(1)
  })

  it('does not downgrade resultReceived status when websocket error or close arrives after GAME_RESULT', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    const handlers = getGameWebSocketHandlers()

    handlers.onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: createGameResultPayload({
          winnerUserId: 1,
          result: 'PLAYER1_WIN',
        }),
      },
      new MessageEvent('message'),
    )
    handlers.onError?.(new Error('AFTER_RESULT_ERROR'))
    handlers.onClose?.(new CloseEvent('close'))
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('resultReceived')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe('')
  })

  it('does not show the route leave warning during internal result transition', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    saveValidPlayPayloads()

    mount(GamePlayPage)
    await flushPromises()

    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: createGameResultPayload({
          winnerUserId: 1,
          result: 'PLAYER1_WIN',
        }),
      },
      new MessageEvent('message'),
    )
    await flushPromises()

    const guard = getLatestRouteLeaveGuard()

    expect(guard({ name: ROUTE_NAMES.gameResult })).toBe(true)
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('renders reconnect failures in place instead of returning to match', async () => {
    gameWebSocketMock.connect.mockImplementationOnce(() => {
      throw new Error('RECONNECT_FAILED')
    })
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-game-socket-lightning-ready')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe(
      'RECONNECT_FAILED',
    )
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('error')
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

    const nextElapsedMs = Number(wrapper.get('main').attributes('data-game-elapsed-ms'))
    const nextHp = Number(wrapper.get('main').attributes('data-game-current-hp'))

    expect(nextElapsedMs).toBeGreaterThanOrEqual(2980)
    expect(nextElapsedMs).toBeLessThanOrEqual(3020)
    expect(nextHp).toBeLessThan(8667)
  })

  it('shows natural death waiting after the scenario duration without moving routes', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:20.500Z'))
    saveValidPlayPayloads({
      startAt: Date.now() - 15500,
    })

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-natural-death-waiting')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-current-hp')).toBe('0')
    expect(wrapper.find('[data-testid="lightning-hud"]').exists()).toBe(true)
    expect(routerReplaceMock).not.toHaveBeenCalled()
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
        starCoreMaxHp: 10000,
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
          starCoreMaxHp: 10000,
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
        starCoreMaxHp: 10000,
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

  it('renders the galaxy background and LIGHTNING HUD without legacy combat overlays or top navigation', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()

    expect(wrapper.find('.game-play-header').exists()).toBe(false)
    expect(wrapper.find('.locale-toggle').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('LEAGUE OF STAR')
    expect(wrapper.find('.star-core-hp-slot').exists()).toBe(false)
    expect(wrapper.find('.target-reticle').exists()).toBe(false)
    expect(wrapper.find('[data-testid="lightning-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="three-scene"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="opponent-lightning-hud"]').exists()).toBe(false)
    expect(wrapper.find('.lightning-hud--opponent').exists()).toBe(false)
    expect(wrapper.get('[data-testid="lightning-hud"]').classes()).toContain('lightning-hud--mine')
    expect(wrapper.get('[data-testid="lightning-hud"]').text()).toContain('D')
    expect(wrapper.get('[data-testid="lightning-hud"]').text()).toContain('F')
    expect(wrapper.get('[data-testid="lightning-damage"]').text()).toBe('1,200')
    expect(wrapper.get('.lightning-spell__icon').attributes('alt')).toBe('LIGHTNING')
    expect(wrapper.text()).not.toContain('발동 준비')
    expect(wrapper.text()).not.toContain('타겟 조준 대기')
    expect(wrapper.text()).not.toContain('타겟 고정')
  })

  it('sends LIGHTNING once when D is pressed while the target is hovered', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-star-targeted')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('true')
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('active')

    const firstEvent = new KeyboardEvent('keydown', {
      cancelable: true,
      key: 'd',
    })
    window.dispatchEvent(firstEvent)
    await wrapper.vm.$nextTick()

    expect(firstEvent.defaultPrevented).toBe(true)
    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.lightning-impact').exists()).toBe(true)
    expect(wrapper.get('.lightning-impact').attributes('data-lightning-impact-owner')).toBe('mine')
    expect(wrapper.find('.lightning-impact__bolt').exists()).toBe(false)
    expect(wrapper.find('.lightning-impact__ring').exists()).toBe(true)
    expect(wrapper.get('main').attributes('data-game-lightning-sent')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('false')
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('cooldown')
    expect(window.sessionStorage.getItem('league-of-star.gamePlayLightningSent:100')).toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd' }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(1)
  })

  it('sends LIGHTNING when F is pressed while the target is hovered', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F' }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('false')
  })

  it('uses physical D/F key codes when the keyboard layout changes the key value', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyD', key: 'ㅇ' }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.lightning-impact').attributes('data-lightning-impact-owner')).toBe('mine')
  })

  it('shows a miss impact at the pointer without sending LIGHTNING when the target is not hovered', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    window.dispatchEvent(new MouseEvent('pointermove', { clientX: 123, clientY: 234 }))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd', cancelable: true }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).not.toHaveBeenCalled()
    expect(wrapper.get('.lightning-impact').attributes('data-lightning-impact-owner')).toBe('miss')
    expect(wrapper.get('.lightning-impact').attributes('style')).toContain(
      '--lightning-impact-x: 123px',
    )
    expect(wrapper.get('.lightning-impact').attributes('style')).toContain(
      '--lightning-impact-y: 234px',
    )
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('cooldown')
  })

  it('does not cast LIGHTNING when key, repeat, or result conditions are invalid', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd', repeat: true }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).not.toHaveBeenCalled()

    const handlers = getGameWebSocketHandlers()
    handlers.onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: {
          gameRoomId: 100,
          result: 'DRAW',
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd' }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).not.toHaveBeenCalled()
  })

  it('keeps the local LIGHTNING cooldown when websocket send fails after casting', async () => {
    gameWebSocketMock.state.connection.sendLightning.mockImplementationOnce(() => {
      throw new Error('SEND_FAILED')
    })
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd' }))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-lightning-sent')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-socket-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-game-socket-error-message')).toBe('SEND_FAILED')
    expect(wrapper.get('.lightning-impact').attributes('data-lightning-impact-owner')).toBe('mine')
    expect(
      wrapper.get('[data-testid="lightning-hud"]').attributes('data-lightning-hud-status'),
    ).toBe('cooldown')
    expect(window.sessionStorage.getItem('league-of-star.gamePlayLightningSent:100')).toBeNull()
  })

  it('blocks LIGHTNING while the local 2 second cooldown is active', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd' }))
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'f' }))
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('false')
    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(1)
  })

  it('allows LIGHTNING again after server cooldown correction has expired', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:05.000Z'))
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'd' }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-game-lightning-ready')).toBe('false')

    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'LIGHTNING_APPLIED',
        payload: {
          gameRoomId: 100,
          userId: 1,
          serverReceiveTime: Date.now(),
          lightningTimeMs: 2000,
          starCoreHpAtLightning: 8667,
          damage: 1200,
          afterHp: 7467,
          isKill: false,
          cooldownUntil: Date.now() - 1,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'f' }))
    await wrapper.vm.$nextTick()

    expect(gameWebSocketMock.state.connection.sendLightning).toHaveBeenCalledTimes(2)
  })

  it('applies LIGHTNING damage after LIGHTNING_APPLIED is received', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    const hpBeforeLightning = Number(wrapper.get('main').attributes('data-game-current-hp'))

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'LIGHTNING_APPLIED',
        payload: {
          gameRoomId: 100,
          userId: 1,
          serverReceiveTime: Date.now(),
          lightningTimeMs: 2000,
          starCoreHpAtLightning: hpBeforeLightning,
          damage: 1200,
          afterHp: hpBeforeLightning - 1200,
          isKill: false,
          cooldownUntil: Date.now() + 2000,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    const hpAfterLightning = Number(wrapper.get('main').attributes('data-game-current-hp'))

    expect(hpAfterLightning).toBeLessThanOrEqual(hpBeforeLightning - 1200)
    expect(hpAfterLightning).toBeGreaterThanOrEqual(hpBeforeLightning - 1210)
    expect(wrapper.get('main').attributes('data-game-lightning-sent')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('false')
  })

  it('renders opponent LIGHTNING as a red impact without showing an opponent spell HUD', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    const hpBeforeLightning = Number(wrapper.get('main').attributes('data-game-current-hp'))

    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'LIGHTNING_APPLIED',
        payload: {
          gameRoomId: 100,
          userId: 2,
          serverReceiveTime: Date.now(),
          lightningTimeMs: 2000,
          starCoreHpAtLightning: hpBeforeLightning,
          damage: 1200,
          afterHp: hpBeforeLightning - 1200,
          isKill: false,
          cooldownUntil: Date.now() + 2000,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="opponent-lightning-hud"]').exists()).toBe(false)
    expect(wrapper.get('.lightning-impact').attributes('data-lightning-impact-owner')).toBe(
      'opponent',
    )
    expect(Number(wrapper.get('main').attributes('data-game-current-hp'))).toBeLessThanOrEqual(
      hpBeforeLightning - 1200,
    )
  })

  it('waits for GAME_RESULT winnerUserId before treating a kill LIGHTNING as a result', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'LIGHTNING_APPLIED',
        payload: {
          gameRoomId: 100,
          userId: 2,
          serverReceiveTime: Date.now(),
          lightningTimeMs: 2000,
          starCoreHpAtLightning: 1000,
          damage: 1200,
          afterHp: 0,
          isKill: true,
          cooldownUntil: Date.now() + 2000,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('LIGHTNING_APPLIED')

    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: {
          gameRoomId: 100,
          result: 'PLAYER2_WIN',
          winnerUserId: 2,
          reason: 'LIGHTNING_KILL',
          finishedAt: Date.now(),
          actions: [
            {
              userId: 2,
              serverReceiveTime: Date.now(),
              lightningTimeMs: 2000,
              starCoreHpAtLightning: 1000,
              damage: 1200,
              afterHp: 0,
              isKill: true,
            },
          ],
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-socket-last-event')).toBe('GAME_RESULT')
  })

  it('does not overwrite the current HP with GAME_RESULT action afterHp', async () => {
    saveValidPlayPayloads()

    const wrapper = mount(GamePlayPage)
    await flushPromises()
    getGameWebSocketHandlers().onOpen?.(new Event('open'))
    await wrapper.vm.$nextTick()

    setStarTargeted(true)
    await wrapper.vm.$nextTick()
    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'LIGHTNING_APPLIED',
        payload: {
          gameRoomId: 100,
          userId: 1,
          serverReceiveTime: Date.now(),
          lightningTimeMs: 2000,
          starCoreHpAtLightning: 8666,
          damage: 1200,
          afterHp: 7466,
          isKill: false,
          cooldownUntil: Date.now() + 2000,
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    const hpAfterPendingDamage = Number(wrapper.get('main').attributes('data-game-current-hp'))

    getGameWebSocketHandlers().onMessage?.(
      {
        type: 'GAME_RESULT',
        payload: {
          gameRoomId: 100,
          result: 'PLAYER1_WIN',
          winnerUserId: 1,
          reason: 'LIGHTNING_KILL',
          finishedAt: Date.now(),
          actions: [
            {
              userId: 1,
              serverReceiveTime: Date.now(),
              lightningTimeMs: 2000,
              starCoreHpAtLightning: 10000,
              damage: 1200,
              afterHp: 8800,
              isKill: false,
            },
          ],
        },
      },
      new MessageEvent('message'),
    )
    await wrapper.vm.$nextTick()

    expect(Number(wrapper.get('main').attributes('data-game-current-hp'))).toBe(
      hpAfterPendingDamage,
    )
    expect(wrapper.get('main').attributes('data-game-result-received')).toBe('true')
  })
})

function createGameResultPayload(
  overrides: {
    result?: 'PLAYER1_WIN' | 'PLAYER2_WIN' | 'DRAW'
    winnerUserId?: number | null
    reason?: string
  } = {},
) {
  const winnerUserId = overrides.winnerUserId ?? 1

  return {
    gameRoomId: 100,
    result: overrides.result ?? 'PLAYER1_WIN',
    winnerUserId,
    reason: overrides.reason ?? 'LIGHTNING_KILL',
    finishedAt: Date.now(),
    actions:
      winnerUserId === null
        ? []
        : [
            {
              userId: winnerUserId,
              serverReceiveTime: Date.now(),
              lightningTimeMs: 2000,
              starCoreHpAtLightning: 1000,
              damage: 1200,
              afterHp: 0,
              isKill: true,
            },
          ],
  }
}

function saveValidPlayPayloads(options: { startAt?: number } = {}): void {
  const startAt = options.startAt ?? Date.now() - 2000

  saveGameStartPayload({
    gameRoomId: 100,
    serverTime: Date.now() - 5000,
    startAt,
    scenario: {
      starCoreMaxHp: 10000,
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
      webSocketUrl: '/ws/game/100',
    },
    receivedAt: '2026-06-01T00:00:00.000Z',
  })
}
