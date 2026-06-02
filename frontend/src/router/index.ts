import { createRouter, createWebHistory } from 'vue-router'

import { ROUTE_NAMES, ROUTE_PATHS } from '@/constants/routes'
import GamePlayPage from '@/pages/GamePlayPage.vue'
import GameResultPage from '@/pages/GameResultPage.vue'
import GameWaitingPage from '@/pages/GameWaitingPage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import MatchPage from '@/pages/MatchPage.vue'

import { authGuard } from './authGuard'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: ROUTE_PATHS.home,
      name: ROUTE_NAMES.home,
      redirect: {
        name: ROUTE_NAMES.match,
      },
    },
    {
      path: ROUTE_PATHS.login,
      name: ROUTE_NAMES.login,
      component: LoginPage,
      meta: {
        guestOnly: true,
      },
    },
    {
      path: ROUTE_PATHS.match,
      name: ROUTE_NAMES.match,
      component: MatchPage,
      // TODO(issue-78): Temporary preview access while the match page UI is being implemented.
      // Restore requiresAuth before merging the completed authenticated match flow.
    },
    {
      path: ROUTE_PATHS.gameWaiting,
      name: ROUTE_NAMES.gameWaiting,
      component: GameWaitingPage,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.gamePlay,
      name: ROUTE_NAMES.gamePlay,
      component: GamePlayPage,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.gameResult,
      name: ROUTE_NAMES.gameResult,
      component: GameResultPage,
      meta: {
        requiresAuth: true,
      },
    },
  ],
})

router.beforeEach(authGuard)
