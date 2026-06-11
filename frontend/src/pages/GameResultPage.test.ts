import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { saveGameResultPayload } from '@/services/gameResultPayload'
import { saveGameWaitingPayload } from '@/services/gameWaitingPayload'
import { getGameSummary } from '@/services/gameSummaryService'
import type { GameSummaryDoneResponse, GameSummaryPlayer } from '@/types/game'

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
    vi.useRealTimers()
    window.sessionStorage.clear()
    routeMock.params.gameRoomId = '100'
    routerReplaceMock.mockResolvedValue(undefined)
    setLocale('ko')
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('calls the summary API even when the stored GAME_RESULT payload is missing', async () => {
    getGameSummaryMock.mockResolvedValue(createDoneSummary())

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(getGameSummaryMock).toHaveBeenCalledWith(100, expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-game-result-payload-ready')).toBe('false')
    expect(wrapper.get('main').attributes('data-game-summary-status')).toBe('done')
    expect(wrapper.get('h1').text()).toBe('YOU WIN')
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('uses a stored GAME_RESULT payload as a temporary title before summary is done', async () => {
    saveValidResultPayload({
      winnerUserId: 2,
      result: 'PLAYER2_WIN',
    })
    saveValidWaitingPayload()
    getGameSummaryMock.mockImplementation(() => new Promise(() => {}))

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('YOU LOSE')
    expect(wrapper.get('main').attributes('data-game-summary-status')).toBe('loading')
  })

  it('polls again after PENDING using retryAfterMillis', async () => {
    vi.useFakeTimers()
    getGameSummaryMock
      .mockResolvedValueOnce({
        summaryStatus: 'PENDING',
        gameId: 100,
        retryAfterMillis: 250,
      })
      .mockResolvedValueOnce(createDoneSummary())

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-summary-status')).toBe('pending')
    expect(wrapper.text()).toContain('랭크 정산이 진행 중입니다')

    await vi.advanceTimersByTimeAsync(249)
    expect(getGameSummaryMock).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()

    expect(getGameSummaryMock).toHaveBeenCalledTimes(2)
    expect(wrapper.get('main').attributes('data-game-summary-status')).toBe('done')
  })

  it('falls back to 1000ms when retryAfterMillis is invalid', async () => {
    vi.useFakeTimers()
    getGameSummaryMock
      .mockResolvedValueOnce({
        summaryStatus: 'PENDING',
        gameId: 100,
        retryAfterMillis: 0,
      })
      .mockResolvedValueOnce(createDoneSummary())

    mount(GameResultPage)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(999)
    expect(getGameSummaryMock).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1)
    expect(getGameSummaryMock).toHaveBeenCalledTimes(2)
  })

  it('renders YOU LOSE from a DONE summary when my result is LOSS', async () => {
    getGameSummaryMock.mockResolvedValue(
      createDoneSummary({
        gameResult: 'PLAYER2_WIN',
        winnerUserId: 2,
        me: createPlayerSummary({
          result: 'LOSS',
        }),
        opponent: createPlayerSummary({
          userId: 2,
          nickname: 'Voidwalker',
          result: 'WIN',
        }),
      }),
    )

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-result-outcome')).toBe('lose')
    expect(wrapper.get('h1').text()).toBe('YOU LOSE')
  })

  it('renders DRAW from a DONE summary', async () => {
    getGameSummaryMock.mockResolvedValue(
      createDoneSummary({
        gameResult: 'DRAW',
        winnerUserId: null,
        me: createPlayerSummary({
          result: 'DRAW',
          lpChange: 0,
        }),
        opponent: createPlayerSummary({
          userId: 2,
          nickname: 'Voidwalker',
          result: 'DRAW',
          lpChange: 0,
        }),
      }),
    )

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-result-outcome')).toBe('draw')
    expect(wrapper.get('h1').text()).toBe('DRAW')
  })

  it('renders player LP, rank, and series summary fields from DONE', async () => {
    getGameSummaryMock.mockResolvedValue(
      createDoneSummary({
        me: createPlayerSummary({
          nickname: 'Starlord',
          lpBefore: 100,
          lpAfter: 125,
          lpChange: 25,
          rankBefore: 'GOLD_IV',
          rankAfter: 'GOLD_III',
          seriesType: 'PROMOTION',
          rankSeriesId: 77,
        }),
        opponent: createPlayerSummary({
          userId: 2,
          nickname: 'Voidwalker',
          result: 'LOSS',
          lpBefore: 80,
          lpAfter: 65,
          lpChange: -15,
          rankBefore: 'SILVER_I',
          rankAfter: 'SILVER_I',
        }),
      }),
    )

    const wrapper = mount(GameResultPage)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Starlord')
    expect(text).toContain('Voidwalker')
    expect(text).toContain('GOLD_IV -> GOLD_III')
    expect(text).toContain('100 -> 125 (+25)')
    expect(text).toContain('승급전')
    expect(text).toContain('77')
    expect(text).toContain('80 -> 65 (-15)')
    expect(text).not.toContain('종료 사유')
    expect(text).not.toContain('게임룸')
  })

  it('renders API error messages and allows returning to match', async () => {
    getGameSummaryMock.mockRejectedValue(
      new ApiClientError(403, {
        message: '참가자가 아닌 게임입니다.',
        code: 'AUTH_FORBIDDEN',
      }),
    )

    const wrapper = mount(GameResultPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-game-summary-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-game-summary-error-status')).toBe('403')
    expect(wrapper.get('[role="alert"]').text()).toBe('참가자가 아닌 게임입니다.')

    await wrapper.get('button').trigger('click')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('returns to match without calling summary when the route gameRoomId is invalid', async () => {
    routeMock.params.gameRoomId = 'invalid'

    mount(GameResultPage)
    await flushPromises()

    expect(getGameSummaryMock).not.toHaveBeenCalled()
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('aborts in-flight requests when unmounted', async () => {
    let capturedSignal: AbortSignal | undefined
    getGameSummaryMock.mockImplementation((_gameId, signal) => {
      capturedSignal = signal
      return new Promise(() => {})
    })

    const wrapper = mount(GameResultPage)
    await flushPromises()
    wrapper.unmount()

    expect(capturedSignal?.aborted).toBe(true)
  })

  it('stops polling when unmounted before the next retry', async () => {
    vi.useFakeTimers()
    getGameSummaryMock.mockResolvedValue({
      summaryStatus: 'PENDING',
      gameId: 100,
      retryAfterMillis: 250,
    })

    const wrapper = mount(GameResultPage)
    await flushPromises()
    wrapper.unmount()

    await vi.advanceTimersByTimeAsync(250)

    expect(getGameSummaryMock).toHaveBeenCalledTimes(1)
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

function createDoneSummary(
  overrides: Partial<GameSummaryDoneResponse> = {},
): GameSummaryDoneResponse {
  return {
    summaryStatus: 'DONE',
    gameId: 100,
    gameResult: 'PLAYER1_WIN',
    winnerUserId: 1,
    finishedAt: '2026-06-01T00:00:00',
    me: createPlayerSummary({
      result: 'WIN',
    }),
    opponent: createPlayerSummary({
      userId: 2,
      nickname: 'Voidwalker',
      result: 'LOSS',
    }),
    ...overrides,
  }
}

function createPlayerSummary(overrides: Partial<GameSummaryPlayer> = {}): GameSummaryPlayer {
  return {
    userId: 1,
    nickname: 'Starlord',
    result: 'WIN',
    lpBefore: 100,
    lpAfter: 125,
    lpChange: 25,
    rankBefore: 'GOLD_IV',
    rankAfter: 'GOLD_III',
    seriesType: 'RANK',
    rankSeriesId: null,
    ...overrides,
  }
}
