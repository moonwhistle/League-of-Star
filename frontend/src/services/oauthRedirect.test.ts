import { describe, expect, it } from 'vitest'

import { buildGoogleOAuthRedirectUrl } from './oauthRedirect'

describe('oauthRedirect', () => {
  it('builds the Google OAuth redirect URL from the backend API base URL', () => {
    expect(buildGoogleOAuthRedirectUrl()).toBe(
      'http://localhost:8080/oauth2/authorization/google',
    )
  })
})
