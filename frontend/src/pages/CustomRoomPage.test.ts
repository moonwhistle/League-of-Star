import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { getCustomRoom, leaveCustomRoom } from '@/services/customRoomService'
import { connectCustomRoomWebSocket } from '@/services/realtime/customRoomWebSocket'
import type { CustomRoomResponse, CustomRoomWebSocketServerMessage } from '@/types/customRoom'

import CustomRoomPage from './CustomRoomPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    roomId: '100',
  },
}))
const routerPushMock = vi.hoisted(() => vi.fn())
const customRoomSocketMock = vi.hoisted(() => ({
  handlers: undefined as
    | {
        onOpen?: (event: Event) => void
        onMessage?: (message: CustomRoomWebSocketServerMessage, event: MessageEvent<string>) => void
        onError?: (error: Event | unknown) => void
        onClose?: (event: CloseEvent) => void
      }
    | undefined,
  close: vi.fn(),
  socket: new EventTarget() as WebSocket,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/customRoomService', () => ({
  getCustomRoom: vi.fn(),
  leaveCustomRoom: vi.fn(),
}))

vi.mock('@/services/realtime/customRoomWebSocket', () => ({
  connectCustomRoomWebSocket: vi.fn(),
}))

const getCustomRoomMock = vi.mocked(getCustomRoom)
const leaveCustomRoomMock = vi.mocked(leaveCustomRoom)
const connectCustomRoomWebSocketMock = vi.mocked(connectCustomRoomWebSocket)
const { setLocale } = useLocale()

describe('CustomRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('ko')
    routeMock.params.roomId = '100'
    routerPushMock.mockResolvedValue(undefined)
    getCustomRoomMock.mockResolvedValue(createRoomResponse())
    leaveCustomRoomMock.mockResolvedValue(createRoomResponse())
    customRoomSocketMock.handlers = undefined
    customRoomSocketMock.close.mockClear()
    connectCustomRoomWebSocketMock.mockImplementation((_roomId, handlers) => {
      customRoomSocketMock.handlers = handlers
      return {
        socket: customRoomSocketMock.socket,
        setHandlers: vi.fn(),
        close: customRoomSocketMock.close,
      }
    })
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
    expect(connectCustomRoomWebSocketMock).toHaveBeenCalledWith(100, expect.any(Object))
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

  it('updates room participants from ROOM_UPDATED messages', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onMessage?.(
      {
        type: 'ROOM_UPDATED',
        payload: createRoomResponse({
          participants: [
            {
              userId: 1,
              nickname: 'Host',
              role: 'OWNER',
            },
          ],
        }),
      },
      new MessageEvent('message', { data: '{}' }),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-participant-count')).toBe('1')
    expect(wrapper.text()).toContain('Host')
    expect(wrapper.text()).not.toContain('Guest')
  })

  it('shows closed state from ROOM_CLOSED without calling leave API', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onMessage?.(
      {
        type: 'ROOM_CLOSED',
        payload: createRoomResponse({
          status: 'CLOSED',
          participants: [],
        }),
      },
      new MessageEvent('message', { data: '{}' }),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-status')).toBe('closed')
    expect(wrapper.text()).toContain('방이 닫혔습니다.')
    expect(leaveCustomRoomMock).not.toHaveBeenCalled()

    await wrapper.get('.custom-room-state--closed button').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('leaves the room by HTTP command and moves to public rooms', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-leave-button"]').trigger('click')
    await flushPromises()

    expect(leaveCustomRoomMock).toHaveBeenCalledWith(100, expect.any(AbortSignal))
    expect(customRoomSocketMock.close).toHaveBeenCalledWith(1000, 'custom room page closed')
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.customRooms })
  })

  it('renders leave failures without moving route', async () => {
    leaveCustomRoomMock.mockRejectedValueOnce(
      new ApiClientError(400, { message: '참가자가 아닙니다.' }),
    )
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-leave-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-leave-status')).toBe('error')
    expect(wrapper.text()).toContain('참가자가 아닙니다.')
    expect(routerPushMock).not.toHaveBeenCalledWith({ name: ROUTE_NAMES.customRooms })
  })

  it('shows socket errors without calling leave API', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onError?.(new Error('socket failed'))
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-socket-status')).toBe('error')
    expect(wrapper.text()).toContain('socket failed')
    expect(leaveCustomRoomMock).not.toHaveBeenCalled()
  })
})

function createRoomResponse(overrides: Partial<CustomRoomResponse> = {}): CustomRoomResponse {
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
    ...overrides,
  }
}
