import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { login, requestPasswordReset } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'
import { startGoogleOAuthRedirect } from '@/services/oauthRedirect'

import LoginPage from './LoginPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())
const routeQueryMock = vi.hoisted(() => ({ value: {} as Record<string, string> }))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: routeQueryMock.value,
  }),
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/authService', () => ({
  login: vi.fn(),
  requestPasswordReset: vi.fn(),
}))

vi.mock('@/services/authToken', () => ({
  setAuthTokens: vi.fn(),
}))

vi.mock('@/services/oauthRedirect', () => ({
  startGoogleOAuthRedirect: vi.fn(),
}))

const loginMock = vi.mocked(login)
const requestPasswordResetMock = vi.mocked(requestPasswordReset)
const setAuthTokensMock = vi.mocked(setAuthTokens)
const startGoogleOAuthRedirectMock = vi.mocked(startGoogleOAuthRedirect)
const { setLocale } = useLocale()

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeQueryMock.value = {}
    setLocale('ko')
  })

  it('renders the login page', () => {
    const wrapper = mount(LoginPage)

    expect(wrapper.get('h1').text()).toBe('LEAGUE OF STAR')
    expect(wrapper.get('.login-heading p').text()).toBe('MASTER YOUR LIGHTNING TIMING')
    expect(wrapper.find('#login-email').exists()).toBe(true)
    expect(wrapper.find('#login-password').exists()).toBe(true)
    expect(wrapper.get('.login-button').text()).toBe('로그인')
  })

  it('toggles login copy between Korean and English', async () => {
    const wrapper = mount(LoginPage)

    expect(wrapper.get('.login-button').text()).toBe('로그인')

    await wrapper.get('.locale-toggle').trigger('click')

    expect(wrapper.get('.login-button').text()).toBe('Login')
    expect(wrapper.get('.login-links').text()).toContain('Forgot Password?')
    expect(wrapper.get('.bridge-divider').text()).toBe('Or continue with')
  })

  it('moves to signup route from the sign up button', async () => {
    const wrapper = mount(LoginPage)

    await wrapper.get('.login-links button:nth-child(2)').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.signup })
  })

  it('shows a signup success message when redirected from signup', () => {
    routeQueryMock.value = {
      signup: 'success',
    }

    const wrapper = mount(LoginPage)

    expect(wrapper.get('[role="status"]').text()).toBe(
      '회원가입이 완료되었습니다. 로그인해 주세요.',
    )
  })

  it('shows a password reset success message when redirected after reset submit', () => {
    routeQueryMock.value = {
      passwordReset: 'success',
    }

    const wrapper = mount(LoginPage)

    expect(wrapper.get('[role="status"]').text()).toBe(
      '비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.',
    )
  })

  it('shows an OAuth failure message when redirected after OAuth exchange failure', () => {
    routeQueryMock.value = {
      oauth: 'failed',
    }

    const wrapper = mount(LoginPage)

    expect(wrapper.get('[role="alert"]').text()).toBe(
      'Google 로그인에 실패했습니다. 다시 시도해 주세요.',
    )
  })

  it('starts Google OAuth through the backend redirect entrypoint command', async () => {
    const wrapper = mount(LoginPage)

    await wrapper.get('.google-button').trigger('click')

    expect(startGoogleOAuthRedirectMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get<HTMLButtonElement>('.google-button').element.disabled).toBe(true)
    expect(wrapper.get('.google-button').text()).toContain('Google로 이동 중')
  })

  it('opens the game introduction modal without leaving the login page', async () => {
    const wrapper = mount(LoginPage)

    await wrapper.get('.about-button').trigger('click')

    const dialog = wrapper.get('[role="dialog"]')

    expect(dialog.text()).toContain('이 자식을 잡는 게 목표입니다.')
    expect(dialog.text()).toContain('League of Legends의 Smite 싸움')
    expect(dialog.find('img').attributes('alt')).toBe('잡아야 하는 장난꾸러기 별 캐릭터')

    await dialog.get('.about-modal-button').trigger('click')

    expect(wrapper.find('.about-modal').exists()).toBe(false)
  })

  it('opens password reset modal with the login email as prefill', async () => {
    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('.login-links button:first-child').trigger('click')

    expect(wrapper.get('[role="dialog"]').text()).toContain('비밀번호 찾기')
    expect(wrapper.get<HTMLInputElement>('#password-reset-email').element.value).toBe(
      'test@example.com',
    )
  })

  it('validates password reset email before request', async () => {
    const wrapper = mount(LoginPage)

    await wrapper.get('.login-links button:first-child').trigger('click')
    await wrapper.get('.password-reset-form').trigger('submit')

    expect(requestPasswordResetMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('이메일을 입력해 주세요.')
  })

  it('requests password reset without revealing whether email exists', async () => {
    requestPasswordResetMock.mockResolvedValue('ok')
    const wrapper = mount(LoginPage)

    await wrapper.get('.login-links button:first-child').trigger('click')
    await wrapper.get('#password-reset-email').setValue('reset@example.com')
    await wrapper.get('.password-reset-form').trigger('submit')
    await flushPromises()

    expect(requestPasswordResetMock).toHaveBeenCalledWith({
      email: 'reset@example.com',
    })
    expect(wrapper.get('[role="status"]').text()).toBe(
      '가입 여부와 관계없이 메일함에서 재설정 링크를 확인해 주세요.',
    )
  })

  it('shows backend password reset request errors', async () => {
    requestPasswordResetMock.mockRejectedValue(
      new ApiClientError(500, { message: '메일 전송 요청에 실패했습니다.' }),
    )
    const wrapper = mount(LoginPage)

    await wrapper.get('.login-links button:first-child').trigger('click')
    await wrapper.get('#password-reset-email').setValue('reset@example.com')
    await wrapper.get('.password-reset-form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('메일 전송 요청에 실패했습니다.')
  })

  it('submits email and password through the login service', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'starcaster',
    })

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(loginMock).toHaveBeenCalledWith({
      email: 'test@example.com',
      password: 'password123',
    })
  })

  it('stores tokens and moves to match route after login success', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'starcaster',
    })

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(setAuthTokensMock).toHaveBeenCalledWith('access-token', 'refresh-token')
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('moves to a safe internal redirect path after login success', async () => {
    routeQueryMock.value = {
      redirect: '/custom-games/join/AB12CD',
    }
    loginMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'starcaster',
    })

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(routerPushMock).toHaveBeenCalledWith('/custom-games/join/AB12CD')
  })

  it('ignores unsafe external redirect paths after login success', async () => {
    routeQueryMock.value = {
      redirect: 'https://example.test/custom-games/join/AB12CD',
    }
    loginMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'starcaster',
    })

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('shows an error message after login failure', async () => {
    loginMock.mockRejectedValue(new ApiClientError(401, { message: 'Invalid credentials.' }))

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('wrong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('Invalid credentials.')
  })

  it('disables the submit button while login is pending', async () => {
    let resolveLogin: () => void

    loginMock.mockReturnValue(
      new Promise((resolve) => {
        resolveLogin = () => {
          resolve({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            userId: 1,
            nickname: 'starcaster',
          })
        }
      }),
    )

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get<HTMLButtonElement>('.login-button').element.disabled).toBe(true)
    expect(wrapper.get('.login-button').text()).toBe('로그인 중')

    resolveLogin!()
    await flushPromises()

    expect(wrapper.get<HTMLButtonElement>('.login-button').element.disabled).toBe(false)
  })
})
