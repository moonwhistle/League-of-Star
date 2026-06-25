import { API_BASE_URL } from '@/constants/env'

const GOOGLE_OAUTH_PATH = '/oauth2/authorization/google'

export function buildGoogleOAuthRedirectUrl() {
  return `${API_BASE_URL.replace(/\/$/, '')}${GOOGLE_OAUTH_PATH}`
}

export function startGoogleOAuthRedirect() {
  window.location.assign(buildGoogleOAuthRedirectUrl())
}
