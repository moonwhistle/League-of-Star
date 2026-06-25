<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
  <main class="login-page" :style="{ '--login-background-image': `url(${backgroundImageUrl})` }">
    <button class="locale-toggle" type="button" @click="toggleLocale">
      {{ nextLocaleLabel }}
    </button>

    <section class="login-card" aria-labelledby="login-title">
      <div class="login-heading">
        <h1 id="login-title">LEAGUE OF STAR</h1>
        <p>MASTER YOUR LIGHTNING TIMING</p>
      </div>

      <form class="login-form" aria-label="Login form" @submit.prevent="handleSubmit">
        <label class="field-group" for="login-email">
          <span>{{ t('login.email') }}</span>
          <span class="field-control">
            <input
              id="login-email"
              v-model.trim="email"
              type="email"
              name="email"
              autocomplete="email"
              placeholder="email"
            />
            <span class="field-icon field-icon-email" aria-hidden="true"></span>
          </span>
        </label>

        <label class="field-group" for="login-password">
          <span>{{ t('login.password') }}</span>
          <span class="field-control">
            <input
              id="login-password"
              v-model="password"
              type="password"
              name="password"
              autocomplete="current-password"
              placeholder="password"
            />
            <span class="field-icon field-icon-password" aria-hidden="true"></span>
          </span>
        </label>

        <div class="login-links">
          <button type="button" @click="openPasswordResetModal">
            {{ t('login.forgotPassword') }}
          </button>
          <button type="button" @click="goToSignup">{{ t('login.signUp') }}</button>
        </div>

        <p v-if="successMessage !== ''" class="login-success" role="status">
          {{ successMessage }}
        </p>

        <p v-if="errorMessage !== ''" class="login-error" role="alert">
          {{ errorMessage }}
        </p>

        <button class="login-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('login.submitting') : t('login.submit') }}
        </button>
      </form>

      <div class="bridge-divider">
        <span>{{ t('login.bridgeWith') }}</span>
      </div>

      <button
        class="google-button"
        type="button"
        :disabled="isOAuthStarting"
        :aria-label="t('login.continueGoogle')"
        @click="startGoogleOAuth"
      >
        <svg class="google-mark" viewBox="0 0 24 24" aria-hidden="true">
          <path
            fill="#4285f4"
            d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.4h6.5c-.3 1.4-1.1 2.7-2.3 3.5v2.9h3.8c2.2-2 3.5-5 3.5-8.5z"
          />
          <path
            fill="#34a853"
            d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.8-2.9c-1.1.7-2.4 1.1-4.1 1.1-3.1 0-5.7-2.1-6.6-4.9H1.5v3c2 3.9 6 6.6 10.5 6.6z"
          />
          <path
            fill="#fbbc05"
            d="M5.4 14.4c-.2-.7-.4-1.5-.4-2.4s.1-1.6.4-2.4v-3H1.5C.6 8.2 0 10.1 0 12s.6 3.8 1.5 5.4l3.9-3z"
          />
          <path
            fill="#ea4335"
            d="M12 4.7c1.7 0 3.3.6 4.5 1.8l3.4-3.4C17.9 1.2 15.2 0 12 0 7.5 0 3.5 2.7 1.5 6.6l3.9 3C6.3 6.8 8.9 4.7 12 4.7z"
          />
        </svg>
        <span>{{ isOAuthStarting ? t('login.oauthStarting') : 'Google' }}</span>
      </button>

      <button class="about-button" type="button" @click="openAboutModal">
        {{ t('login.about') }}
      </button>
    </section>

    <div
      v-if="isPasswordResetModalOpen"
      class="password-reset-modal-backdrop"
      role="presentation"
      @click.self="closePasswordResetModal"
    >
      <section
        class="password-reset-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="password-reset-request-title"
      >
        <div class="password-reset-modal-heading">
          <h2 id="password-reset-request-title">{{ t('login.passwordResetTitle') }}</h2>
          <p>{{ t('login.passwordResetDescription') }}</p>
        </div>

        <form class="password-reset-form" @submit.prevent="handlePasswordResetRequest">
          <label class="field-group" for="password-reset-email">
            <span>{{ t('login.email') }}</span>
            <span class="field-control">
              <input
                id="password-reset-email"
                v-model.trim="passwordResetEmail"
                type="email"
                name="email"
                autocomplete="email"
                placeholder="email"
              />
              <span class="field-icon field-icon-email" aria-hidden="true"></span>
            </span>
          </label>

          <p v-if="passwordResetSuccessMessage !== ''" class="login-success" role="status">
            {{ passwordResetSuccessMessage }}
          </p>

          <p v-if="passwordResetErrorMessage !== ''" class="login-error" role="alert">
            {{ passwordResetErrorMessage }}
          </p>

          <div class="password-reset-actions">
            <button
              class="password-reset-secondary-button"
              type="button"
              :disabled="isPasswordResetSubmitting"
              @click="closePasswordResetModal"
            >
              {{ t('login.passwordResetCancel') }}
            </button>
            <button
              class="password-reset-primary-button"
              type="submit"
              :disabled="isPasswordResetSubmitting"
            >
              {{
                isPasswordResetSubmitting
                  ? t('login.passwordResetSubmitting')
                  : t('login.passwordResetSubmit')
              }}
            </button>
          </div>
        </form>
      </section>
    </div>

    <div
      v-if="isAboutModalOpen"
      class="about-modal-backdrop"
      role="presentation"
      @click.self="closeAboutModal"
    >
      <section class="about-modal" role="dialog" aria-modal="true" aria-labelledby="about-title">
        <div class="about-modal-media">
          <img :src="characterImageUrl" :alt="t('login.aboutImageAlt')" />
        </div>

        <div class="about-modal-copy">
          <h2 id="about-title">{{ t('login.aboutTitle') }}</h2>
          <p>{{ t('login.aboutTarget') }}</p>
          <p>{{ t('login.aboutDescription') }}</p>
          <p>{{ t('login.aboutHomage') }}</p>
        </div>

        <button class="about-modal-button" type="button" @click="closeAboutModal">
          {{ t('login.aboutClose') }}
        </button>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { login, requestPasswordReset } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'
