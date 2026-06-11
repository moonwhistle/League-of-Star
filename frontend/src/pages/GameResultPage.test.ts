import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { saveGameResultPayload } from '@/services/gameResultPayload'
import { saveGameWaitingPayload } from '@/services/gameWaitingPayload'
import { getGameSummary } from '@/services/gameSummaryService'

import GameResultPage from './GameResultPage.vue'

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

vi.mock('@/services/gameSummaryService', () => ({
  getGameSummary: vi.fn(),
}))

const { setLocale } = useLocale()
const getGameSummaryMock = vi.mocked(getGameSummary)

enableAutoUnmount(afterEach)

describe('GameResultPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
    setLocale('ko')
  })

  it('renders YOU WIN when the winner is not the stored opponent', async () => {
    saveValidResultPayload({
      winnerUserId: 1,
      result: 'PLAYER1_WIN',
    })
    saveValidWaitingPayload()

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-result-payload-ready')).toBe('true')
    expect(wrapper.get('main').attributes('data-game-result-outcome')).toBe('win')
    expect(wrapper.get('h1').text()).toBe('YOU WIN')
    expect(wrapper.get('main').attributes('data-game-result-reason')).toBe('LIGHTNING_KILL')
    expect(routerReplaceMock).not.toHaveBeenCalled()
    expect(getGameSummaryMock).not.toHaveBeenCalled()
  })

  it('renders YOU LOSE when the winner is the stored opponent', async () => {
    saveValidResultPayload({
      winnerUserId: 2,
      result: 'PLAYER2_WIN',
    })
    saveValidWaitingPayload()

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-result-outcome')).toBe('lose')
    expect(wrapper.get('h1').text()).toBe('YOU LOSE')
  })

  it('renders DRAW when winnerUserId is null', async () => {
    saveValidResultPayload({
      winnerUserId: null,
      result: 'DRAW',
      reason: 'NATURAL_DEATH_DRAW',
    })

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-result-outcome')).toBe('draw')
    expect(wrapper.get('h1').text()).toBe('DRAW')
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('returns to match when the result payload is missing', async () => {
    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-result-payload-ready')).toBe('false')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match when the result payload cannot resolve a win or loss', async () => {
    saveValidResultPayload({
      winnerUserId: 1,
      result: 'PLAYER1_WIN',
    })

    mount(GameResultPage)
    await flushPromises()

    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })
})

function saveValidResultPayload(
  overrides: {
    result?: 'PLAYER1_WIN' | 'PLAYER2_WIN' | 'DRAW'
    winnerUserId?: number | null
    reason?: string
  } = {},
): void {
  saveGameResultPayload({
    gameRoomId: 100,
    result: overrides.result ?? 'PLAYER1_WIN',
    winnerUserId: overrides.winnerUserId ?? 1,
    reason: overrides.reason ?? 'LIGHTNING_KILL',
    finishedAt: 1716192017000,
    actions: [
      {
        userId: overrides.winnerUserId ?? 1,
        serverReceiveTime: 1716192010000,
        lightningTimeMs: 6000,
        starCoreHpAtLightning: 1000,
        damage: 1200,
        afterHp: 0,
        isKill: true,
      },
    ],
    receivedAt: '2026-06-01T00:00:00.000Z',
  })
}

function saveValidWaitingPayload(): void {
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
      webSocketUrl: '/ws/game/100',
    },
    receivedAt: '2026-06-01T00:00:00.000Z',
  })
}
