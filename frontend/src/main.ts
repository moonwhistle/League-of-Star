import { createApp } from 'vue'
import App from './App.vue'
import { ROUTE_NAMES } from './constants/routes'
import { router } from './router'
import { addAuthSessionExpiredListener } from './services/authSessionEvents'
import './styles/variables.css'
import './styles/base.css'

addAuthSessionExpiredListener(() => {
  if (router.currentRoute.value.name !== ROUTE_NAMES.login) {
    void router.push({ name: ROUTE_NAMES.login })
  }
})

createApp(App).use(router).mount('#app')
