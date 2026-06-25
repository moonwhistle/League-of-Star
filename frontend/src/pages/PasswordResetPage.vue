<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
  <main
    class="password-reset-page"
    :style="{ '--password-reset-background-image': `url(${backgroundImageUrl})` }"
  >
    <button class="locale-toggle" type="button" @click="toggleLocale">
      {{ nextLocaleLabel }}
    </button>

    <section class="password-reset-card" aria-labelledby="password-reset-title">
      <div class="password-reset-heading">
        <h1 id="password-reset-title">{{ t('passwordReset.title') }}</h1>
        <p>{{ t('passwordReset.subtitle') }}</p>
      </div>

      <div v-if="isTokenMissing" class="password-reset-state" role="alert">
        <strong>{{ t('passwordReset.invalidLinkTitle') }}</strong>
        <p>{{ t('passwordReset.invalidLinkDescription') }}</p>
        <button class="back-login-button" type="button" @click="goToLogin">
          {{ t('passwordReset.backToLogin') }}
        </button>
      </div>

      <form
        v-else
        class="password-reset-form"
        aria-label="Password reset form"
        @submit.prevent="handleSubmit"
      >
        <label class="field-group" for="password-reset-new-password">
          <span>{{ t('passwordReset.newPassword') }}</span>
          <span class="field-control">
            <input
              id="password-reset-new-password"
              v-model="newPassword"
              type="password"
              name="newPassword"
              autocomplete="new-password"
              placeholder="password"
            />
          </span>
        </label>

        <label class="field-group" for="password-reset-confirm-password">
          <span>{{ t('passwordReset.confirmPassword') }}</span>
          <span class="field-control">
            <input
              id="password-reset-confirm-password"
              v-model="confirmPassword"
              type="password"
              name="confirmPassword"
              autocomplete="new-password"
              placeholder="password"
            />
          </span>
        </label>

        <p v-if="errorMessage !== ''" class="password-reset-error" role="alert">
          {{ errorMessage }}
        </p>

        <button class="password-reset-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('passwordReset.submitting') : t('passwordReset.submit') }}
        </button>

        <button class="back-login-button" type="button" :disabled="isSubmitting" @click="goToLogin">
          {{ t('passwordReset.backToLogin') }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { submitPasswordReset } from '@/services/authService'

import backgroundImageUrl from '../../img/background-new-sharp.png'

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

const route = useRoute()
const router = useRouter()
const { nextLocaleLabel, t, toggleLocale } = useLocale()

const token = computed(() => getTokenQuery())
const newPassword = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)
const isTokenMissing = computed(() => token.value === '')

async function handleSubmit() {
  if (isSubmitting.value || isTokenMissing.value) {
    return
  }

  errorMessage.value = ''

  if (newPassword.value === '' || confirmPassword.value === '') {
    errorMessage.value = t('passwordReset.required')
    return
  }

  if (!PASSWORD_PATTERN.test(newPassword.value)) {
    errorMessage.value = t('passwordReset.passwordRule')
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = t('passwordReset.passwordMismatch')
    return
  }

  isSubmitting.value = true

  try {
    await submitPasswordReset({
      token: token.value,
      newPassword: newPassword.value,
    })

    await router.push({
      name: ROUTE_NAMES.login,
      query: {
        passwordReset: 'success',
      },
    })
  } catch (error) {
    errorMessage.value =
      error instanceof ApiClientError ? error.message : t('passwordReset.submitFailed')
  } finally {
    isSubmitting.value = false
  }
}

async function goToLogin() {
  await router.push({ name: ROUTE_NAMES.login })
}

function getTokenQuery() {
  const rawToken = route.query.token
  const tokenValue = Array.isArray(rawToken) ? rawToken[0] : rawToken

  return typeof tokenValue === 'string' ? tokenValue.trim() : ''
}
</script>

<style scoped>
.password-reset-page {
  position: relative;
  height: 100vh;
  padding: 24px clamp(16px, 3vw, 40px);
  display: flex;
  align-items: center;
  overflow: hidden;
  background-color: #0d1723;
  background:
    linear-gradient(90deg, rgb(4 8 22 / 0.58), rgb(4 8 22 / 0.14) 58%),
    linear-gradient(0deg, rgb(4 8 22 / 0.5), rgb(4 8 22 / 0.1) 50%),
    var(--password-reset-background-image);
  background-repeat: no-repeat;
  background-size:
    100% 100%,
    100% 100%,
    contain;
  background-position: center;
  background-color: #030610;
}

