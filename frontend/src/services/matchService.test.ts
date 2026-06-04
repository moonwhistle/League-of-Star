import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestVoid } from './apiClient'
import { acceptMatch, joinMatchQueue, leaveMatchQueue, rejectMatch } from './matchService'

vi.mock('./apiClient', () => ({
  requestVoid: vi.fn(),
}))

const requestVoidMock = vi.mocked(requestVoid)

describe('matchService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestVoidMock.mockResolvedValue(undefined)
  })

  it('joins the match queue with the backend join contract', async () => {
    const abortController = new AbortController()

    await joinMatchQueue(abortController.signal)

    expect(requestVoidMock).toHaveBeenCalledWith('/api/v1/match/join', {
      method: 'POST',
      signal: abortController.signal,
    })
  })

  it('leaves the match queue with the backend leave contract', async () => {
    const abortController = new AbortController()

    await leaveMatchQueue(abortController.signal)

    expect(requestVoidMock).toHaveBeenCalledWith('/api/v1/match/leave', {
      method: 'DELETE',
      signal: abortController.signal,
    })
  })

  it('accepts a match with the backend accept command contract', async () => {
    const abortController = new AbortController()

    await acceptMatch('match-1', abortController.signal)

    expect(requestVoidMock).toHaveBeenCalledWith('/api/v1/match/match-1/accept', {
      method: 'POST',
      signal: abortController.signal,
    })
  })

  it('rejects a match with the backend reject command contract', async () => {
    const abortController = new AbortController()

    await rejectMatch('match-1', abortController.signal)

    expect(requestVoidMock).toHaveBeenCalledWith('/api/v1/match/match-1/reject', {
      method: 'POST',
      signal: abortController.signal,
    })
  })

  it('encodes the match id when sending accept and reject commands', async () => {
    await acceptMatch('match/with space')
    await rejectMatch('match/with space')

    expect(requestVoidMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/match/match%2Fwith%20space/accept',
      {
        method: 'POST',
        signal: undefined,
      },
    )
    expect(requestVoidMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/match/match%2Fwith%20space/reject',
      {
        method: 'POST',
        signal: undefined,
      },
    )
  })
})
