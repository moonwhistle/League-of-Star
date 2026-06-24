import type {
  NavigationGuardNext,
  RouteLocationNormalized,
  RouteLocationNormalizedLoaded,
  RouteMeta,
} from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ROUTE_NAMES } from '@/constants/routes'
import { hasAuthSession } from '@/services/authSession'

import { authGuard } from './authGuard'
import { router } from './index'

vi.mock('@/services/authSession', () => ({
  hasAuthSession: vi.fn(),
}))

const hasAuthSessionMock = vi.mocked(hasAuthSession)

const protectedRouteNames = [
  ROUTE_NAMES.match,
  ROUTE_NAMES.profile,
  ROUTE_NAMES.customRooms,
  ROUTE_NAMES.customRoom,
  ROUTE_NAMES.customRoomInvite,
  ROUTE_NAMES.gameWaiting,
  ROUTE_NAMES.gamePlay,
  ROUTE_NAMES.gameResult,
] as const

function getRouteMeta(routeName: string): RouteMeta {
  const route = router.getRoutes().find((item) => item.name === routeName)

  expect(route).toBeDefined()

  return route!.meta
}

function createRoute(meta: RouteMeta = {}, fullPath = '/protected'): RouteLocationNormalized {
  return { fullPath, meta } as RouteLocationNormalized
}

function runGuard(meta: RouteMeta, fullPath?: string) {
  return authGuard(
    createRoute(meta, fullPath),
    {} as RouteLocationNormalizedLoaded,
    vi.fn() as NavigationGuardNext,
  )
}

describe('authGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each(protectedRouteNames)('redirects unauthenticated %s route to login', (routeName) => {
    hasAuthSessionMock.mockReturnValue(false)

    expect(runGuard(getRouteMeta(routeName), '/custom-games/join/AB12CD')).toEqual({
      name: ROUTE_NAMES.login,
      query: {
        redirect: '/custom-games/join/AB12CD',
      },
    })
  })

  it.each(protectedRouteNames)('allows authenticated %s route', (routeName) => {
    hasAuthSessionMock.mockReturnValue(true)

    expect(runGuard(getRouteMeta(routeName))).toBe(true)
  })

  it('redirects authenticated guest only routes to match', () => {
    hasAuthSessionMock.mockReturnValue(true)

    expect(runGuard({ guestOnly: true })).toEqual({ name: ROUTE_NAMES.match })
  })

  it('allows unauthenticated guest only routes', () => {
    hasAuthSessionMock.mockReturnValue(false)

    expect(runGuard({ guestOnly: true })).toBe(true)
  })

  it.each([false, true])('allows public routes when authenticated is %s', (isAuthenticated) => {
    hasAuthSessionMock.mockReturnValue(isAuthenticated)

    expect(runGuard({})).toBe(true)
  })
})
