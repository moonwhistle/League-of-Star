import { createRouter, createWebHistory } from 'vue-router'

import { ROUTE_NAMES, ROUTE_PATHS } from '@/constants/routes'
import GamePlayPage from '@/pages/GamePlayPage.vue'
import GameResultPage from '@/pages/GameResultPage.vue'
import GameWaitingPage from '@/pages/GameWaitingPage.vue'
import HomePage from '@/pages/HomePage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import MatchPage from '@/pages/MatchPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: ROUTE_PATHS.home,
      name: ROUTE_NAMES.home,
      component: HomePage,
    },
    {
      path: ROUTE_PATHS.login,
      name: ROUTE_NAMES.login,
      component: LoginPage,
    },
    {
      path: ROUTE_PATHS.match,
      name: ROUTE_NAMES.match,
      component: MatchPage,
    },
    {
      path: ROUTE_PATHS.gameWaiting,
      name: ROUTE_NAMES.gameWaiting,
      component: GameWaitingPage,
    },
    {
      path: ROUTE_PATHS.gamePlay,
      name: ROUTE_NAMES.gamePlay,
      component: GamePlayPage,
    },
    {
      path: ROUTE_PATHS.gameResult,
      name: ROUTE_NAMES.gameResult,
      component: GameResultPage,
    },
  ],
})
