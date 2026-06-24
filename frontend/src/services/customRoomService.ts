import type { CustomRoomListResponse, CustomRoomResponse } from '@/types/customRoom'

import { requestJson } from './apiClient'

const CUSTOM_ROOM_BASE_PATH = '/api/v1/custom-games/rooms'

export function getCustomRooms(signal?: AbortSignal): Promise<CustomRoomListResponse> {
  return requestJson<CustomRoomListResponse>(CUSTOM_ROOM_BASE_PATH, {
    method: 'GET',
    signal,
    auth: false,
  })
}

export function createCustomRoom(signal?: AbortSignal): Promise<CustomRoomResponse> {
  return requestJson<CustomRoomResponse>(CUSTOM_ROOM_BASE_PATH, {
    method: 'POST',
    signal,
  })
}

export function getCustomRoom(
  roomId: number | string,
  signal?: AbortSignal,
): Promise<CustomRoomResponse> {
  return requestJson<CustomRoomResponse>(`${CUSTOM_ROOM_BASE_PATH}/${encodeURIComponent(roomId)}`, {
    method: 'GET',
    signal,
  })
}

export function getCustomRoomInvitePreview(
  inviteCode: string,
  signal?: AbortSignal,
): Promise<CustomRoomResponse> {
  return requestJson<CustomRoomResponse>(
    `${CUSTOM_ROOM_BASE_PATH}/invites/${encodeURIComponent(inviteCode)}`,
    {
      method: 'GET',
      signal,
      auth: false,
    },
  )
}

export function joinCustomRoom(
  inviteCode: string,
  signal?: AbortSignal,
): Promise<CustomRoomResponse> {
  return requestJson<CustomRoomResponse>(
    `${CUSTOM_ROOM_BASE_PATH}/${encodeURIComponent(inviteCode)}/join`,
    {
      method: 'POST',
      signal,
    },
  )
}

export function leaveCustomRoom(
  roomId: number | string,
  signal?: AbortSignal,
): Promise<CustomRoomResponse> {
  return requestJson<CustomRoomResponse>(
    `${CUSTOM_ROOM_BASE_PATH}/${encodeURIComponent(roomId)}/leave`,
    {
      method: 'POST',
      signal,
    },
  )
}
