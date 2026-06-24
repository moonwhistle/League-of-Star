import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { createCustomRoom, getCustomRoom, getCustomRooms } from '@/services/customRoomService'
import type { CustomRoomResponse } from '@/types/customRoom'

import CustomRoomsPage from './CustomRoomsPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/customRoomService', () => ({
  createCustomRoom: vi.fn(),
  getCustomRoom: vi.fn(),
  getCustomRooms: vi.fn(),
}))

const getCustomRoomsMock = vi.mocked(getCustomRooms)
const createCustomRoomMock = vi.mocked(createCustomRoom)
const getCustomRoomMock = vi.mocked(getCustomRoom)
const { setLocale } = useLocale()

describe('CustomRoomsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setLocale('ko')
    routerPushMock.mockResolvedValue(undefined)
    getCustomRoomMock.mockResolvedValue(createRoomResponse({ roomId: 99 }))
    getCustomRoomsMock.mockResolvedValue({
      rooms: [
        {
          roomId: 100,
          roomName: "Host's room",
          inviteCode: 'AB12CD',
          ownerUserId: 1,
          status: 'WAITING',
          maxParticipants: 2,
          currentParticipants: 1,
        },
      ],
    })
    createCustomRoomMock.mockResolvedValue(createRoomResponse({ roomId: 101 }))
  })

  it('redirects to the current joined room instead of showing the public list', async () => {
    localStorage.setItem('league-of-star.currentCustomRoomId', '99')

    mount(CustomRoomsPage)
    await flushPromises()

    expect(getCustomRoomMock).toHaveBeenCalledWith('99')
    expect(getCustomRoomsMock).not.toHaveBeenCalled()
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.customRoom,
      params: {
        roomId: '99',
      },
    })
  })

  it('clears stale current room state and loads the public list', async () => {
    localStorage.setItem('league-of-star.currentCustomRoomId', '99')
    getCustomRoomMock.mockRejectedValueOnce(new Error('not participant'))

    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    expect(localStorage.getItem('league-of-star.currentCustomRoomId')).toBeNull()
    expect(getCustomRoomsMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-custom-rooms-status')).toBe('success')
  })

  it('loads and renders public custom rooms on mount', async () => {
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    expect(getCustomRoomsMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-custom-rooms-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-custom-rooms-count')).toBe('1')
    expect(wrapper.text()).toContain("Host's room")
    expect(wrapper.text()).toContain('현재 인원 1/2')
    expect(wrapper.text()).toContain('방장 #1')
  })

  it('moves to the invite join route when selecting a public room', async () => {
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    await wrapper.get('.custom-rooms-list button').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.customRoomInvite,
      params: {
        inviteCode: 'AB12CD',
      },
    })
    expect(createCustomRoomMock).not.toHaveBeenCalled()
  })

  it('renders an empty state when there are no waiting custom rooms', async () => {
    getCustomRoomsMock.mockResolvedValueOnce({
      rooms: [],
    })

    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-rooms-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-custom-rooms-count')).toBe('0')
    expect(wrapper.text()).toContain('대기 중인 사용자 지정 방이 없습니다.')
  })

  it('creates a custom room and moves to its detail route', async () => {
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-create-button"]').trigger('click')
    await flushPromises()

    expect(createCustomRoomMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(localStorage.getItem('league-of-star.currentCustomRoomId')).toBe('101')
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.customRoom,
      params: {
        roomId: '101',
      },
    })
  })

  it('moves to the invite join route after trimming and uppercasing invite code', async () => {
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-invite-input"]').setValue(' ab12cd ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.customRoomInvite,
      params: {
        inviteCode: 'AB12CD',
      },
    })
  })

  it('does not request invite preview when invite code is blank', async () => {
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-invite-status')).toBe('error')
    expect(wrapper.text()).toContain('초대 코드를 입력해 주세요.')
  })

  it('renders backend error messages for room list and create failures', async () => {
    getCustomRoomsMock.mockRejectedValueOnce(new Error('list failed'))
    createCustomRoomMock.mockRejectedValueOnce(
      new ApiClientError(409, { message: '이미 대기 중인 방이 있습니다.' }),
    )
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-rooms-status')).toBe('error')
    expect(wrapper.text()).toContain('list failed')

    await wrapper.get('[data-testid="custom-room-create-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-create-status')).toBe('error')
    expect(wrapper.text()).toContain('이미 대기 중인 방이 있습니다.')
  })

  it('moves back to the match route from the header action', async () => {
    const wrapper = mount(CustomRoomsPage)
    await flushPromises()

    await wrapper.get('.custom-rooms-actions button').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })
})

function createRoomResponse(overrides: Partial<{ roomId: number }> = {}): CustomRoomResponse {
  return {
    roomId: overrides.roomId ?? 100,
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
    ],
  }
}
