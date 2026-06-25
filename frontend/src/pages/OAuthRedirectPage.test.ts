import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { exchangeOAuthToken } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'

import OAuthRedirectPage from './OAuthRedirectPage.vue'

const routerReplaceMock = vi.hoisted(() => vi.fn())
const routeMock = vi.hoisted(() => ({
  value: {
    query: {} as Record<string, string | string[]>,
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeMock.value,
  useRouter: () => ({
    replace: routerReplaceMock,
  }),
}))

vi.mock('@/services/authService', () => ({
  exchangeOAuthToken: vi.fn(),
}))

vi.mock('@/services/authToken', () => ({
  setAuthTokens: vi.fn(),
}))

const exchangeOAuthTokenMock = vi.mocked(exchangeOAuthToken)
const setAuthTokensMock = vi.mocked(setAuthTokens)
const { setLocale } = useLocale()

describe('OAuthRedirectPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeMock.value.query = reactive({
      code: 'oauth-code',
    })
    setLocale('ko')
  })

  it('does not call exchange API when code is missing', async () => {
    routeMock.value.query = reactive({})

    const wrapper = mount(OAuthRedirectPage)
    await flushPromises()

    expect(exchangeOAuthTokenMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('OAuth 로그인 코드가 올바르지 않습니다.')
  })

  it('exchanges code, stores tokens, and moves to match route', async () => {
    exchangeOAuthTokenMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'OAuthUser',
    })

    mount(OAuthRedirectPage)
    await flushPromises()

    expect(exchangeOAuthTokenMock).toHaveBeenCalledWith({
      code: 'oauth-code',
    })
    expect(setAuthTokensMock).toHaveBeenCalledWith('access-token', 'refresh-token')
    expect(routerReplaceMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('shows backend error message when token exchange fails', async () => {
    exchangeOAuthTokenMock.mockRejectedValue(
      new ApiClientError(401, { message: '유효하지 않은 OAuth 로그인 코드입니다.' }),
    )

    const wrapper = mount(OAuthRedirectPage)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('유효하지 않은 OAuth 로그인 코드입니다.')
    expect(setAuthTokensMock).not.toHaveBeenCalled()
  })

  it('uses the latest code when route query changes on the same page instance', async () => {
    exchangeOAuthTokenMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'OAuthUser',
    })
    const wrapper = mount(OAuthRedirectPage)
    await flushPromises()
    vi.clearAllMocks()

    routeMock.value.query.code = 'next-oauth-code'
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(exchangeOAuthTokenMock).toHaveBeenCalledWith({
      code: 'next-oauth-code',
    })
  })

  it('moves back to login with oauth failed query from error state', async () => {
    routeMock.value.query = reactive({})
    const wrapper = mount(OAuthRedirectPage)
    await flushPromises()

    await wrapper.get('.oauth-redirect-button').trigger('click')

    expect(routerReplaceMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.login,
      query: {
        oauth: 'failed',
      },
    })
  })
})
