import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { getCustomRoom } from '@/services/customRoomService'
import type { CustomRoomResponse } from '@/types/customRoom'

import CustomRoomPage from './CustomRoomPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    roomId: '100',
  },
}))
const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/customRoomService', () => ({
  getCustomRoom: vi.fn(),
}))

const getCustomRoomMock = vi.mocked(getCustomRoom)
const { setLocale } = useLocale()

describe('CustomRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('ko')
    routeMock.params.roomId = '100'
    routerPushMock.mockResolvedValue(undefined)
    getCustomRoomMock.mockResolvedValue(createRoomResponse())
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        origin: 'http://localhost:5173',
      },
    })
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('loads and renders custom room detail from the route roomId', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    expect(getCustomRoomMock).toHaveBeenCalledWith('100', expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-custom-room-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-custom-room-id')).toBe('100')
    expect(wrapper.get('main').attributes('data-custom-room-participant-count')).toBe('2')
    expect(wrapper.text()).toContain("Host's room")
    expect(wrapper.text()).toContain('현재 인원')
    expect(wrapper.text()).toContain('2/2')
    expect(wrapper.text()).toContain('Host')
    expect(wrapper.text()).toContain('Guest')
    expect(wrapper.text()).toContain('방장')
  })

  it('builds and copies invite links from inviteCode', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    const inviteLink = wrapper.get('[data-testid="custom-room-invite-link"]')
    expect(inviteLink.text()).toBe('http://localhost:5173/custom-games/join/AB12CD')

    await wrapper.get('[data-testid="custom-room-copy-button"]').trigger('click')
    await flushPromises()

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      'http://localhost:5173/custom-games/join/AB12CD',
    )
    expect(wrapper.get('main').attributes('data-custom-room-copy-status')).toBe('success')
    expect(wrapper.text()).toContain('초대 링크를 복사했습니다.')
  })

  it('shows a copy failure message when clipboard write fails', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockRejectedValue(new Error('copy failed')),
      },
    })
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-copy-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-copy-status')).toBe('error')
    expect(wrapper.text()).toContain('초대 링크를 복사하지 못했습니다.')
  })

  it('renders backend error messages when room detail loading fails', async () => {
    getCustomRoomMock.mockRejectedValueOnce(
      new ApiClientError(404, { message: '사용자 지정 방을 찾을 수 없습니다.' }),
    )

    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-status')).toBe('error')
    expect(wrapper.text()).toContain('사용자 지정 방을 찾을 수 없습니다.')
  })

  it('does not request the API when roomId is missing', async () => {
    routeMock.params.roomId = ''

    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    expect(getCustomRoomMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-custom-room-status')).toBe('error')
    expect(wrapper.text()).toContain('방 ID가 올바르지 않습니다.')
  })

  it('moves back to the custom rooms route from the header action', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    await wrapper.get('.custom-room-actions button').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.customRooms })
  })
})

function createRoomResponse(): CustomRoomResponse {
  return {
    roomId: 100,
    roomName: "Host's room",
    inviteCode: 'AB12CD',
    ownerUserId: 1,
    status: 'WAITING',
    maxParticipants: 2,
    participants: [
      {
        userId: 1,
        nickname: 'Host',
        role: 'OWNER',
      },
      {
        userId: 2,
        nickname: 'Guest',
        role: 'PLAYER',
      },
    ],
  }
}
