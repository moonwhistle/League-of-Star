import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import { getMyGameRecords } from './gameRecordService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('gameRecordService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests current user game records with page only', async () => {
    const abortController = new AbortController()
    const response = {
      page: 2,
      size: 10,
      totalPages: 3,
      totalElements: 30,
      hasNext: true,
      records: [],
    }
    requestJsonMock.mockResolvedValue(response)

    await expect(getMyGameRecords(2, abortController.signal)).resolves.toEqual(response)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/users/me/game-records?page=2', {
      method: 'GET',
      signal: abortController.signal,
    })
    expect(requestJsonMock).not.toHaveBeenCalledWith(
      expect.stringContaining('size='),
      expect.anything(),
    )
  })
})
