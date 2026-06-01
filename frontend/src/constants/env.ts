function getRequiredEnv(name: string): string {
  const value = import.meta.env[name]

  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`Missing required environment variable: ${name}`)
  }

  return value
}

export const API_BASE_URL = getRequiredEnv('VITE_API_BASE_URL')
export const WS_BASE_URL = getRequiredEnv('VITE_WS_BASE_URL')
export const GAME_VIDEO_URL = getRequiredEnv('VITE_GAME_VIDEO_URL')
