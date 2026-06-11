export const ROUTE_PATHS = {
  home: '/',
  login: '/login',
  signup: '/signup',
  match: '/match',
  gameWaiting: '/game/:gameRoomId/waiting',
  gamePlay: '/game/:gameRoomId/play',
  gameResult: '/game/:gameRoomId/result',
} as const

export const ROUTE_NAMES = {
  home: 'home',
  login: 'login',
  signup: 'signup',
  match: 'match',
  gameWaiting: 'game-waiting',
  gamePlay: 'game-play',
  gameResult: 'game-result',
} as const