.password-reset-card {
  position: relative;
  z-index: 1;
  width: min(100%, 420px);
  max-height: calc(100vh - 48px);
  padding: 32px 34px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  border: 1px solid rgb(206 224 255 / 0.14);
  border-radius: 8px;
  background: rgb(6 10 24 / 0.88);
  box-shadow: 0 18px 54px rgb(0 0 0 / 0.38);
  color: #f8fbff;
}

.locale-toggle {
  position: absolute;
  z-index: 2;
  top: 24px;
  right: clamp(16px, 3vw, 40px);
  min-width: 42px;
  height: 32px;
  color: rgb(219 232 244 / 0.78);
  background: rgb(8 15 34 / 0.72);
  border: 1px solid rgb(206 224 255 / 0.14);
  border-radius: 4px;
  font-size: 0.76rem;
  font-weight: 900;
}

.password-reset-heading {
  text-align: center;
}

.password-reset-heading h1 {
  margin: 0;
  color: #f0d7ff;
  font-size: 2.2rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  text-shadow: 0 0 16px rgb(188 107 255 / 0.72);
}

.password-reset-heading p {
  margin: 8px 0 0;
  color: rgb(219 232 244 / 0.72);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
}

.password-reset-form,
.password-reset-state {
  margin-top: 30px;
}

.password-reset-state {
  color: rgb(219 232 244 / 0.78);
}

.password-reset-state strong {
  display: block;
  color: #ffd3d3;
  font-size: 0.92rem;
  font-weight: 900;
}

.password-reset-state p {
  margin: 10px 0 0;
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.5;
}

.field-group {
  display: block;
  color: rgb(219 232 244 / 0.78);
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0;
}

.field-group + .field-group {
  margin-top: 22px;
}

.field-control {
  height: 52px;
  margin-top: 10px;
  padding: 0 20px;
  display: grid;
  align-items: center;
  border: 1px solid rgb(206 224 255 / 0.12);
  border-radius: 4px;
  background: rgb(8 15 34 / 0.72);
}

.field-control input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #f8fbff;
  font-size: 0.98rem;
  font-weight: 600;
}

.field-control input::placeholder {
  color: rgb(219 232 244 / 0.48);
}

.password-reset-button {
  width: 100%;
  min-height: 58px;
  margin-top: 24px;
  border: 1px solid rgb(99 242 232 / 0.42);
  border-radius: 4px;
  background: #162a42;
  box-shadow: 0 14px 34px rgb(0 0 0 / 0.28);
  color: #e9feff;
  font-size: 1rem;
  font-weight: 800;
}

.password-reset-button:hover:not(:disabled) {
  background: #1b3854;
  border-color: rgb(99 242 232 / 0.68);
}

.password-reset-button:disabled,
.back-login-button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.back-login-button {
  width: 100%;
  min-height: 44px;
  margin-top: 16px;
  border: 1px solid rgb(206 224 255 / 0.12);
  border-radius: 4px;
  background: rgb(8 15 34 / 0.72);
  color: rgb(219 232 244 / 0.82);
  font-size: 0.82rem;
  font-weight: 900;
}

.back-login-button:hover:not(:disabled) {
  border-color: rgb(99 242 232 / 0.58);
  color: #f8fbff;
}

.password-reset-error {
  min-height: 18px;
  margin: 16px 0 0;
  color: #ffd3d3;
  font-size: 0.76rem;
  font-weight: 700;
}

@media (max-width: 760px) {
  .password-reset-page {
    padding: 16px;
    align-items: center;
    justify-content: center;
    background-position: center;
  }

  .locale-toggle {
    top: 16px;
    right: 16px;
  }

  .password-reset-card {
    width: 358px;
    max-width: calc(100dvw - 32px);
    min-width: 0;
    padding: 28px 22px;
    border-radius: 8px;
  }

  .password-reset-heading h1 {
    font-size: 1.72rem;
  }

  .password-reset-heading p {
    font-size: 0.72rem;
  }

  .password-reset-form,
  .password-reset-state {
    margin-top: 28px;
  }

  .field-control {
    height: 50px;
  }

  .field-control input {
    font-size: 1rem;
  }

  .password-reset-button {
    min-height: 54px;
    margin-top: 26px;
  }
}
</style>
