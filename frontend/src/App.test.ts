import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import App from './App.vue'

describe('App', () => {
  it('mounts the router shell', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: true,
        },
      },
    })

    expect(wrapper.findComponent({ name: 'RouterView' }).exists()).toBe(true)
  })
})
