import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { readCustomGameStartPayload } from '@/services/customGameStartPayload'
import { getCustomRoom, leaveCustomRoom, startCustomRoom } from '@/services/customRoomService'
import { getMyProfile } from '@/services/profileService'
import { connectCustomRoomWebSocket } from '@/services/realtime/customRoomWebSocket'
import type { CustomRoomResponse, CustomRoomWebSocketServerMessage } from '@/types/customRoom'

import CustomRoomPage from './CustomRoomPage.vue'

const routeMock = vi.hoisted(() => ({
  params: {
    roomId: '100',
  },
}))
const routerPushMock = vi.hoisted(() => vi.fn())
const routerReplaceMock = vi.hoisted(() => vi.fn())
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
    replace: routerReplaceMock,
  }),
}))

vi.mock('@/services/customRoomService', () => ({
  getCustomRoom: vi.fn(),
  leaveCustomRoom: vi.fn(),
  startCustomRoom: vi.fn(),
}))

vi.mock('@/services/profileService', () => ({
  getMyProfile: vi.fn(),
}))

vi.mock('@/services/realtime/customRoomWebSocket', () => ({
  connectCustomRoomWebSocket: vi.fn(),
}))

const getCustomRoomMock = vi.mocked(getCustomRoom)
const leaveCustomRoomMock = vi.mocked(leaveCustomRoom)
const startCustomRoomMock = vi.mocked(startCustomRoom)
const getMyProfileMock = vi.mocked(getMyProfile)
const connectCustomRoomWebSocketMock = vi.mocked(connectCustomRoomWebSocket)
const { setLocale } = useLocale()

