import type { NavigationGuard } from 'vue-router'

import { ROUTE_NAMES } from '@/constants/routes'
import { hasAuthSession } from '@/services/authSession'

export const authGuard: NavigationGuard = (to) => {
  const isAuthenticated = hasAuthSession()

  if (to.meta.requiresAuth === true && !isAuthenticated) {
    return { name: ROUTE_NAMES.login }
  }

  if (to.meta.guestOnly === true && isAuthenticated) {
    return { name: ROUTE_NAMES.match }
  }

  return true
}
