import { beforeEach, describe, expect, it, vi } from 'vitest'

import { requestJson } from './apiClient'
import {
  createCustomRoom,
  getCustomRoom,
  getCustomRoomInvitePreview,
  getCustomRooms,
  joinCustomRoom,
  leaveCustomRoom,
} from './customRoomService'

vi.mock('./apiClient', () => ({
  requestJson: vi.fn(),
}))

const requestJsonMock = vi.mocked(requestJson)

describe('customRoomService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests public custom room list without authentication', async () => {
    const abortController = new AbortController()
    const response = {
      rooms: [
        {
          roomId: 100,
          roomName: "Host's room",
          inviteCode: 'AB12CD',
          ownerUserId: 1,
          status: 'WAITING',
          maxParticipants: 2,
          currentParticipants: 1,
        },
      ],
    }
    requestJsonMock.mockResolvedValue(response)

    await expect(getCustomRooms(abortController.signal)).resolves.toEqual(response)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/custom-games/rooms', {
      method: 'GET',
      signal: abortController.signal,
      auth: false,
    })
  })

  it('creates a custom room with the authenticated backend contract', async () => {
    const abortController = new AbortController()
    const response = createRoomResponse()
    requestJsonMock.mockResolvedValue(response)

    await expect(createCustomRoom(abortController.signal)).resolves.toEqual(response)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/custom-games/rooms', {
      method: 'POST',
      signal: abortController.signal,
    })
  })

  it('requests custom room detail by roomId with authentication', async () => {
    const abortController = new AbortController()
    const response = createRoomResponse()
    requestJsonMock.mockResolvedValue(response)

    await expect(getCustomRoom(100, abortController.signal)).resolves.toEqual(response)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/custom-games/rooms/100', {
      method: 'GET',
      signal: abortController.signal,
    })
  })

  it('encodes roomId when requesting custom room detail', async () => {
    requestJsonMock.mockResolvedValue(createRoomResponse())

    await getCustomRoom('room/with space')

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/custom-games/rooms/room%2Fwith%20space', {
      method: 'GET',
      signal: undefined,
    })
  })

  it('requests invite preview by encoded inviteCode without authentication', async () => {
    const abortController = new AbortController()
    const response = createRoomResponse()
    requestJsonMock.mockResolvedValue(response)

    await expect(getCustomRoomInvitePreview('AB/12 CD', abortController.signal)).resolves.toEqual(
      response,
    )

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/custom-games/rooms/invites/AB%2F12%20CD',
      {
        method: 'GET',
        signal: abortController.signal,
        auth: false,
      },
    )
  })

  it('joins a custom room by encoded inviteCode with authentication', async () => {
    const abortController = new AbortController()
    const response = createRoomResponse()
    requestJsonMock.mockResolvedValue(response)

    await expect(joinCustomRoom('AB/12 CD', abortController.signal)).resolves.toEqual(response)

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/custom-games/rooms/AB%2F12%20CD/join', {
      method: 'POST',
      signal: abortController.signal,
    })
  })

  it('leaves a custom room by encoded roomId with authentication', async () => {
    const abortController = new AbortController()
    const response = createRoomResponse()
    requestJsonMock.mockResolvedValue(response)

    await expect(leaveCustomRoom('room/with space', abortController.signal)).resolves.toEqual(
      response,
    )

    expect(requestJsonMock).toHaveBeenCalledWith(
      '/api/v1/custom-games/rooms/room%2Fwith%20space/leave',
      {
        method: 'POST',
        signal: abortController.signal,
      },
    )
  })
})

function createRoomResponse() {
  return {
    roomId: 100,
    roomName: "Host's room",
    inviteCode: 'AB12CD',
    ownerUserId: 1,
    status: 'WAITING',
    maxParticipants: 2,
    participants: [
      {
        userId: 1,
        nickname: 'Host',
        role: 'OWNER',
      },
    ],
  }
}
