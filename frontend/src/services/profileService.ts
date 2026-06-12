import type { UserProfileResponse } from '@/types/user'

import { requestJson } from './apiClient'

export function getMyProfile(signal?: AbortSignal): Promise<UserProfileResponse> {
  return requestJson<UserProfileResponse>('/api/v1/users/me/profile', {
    method: 'GET',
    signal,
  })
}
