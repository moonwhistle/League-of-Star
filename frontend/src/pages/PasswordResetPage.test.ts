import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { submitPasswordReset } from '@/services/authService'

import PasswordResetPage from './PasswordResetPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())
const routeQueryMock = vi.hoisted(() => ({ value: {} as Record<string, string | string[]> }))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: routeQueryMock.value,
  }),
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/authService', () => ({
  submitPasswordReset: vi.fn(),
}))

const submitPasswordResetMock = vi.mocked(submitPasswordReset)
const { setLocale } = useLocale()

describe('PasswordResetPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeQueryMock.value = {
      token: 'reset-token',
    }
    setLocale('ko')
  })

  it('renders invalid link state without calling backend when token is missing', async () => {
    routeQueryMock.value = {}

    const wrapper = mount(PasswordResetPage)

    expect(wrapper.get('[role="alert"]').text()).toContain('재설정 링크가 올바르지 않습니다.')
    expect(wrapper.find('form').exists()).toBe(false)

    expect(submitPasswordResetMock).not.toHaveBeenCalled()
  })

  it('validates required passwords before submit', async () => {
    const wrapper = mount(PasswordResetPage)

    await wrapper.get('form').trigger('submit')

    expect(submitPasswordResetMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '새 비밀번호와 확인 비밀번호를 입력해 주세요.',
    )
  })

  it('validates password rule before submit', async () => {
    const wrapper = mount(PasswordResetPage)

    await wrapper.get('#password-reset-new-password').setValue('password')
    await wrapper.get('#password-reset-confirm-password').setValue('password')
    await wrapper.get('form').trigger('submit')

    expect(submitPasswordResetMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(
      '비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다.',
    )
  })

  it('validates password confirmation before submit', async () => {
    const wrapper = mount(PasswordResetPage)

    await wrapper.get('#password-reset-new-password').setValue('password123')
    await wrapper.get('#password-reset-confirm-password').setValue('password124')
    await wrapper.get('form').trigger('submit')

    expect(submitPasswordResetMock).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('확인 비밀번호가 일치하지 않습니다.')
  })

  it('submits reset token and moves to login success state', async () => {
    submitPasswordResetMock.mockResolvedValue('ok')
    const wrapper = mount(PasswordResetPage)

    await wrapper.get('#password-reset-new-password').setValue('password123')
    await wrapper.get('#password-reset-confirm-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(submitPasswordResetMock).toHaveBeenCalledWith({
      token: 'reset-token',
      newPassword: 'password123',
    })
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.login,
      query: {
        passwordReset: 'success',
      },
    })
  })

  it('shows backend invalid token message', async () => {
    submitPasswordResetMock.mockRejectedValue(
      new ApiClientError(400, { message: '재설정 토큰이 유효하지 않습니다.' }),
    )
    const wrapper = mount(PasswordResetPage)

    await wrapper.get('#password-reset-new-password').setValue('password123')
    await wrapper.get('#password-reset-confirm-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('재설정 토큰이 유효하지 않습니다.')
  })
})