import { startGoogleOAuthRedirect } from '@/services/oauthRedirect'

import backgroundImageUrl from '../../img/background-new-sharp.png'
import characterImageUrl from '../../img/character-cutout.png'

const router = useRouter()
const route = useRoute()
const { nextLocaleLabel, t, toggleLocale } = useLocale()

const email = ref('')
const password = ref('')
const errorMessage = ref(getInitialErrorMessage())
const successMessage = ref(getInitialSuccessMessage())
const isSubmitting = ref(false)
const isOAuthStarting = ref(false)
const isAboutModalOpen = ref(false)
const isPasswordResetModalOpen = ref(false)
const passwordResetEmail = ref('')
const passwordResetErrorMessage = ref('')
const passwordResetSuccessMessage = ref('')
const isPasswordResetSubmitting = ref(false)

async function goToSignup() {
  await router.push({ name: ROUTE_NAMES.signup })
}

function startGoogleOAuth() {
  if (isOAuthStarting.value) {
    return
  }

  errorMessage.value = ''
  successMessage.value = ''
  isOAuthStarting.value = true
  startGoogleOAuthRedirect()
}

function openAboutModal() {
  isAboutModalOpen.value = true
}

function closeAboutModal() {
  isAboutModalOpen.value = false
}

function openPasswordResetModal() {
  passwordResetEmail.value = email.value
  passwordResetErrorMessage.value = ''
  passwordResetSuccessMessage.value = ''
  isPasswordResetModalOpen.value = true
}

function closePasswordResetModal() {
  if (isPasswordResetSubmitting.value) {
    return
  }

  isPasswordResetModalOpen.value = false
}

