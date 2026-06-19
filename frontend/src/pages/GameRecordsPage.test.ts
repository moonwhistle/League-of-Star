import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { getMyGameRecords } from '@/services/gameRecordService'
import type { GameRecordListResponse } from '@/types/gameRecord'

import GameRecordsPage from './GameRecordsPage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/gameRecordService', () => ({
  getMyGameRecords: vi.fn(),
}))

const { setLocale } = useLocale()
const getMyGameRecordsMock = vi.mocked(getMyGameRecords)

enableAutoUnmount(afterEach)

describe('GameRecordsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routerPushMock.mockResolvedValue(undefined)
    setLocale('ko')
  })

  it('loads page 1 on mount', async () => {
    getMyGameRecordsMock.mockResolvedValue(createRecordsResponse())

    const wrapper = mount(GameRecordsPage)
    await flushPromises()

    expect(getMyGameRecordsMock).toHaveBeenCalledWith(1, expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-records-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-page')).toBe('1')
  })

  it('renders loading, empty, and return-to-match states', async () => {
    getMyGameRecordsMock.mockImplementation(() => new Promise(() => {}))

    const loadingWrapper = mount(GameRecordsPage)
    await loadingWrapper.vm.$nextTick()

    expect(loadingWrapper.get('main').attributes('data-records-status')).toBe('loading')
    expect(loadingWrapper.text()).toContain('전적을 불러오는 중')
    loadingWrapper.unmount()

    getMyGameRecordsMock.mockResolvedValueOnce(
      createRecordsResponse({
        totalPages: 0,
        totalElements: 0,
        hasNext: false,
        records: [],
      }),
    )

    const emptyWrapper = mount(GameRecordsPage)
    await flushPromises()

    expect(emptyWrapper.text()).toContain('아직 전적이 없습니다.')

    await emptyWrapper.get('.records-return-button').trigger('click')
    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('renders error state and retries the same page', async () => {
    getMyGameRecordsMock
      .mockRejectedValueOnce(
        new ApiClientError(500, {
          message: '전적 서버 오류',
        }),
      )
      .mockResolvedValueOnce(createRecordsResponse())

    const wrapper = mount(GameRecordsPage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-records-status')).toBe('error')
    expect(wrapper.text()).toContain('전적 서버 오류')

    await wrapper.get('.records-state button').trigger('click')
    await flushPromises()

    expect(getMyGameRecordsMock).toHaveBeenNthCalledWith(2, 1, expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-records-status')).toBe('success')
  })

  it('renders records in horizontal matchup bars', async () => {
    getMyGameRecordsMock.mockResolvedValue(createRecordsResponse())

    const wrapper = mount(GameRecordsPage)
    await flushPromises()

    const text = wrapper.text()

    expect(wrapper.findAll('.record-card')).toHaveLength(2)
    expect(wrapper.find('.record-matchup').text()).toContain('나VSVoidwalker')
    expect(text).toContain('승리')
    expect(text).toContain('패배')
    expect(text).toContain('GOLD_IV -> GOLD_III')
    expect(text).toContain('SILVER_I')
    expect(text).not.toContain('SILVER_I -> SILVER_I')
    expect(text).toContain('80 -> 105')
    expect(text).toContain('+25')
    expect(text).toContain('60 -> 45')
    expect(text).toContain('-15')
    expect(text).toContain('12 경기')
  })

  it('uses totalPages and hasNext metadata for pagination', async () => {
    getMyGameRecordsMock.mockResolvedValueOnce(createRecordsResponse()).mockResolvedValueOnce(
      createRecordsResponse({
        page: 2,
        totalPages: 2,
        hasNext: false,
        records: [
          createRecord({
            gameId: 12,
            opponentNickname: 'SecondPage',
          }),
        ],
      }),
    )

    const wrapper = mount(GameRecordsPage)
    await flushPromises()

    const nextButton = wrapper
      .findAll('.records-pagination button')
      .find((button) => button.text() === '다음')

    expect(nextButton?.attributes('disabled')).toBeUndefined()

    await nextButton?.trigger('click')
    await flushPromises()

    expect(getMyGameRecordsMock).toHaveBeenNthCalledWith(2, 2, expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-records-page')).toBe('2')
    expect(wrapper.text()).toContain('SecondPage')
    expect(
      wrapper.findAll('.records-pagination button').at(-1)?.attributes('disabled'),
    ).toBeDefined()
  })

  it('aborts in-flight requests when unmounted', async () => {
    let capturedSignal: AbortSignal | undefined
    getMyGameRecordsMock.mockImplementation((_page, signal) => {
      capturedSignal = signal
      return new Promise(() => {})
    })

    const wrapper = mount(GameRecordsPage)
    await wrapper.vm.$nextTick()
    wrapper.unmount()

    expect(capturedSignal?.aborted).toBe(true)
  })
})

function createRecordsResponse(
  overrides: Partial<GameRecordListResponse> = {},
): GameRecordListResponse {
  return {
    page: 1,
    size: 10,
    totalPages: 2,
    totalElements: 12,
    hasNext: true,
    records: [
      createRecord(),
      createRecord({
        gameId: 11,
        result: 'LOSS',
        opponentUserId: 3,
        opponentNickname: 'MirrorStar',
        rankBefore: 'SILVER_I',
        rankAfter: 'SILVER_I',
        lpBefore: 60,
        lpAfter: 45,
        lpChange: -15,
      }),
    ],
    ...overrides,
  }
}

function createRecord(overrides = {}) {
  return {
    gameId: 10,
    result: 'WIN',
    opponentUserId: 2,
    opponentNickname: 'Voidwalker',
    rankBefore: 'GOLD_IV',
    rankAfter: 'GOLD_III',
    lpBefore: 80,
    lpAfter: 105,
    lpChange: 25,
    playedAt: '2026-06-19T10:30:00',
    ...overrides,
  } satisfies GameRecordListResponse['records'][number]
}
