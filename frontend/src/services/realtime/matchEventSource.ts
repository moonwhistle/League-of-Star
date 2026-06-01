import { API_BASE_URL } from '@/constants/env'
import type {
  MatchFoundNotification,
  MatchResponseResultNotification,
  MatchSseConnectedEvent,
  MatchSseHeartbeatEvent,
} from '@/types/match'

export interface MatchEventSourceHandlers {
  onConnected?: (payload: MatchSseConnectedEvent) => void
  onHeartbeat?: (payload: MatchSseHeartbeatEvent) => void
  onMatchFound?: (payload: MatchFoundNotification) => void
  onMatchResponseResult?: (payload: MatchResponseResultNotification) => void
  onOpen?: (event: Event) => void
  onError?: (event: Event) => void
}

export interface MatchEventSourceConnection {
  eventSource: EventSource
  close: () => void
}

export function connectMatchEventSource(
  handlers: MatchEventSourceHandlers = {},
): MatchEventSourceConnection {
  const eventSource = new EventSource(buildMatchStreamUrl(), {
    withCredentials: true,
  })

  eventSource.addEventListener('connected', createJsonListener(handlers.onConnected))
  eventSource.addEventListener('heartbeat', createJsonListener(handlers.onHeartbeat))
  eventSource.addEventListener('match_found', createJsonListener(handlers.onMatchFound))
  eventSource.addEventListener(
    'match_response_result',
    createJsonListener(handlers.onMatchResponseResult),
  )

  if (handlers.onOpen !== undefined) {
    eventSource.addEventListener('open', handlers.onOpen)
  }

  if (handlers.onError !== undefined) {
    eventSource.addEventListener('error', handlers.onError)
  }

  return {
    eventSource,
    close: () => eventSource.close(),
  }
}

function buildMatchStreamUrl(): string {
  const baseUrl = API_BASE_URL.replace(/\/$/, '')

  return `${baseUrl}/api/v1/notifications/match/stream`
}

function createJsonListener<TPayload>(
  handler: ((payload: TPayload) => void) | undefined,
): (event: MessageEvent) => void {
  return (event) => {
    if (handler === undefined) {
      return
    }

    handler(JSON.parse(event.data as string) as TPayload)
  }
}
