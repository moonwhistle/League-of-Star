<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
  <main class="login-page" :style="{ '--login-background-image': `url(${backgroundImageUrl})` }">
    <section class="login-card" aria-labelledby="login-title">
      <div class="login-heading">
        <h1 id="login-title">LEAGUE OF SMITE</h1>
        <p>PROVE YOUR REACTION</p>
      </div>

      <form class="login-form" aria-label="Login form" @submit.prevent="handleSubmit">
        <label class="field-group" for="login-email">
          <span>EMAIL</span>
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
          <span>PASSWORD</span>
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
          <button type="button">FORGOT PASSWORD?</button>
          <button type="button">SIGN UP</button>
        </div>

        <p v-if="errorMessage !== ''" class="login-error" role="alert">
          {{ errorMessage }}
        </p>

        <button class="login-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Logging in' : 'Login' }}
        </button>
      </form>

      <div class="bridge-divider">
        <span>OR BRIDGE WITH</span>
      </div>

      <button class="google-button" type="button" aria-label="Continue with Google">
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
        <span>Google</span>
      </button>

      <button class="about-button" type="button">ABOUT THIS GAME</button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { login } from '@/services/authService'
import { setAuthTokens } from '@/services/authToken'

import backgroundImageUrl from '../../img/background.png'

const router = useRouter()

const email = ref('')
const password = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function handleSubmit() {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''

  if (email.value === '' || password.value === '') {
    errorMessage.value = 'Email and password are required.'
    return
  }

  isSubmitting.value = true

  try {
    const response = await login({
      email: email.value,
      password: password.value,
    })

    setAuthTokens(response.accessToken, response.refreshToken)

    await router.push({ name: ROUTE_NAMES.match })
  } catch (error) {
    errorMessage.value =
      error instanceof ApiClientError ? error.message : 'Failed to login. Please try again.'
  } finally {
    isSubmitting.value = false
  }
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
    linear-gradient(90deg, rgb(21 23 34 / 0.3), rgb(70 82 92 / 0.42)), var(--login-background-image);
  background-repeat: no-repeat;
  background-size: cover;
  background-position: center;
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(100%, 420px);
  max-height: calc(100vh - 48px);
  padding: 32px 34px;
  display: flex;
  flex-direction: column;
  border-radius: 28px;
  background: rgb(247 247 249 / 0.92);
  box-shadow: 0 18px 48px rgb(20 23 34 / 0.28);
  color: #6d647f;
}

.login-heading {
  text-align: center;
}

.login-heading h1 {
  margin: 0;
  color: #6a607f;
  font-size: 2.2rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0;
}

.login-heading p {
  margin: 8px 0 0;
  color: #8b8993;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
}

.login-form {
  margin-top: 34px;
}

.field-group {
  display: block;
  color: #625e68;
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
  border-radius: 4px;
  background: rgb(243 243 246 / 0.82);
}

.field-control input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #5f5b68;
  font-size: 0.98rem;
  font-weight: 600;
}

.field-control input::placeholder {
  color: #a7a5ae;
}

.field-icon {
  position: relative;
  width: 22px;
  height: 22px;
  color: #b6b2c4;
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
  color: #7c748e;
  font-size: 0.68rem;
  font-weight: 900;
  letter-spacing: 0;
}

.login-button {
  width: 100%;
  min-height: 58px;
  margin-top: 24px;
  border: 0;
  border-radius: 29px;
  background: #6d6388;
  box-shadow: 0 16px 26px rgb(91 83 116 / 0.25);
  color: #ffffff;
  font-size: 1rem;
  font-weight: 800;
}

.login-button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.login-error {
  min-height: 18px;
  margin: 16px 0 0;
  color: #b54a60;
  font-size: 0.76rem;
  font-weight: 700;
}

.bridge-divider {
  position: relative;
  margin-top: 20px;
  display: flex;
  justify-content: center;
  color: #9c98a7;
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
  background: rgb(188 184 199 / 0.28);
  content: '';
}

.bridge-divider span {
  position: relative;
  padding: 2px 16px;
  background: rgb(247 247 249 / 0.72);
  border-radius: 999px;
}

.google-button {
  width: 100%;
  min-height: 52px;
  margin-top: 18px;
  border: 0;
  border-radius: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  background: #ffffff;
  color: #557a86;
  font-size: 0.98rem;
  font-weight: 800;
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

@media (max-width: 760px) {
  .login-page {
    padding: 16px;
    align-items: center;
    justify-content: center;
    background-position: center;
  }

  .login-card {
    width: 358px;
    max-width: calc(100dvw - 32px);
    min-width: 0;
    padding: 28px 22px;
    border-radius: 24px;
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
}
</style>
