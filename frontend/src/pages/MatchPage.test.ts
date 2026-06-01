import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  connectMatchEventSource,
  type MatchEventSourceHandlers,
} from '@/services/realtime/matchEventSource'

import MatchPage from './MatchPage.vue'

vi.mock('@/services/realtime/matchEventSource', () => ({
  connectMatchEventSource: vi.fn(),
}))

const connectMatchEventSourceMock = vi.mocked(connectMatchEventSource)
const closeMatchEventSourceMock = vi.fn()

let currentHandlers: MatchEventSourceHandlers | undefined

function getCurrentHandlers(): MatchEventSourceHandlers {
  if (currentHandlers === undefined) {
    throw new Error('Match event source handlers were not registered.')
  }

  return currentHandlers
}

describe('MatchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentHandlers = undefined
    connectMatchEventSourceMock.mockImplementation((handlers = {}) => {
      currentHandlers = handlers

      return {
        close: closeMatchEventSourceMock,
      }
    })
  })

  it('connects the match event stream on mount', async () => {
    const wrapper = mount(MatchPage)
    await wrapper.vm.$nextTick()

    expect(connectMatchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-stream-status')).toBe('connecting')
  })

  it('stores stream event payloads as page local state', async () => {
    const wrapper = mount(MatchPage)
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

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('connected')
    expect(main.attributes('data-connected-user-id')).toBe('1')
    expect(main.attributes('data-last-heartbeat-at')).toBe('2026-06-01T00:00:01Z')
    expect(main.attributes('data-match-found-id')).toBe('match-1')
    expect(main.attributes('data-match-result-action')).toBe('GO_TO_GAME_WAITING')
  })

  it('stores a local error state when the stream reports an error', async () => {
    const wrapper = mount(MatchPage)
    const handlers = getCurrentHandlers()

    handlers.onError?.(new Error('stream failed'))
    await wrapper.vm.$nextTick()

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('error')
    expect(main.attributes('data-stream-error-message')).toBe(
      'Match event stream is currently unavailable.',
    )
  })

  it('stores a local error state when stream connection throws', async () => {
    connectMatchEventSourceMock.mockImplementationOnce(() => {
      throw new Error('access token is missing')
    })

    const wrapper = mount(MatchPage)
    await flushPromises()

    const main = wrapper.get('main')

    expect(main.attributes('data-stream-status')).toBe('error')
    expect(main.attributes('data-stream-error-message')).toBe(
      'Match event stream is currently unavailable.',
    )
  })

  it('closes the match event stream on unmount', () => {
    const wrapper = mount(MatchPage)

    wrapper.unmount()

    expect(closeMatchEventSourceMock).toHaveBeenCalledTimes(1)
  })
})
