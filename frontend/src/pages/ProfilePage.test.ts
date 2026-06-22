import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { getMyGameRecords } from '@/services/gameRecordService'
import { getMyProfile } from '@/services/profileService'
import { getMyRank } from '@/services/rankService'
import type { GameRecordListResponse } from '@/types/gameRecord'

import ProfilePage from './ProfilePage.vue'

const routerPushMock = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: routerPushMock,
  }),
}))

vi.mock('@/services/profileService', () => ({
  getMyProfile: vi.fn(),
}))

vi.mock('@/services/rankService', () => ({
  getMyRank: vi.fn(),
}))

vi.mock('@/services/gameRecordService', () => ({
  getMyGameRecords: vi.fn(),
}))

const getMyProfileMock = vi.mocked(getMyProfile)
const getMyRankMock = vi.mocked(getMyRank)
const getMyGameRecordsMock = vi.mocked(getMyGameRecords)
const { setLocale } = useLocale()

function recordPage(
  page: number,
  totalPages: number,
  totalElements: number,
): GameRecordListResponse {
  return {
    page,
    size: 10,
    totalPages,
    totalElements,
    hasNext: page < totalPages,
    records: Array.from({ length: page === 3 ? 2 : 10 }, (_, index) => {
      const id = (page - 1) * 10 + index + 1

      return {
        gameId: id,
        result: id % 3 === 0 ? 'DRAW' : id % 2 === 0 ? 'LOSS' : 'WIN',
        opponentUserId: id + 100,
        opponentNickname: `Opponent${id}`,
        rankBefore: 'GOLD_IV',
        rankAfter: id === 1 ? 'GOLD_III' : 'GOLD_IV',
        lpBefore: 40,
        lpAfter: 45,
        lpChange: 5,
        playedAt: '2026-06-20T18:30:00',
      }
    }),
  }
}

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('ko')
    routerPushMock.mockResolvedValue(undefined)
    getMyProfileMock.mockResolvedValue({
      userId: 1,
      email: 'moon@example.com',
      nickname: 'MoonStar',
      createdAt: '2026-06-12T10:00:00',
    })
    getMyRankMock.mockResolvedValue({
      userId: 1,
      tier: 'GOLD',
      division: 'IV',
      rank: 'GOLD_IV',
      lp: 40,
      tierScore: 13,
      wins: 12,
      losses: 8,
      draws: 1,
      rankUpdatedAt: '2026-06-12T11:00:00',
    })
    getMyGameRecordsMock.mockImplementation((page: number) =>
      Promise.resolve(recordPage(page, 3, 22)),
    )
  })

  it('renders profile, rank, and the first records page', async () => {
    const wrapper = mount(ProfilePage)
    await flushPromises()

    expect(getMyProfileMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(getMyRankMock).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(getMyGameRecordsMock).toHaveBeenNthCalledWith(1, 1, expect.any(AbortSignal))
    expect(getMyGameRecordsMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-profile-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-rank-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-count')).toBe('10')
    expect(wrapper.get('main').attributes('data-records-page')).toBe('1')
    expect(wrapper.get('main').attributes('data-records-total-pages')).toBe('3')
    expect(wrapper.text()).toContain('MoonStar')
    expect(wrapper.text()).toContain('moon@example.com')
    expect(wrapper.text()).toContain('GOLD_IV')
    expect(wrapper.text()).toContain('40 LP')
    expect(wrapper.text()).toContain('12승 8패 1무')
    expect(wrapper.text()).toContain('1/3 · 10/22 경기')
    expect(wrapper.text()).toContain('Opponent1')
    expect(wrapper.text()).not.toContain('Opponent11')
    expect(wrapper.findAll('.profile-record-list li')).toHaveLength(10)
    expect(wrapper.findAll('.profile-record-pagination button')).toHaveLength(3)
  })

  it('loads the selected records page from the backend contract', async () => {
    const wrapper = mount(ProfilePage)
    await flushPromises()

    const pageButtons = wrapper.findAll('.profile-record-pagination button')
    expect(pageButtons).toHaveLength(3)

    await pageButtons[1]!.trigger('click')
    await flushPromises()

    const recordRows = wrapper.findAll('.profile-record-list li')

    expect(getMyGameRecordsMock).toHaveBeenNthCalledWith(2, 2, expect.any(AbortSignal))
    expect(wrapper.get('main').attributes('data-records-page')).toBe('2')
    expect(wrapper.get('main').attributes('data-records-count')).toBe('10')
    expect(wrapper.text()).toContain('2/3 · 10/22 경기')
    expect(recordRows).toHaveLength(10)
    expect(recordRows[0]!.text()).toContain('Opponent11')
    expect(recordRows[9]!.text()).toContain('Opponent20')
  })

  it('moves back to the match route from the lobby action', async () => {
    const wrapper = mount(ProfilePage)
    await flushPromises()

    await wrapper.get('[aria-label="로비로"]').trigger('click')

    expect(routerPushMock).toHaveBeenCalledWith({ name: ROUTE_NAMES.match })
  })

  it('keeps rank and records visible when profile loading fails', async () => {
    getMyProfileMock.mockRejectedValueOnce(new Error('profile failed'))

    const wrapper = mount(ProfilePage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-profile-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-rank-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-status')).toBe('success')
    expect(wrapper.text()).toContain('profile failed')
    expect(wrapper.text()).toContain('GOLD_IV')
    expect(wrapper.text()).toContain('Opponent10')
  })

  it('renders an empty records state without blocking profile and rank', async () => {
    getMyGameRecordsMock.mockResolvedValueOnce({
      page: 1,
      size: 10,
      totalPages: 0,
      totalElements: 0,
      hasNext: false,
      records: [],
    })

    const wrapper = mount(ProfilePage)
    await flushPromises()

    expect(getMyGameRecordsMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('main').attributes('data-profile-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-rank-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-count')).toBe('0')
    expect(wrapper.text()).toContain('MoonStar')
    expect(wrapper.text()).toContain('GOLD_IV')
    expect(wrapper.text()).toContain('최근 전적 없음')
  })

  it('keeps profile and records visible when rank loading fails', async () => {
    getMyRankMock.mockRejectedValueOnce(new Error('rank failed'))

    const wrapper = mount(ProfilePage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-profile-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-rank-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-records-status')).toBe('success')
    expect(wrapper.text()).toContain('MoonStar')
    expect(wrapper.text()).toContain('rank failed')
    expect(wrapper.text()).toContain('Opponent10')
  })

  it('keeps profile and rank visible when records loading fails', async () => {
    getMyGameRecordsMock.mockRejectedValueOnce(new Error('records failed'))

    const wrapper = mount(ProfilePage)
    await flushPromises()

    expect(wrapper.get('main').attributes('data-profile-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-rank-status')).toBe('success')
    expect(wrapper.get('main').attributes('data-records-status')).toBe('error')
    expect(wrapper.get('main').attributes('data-records-count')).toBe('0')
    expect(wrapper.text()).toContain('MoonStar')
    expect(wrapper.text()).toContain('GOLD_IV')
    expect(wrapper.text()).toContain('records failed')
  })
})
