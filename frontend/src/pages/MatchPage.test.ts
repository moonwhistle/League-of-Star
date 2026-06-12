import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { logout } from '@/services/authService'
import { clearAuthTokens, getRefreshToken } from '@/services/authToken'
import { readGameWaitingPayload } from '@/services/gameWaitingPayload'
import { acceptMatch, joinMatchQueue, leaveMatchQueue, rejectMatch } from '@/services/matchService'
import { getMyProfile } from '@/services/profileService'
import { getMyRank } from '@/services/rankService'
import {
  connectMatchEventSource,
  type MatchEventSourceHandlers,
} from '@/services/realtime/matchEventSource'

import MatchPage from './MatchPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/realtime/matchEventSource', () => ({
  connectMatchEventSource: vi.fn(),
}))

vi.mock('@/services/matchService', () => ({
  acceptMatch: vi.fn(),
  joinMatchQueue: vi.fn(),
  leaveMatchQueue: vi.fn(),
  rejectMatch: vi.fn(),
}))

vi.mock('@/services/profileService', () => ({
  getMyProfile: vi.fn(),
}))

vi.mock('@/services/rankService', () => ({
  getMyRank: vi.fn(),
}))

vi.mock('@/services/authService', () => ({
  logout: vi.fn(),
}))

vi.mock('@/services/authToken', () => ({
  clearAuthTokens: vi.fn(),
  getRefreshToken: vi.fn(),
}))

const connectMatchEventSourceMock = vi.mocked(connectMatchEventSource)
const logoutMock = vi.mocked(logout)
const clearAuthTokensMock = vi.mocked(clearAuthTokens)
const getRefreshTokenMock = vi.mocked(getRefreshToken)
const acceptMatchMock = vi.mocked(acceptMatch)
const joinMatchQueueMock = vi.mocked(joinMatchQueue)
const leaveMatchQueueMock = vi.mocked(leaveMatchQueue)
const rejectMatchMock = vi.mocked(rejectMatch)
const getMyProfileMock = vi.mocked(getMyProfile)
const getMyRankMock = vi.mocked(getMyRank)
const closeMatchEventSourceMock = vi.fn()
const { setLocale } = useLocale()

let currentHandlers: MatchEventSourceHandlers | undefined

function getCurrentHandlers(): MatchEventSourceHandlers {
  if (currentHandlers === undefined) {
    throw new Error('Match event source handlers were not registered.')
  }

  return currentHandlers
}

function getStartButton(wrapper: VueWrapper) {
  return wrapper.get('[data-testid="match-start-button"]')
}

function getLogoutButton(wrapper: VueWrapper) {
  return wrapper.get('[aria-label="로그아웃"]')
}

async function openLogoutConfirm(wrapper: VueWrapper) {
  await getLogoutButton(wrapper).trigger('click')
  await wrapper.vm.$nextTick()
}

async function confirmLogout(wrapper: VueWrapper) {
  await openLogoutConfirm(wrapper)
  await wrapper.get('.match-logout-confirm').trigger('click')
  await flushPromises()
}

