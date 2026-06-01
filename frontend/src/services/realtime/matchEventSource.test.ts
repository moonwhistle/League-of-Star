import type { FetchEventSourceInit } from '@microsoft/fetch-event-source'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  MatchFoundNotification,
  MatchResponseResultNotification,
  MatchSseConnectedEvent,
  MatchSseHeartbeatEvent,
} from '@/types/match'

import { getAccessToken } from '../authToken'
import { connectMatchEventSource, MatchEventSourceAuthError } from './matchEventSource'

vi.mock('@microsoft/fetch-event-source', () => ({
  EventStreamContentType: 'text/event-stream',
  fetchEventSource: vi.fn(),
}))

vi.mock('../authToken', () => ({
  getAccessToken: vi.fn(),
}))

const fetchEventSourceMock = vi.mocked(fetchEventSource)
const getAccessTokenMock = vi.mocked(getAccessToken)

function getFetchEventSourceInit(): FetchEventSourceInit {
  const call = fetchEventSourceMock.mock.calls[0]

  if (call === undefined || call[1] === undefined) {
    throw new Error('fetchEventSource was not called with init.')
  }

  return call[1]
}

describe('connectMatchEventSource', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAccessTokenMock.mockReturnValue('access-token')
    fetchEventSourceMock.mockResolvedValue(undefined)
  })

  it('does not connect without an access token', () => {
    getAccessTokenMock.mockReturnValue(null)

    expect(() => connectMatchEventSource()).toThrow(MatchEventSourceAuthError)
    expect(fetchEventSourceMock).not.toHaveBeenCalled()
  })

  it('connects to the backend match stream with authorization headers', () => {
    connectMatchEventSource()

    const [url, init] = fetchEventSourceMock.mock.calls[0] ?? []

    expect(url).toBe('http://localhost:8080/api/v1/notifications/match/stream')
    expect(init).toEqual(
      expect.objectContaining({
        method: 'GET',
        headers: {
          accept: 'text/event-stream',
          Authorization: 'Bearer access-token',
        },
        signal: expect.any(AbortSignal),
      }),
    )
  })

  it('dispatches connected events', () => {
    const payload: MatchSseConnectedEvent = {
      userId: 1,
      connectedAt: '2026-06-01T00:00:00Z',
    }
    const onConnected = vi.fn()

    fetchEventSourceMock.mockImplementation((_url, init) => {
      init.onmessage?.({
        id: '',
        event: 'connected',
        data: JSON.stringify(payload),
      })

      return Promise.resolve()
    })

    connectMatchEventSource({ onConnected })

    expect(onConnected).toHaveBeenCalledWith(payload)
  })

  it('dispatches heartbeat events', () => {
    const payload: MatchSseHeartbeatEvent = {
      sentAt: '2026-06-01T00:00:01Z',
    }
    const onHeartbeat = vi.fn()

    fetchEventSourceMock.mockImplementation((_url, init) => {
      init.onmessage?.({
        id: '',
        event: 'heartbeat',
        data: JSON.stringify(payload),
      })

      return Promise.resolve()
    })

    connectMatchEventSource({ onHeartbeat })

    expect(onHeartbeat).toHaveBeenCalledWith(payload)
  })

  it('dispatches match_found events', () => {
    const payload: MatchFoundNotification = {
      matchId: 'match-1',
      userId: 1,
      opponentUserId: 2,
      acceptTimeoutSeconds: 10,
      eventCreatedAt: '2026-06-01T00:00:02Z',
    }
    const onMatchFound = vi.fn()

    fetchEventSourceMock.mockImplementation((_url, init) => {
      init.onmessage?.({
        id: '',
        event: 'match_found',
        data: JSON.stringify(payload),
      })

      return Promise.resolve()
    })

    connectMatchEventSource({ onMatchFound })

    expect(onMatchFound).toHaveBeenCalledWith(payload)
  })

  it('dispatches match_response_result events', () => {
    const payload: MatchResponseResultNotification = {
      matchId: 'match-1',
      outcome: 'MATCHED',
      reason: 'BOTH_ACCEPTED',
      action: 'GO_TO_GAME_WAITING',
      opponent: {
        userId: 2,
        nickname: 'opponent',
        tier: 'GOLD',
        tierScore: 1200,
      },
      game: {
        gameRoomId: 10,
        videoUrl: 'https://example.com/game.mp4',
        webSocketUrl: 'ws://localhost:8080/game',
      },
    }
    const onMatchResponseResult = vi.fn()

    fetchEventSourceMock.mockImplementation((_url, init) => {
      init.onmessage?.({
        id: '',
        event: 'match_response_result',
        data: JSON.stringify(payload),
      })

      return Promise.resolve()
    })

    connectMatchEventSource({ onMatchResponseResult })

    expect(onMatchResponseResult).toHaveBeenCalledWith(payload)
  })

  it('ignores unknown events', () => {
    const handlers = {
      onConnected: vi.fn(),
      onHeartbeat: vi.fn(),
      onMatchFound: vi.fn(),
      onMatchResponseResult: vi.fn(),
      onError: vi.fn(),
    }

    fetchEventSourceMock.mockImplementation((_url, init) => {
      init.onmessage?.({
        id: '',
        event: 'unknown',
        data: '{"ignored":true}',
      })

      return Promise.resolve()
    })

    connectMatchEventSource(handlers)

    expect(handlers.onConnected).not.toHaveBeenCalled()
    expect(handlers.onHeartbeat).not.toHaveBeenCalled()
    expect(handlers.onMatchFound).not.toHaveBeenCalled()
    expect(handlers.onMatchResponseResult).not.toHaveBeenCalled()
    expect(handlers.onError).not.toHaveBeenCalled()
  })

  it('passes JSON parse failures to the error callback', () => {
    const onConnected = vi.fn()
    const onError = vi.fn()

    fetchEventSourceMock.mockImplementation((_url, init) => {
      init.onmessage?.({
        id: '',
        event: 'connected',
        data: '{',
      })

      return Promise.resolve()
    })

    connectMatchEventSource({ onConnected, onError })

    expect(onConnected).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(expect.any(SyntaxError))
  })

  it('aborts the stream when close is called', () => {
    fetchEventSourceMock.mockReturnValue(new Promise(() => {}))

    const connection = connectMatchEventSource()
    const init = getFetchEventSourceInit()

    expect(init.signal?.aborted).toBe(false)

    connection.close()
    connection.close()

    expect(init.signal?.aborted).toBe(true)
  })
})