async function handlePasswordResetRequest() {
  if (isPasswordResetSubmitting.value) {
    return
  }

  passwordResetErrorMessage.value = ''
  passwordResetSuccessMessage.value = ''

  if (passwordResetEmail.value === '') {
    passwordResetErrorMessage.value = t('login.passwordResetEmailRequired')
    return
  }

  isPasswordResetSubmitting.value = true

  try {
    await requestPasswordReset({
      email: passwordResetEmail.value,
    })

    passwordResetSuccessMessage.value = t('login.passwordResetRequestSuccess')
  } catch (error) {
    passwordResetErrorMessage.value =
      error instanceof ApiClientError ? error.message : t('login.passwordResetRequestFailed')
  } finally {
    isPasswordResetSubmitting.value = false
  }
}

async function handleSubmit() {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''
  successMessage.value = ''

  if (email.value === '' || password.value === '') {
    errorMessage.value = t('login.required')
    return
  }

  isSubmitting.value = true

  try {
    const response = await login({
      email: email.value,
      password: password.value,
    })

    setAuthTokens(response.accessToken, response.refreshToken)

    await router.push(loginSuccessTarget())
  } catch (error) {
    errorMessage.value = error instanceof ApiClientError ? error.message : t('login.failed')
  } finally {
    isSubmitting.value = false
  }
}

function loginSuccessTarget() {
  const redirect = route.query.redirect
  const redirectPath = Array.isArray(redirect) ? redirect[0] : redirect

  if (typeof redirectPath === 'string' && isSafeInternalRedirect(redirectPath)) {
    return redirectPath
  }

  return { name: ROUTE_NAMES.match }
}

function isSafeInternalRedirect(redirectPath: string) {
  return redirectPath.startsWith('/') && !redirectPath.startsWith('//')
}

function getInitialSuccessMessage() {
  if (route.query.signup === 'success') {
    return t('login.signupSuccess')
  }

  if (route.query.passwordReset === 'success') {
    return t('login.passwordResetSuccess')
  }

  return ''
}

