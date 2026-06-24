import { createRouter, createWebHistory } from 'vue-router'

import { ROUTE_NAMES, ROUTE_PATHS } from '@/constants/routes'

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
      component: () => import('@/pages/LoginPage.vue'),
      meta: {
        guestOnly: true,
      },
    },
    {
      path: ROUTE_PATHS.signup,
      name: ROUTE_NAMES.signup,
      component: () => import('@/pages/SignupPage.vue'),
      meta: {
        guestOnly: true,
      },
    },
    {
      path: ROUTE_PATHS.match,
      name: ROUTE_NAMES.match,
      component: () => import('@/pages/MatchPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.profile,
      name: ROUTE_NAMES.profile,
      component: () => import('@/pages/ProfilePage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.customRooms,
      name: ROUTE_NAMES.customRooms,
      component: () => import('@/pages/CustomRoomsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.customRoom,
      name: ROUTE_NAMES.customRoom,
      component: () => import('@/pages/CustomRoomPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.gameWaiting,
      name: ROUTE_NAMES.gameWaiting,
      component: () => import('@/pages/GameWaitingPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.gamePlay,
      name: ROUTE_NAMES.gamePlay,
      component: () => import('@/pages/GamePlayPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: ROUTE_PATHS.gameResult,
      name: ROUTE_NAMES.gameResult,
      component: () => import('@/pages/GameResultPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
  ],
})

router.beforeEach(authGuard)
