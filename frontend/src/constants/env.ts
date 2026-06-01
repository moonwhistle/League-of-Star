const DEFAULT_ENV = {
  VITE_API_BASE_URL: 'http://localhost:8080',
  VITE_WS_BASE_URL: 'ws://localhost:8080',
  VITE_GAME_VIDEO_URL: '/assets/game/dragon-view.mp4',
} as const

function getEnv(name: keyof typeof DEFAULT_ENV): string {
  const value = import.meta.env[name]

  if (typeof value === 'string' && value.trim() !== '') {
    return value
  }

  if (import.meta.env.DEV) {
    return DEFAULT_ENV[name]
  }

  throw new Error(`Missing required environment variable: ${name}`)
}

export const API_BASE_URL = getEnv('VITE_API_BASE_URL')
export const WS_BASE_URL = getEnv('VITE_WS_BASE_URL')
export const GAME_VIDEO_URL = getEnv('VITE_GAME_VIDEO_URL')
