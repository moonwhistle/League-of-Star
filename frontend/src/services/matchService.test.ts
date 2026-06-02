import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestVoid } from './apiClient'
import { joinMatchQueue, leaveMatchQueue } from './matchService'

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
})
