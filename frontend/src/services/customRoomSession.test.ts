import { beforeEach, describe, expect, it } from 'vitest'

import {
  clearCurrentCustomRoom,
  getCurrentCustomRoomId,
  rememberCurrentCustomRoom,
} from './customRoomSession'

describe('customRoomSession', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores and clears the current custom room id', () => {
    rememberCurrentCustomRoom(100)

    expect(getCurrentCustomRoomId()).toBe('100')

    clearCurrentCustomRoom()

    expect(getCurrentCustomRoomId()).toBeNull()
  })

  it('ignores blank room ids', () => {
    rememberCurrentCustomRoom(' ')

    expect(getCurrentCustomRoomId()).toBeNull()
  })
})
