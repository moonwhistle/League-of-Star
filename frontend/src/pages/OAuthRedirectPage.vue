<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main
    class="oauth-redirect-page"
    :style="{ '--oauth-background-image': `url(${backgroundImageUrl})` }"
  >
    <section class="oauth-redirect-card" aria-labelledby="oauth-redirect-title">
      <div class="oauth-redirect-heading">
        <h1 id="oauth-redirect-title">
          {{ t('oauthRedirect.title') }}
        </h1>
        <p>{{ t('oauthRedirect.subtitle') }}</p>
      </div>

      <div class="oauth-redirect-state" :data-status="exchangeStatus">
        <strong>{{ statusTitle }}</strong>
        <p>{{ statusDescription }}</p>
      </div>

      <p v-if="errorMessage !== ''" class="oauth-redirect-error" role="alert">
        {{ errorMessage }}
      </p>

      <button
        v-if="exchangeStatus === 'error'"
        class="oauth-redirect-button"
        type="button"
        @click="goToLogin"
      >
        {{ t('oauthRedirect.backToLogin') }}
      </button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { exchangeOAuthToken } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'

import backgroundImageUrl from '../../img/background-new-sharp.png'

type ExchangeStatus = 'idle' | 'loading' | 'error' | 'success'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()

const exchangeStatus = ref<ExchangeStatus>('idle')
const errorMessage = ref('')
const latestExchangeToken = ref('')

const oauthCode = computed(() => getCodeQuery())
const statusTitle = computed(() => {
  if (exchangeStatus.value === 'success') {
    return t('oauthRedirect.successTitle')
  }

  if (exchangeStatus.value === 'error') {
    return t('oauthRedirect.errorTitle')
  }

  return t('oauthRedirect.loadingTitle')
})
const statusDescription = computed(() => {
  if (exchangeStatus.value === 'success') {
    return t('oauthRedirect.successDescription')
  }

  if (exchangeStatus.value === 'error') {
    return t('oauthRedirect.errorDescription')
  }

  return t('oauthRedirect.loadingDescription')
})

onMounted(() => {
  void exchangeCurrentCode()
})

watch(oauthCode, () => {
  void exchangeCurrentCode()
})

async function exchangeCurrentCode() {
  const code = oauthCode.value
  const exchangeToken = `${code}:${Date.now()}`
  latestExchangeToken.value = exchangeToken
  errorMessage.value = ''

  if (code === '') {
    exchangeStatus.value = 'error'
    errorMessage.value = t('oauthRedirect.codeMissing')
    return
  }

  exchangeStatus.value = 'loading'

  try {
    const response = await exchangeOAuthToken({
      code,
    })

    if (latestExchangeToken.value !== exchangeToken) {
      return
    }

    setAuthTokens(response.accessToken, response.refreshToken)
    exchangeStatus.value = 'success'
    await router.replace({ name: ROUTE_NAMES.match })
  } catch (error) {
    if (latestExchangeToken.value !== exchangeToken) {
      return
    }

    exchangeStatus.value = 'error'
    errorMessage.value =
      error instanceof ApiClientError ? error.message : t('oauthRedirect.exchangeFailed')
  }
}

async function goToLogin() {
  await router.replace({
    name: ROUTE_NAMES.login,
    query: {
      oauth: 'failed',
    },
  })
}

function getCodeQuery() {
  const rawCode = route.query.code
  const code = Array.isArray(rawCode) ? rawCode[0] : rawCode

  return typeof code === 'string' ? code.trim() : ''
}
</script>

<style scoped>
.oauth-redirect-page {
  position: relative;
  height: 100vh;
  padding: 24px clamp(16px, 3vw, 40px);
  display: flex;
  align-items: center;
  overflow: hidden;
  background-color: #0d1723;
  background:
    linear-gradient(90deg, rgb(4 8 22 / 0.58), rgb(4 8 22 / 0.14) 58%),
    linear-gradient(0deg, rgb(4 8 22 / 0.5), rgb(4 8 22 / 0.1) 50%), var(--oauth-background-image);
  background-repeat: no-repeat;
  background-size:
    100% 100%,
    100% 100%,
    contain;
  background-position: center;
  background-color: #030610;
}

.oauth-redirect-card {
  position: relative;
  z-index: 1;
  width: min(100%, 420px);
  max-height: calc(100vh - 48px);
  padding: 32px 34px;
  overflow-y: auto;
  border: 1px solid rgb(206 224 255 / 0.14);
  border-radius: 8px;
  background: rgb(6 10 24 / 0.88);
  box-shadow: 0 18px 54px rgb(0 0 0 / 0.38);
  color: #f8fbff;
}

.oauth-redirect-heading {
  text-align: center;
}

.oauth-redirect-heading h1 {
  margin: 0;
  color: #f0d7ff;
  font-size: 2.2rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  text-shadow: 0 0 16px rgb(188 107 255 / 0.72);
}

.oauth-redirect-heading p {
  margin: 8px 0 0;
  color: rgb(219 232 244 / 0.72);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
}

.oauth-redirect-state {
  margin-top: 30px;
  color: rgb(219 232 244 / 0.78);
}

.oauth-redirect-state strong {
  display: block;
  color: #63f2e8;
  font-size: 0.92rem;
  font-weight: 900;
}

.oauth-redirect-state[data-status='error'] strong {
  color: #ffd3d3;
}

.oauth-redirect-state p {
  margin: 10px 0 0;
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.5;
}

.oauth-redirect-error {
  min-height: 18px;
  margin: 16px 0 0;
  color: #ffd3d3;
  font-size: 0.76rem;
  font-weight: 700;
}

.oauth-redirect-button {
  width: 100%;
  min-height: 46px;
  margin-top: 20px;
  border: 1px solid rgb(99 242 232 / 0.42);
  border-radius: 4px;
  background: #162a42;
  color: #e9feff;
  font-size: 0.88rem;
  font-weight: 900;
}

.oauth-redirect-button:hover {
  background: #1b3854;
  border-color: rgb(99 242 232 / 0.68);
}

@media (max-width: 760px) {
  .oauth-redirect-page {
    padding: 16px;
    align-items: center;
    justify-content: center;
    background-position: center;
  }

  .oauth-redirect-card {
    width: 358px;
    max-width: calc(100dvw - 32px);
    min-width: 0;
    padding: 28px 22px;
    border-radius: 8px;
  }

  .oauth-redirect-heading h1 {
    font-size: 1.72rem;
  }

  .oauth-redirect-heading p {
    font-size: 0.72rem;
  }
}
</style>
