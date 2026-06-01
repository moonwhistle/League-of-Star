import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the login page', () => {
    const wrapper = mount(LoginPage)

    expect(wrapper.get('h1').text()).toBe('LEAGUE OF SMITE')
    expect(wrapper.find('#login-email').exists()).toBe(true)
    expect(wrapper.find('#login-password').exists()).toBe(true)
    expect(wrapper.get('.login-button').text()).toBe('Login')
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
    expect(wrapper.get('.login-button').text()).toBe('Logging in')

    resolveLogin!()
    await flushPromises()

    expect(wrapper.get<HTMLButtonElement>('.login-button').element.disabled).toBe(false)
  })
})