function getInitialErrorMessage() {
  if (route.query.oauth === 'failed') {
    return t('login.oauthFailed')
  }

  return ''
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  padding: 24px clamp(16px, 3vw, 40px);
  display: flex;
  align-items: center;
  overflow: hidden;
  background-color: #0d1723;
  background:
    linear-gradient(90deg, rgb(4 8 22 / 0.56), rgb(4 8 22 / 0.12) 58%),
    linear-gradient(0deg, rgb(4 8 22 / 0.48), rgb(4 8 22 / 0.08) 50%), var(--login-background-image);
  background-repeat: no-repeat;
  background-size:
    100% 100%,
    100% 100%,
    contain;
  background-position: center;
  background-color: #030610;
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(100%, 420px);
  max-height: calc(100vh - 48px);
  padding: 32px 34px;
  display: flex;
  flex-direction: column;
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

.login-heading {
  text-align: center;
}

.login-heading h1 {
  margin: 0;
  color: #f0d7ff;
  font-size: 2.2rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  text-shadow: 0 0 16px rgb(188 107 255 / 0.72);
}

.login-heading p {
  margin: 8px 0 0;
  color: rgb(219 232 244 / 0.72);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
}

.login-form {
  margin-top: 34px;
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
  padding: 0 18px 0 20px;
  display: grid;
  grid-template-columns: 1fr auto;
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

.field-icon {
  position: relative;
  width: 22px;
  height: 22px;
  color: rgb(99 242 232 / 0.66);
}

.field-icon-email::before {
  position: absolute;
  left: 6px;
  top: 4px;
  width: 10px;
  height: 10px;
  border: 2px solid currentcolor;
  border-radius: 50%;
  content: '';
}

.field-icon-email::after {
  position: absolute;
  left: 3px;
  bottom: 3px;
  width: 16px;
  height: 8px;
  border: 2px solid currentcolor;
  border-top: 0;
  border-radius: 0 0 12px 12px;
  content: '';
}

.field-icon-password::before {
  position: absolute;
  left: 4px;
  bottom: 3px;
  width: 14px;
  height: 12px;
  border: 2px solid currentcolor;
  border-radius: 3px;
  content: '';
}

.field-icon-password::after {
  position: absolute;
  left: 7px;
  top: 2px;
  width: 8px;
  height: 10px;
  border: 2px solid currentcolor;
  border-bottom: 0;
  border-radius: 10px 10px 0 0;
  content: '';
}

.login-links {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.login-links button,
.about-button {
  border: 0;
  background: transparent;
  color: rgb(219 232 244 / 0.68);
  font-size: 0.68rem;
  font-weight: 900;
  letter-spacing: 0;
}

.login-button {
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

.login-button:hover:not(:disabled) {
  background: #1b3854;
  border-color: rgb(99 242 232 / 0.68);
}

.login-button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.login-error {
  min-height: 18px;
  margin: 16px 0 0;
  color: #ffd3d3;
  font-size: 0.76rem;
  font-weight: 700;
}

.login-success {
  min-height: 18px;
  margin: 16px 0 0;
  color: #63f2e8;
  font-size: 0.76rem;
  font-weight: 700;
}

.bridge-divider {
  position: relative;
  margin-top: 20px;
  display: flex;
  justify-content: center;
  color: rgb(219 232 244 / 0.64);
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0;
  opacity: 0.76;
}

.bridge-divider::before {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: rgb(206 224 255 / 0.12);
  content: '';
}

.bridge-divider span {
  position: relative;
  padding: 2px 16px;
  background: rgb(6 10 24 / 0.92);
  border-radius: 999px;
}

.google-button {
  width: 100%;
  min-height: 52px;
  margin-top: 18px;
  border: 1px solid rgb(206 224 255 / 0.12);
  border-radius: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  background: rgb(248 251 255 / 0.94);
  color: #183349;
  font-size: 0.98rem;
  font-weight: 800;
}

.google-button:hover:not(:disabled) {
  border-color: rgb(99 242 232 / 0.52);
  box-shadow: 0 0 22px rgb(99 242 232 / 0.14);
}

.google-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.google-mark {
  width: 22px;
  height: 22px;
  display: block;
  flex: 0 0 auto;
}

.about-button {
  margin: auto auto 0;
  padding-top: 34px;
  align-self: center;
}

.password-reset-modal-backdrop {
  position: fixed;
  z-index: 5;
  inset: 0;
  padding: 24px;
  display: grid;
  place-items: center;
  background: rgb(0 0 0 / 0.62);
}

.about-modal-backdrop {
  position: fixed;
  z-index: 5;
  inset: 0;
  padding: 24px;
  display: grid;
  place-items: center;
  background: rgb(0 0 0 / 0.68);
}

.password-reset-modal {
  width: min(100%, 420px);
  max-height: calc(100dvh - 48px);
  padding: 28px;
  border: 1px solid rgb(99 242 232 / 0.2);
  border-radius: 8px;
  overflow-y: auto;
  background: rgb(6 10 24 / 0.96);
  box-shadow: 0 24px 70px rgb(0 0 0 / 0.48);
  color: #f8fbff;
}

.about-modal {
  width: min(100%, 460px);
  max-height: calc(100dvh - 48px);
  padding: 28px;
  border: 1px solid rgb(99 242 232 / 0.2);
  border-radius: 8px;
  overflow-y: auto;
  background: rgb(6 10 24 / 0.96);
  box-shadow: 0 24px 70px rgb(0 0 0 / 0.48);
  color: #f8fbff;
}

.about-modal-media {
  min-height: 210px;
  display: grid;
  place-items: center;
  border: 1px solid rgb(206 224 255 / 0.12);
  border-radius: 8px;
  overflow: hidden;
  background:
    radial-gradient(circle at 52% 42%, rgb(99 242 232 / 0.16), transparent 38%),
    radial-gradient(circle at 46% 58%, rgb(188 107 255 / 0.2), transparent 48%), rgb(8 15 34 / 0.86);
}

.about-modal-media img {
  width: min(72%, 260px);
  height: auto;
  display: block;
  filter: drop-shadow(0 0 22px rgb(99 242 232 / 0.2));
}

.about-modal-copy {
  margin-top: 22px;
}

.about-modal-copy h2 {
  margin: 0;
  color: #f0d7ff;
  font-size: 1.22rem;
  font-weight: 900;
  letter-spacing: 0;
}

.about-modal-copy p {
  margin: 10px 0 0;
  color: rgb(219 232 244 / 0.76);
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.55;
}

.about-modal-copy p:first-of-type {
  color: #63f2e8;
  font-weight: 900;
}

.about-modal-button {
  width: 100%;
  min-height: 46px;
  margin-top: 22px;
  border: 1px solid rgb(99 242 232 / 0.42);
  border-radius: 4px;
  background: #162a42;
  color: #e9feff;
  font-size: 0.82rem;
  font-weight: 900;
}

.about-modal-button:hover {
  border-color: rgb(99 242 232 / 0.68);
  filter: brightness(1.08);
}

.password-reset-modal-heading h2 {
  margin: 0;
  color: #f0d7ff;
  font-size: 1.22rem;
  font-weight: 900;
  letter-spacing: 0;
}

.password-reset-modal-heading p {
  margin: 10px 0 0;
  color: rgb(219 232 244 / 0.72);
  font-size: 0.76rem;
  font-weight: 700;
  line-height: 1.5;
}

.password-reset-form {
  margin-top: 22px;
}

.password-reset-actions {
  margin-top: 22px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.password-reset-secondary-button,
.password-reset-primary-button {
  min-height: 46px;
  border-radius: 4px;
  font-size: 0.82rem;
  font-weight: 900;
}

.password-reset-secondary-button {
  border: 1px solid rgb(206 224 255 / 0.16);
  background: rgb(8 15 34 / 0.72);
  color: rgb(219 232 244 / 0.82);
}

.password-reset-primary-button {
  border: 1px solid rgb(99 242 232 / 0.42);
  background: #162a42;
  color: #e9feff;
}

.password-reset-secondary-button:hover:not(:disabled),
.password-reset-primary-button:hover:not(:disabled) {
  border-color: rgb(99 242 232 / 0.68);
  filter: brightness(1.08);
}

.password-reset-secondary-button:disabled,
.password-reset-primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

@media (max-width: 760px) {
  .login-page {
    padding: 16px;
    align-items: center;
    justify-content: center;
    background-position: center;
  }

  .locale-toggle {
    top: 16px;
    right: 16px;
  }

  .login-card {
    width: 358px;
    max-width: calc(100dvw - 32px);
    min-width: 0;
    padding: 28px 22px;
    border-radius: 8px;
  }

  .login-heading h1 {
    font-size: 1.72rem;
  }

  .login-heading p {
    font-size: 0.72rem;
  }

  .login-form {
    margin-top: 28px;
  }

  .field-control {
    height: 50px;
  }

  .field-control input {
    font-size: 1rem;
  }

  .login-links {
    align-items: flex-start;
  }

  .login-links button,
  .about-button,
  .bridge-divider {
    font-size: 0.72rem;
  }

  .login-button {
    min-height: 54px;
    margin-top: 26px;
  }

  .google-button {
    min-height: 50px;
  }

  .about-button {
    padding-top: 28px;
  }

  .password-reset-modal-backdrop,
  .about-modal-backdrop {
    padding: 16px;
  }

  .password-reset-modal,
  .about-modal {
    max-height: calc(100dvh - 32px);
    padding: 24px 20px;
  }

  .about-modal-media {
    min-height: 190px;
  }

  .password-reset-actions {
    grid-template-columns: 1fr;
  }
}
</style>
