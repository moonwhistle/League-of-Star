import { describe, expect, it } from 'vitest'

import { ROUTE_NAMES } from '@/constants/routes'

import { router } from './index'

const protectedRouteNames = [
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

  it('redirects home to match', () => {
    const homeRoute = getRoute(ROUTE_NAMES.home)

    expect(homeRoute.redirect).toEqual({
      name: ROUTE_NAMES.match,
    })
  })

  it('keeps match public for temporary issue 78 UI preview', () => {
    const matchRoute = getRoute(ROUTE_NAMES.match)

    expect(matchRoute.meta.requiresAuth).not.toBe(true)
  })
})