describe('MatchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
    setLocale('ko')
    currentHandlers = undefined
    window.sessionStorage.clear()
    routerPushMock.mockResolvedValue(undefined)
    getRefreshTokenMock.mockReturnValue('refresh-token')
    logoutMock.mockResolvedValue(undefined)
    acceptMatchMock.mockResolvedValue(undefined)
    joinMatchQueueMock.mockResolvedValue(undefined)
    leaveMatchQueueMock.mockResolvedValue(undefined)
    rejectMatchMock.mockResolvedValue(undefined)
    getMyProfileMock.mockResolvedValue({
      userId: 1,
      email: 'moon@example.com',
      nickname: 'MoonStar',
      createdAt: '2026-06-12T10:00:00',
    })
    getMyRankMock.mockResolvedValue({
      userId: 1,
      tier: 'GOLD',
      division: 'IV',
      rank: 'GOLD_IV',
      lp: 40,
      tierScore: 13,
      wins: 12,
      losses: 8,
      draws: 1,
      rankUpdatedAt: '2026-06-12T10:00:00',
    })
    connectMatchEventSourceMock.mockImplementation((handlers = {}) => {
      currentHandlers = handlers

      return {
        close: closeMatchEventSourceMock,
      }
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not connect the match event stream on mount', async () => {
    const wrapper = mount(MatchPage)
    await wrapper.vm.$nextTick()

    expect(connectMatchEventSourceMock).not.toHaveBeenCalled()
    expect(joinMatchQueueMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(wrapper.get('main').attributes('data-queue-error-message')).toBe('')
    expect(getStartButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('renders the match page lobby layout with profile and rank from backend contracts', async () => {
    const wrapper = mount(MatchPage)
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('LEAGUE OF STAR')
    expect(wrapper.findAll('.match-actions .icon-button')).toHaveLength(2)
    expect(wrapper.find('[aria-label="전적 보기"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="로그아웃"]').exists()).toBe(true)
    expect(getMyProfileMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(getMyRankMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(wrapper.get('[aria-label="Player profile"]').text()).toContain('MoonStar')
    expect(wrapper.get('[aria-label="Player profile"]').text()).not.toContain('Bronze IV')
    expect(wrapper.get('[aria-label="Ranking summary"]').text()).toContain('랭킹')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('GOLD_IV')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('현재 랭크')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('40 LP')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('12승 8패 1무')
    expect(wrapper.text()).not.toContain('RP')
    expect(wrapper.get('[data-testid="match-start-button"]').text()).toContain('매칭 시작')
    expect(wrapper.text()).toContain('연습 모드')
    expect(wrapper.text()).toContain('사용자 지정')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(wrapper.get('main').attributes('data-profile-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-rank-status')).toBe('success')
  })

  it('keeps matchmaking available when the profile request fails', async () => {
    getMyProfileMock.mockRejectedValueOnce(new Error('profile failed'))
    const wrapper = mount(MatchPage)
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-profile-status')).toBe('error')
    expect(main.attributes('data-profile-error-message')).toBe('profile failed')
    expect(main.attributes('data-rank-status')).toBe('success')
    expect(wrapper.get('[aria-label="Player profile"]').text()).toContain('프로필 정보 없음')
    expect(getStartButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('keeps matchmaking available when the rank request fails', async () => {
    getMyRankMock.mockRejectedValueOnce(new Error('rank failed'))
    const wrapper = mount(MatchPage)
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-profile-status')).toBe('success')
    expect(main.attributes('data-rank-status')).toBe('error')
    expect(main.attributes('data-rank-error-message')).toBe('rank failed')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('랭크 정보 없음')
    expect(getStartButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('toggles match page copy between Korean and English', async () => {
    const wrapper = mount(MatchPage)

    expect(wrapper.get('[aria-label="Ranking summary"]').text()).toContain('랭킹')

    await wrapper.get('.match-locale-toggle').trigger('click')

    expect(wrapper.get('[aria-label="Ranking summary"]').text()).toContain('Ranking')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('Current Rank')
    expect(wrapper.get('[data-testid="match-start-button"]').text()).toContain('Start Matching')
    expect(wrapper.find('[aria-label="Records"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Logout"]').exists()).toBe(true)
  })

  it('logs out from ready state with backend revoke before clearing local tokens', async () => {
    const wrapper = mount(MatchPage)

    await confirmLogout(wrapper)

    expect(logoutMock).toHaveBeenCalledWith('refresh-token', expect.any(AbortSignal))
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.login })
    expect(wrapper.get('main').attributes('data-logout-pending')).toBe('false')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
  })

  it('opens and cancels the logout confirmation without logging out', async () => {
    const wrapper = mount(MatchPage)

    await openLogoutConfirm(wrapper)

    expect(wrapper.get('main').attributes('data-logout-confirm-open')).toBe('true')
    expect(wrapper.get('[aria-labelledby="match-logout-title"]').text()).toContain(
      '로그아웃하시겠습니까?',
    )

    await wrapper.get('.match-logout-cancel').trigger('click')
    await flushPromises()

    expect(wrapper.get('main').attributes('data-logout-confirm-open')).toBe('false')
    expect(logoutMock).not.toHaveBeenCalled()
    expect(clearAuthTokensMock).not.toHaveBeenCalled()
    expect(routerPushMock).not.toHaveBeenCalled()
  })

  it('clears local tokens without backend logout when refresh token is missing', async () => {
    getRefreshTokenMock.mockReturnValue(null)
    const wrapper = mount(MatchPage)

    await confirmLogout(wrapper)

    expect(logoutMock).not.toHaveBeenCalled()
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.login })
  })

  it('clears local tokens and moves to login even when backend logout fails', async () => {
    logoutMock.mockRejectedValueOnce(new Error('logout failed'))
    const wrapper = mount(MatchPage)

    await confirmLogout(wrapper)

    expect(logoutMock).toHaveBeenCalledTimes(1)
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.login })
  })

  it('leaves the queue before logout when queued state has joined the backend queue', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    await confirmLogout(wrapper)

    expect(leaveMatchQueueMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(logoutMock).toHaveBeenCalledWith('refresh-token', expect.any(AbortSignal))
    const leaveCallOrder = leaveMatchQueueMock.mock.invocationCallOrder[0]
    const logoutCallOrder = logoutMock.mock.invocationCallOrder[0]

    expect(leaveCallOrder).toBeDefined()
    expect(logoutCallOrder).toBeDefined()
    expect(leaveCallOrder!).toBeLessThan(logoutCallOrder!)
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.login })
    expect(closeMatchEventSourceMock).toHaveBeenCalled()
  })

  it('continues logout even when queue leave fails', async () => {
    leaveMatchQueueMock.mockRejectedValueOnce(new Error('leave failed'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    await confirmLogout(wrapper)

    expect(leaveMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(logoutMock).toHaveBeenCalledTimes(1)
    expect(clearAuthTokensMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.login })
  })

  it('blocks duplicate logout clicks while logout is pending', async () => {
    logoutMock.mockReturnValueOnce(new Promise<void>(() => {}))
    const wrapper = mount(MatchPage)

    await openLogoutConfirm(wrapper)
    await wrapper.get('.match-logout-confirm').trigger('click')
    await wrapper.vm.$nextTick()
    await getLogoutButton(wrapper).trigger('click')

    expect(logoutMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-logout-pending')).toBe('true')
    expect(wrapper.find('.match-logout-confirm').exists()).toBe(false)
  })

  it('starts the waiting timer immediately while the stream is connecting', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    const handlers = getCurrentHandlers()

    handlers.onOpen?.(new Response())
    await wrapper.vm.$nextTick()

    expect(connectMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(joinMatchQueueMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('connecting')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')
    expect(getStartButton(wrapper).text()).toBe('1')
    expect(getStartButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('cancels locally without leave when the queue join has not started yet', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-stream-status')).toBe('connecting')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')

    await getStartButton(wrapper).trigger('click')
    await flushPromises()

    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
    expect(joinMatchQueueMock).not.toHaveBeenCalled()
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(getStartButton(wrapper).text()).toContain('매칭 시작')
  })

  it('connects the stream before joining the queue and cancels with leave', async () => {
    vi.useFakeTimers()
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')

    expect(connectMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(joinMatchQueueMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('connecting')

    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    expect(joinMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')
    expect(getStartButton(wrapper).text()).toBe('1')
    expect(getStartButton(wrapper).classes()).toContain('is-waiting')

    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()

    expect(getStartButton(wrapper).text()).toBe('3')

    await getStartButton(wrapper).trigger('click')
    await flushPromises()

    expect(leaveMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(getStartButton(wrapper).text()).toContain('매칭 시작')
    expect(getStartButton(wrapper).classes()).not.toContain('is-waiting')
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
  })

  it('stores stream event payloads and moves to game waiting when matched', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    const handlers = getCurrentHandlers()

    handlers.onOpen?.(new Response())
    handlers.onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    handlers.onHeartbeat?.({
      sentAt: '2026-06-01T00:00:01Z',
    })
    handlers.onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: '2026-06-01T00:00:02Z',
    })
    handlers.onMatchResponseResult?.({
      matchId: 'match-1',
      outcome: 'MATCHED',
      reason: 'BOTH_ACCEPTED',
      action: 'GO_TO_GAME_WAITING',
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
    })
    await wrapper.vm.$nextTick()
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-can-start-match')).toBe('true')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-connected-user-id')).toBe('1')
    expect(main.attributes('data-last-heartbeat-at')).toBe('2026-06-01T00:00:01Z')
    expect(main.attributes('data-match-found-id')).toBe('match-1')
    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-match-response-command-status')).toBe('idle')
    expect(main.attributes('data-match-response-command-pending')).toBe('false')
    expect(main.attributes('data-match-result-action')).toBe('GO_TO_GAME_WAITING')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(getStartButton(wrapper).attributes('disabled')).toBeUndefined()
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(readGameWaitingPayload(100)).toEqual({
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
      receivedAt: expect.any(String),
    })
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.gameWaiting,
      params: {
        gameRoomId: '100',
      },
    })

    handlers.onMatchResponseResult?.({
      matchId: 'match-1',
      outcome: 'FAILED',
      reason: 'OPPONENT_REJECTED',
      action: 'RETURN_TO_MATCHING',
      opponent: null,
      game: null,
    })
    handlers.onMatchFound?.({
      matchId: 'late-match',
      userId: 1,
      opponentUserId: 3,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: '2026-06-01T00:00:03Z',
    })
    handlers.onConnected?.({
      userId: 9,
      connectedAt: '2026-06-01T00:00:04Z',
    })
    handlers.onHeartbeat?.({
      sentAt: '2026-06-01T00:00:05Z',
    })
    handlers.onError?.(new Error('late stream error'))
    await wrapper.vm.$nextTick()

    expect(main.attributes('data-match-result-action')).toBe('GO_TO_GAME_WAITING')
    expect(main.attributes('data-match-found-id')).toBe('match-1')
    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-connected-user-id')).toBe('1')
    expect(main.attributes('data-last-heartbeat-at')).toBe('2026-06-01T00:00:01Z')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledTimes(1)
  })

  it('opens match found state and stops the waiting timer when match_found arrives', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00Z'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()

    expect(getStartButton(wrapper).text()).toBe('3')

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: '2026-06-01T00:00:02Z',
    })
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-id')).toBe('match-1')
    expect(main.attributes('data-match-found-modal-open')).toBe('true')
    expect(main.attributes('data-match-found-countdown-seconds')).toBe('10')
    expect(main.attributes('data-match-found-loading')).toBe('false')
    expect(main.attributes('data-match-response-command-status')).toBe('idle')
    expect(main.attributes('data-match-response-command-pending')).toBe('false')
    expect(main.attributes('data-can-submit-match-response')).toBe('true')
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(getStartButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('매칭 성사')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain(
      '상대를 찾았습니다',
    )
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('응답 대기 시간')
    expect(wrapper.get('[data-testid="match-found-logo"]').attributes('src')).toBeTruthy()
    expect(wrapper.get('.match-found-accept').text()).toBe('수락')
    expect(wrapper.get('.match-found-decline').text()).toBe('거절')
    expect(wrapper.get('.match-found-accept').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.match-found-decline').attributes('disabled')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()

    expect(main.attributes('data-match-found-countdown-seconds')).toBe('8')
    expect(getStartButton(wrapper).text()).toBe('3')
  })

  it('keeps match found state in loading without resetting when the countdown reaches zero', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00Z'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 2,
      eventCreatedAt: '2026-06-01T00:00:00Z',
    })
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-modal-open')).toBe('true')
    expect(main.attributes('data-match-found-countdown-seconds')).toBe('0')
    expect(main.attributes('data-match-found-loading')).toBe('true')
    expect(main.attributes('data-match-response-command-status')).toBe('idle')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('로딩중...')

    await wrapper.get('.match-found-accept').trigger('click')
    await wrapper.get('.match-found-decline').trigger('click')

    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
    expect(acceptMatchMock).not.toHaveBeenCalled()
    expect(rejectMatchMock).not.toHaveBeenCalled()
    expect(closeMatchEventSourceMock).not.toHaveBeenCalled()
  })

  it('uses acceptTimeoutSeconds as a local countdown fallback when eventCreatedAt is invalid', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00Z'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 7,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-countdown-seconds')).toBe('7')

    await vi.advanceTimersByTimeAsync(3000)
    await wrapper.vm.$nextTick()

    expect(main.attributes('data-match-found-countdown-seconds')).toBe('4')
  })

  it('falls back to loading state when acceptTimeoutSeconds is not finite', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00Z'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: Number.NaN,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-countdown-seconds')).toBe('0')
    expect(main.attributes('data-match-found-loading')).toBe('true')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('로딩중...')
  })

  it('blocks match response submission when match_found does not include a match id', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: '',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-modal-open')).toBe('true')
    expect(main.attributes('data-match-response-command-status')).toBe('idle')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
  })

  it('blocks the background match action while match found state is active', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await getStartButton(wrapper).trigger('click')
    await flushPromises()

    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')
    expect(getStartButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('blocks logout while match found state is active', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    expect(getLogoutButton(wrapper).attributes('disabled')).toBeDefined()

    await getLogoutButton(wrapper).trigger('click')
    await flushPromises()

    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
    expect(logoutMock).not.toHaveBeenCalled()
    expect(clearAuthTokensMock).not.toHaveBeenCalled()
    expect(routerPushMock).not.toHaveBeenCalled()
  })

  it('returns to ready and closes the stream when result action is GO_TO_MATCH_START', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    getCurrentHandlers().onMatchResponseResult?.({
      matchId: 'match-1',
      outcome: 'FAILED',
      reason: 'MY_REJECTED',
      action: 'GO_TO_MATCH_START',
      opponent: null,
      game: null,
    })
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-match-found-id')).toBe('')
    expect(main.attributes('data-match-result-action')).toBe('')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).not.toHaveBeenCalled()
  })

  it('returns to queued without closing or rejoining when result action is RETURN_TO_MATCHING', async () => {
    vi.useFakeTimers()
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    getCurrentHandlers().onMatchResponseResult?.({
      matchId: 'match-1',
      outcome: 'FAILED',
      reason: 'OPPONENT_REJECTED',
      action: 'RETURN_TO_MATCHING',
      opponent: null,
      game: null,
    })
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-match-found-id')).toBe('')
    expect(main.attributes('data-match-result-action')).toBe('')
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(getStartButton(wrapper).text()).toBe('1')
    expect(closeMatchEventSourceMock).not.toHaveBeenCalled()
    expect(joinMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
    expect(routerPushMock).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1000)
    await wrapper.vm.$nextTick()

    expect(getStartButton(wrapper).text()).toBe('2')

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-2',
      userId: 1,
      opponentUserId: 3,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    expect(main.attributes('data-match-found-id')).toBe('match-2')
    expect(main.attributes('data-match-found-modal-open')).toBe('true')
    expect(main.attributes('data-can-submit-match-response')).toBe('true')
  })

  it('does not move to game waiting when GO_TO_GAME_WAITING omits game payload', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    getCurrentHandlers().onMatchResponseResult?.({
      matchId: 'match-1',
      outcome: 'MATCHED',
      reason: 'BOTH_ACCEPTED',
      action: 'GO_TO_GAME_WAITING',
      opponent: null,
      game: null,
    })
    await flushPromises()

    const main = wrapper.get('main')

    expect(routerPushMock).not.toHaveBeenCalled()
    expect(readGameWaitingPayload(100)).toBeNull()
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 결과를 처리하지 못했습니다. 다시 시도해 주세요.',
    )
  })

  it('toggles match found modal copy between Korean and English', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('매칭 성사')

    await wrapper.get('.match-locale-toggle').trigger('click')

    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('Match Found')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('Opponent Found')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('Response Time')
    expect(wrapper.get('.match-found-accept').text()).toBe('Accept')
    expect(wrapper.get('.match-found-decline').text()).toBe('Decline')
  })

  it('submits accept command and waits for the final SSE result', async () => {
    let resolveAccept: () => void = () => {}
    acceptMatchMock.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        resolveAccept = resolve
      }),
    )
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await wrapper.get('.match-found-accept').trigger('click')
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(acceptMatchMock).toHaveBeenCalledWith('match-1', expect.any(AbortSignal))
    expect(rejectMatchMock).not.toHaveBeenCalled()
    expect(main.attributes('data-match-response-command-status')).toBe('accepting')
    expect(main.attributes('data-match-response-command-pending')).toBe('true')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(wrapper.get('.match-found-accept').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.match-found-decline').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('수락 중...')

    await wrapper.get('.match-found-accept').trigger('click')
    expect(acceptMatchMock).toHaveBeenCalledTimes(1)

    resolveAccept()
    await flushPromises()

    expect(main.attributes('data-match-response-command-status')).toBe('accepted')
    expect(main.attributes('data-match-response-command-pending')).toBe('false')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('상대 응답 대기')
    expect(wrapper.get('.match-found-accept').text()).toBe('수락 완료')
    expect(wrapper.get('main').attributes('data-match-found-modal-open')).toBe('true')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('connected')
    expect(closeMatchEventSourceMock).not.toHaveBeenCalled()

    await wrapper.get('.match-locale-toggle').trigger('click')

    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain(
      'Waiting for opponent',
    )
    expect(wrapper.get('.match-found-accept').text()).toBe('Accepted')
  })

  it('blocks decline when accept command is already pending', async () => {
    acceptMatchMock.mockReturnValueOnce(new Promise<void>(() => {}))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await wrapper.get('.match-found-accept').trigger('click')
    await wrapper.get('.match-found-decline').trigger('click')
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(acceptMatchMock).toHaveBeenCalledTimes(1)
    expect(rejectMatchMock).not.toHaveBeenCalled()
    expect(main.attributes('data-match-response-command-status')).toBe('accepting')
    expect(main.attributes('data-match-response-command-pending')).toBe('true')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('수락 중...')
    expect(getLogoutButton(wrapper).attributes('disabled')).toBeDefined()

    await getLogoutButton(wrapper).trigger('click')
    await flushPromises()

    expect(logoutMock).not.toHaveBeenCalled()
    expect(clearAuthTokensMock).not.toHaveBeenCalled()
  })

  it('submits reject command and waits for the final SSE result', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await wrapper.get('.match-found-decline').trigger('click')
    await flushPromises()

    expect(rejectMatchMock).toHaveBeenCalledWith('match-1', expect.any(AbortSignal))
    expect(acceptMatchMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-match-response-command-status')).toBe('rejected')
    expect(wrapper.get('main').attributes('data-can-submit-match-response')).toBe('false')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('결과 대기')
    expect(wrapper.get('.match-found-decline').text()).toBe('거절 완료')
    expect(wrapper.get('main').attributes('data-match-found-modal-open')).toBe('true')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('connected')
    expect(closeMatchEventSourceMock).not.toHaveBeenCalled()
  })

  it('keeps the modal open and waits for SSE when response lock fails', async () => {
    acceptMatchMock.mockRejectedValueOnce(
      new ApiClientError(409, {
        code: 'MATCH_012',
        message: '매칭 응답 처리 중입니다. 잠시 후 다시 시도해주세요.',
      }),
    )
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await wrapper.get('.match-found-accept').trigger('click')
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-response-command-status')).toBe('lockWaiting')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(main.attributes('data-match-found-modal-open')).toBe('true')
    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('응답 처리 중...')
    expect(wrapper.find('.match-error-dialog').exists()).toBe(false)
    expect(closeMatchEventSourceMock).not.toHaveBeenCalled()
  })

  it('closes the modal and returns to ready when response command fails generally', async () => {
    acceptMatchMock.mockRejectedValueOnce(new Error('accept failed'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await wrapper.get('.match-found-accept').trigger('click')
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-match-response-command-status')).toBe('idle')
    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-queue-error-message')).toBe('accept failed')
    expect(wrapper.get('[role="dialog"]').text()).toContain('accept failed')
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
  })

  it('aborts an in-flight match response command on unmount', async () => {
    let acceptSignal: AbortSignal | undefined
    acceptMatchMock.mockImplementationOnce((_matchId, signal) => {
      acceptSignal = signal

      return new Promise<void>(() => {})
    })
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    await wrapper.get('.match-found-accept').trigger('click')
    await wrapper.vm.$nextTick()

    expect(acceptSignal?.aborted).toBe(false)

    wrapper.unmount()

    expect(acceptSignal?.aborted).toBe(true)
  })

  it('clears the match found countdown timer on unmount', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-01T00:00:00Z'))
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval')
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    wrapper.unmount()

    expect(clearIntervalSpy).toHaveBeenCalled()
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)

    clearIntervalSpy.mockRestore()
  })

  it('clears match found modal state without leave and returns ready after confirming a stream error', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    getCurrentHandlers().onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: 'invalid-date',
    })
    await wrapper.vm.$nextTick()

    getCurrentHandlers().onError?.(new Error('stream failed'))
    await flushPromises()

    const main = wrapper.get('main')

    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
    expect(main.attributes('data-match-found-modal-open')).toBe('false')
    expect(main.attributes('data-match-found-countdown-seconds')).toBe('0')
    expect(main.attributes('data-match-response-command-status')).toBe('idle')
    expect(main.attributes('data-can-submit-match-response')).toBe('false')
    expect(main.attributes('data-stream-status')).toBe('error')
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    )

    await wrapper.get('.match-error-dialog button').trigger('click')
    await wrapper.vm.$nextTick()

    expect(main.attributes('data-match-found-id')).toBe('')
    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(getStartButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('stores a local error state when the stream reports an error', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    const handlers = getCurrentHandlers()

    handlers.onError?.(new Error('stream failed'))
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-error-message')).toBe(
      'Match event stream is currently unavailable.',
    )
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    )
    expect(joinMatchQueueMock).not.toHaveBeenCalled()
    expect(leaveMatchQueueMock).not.toHaveBeenCalled()
  })

  it('leaves the queue when the stream fails after join may have reached the backend', async () => {
    let resolveJoin: () => void = () => {}
    joinMatchQueueMock.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        resolveJoin = resolve
      }),
    )
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')

    getCurrentHandlers().onError?.(new Error('stream failed'))
    await flushPromises()

    expect(leaveMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    )
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)

    resolveJoin()
    await flushPromises()

    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
  })

  it('leaves the queue and returns to ready when the stream fails while queued', async () => {
    vi.useFakeTimers()
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')
    expect(getStartButton(wrapper).text()).toBe('1')

    getCurrentHandlers().onError?.(new Error('stream failed'))
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()

    expect(leaveMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(getStartButton(wrapper).text()).toContain('매칭 시작')
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    )
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
  })

  it('stores cleanup errors when stream failure leave cleanup fails', async () => {
    leaveMatchQueueMock.mockRejectedValueOnce(new Error('cleanup failed'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')

    getCurrentHandlers().onError?.(new Error('stream failed'))
    await flushPromises()

    const main = wrapper.get('main')

    expect(leaveMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-queue-error-message')).toBe('cleanup failed')
    expect(main.attributes('data-stream-error-message')).toBe(
      'Match event stream is currently unavailable.',
    )
    expect(wrapper.get('[role="dialog"]').text()).toContain('cleanup failed')
  })

  it('stores a local error state when stream connection throws on start', async () => {
    connectMatchEventSourceMock.mockImplementationOnce(() => {
      throw new Error('access token is missing')
    })

    const wrapper = mount(MatchPage)
    await getStartButton(wrapper).trigger('click')
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-error-message')).toBe(
      'Match event stream is currently unavailable.',
    )
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    )
    expect(joinMatchQueueMock).not.toHaveBeenCalled()
  })

  it('closes the stream and returns to ready when join fails', async () => {
    joinMatchQueueMock.mockRejectedValueOnce(new Error('join failed'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    const main = wrapper.get('main')

    expect(joinMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(main.attributes('data-queue-status')).toBe('ready')
    expect(main.attributes('data-stream-status')).toBe('idle')
    expect(main.attributes('data-queue-error-message')).toBe('join failed')
    expect(wrapper.get('[role="dialog"]').text()).toContain('join failed')
  })

  it('keeps queued state when leave fails', async () => {
    leaveMatchQueueMock.mockRejectedValueOnce(new Error('leave failed'))
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    getCurrentHandlers().onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    await flushPromises()

    expect(wrapper.get('main').attributes('data-queue-status')).toBe('queued')

    await getStartButton(wrapper).trigger('click')
    await flushPromises()

    const main = wrapper.get('main')

    expect(leaveMatchQueueMock).toHaveBeenCalledTimes(1)
    expect(closeMatchEventSourceMock).not.toHaveBeenCalled()
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(main.attributes('data-queue-error-message')).toBe('leave failed')
  })

  it('closes the match event stream on unmount', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    wrapper.unmount()

    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
  })

  it('ignores late stream callbacks after unmount', async () => {
    const wrapper = mount(MatchPage)

    await getStartButton(wrapper).trigger('click')
    const handlers = getCurrentHandlers()

    wrapper.unmount()
    const beforeLateCallbacksHtml = wrapper.html()
    handlers.onConnected?.({
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    })
    handlers.onHeartbeat?.({
      sentAt: '2026-06-01T00:00:01Z',
    })
    handlers.onMatchFound?.({
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: '2026-06-01T00:00:02Z',
    })
    handlers.onMatchResponseResult?.({
      matchId: 'match-1',
      outcome: 'MATCHED',
      reason: 'BOTH_ACCEPTED',
      action: 'GO_TO_GAME_WAITING',
      opponent: null,
      game: null,
    })
    handlers.onError?.(new Error('late stream error'))
    await wrapper.vm.$nextTick()

    expect(wrapper.html()).toBe(beforeLateCallbacksHtml)
  })
})
