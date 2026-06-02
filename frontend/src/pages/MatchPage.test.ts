import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { acceptMatch, joinMatchQueue, leaveMatchQueue, rejectMatch } from '@/services/matchService'
import {
  connectMatchEventSource,
  type MatchEventSourceHandlers,
} from '@/services/realtime/matchEventSource'

import MatchPage from './MatchPage.vue'

vi.mock('@/services/realtime/matchEventSource', () => ({
  connectMatchEventSource: vi.fn(),
}))

vi.mock('@/services/matchService', () => ({
  acceptMatch: vi.fn(),
  joinMatchQueue: vi.fn(),
  leaveMatchQueue: vi.fn(),
  rejectMatch: vi.fn(),
}))

const connectMatchEventSourceMock = vi.mocked(connectMatchEventSource)
const acceptMatchMock = vi.mocked(acceptMatch)
const joinMatchQueueMock = vi.mocked(joinMatchQueue)
const leaveMatchQueueMock = vi.mocked(leaveMatchQueue)
const rejectMatchMock = vi.mocked(rejectMatch)
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

describe('MatchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
    setLocale('ko')
    currentHandlers = undefined
    joinMatchQueueMock.mockResolvedValue(undefined)
    leaveMatchQueueMock.mockResolvedValue(undefined)
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

  it('renders the match page lobby layout', () => {
    const wrapper = mount(MatchPage)

    expect(wrapper.get('h1').text()).toBe('LEAGUE OF SMITE')
    expect(wrapper.findAll('.match-actions .icon-button')).toHaveLength(2)
    expect(wrapper.find('[aria-label="전적 보기"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="로그아웃"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="Player profile"]').text()).toContain('Summoner')
    expect(wrapper.get('[aria-label="Player profile"]').text()).not.toContain('Bronze IV')
    expect(wrapper.get('[aria-label="Ranking summary"]').text()).toContain('랭킹')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('BRONZE IV')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('현재 랭크')
    expect(wrapper.get('[aria-label="Current rank"]').text()).toContain('1,248 LP')
    expect(wrapper.text()).not.toContain('RP')
    expect(wrapper.get('[data-testid="match-start-button"]').text()).toContain('매칭 시작')
    expect(wrapper.text()).toContain('연습 모드')
    expect(wrapper.text()).toContain('사용자 지정')
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('idle')
    expect(wrapper.get('main').attributes('data-queue-status')).toBe('ready')
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

  it('stores stream event payloads as page local state', async () => {
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
      opponent: null,
      game: null,
    })
    await wrapper.vm.$nextTick()
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(main.attributes('data-can-start-match')).toBe('false')
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(main.attributes('data-connected-user-id')).toBe('1')
    expect(main.attributes('data-last-heartbeat-at')).toBe('2026-06-01T00:00:01Z')
    expect(main.attributes('data-match-found-id')).toBe('match-1')
    expect(main.attributes('data-match-found-modal-open')).toBe('true')
    expect(main.attributes('data-match-result-action')).toBe('GO_TO_GAME_WAITING')
    expect(getStartButton(wrapper).attributes('disabled')).toBeDefined()
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
    expect(wrapper.get('.match-found-accept').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.match-found-decline').attributes('disabled')).toBeDefined()

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
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(wrapper.get('[aria-labelledby="match-found-title"]').text()).toContain('로딩중...')
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

  it('keeps accept and decline buttons as UI-only controls in this issue', async () => {
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

    expect(acceptMatchMock).not.toHaveBeenCalled()
    expect(rejectMatchMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-match-found-modal-open')).toBe('true')
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

  it('clears match found modal state without leave when the stream fails after match_found', async () => {
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
    expect(main.attributes('data-stream-status')).toBe('error')
    expect(main.attributes('data-queue-status')).toBe('queued')
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    )
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