describe('CustomRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    setLocale('ko')
    routeMock.params.roomId = '100'
    routerPushMock.mockResolvedValue(undefined)
    routerReplaceMock.mockResolvedValue(undefined)
    getCustomRoomMock.mockResolvedValue(createRoomResponse())
    leaveCustomRoomMock.mockResolvedValue(createRoomResponse())
    startCustomRoomMock.mockResolvedValue(createStartResponse())
    getMyProfileMock.mockResolvedValue({
      userId: 1,
      email: 'host@example.com',
      nickname: 'Host',
      createdAt: '2026-06-12T10:00:00',
    })
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
    expect(localStorage.getItem('league-of-star.currentCustomRoomId')).toBe('100')
    expect(wrapper.text()).toContain("Host's room")
    expect(wrapper.text()).toContain('현재 인원')
    expect(wrapper.text()).toContain('2/2')
    expect(wrapper.text()).toContain('Host')
    expect(wrapper.text()).toContain('Guest')
    expect(wrapper.text()).toContain('방장')
    expect(wrapper.get('[data-testid="custom-room-start-button"]').text()).toContain('게임 시작')
    expect(wrapper.get('[data-testid="custom-room-start-button"]').classes()).toContain(
      'custom-room-primary-start',
    )
    expect(wrapper.find('.custom-room-start-panel').exists()).toBe(true)
    expect(wrapper.text()).toContain('대기실 실시간 연결이 열리면 게임을 시작할 수 있습니다.')
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

  it('does not render a room list escape action while participating in a room', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    expect(wrapper.text()).not.toContain('대기실 목록')
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

  it('starts a custom room by HTTP command without moving routes before ROOM_STARTED', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onOpen?.(new Event('open'))
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-start-button"]').trigger('click')
    await flushPromises()

    expect(startCustomRoomMock).toHaveBeenCalledWith(100, expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-custom-room-start-status')).toBe('waiting')
    expect(wrapper.text()).toContain('게임 시작 이벤트를 기다리는 중입니다.')
    expect(routerReplaceMock).not.toHaveBeenCalled()
  })

  it('does not show the start button to non-owner participants', async () => {
    getMyProfileMock.mockResolvedValueOnce({
      userId: 2,
      email: 'guest@example.com',
      nickname: 'Guest',
      createdAt: '2026-06-12T10:00:00',
    })

    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    expect(wrapper.find('[data-testid="custom-room-start-button"]').exists()).toBe(false)
  })

  it('keeps the start button visible when profile loading fails and relies on backend start validation', async () => {
    getMyProfileMock.mockRejectedValueOnce(new Error('profile unavailable'))

    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onOpen?.(new Event('open'))
    await flushPromises()

    const startButton = wrapper.get('[data-testid="custom-room-start-button"]')
    expect(startButton.text()).toContain('게임 시작')

    await startButton.trigger('click')
    await flushPromises()

    expect(startCustomRoomMock).toHaveBeenCalledWith(100, expect.any(AbortSignal))
  })

  it('keeps start disabled until two participants and websocket open are ready', async () => {
    getCustomRoomMock.mockResolvedValueOnce(
      createRoomResponse({
        participants: [
          {
            userId: 1,
            nickname: 'Host',
            role: 'OWNER',
          },
        ],
      }),
    )

    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    const startButton = wrapper.get('[data-testid="custom-room-start-button"]')
    expect(startButton.attributes('disabled')).toBeDefined()
    expect(startButton.text()).toContain('게임 시작')
    expect(wrapper.text()).toContain('참가자 2명이 모이면 게임을 시작할 수 있습니다.')
  })

  it('stores ROOM_STARTED payload and moves to GamePlay route', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onOpen?.(new Event('open'))
    customRoomSocketMock.handlers?.onMessage?.(
      {
        type: 'ROOM_STARTED',
        payload: createStartResponse(),
      },
      new MessageEvent('message', { data: '{}' }),
    )
    await flushPromises()

    expect(readCustomGameStartPayload(200)).toMatchObject({
      roomId: 100,
      gameRoomId: 200,
      gameMode: 'CUSTOM',
      myUserId: 1,
      opponentUserId: 2,
    })
    expect(localStorage.getItem('league-of-star.currentCustomRoomId')).toBeNull()
    expect(customRoomSocketMock.close).toHaveBeenCalledWith(1000, 'custom room page closed')
    expect(routerReplaceMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.gamePlay,
      params: {
        gameRoomId: '200',
      },
    })
    expect(wrapper.get('main').attributes('data-custom-room-start-status')).toBe('started')
  })

  it('stores ROOM_STARTED payload with owner fallback after starting without profile data', async () => {
    getMyProfileMock.mockRejectedValueOnce(new Error('profile unavailable'))

    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onOpen?.(new Event('open'))
    await flushPromises()

    await wrapper.get('[data-testid="custom-room-start-button"]').trigger('click')
    await flushPromises()

    customRoomSocketMock.handlers?.onMessage?.(
      {
        type: 'ROOM_STARTED',
        payload: createStartResponse(),
      },
      new MessageEvent('message', { data: '{}' }),
    )
    await flushPromises()

    expect(readCustomGameStartPayload(200)).toMatchObject({
      myUserId: 1,
      opponentUserId: 2,
    })
    expect(routerReplaceMock).toHaveBeenCalledWith({
      name: ROUTE_NAMES.gamePlay,
      params: {
        gameRoomId: '200',
      },
    })
  })

  it('shows an error when ROOM_STARTED payload is invalid', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    customRoomSocketMock.handlers?.onMessage?.(
      {
        type: 'ROOM_STARTED',
        payload: {
          ...createStartResponse(),
          gameMode: 'MATCH',
        },
      } as unknown as CustomRoomWebSocketServerMessage,
      new MessageEvent('message', { data: '{}' }),
    )
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-start-status')).toBe('error')
    expect(wrapper.text()).toContain('게임 시작 정보를 확인할 수 없습니다.')
    expect(routerReplaceMock).not.toHaveBeenCalled()
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
    expect(wrapper.get('main').attributes('data-custom-room-socket-status')).toBe('closed')
    expect(localStorage.getItem('league-of-star.currentCustomRoomId')).toBeNull()
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
    expect(localStorage.getItem('league-of-star.currentCustomRoomId')).toBeNull()
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

  it('shows a socket close warning without calling leave API', async () => {
    const wrapper = mount(CustomRoomPage)
    await flushPromises()

    const closeEvent = new CloseEvent('close', {
      code: 1000,
      reason: 'duplicate connection closed',
    })
    Object.defineProperty(closeEvent, 'target', {
      value: customRoomSocketMock.socket,
    })
    customRoomSocketMock.handlers?.onClose?.(closeEvent)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-custom-room-socket-status')).toBe('closed')
    expect(wrapper.text()).toContain('대기실 실시간 연결이 끊겼습니다.')
    expect(getCustomRoomMock).toHaveBeenCalledTimes(2)
    expect(getCustomRoomMock).toHaveBeenLastCalledWith('100')
    expect(connectCustomRoomWebSocketMock).toHaveBeenCalledTimes(1)
    expect(leaveCustomRoomMock).not.toHaveBeenCalled()
  })

  it('reconnects custom room websocket after abnormal close', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mount(CustomRoomPage)
      await flushPromises()

      const closeEvent = new CloseEvent('close', {
        code: 1006,
        reason: 'network closed',
      })
      Object.defineProperty(closeEvent, 'target', {
        value: customRoomSocketMock.socket,
      })
      customRoomSocketMock.handlers?.onClose?.(closeEvent)
      await flushPromises()

      expect(wrapper.get('main').attributes('data-custom-room-socket-status')).toBe('closed')
      expect(connectCustomRoomWebSocketMock).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(800)
      await flushPromises()

      expect(connectCustomRoomWebSocketMock).toHaveBeenCalledTimes(2)
      expect(connectCustomRoomWebSocketMock).toHaveBeenLastCalledWith(100, expect.any(Object))
    } finally {
      vi.useRealTimers()
    }
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

function createStartResponse() {
  return {
    roomId: 100,
    gameRoomId: 200,
    gameMode: 'CUSTOM' as const,
    serverTime: 10_000,
    startAt: 13_000,
    webSocketUrl: '/ws/game/200',
    scenario: {
      starCoreMaxHp: 10000,
      durationMs: 12000,
      hpTimeline: [
        {
          timeMs: 0,
          hp: 10000,
        },
      ],
    },
  }
}
