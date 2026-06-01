import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source'

import { API_BASE_URL } from '@/constants/env'
import type {
  MatchFoundNotification,
  MatchResponseResultNotification,
  MatchSseConnectedEvent,
  MatchSseHeartbeatEvent,
} from '@/types/match'

import { getAccessToken } from '../authToken'

export interface MatchEventSourceHandlers {
  onConnected?: (payload: MatchSseConnectedEvent) => void
  onHeartbeat?: (payload: MatchSseHeartbeatEvent) => void
  onMatchFound?: (payload: MatchFoundNotification) => void
  onMatchResponseResult?: (payload: MatchResponseResultNotification) => void
  onOpen?: (response: Response) => void
  onError?: (error: unknown) => void
}

export interface MatchEventSourceConnection {
  close: () => void
}

export class MatchEventSourceAuthError extends Error {
  constructor() {
    super('Access token is required to connect match event stream.')
    this.name = 'MatchEventSourceAuthError'
  }
}

export class MatchEventSourceOpenError extends Error {
  readonly response: Response

  constructor(response: Response) {
    super(`Match event stream failed to open with status ${response.status}.`)
    this.name = 'MatchEventSourceOpenError'
    this.response = response
  }
}

export function connectMatchEventSource(
  handlers: MatchEventSourceHandlers = {},
): MatchEventSourceConnection {
  const accessToken = getAccessToken()

  if (accessToken === null || accessToken.trim() === '') {
    throw new MatchEventSourceAuthError()
  }

  const abortController = new AbortController()
  let isClosed = false

  void fetchEventSource(buildMatchStreamUrl(), {
    method: 'GET',
    headers: {
      accept: EventStreamContentType,
      Authorization: `Bearer ${accessToken}`,
    },
    signal: abortController.signal,
    onopen: async (response) => {
      if (!response.ok) {
        throw new MatchEventSourceOpenError(response)
      }

      const contentType = response.headers.get('content-type')

      if (contentType?.startsWith(EventStreamContentType) !== true) {
        throw new Error(`Expected content-type to be ${EventStreamContentType}.`)
      }

      handlers.onOpen?.(response)
    },
    onmessage: (message) => {
      dispatchMatchEventMessage(message.event, message.data, handlers)
    },
    onclose: () => {
      isClosed = true
    },
    onerror: (error) => {
      throw error
    },
  }).catch((error: unknown) => {
    if (!isClosed && !abortController.signal.aborted) {
      handlers.onError?.(error)
    }
  })

  return {
    close: () => {
      if (isClosed) {
        return
      }

      isClosed = true
      abortController.abort()
    },
  }
}

function buildMatchStreamUrl(): string {
  const baseUrl = API_BASE_URL.replace(/\/$/, '')

  return `${baseUrl}/api/v1/notifications/match/stream`
}

function dispatchMatchEventMessage(
  eventName: string,
  eventData: string,
  handlers: MatchEventSourceHandlers,
): void {
  switch (eventName) {
    case 'connected':
      dispatchJsonEvent(eventData, handlers.onConnected, handlers.onError)
      break
    case 'heartbeat':
      dispatchJsonEvent(eventData, handlers.onHeartbeat, handlers.onError)
      break
    case 'match_found':
      dispatchJsonEvent(eventData, handlers.onMatchFound, handlers.onError)
      break
    case 'match_response_result':
      dispatchJsonEvent(eventData, handlers.onMatchResponseResult, handlers.onError)
      break
  }
}

function dispatchJsonEvent<TPayload>(
  eventData: string,
  handler: ((payload: TPayload) => void) | undefined,
  onError: ((error: unknown) => void) | undefined,
): void {
  if (handler === undefined || eventData.trim() === '') {
    return
  }

  try {
    handler(JSON.parse(eventData) as TPayload)
  } catch (error) {
    onError?.(error)
  }
}
