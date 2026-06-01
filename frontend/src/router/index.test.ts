import { describe, expect, it } from 'vitest'

import { ROUTE_NAMES } from '@/constants/routes'

import { router } from './index'

const protectedRouteNames = [
  ROUTE_NAMES.match,
  ROUTE_NAMES.gameWaiting,
  ROUTE_NAMES.gamePlay,
  ROUTE_NAMES.gameResult,
] as const

function getRoute(routeName: string) {
  const route = router.getRoutes().find((item) => item.name === routeName)

  expect(route).toBeDefined()

  return route!
}

describe('router route meta', () => {
  it.each(protectedRouteNames)('marks %s as protected', (routeName) => {
    expect(getRoute(routeName).meta.requiresAuth).toBe(true)
  })

  it('marks login as guest only', () => {
    expect(getRoute(ROUTE_NAMES.login).meta.guestOnly).toBe(true)
  })

  it('keeps home as public', () => {
    const homeRoute = getRoute(ROUTE_NAMES.home)

    expect(homeRoute.meta.requiresAuth).not.toBe(true)
    expect(homeRoute.meta.guestOnly).not.toBe(true)
  })
})
