import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { joinCustomRoom } from '@/services/customRoomService'
import type { CustomRoomResponse } from '@/types/customRoom'

import CustomRoomJoinPage from './CustomRoomJoinPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    inviteCode: 'ab12cd',
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
  joinCustomRoom: vi.fn(),
}))

const joinCustomRoomMock = vi.mocked(joinCustomRoom)
const { setLocale } = useLocale()

describe('CustomRoomJoinPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('ko')
    routeMock.params.inviteCode = 'ab12cd'
    routerPushMock.mockResolvedValue(undefined)
    joinCustomRoomMock.mockResolvedValue(createRoomResponse())
  })

  it('joins a room from the invite route and moves to room detail', async () => {
    mount(CustomRoomJoinPage)
    await flushPromises()

    expect(joinCustomRoomMock).toHaveBeenCalledWith('AB12CD', expect.any(AbortSignal))
    expect(routerPushMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.customRoom,
      params: {
        roomId: '100',
      },
    })
  })

  it('renders backend join errors without moving to room detail', async () => {
    joinCustomRoomMock.mockRejectedValueOnce(
      new ApiClientError(409, { message: '가득 찬 방입니다.' }),
    )

    const wrapper = mount(CustomRoomJoinPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-join-status')).toBe('error')
    expect(wrapper.text()).toContain('가득 찬 방입니다.')
    expect(routerPushMock).not.toHaveBeenCalledWith(
      expect.objectContaining({ name: ROUTE_NAMES.customRoom }),
    )
  })

  it('does not request join when inviteCode is missing', async () => {
    routeMock.params.inviteCode = ''

    const wrapper = mount(CustomRoomJoinPage)
    await flushPromises()

    expect(joinCustomRoomMock).not.toHaveBeenCalled()
    expect(wrapper.get('main').attributes('data-custom-room-join-status')).toBe('error')
    expect(wrapper.text()).toContain('초대 코드가 올바르지 않습니다.')
  })

  it('moves back to public rooms from the fallback action', async () => {
    joinCustomRoomMock.mockRejectedValueOnce(new Error('join failed'))
    const wrapper = mount(CustomRoomJoinPage)
    await flushPromises()

    await wrapper.get('button').trigger('click')

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
    ],
  }
}
