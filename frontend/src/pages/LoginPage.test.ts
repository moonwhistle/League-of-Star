import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { login } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'

import LoginPage from './LoginPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/authService', () => ({
  login: vi.fn(),
}))

vi.mock('@/services/authToken', () => ({
  setAuthTokens: vi.fn(),
}))

const loginMock = vi.mocked(login)
const setAuthTokensMock = vi.mocked(setAuthTokens)
const { setLocale } = useLocale()

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('ko')
  })

  it('renders the login page', () => {
    const wrapper = mount(LoginPage)

    expect(wrapper.get('h1').text()).toBe('LEAGUE OF SMITE')
    expect(wrapper.get('.login-heading p').text()).toBe('PROVE YOUR SMITE TIMING')
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
  })

  it('submits email and password through the login service', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      nickname: 'smiter',
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
      nickname: 'smiter',
    })

    const wrapper = mount(LoginPage)

    await wrapper.get('#login-email').setValue('test@example.com')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(setAuthTokensMock).toHaveBeenCalledWith('access-token', 'refresh-token')
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
            nickname: 'smiter',
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
