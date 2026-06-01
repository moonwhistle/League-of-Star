import { getAccessToken } from './authToken'

export function hasAuthSession(): boolean {
  const accessToken = getAccessToken()

  return typeof accessToken === 'string' && accessToken.trim() !== ''
}
