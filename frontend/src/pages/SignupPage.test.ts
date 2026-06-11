import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { signup } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'

import SignupPage from './SignupPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/authService', () => ({
  signup: vi.fn(),
}))

vi.mock('@/services/authToken', () => ({
  setAuthTokens: vi.fn(),
}))

const signupMock = vi.mocked(signup)
const setAuthTokensMock = vi.mocked(setAuthTokens)
const { setLocale } = useLocale()

describe('SignupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('ko')
  })

  it('renders the signup page', () => {
    const wrapper = mount(SignupPage)

    expect(wrapper.get('h1').text()).toBe('LEAGUE OF STAR')
    expect(wrapper.get('.signup-heading p').text()).toBe('CREATE YOUR STAR ACCOUNT')
    expect(wrapper.find('#signup-email').exists()).toBe(true)
    expect(wrapper.find('#signup-password').exists()).toBe(true)
    expect(wrapper.find('#signup-nickname').exists()).toBe(true)
    expect(wrapper.get('.signup-button').text()).toBe('회원가입')
  })

  it('does not call signup when required fields are empty', async () => {
    const wrapper = mount(SignupPage)

    await wrapper.get('form').trigger('submit')

    expect(signupMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '이메일, 비밀번호, 닉네임을 모두 입력해 주세요.',
    )
  })

  it('validates password length before calling signup', async () => {
    const wrapper = mount(SignupPage)

    await wrapper.get('#signup-email').setValue('new@example.com')
    await wrapper.get('#signup-password').setValue('short')
    await wrapper.get('#signup-nickname').setValue('별빛')
    await wrapper.get('form').trigger('submit')

    expect(signupMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '비밀번호는 8자 이상 20자 이하로 입력해 주세요.',
    )
  })

  it('validates nickname length before calling signup', async () => {
    const wrapper = mount(SignupPage)

    await wrapper.get('#signup-email').setValue('new@example.com')
    await wrapper.get('#signup-password').setValue('password123')
    await wrapper.get('#signup-nickname').setValue('a')
    await wrapper.get('form').trigger('submit')

    expect(signupMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '닉네임은 2자 이상 16자 이하로 입력해 주세요.',
    )
  })

  it('submits signup data and moves to login after success', async () => {
    signupMock.mockResolvedValue({
      id: 1,
      email: 'new@example.com',
      nickname: 'StarUser',
    })

    const wrapper = mount(SignupPage)

    await wrapper.get('#signup-email').setValue('new@example.com')
    await wrapper.get('#signup-password').setValue('password123')
    await wrapper.get('#signup-nickname').setValue('StarUser')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(signupMock).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'password123',
      nickname: 'StarUser',
    })
    expect(setAuthTokensMock).not.toHaveBeenCalled()
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.login,
      query: {
        signup: 'success',
      },
    })
  })

  it('shows backend error message after signup failure', async () => {
    signupMock.mockRejectedValue(
      new ApiClientError(400, { message: '이미 사용 중인 이메일입니다.' }),
    )

    const wrapper = mount(SignupPage)

    await wrapper.get('#signup-email').setValue('new@example.com')
    await wrapper.get('#signup-password').setValue('password123')
    await wrapper.get('#signup-nickname').setValue('StarUser')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('이미 사용 중인 이메일입니다.')
  })

  it('moves back to login from the back button', async () => {
    const wrapper = mount(SignupPage)

    await wrapper.get('.back-login-button').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.login })
  })
})
