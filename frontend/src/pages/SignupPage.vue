<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline, vue/html-self-closing -->
  <main class="signup-page" :style="{ '--signup-background-image': `url(${backgroundImageUrl})` }">
    <button class="locale-toggle" type="button" @click="toggleLocale">
      {{ nextLocaleLabel }}
    </button>

    <section class="signup-card" aria-labelledby="signup-title">
      <div class="signup-heading">
        <h1 id="signup-title">{{ t('signup.title') }}</h1>
        <p>{{ t('signup.subtitle') }}</p>
      </div>

      <form class="signup-form" aria-label="Signup form" @submit.prevent="handleSubmit">
        <label class="field-group" for="signup-email">
          <span>{{ t('signup.email') }}</span>
          <span class="field-control">
            <input
              id="signup-email"
              v-model.trim="email"
              type="email"
              name="email"
              autocomplete="email"
              placeholder="email"
            />
          </span>
        </label>

        <label class="field-group" for="signup-password">
          <span>{{ t('signup.password') }}</span>
          <span class="field-control">
            <input
              id="signup-password"
              v-model="password"
              type="password"
              name="password"
              autocomplete="new-password"
              placeholder="password"
            />
          </span>
        </label>

        <label class="field-group" for="signup-nickname">
          <span>{{ t('signup.nickname') }}</span>
          <span class="field-control">
            <input
              id="signup-nickname"
              v-model.trim="nickname"
              type="text"
              name="nickname"
              autocomplete="nickname"
              placeholder="nickname"
            />
          </span>
        </label>

        <p v-if="errorMessage !== ''" class="signup-error" role="alert">
          {{ errorMessage }}
        </p>

        <button class="signup-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('signup.submitting') : t('signup.submit') }}
        </button>

        <button class="back-login-button" type="button" @click="goToLogin">
          {{ t('signup.backToLogin') }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useLocale } from '@/composables/useLocale'
import { ROUTE_NAMES } from '@/constants/routes'
import { ApiClientError } from '@/services/apiClient'
import { signup } from '@/services/authService'

import backgroundImageUrl from '../../img/background-new-sharp.png'

const router = useRouter()
const { nextLocaleLabel, t, toggleLocale } = useLocale()

const email = ref('')
const password = ref('')
const nickname = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function goToLogin() {
  await router.push({ name: ROUTE_NAMES.login })
}

async function handleSubmit() {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''

  if (email.value === '' || password.value === '' || nickname.value === '') {
    errorMessage.value = t('signup.required')
    return
  }

  if (password.value.length < 8 || password.value.length > 20) {
    errorMessage.value = t('signup.passwordLength')
    return
  }

  if (nickname.value.length < 2 || nickname.value.length > 16) {
    errorMessage.value = t('signup.nicknameLength')
    return
  }

  isSubmitting.value = true

  try {
    await signup({
      email: email.value,
      password: password.value,
      nickname: nickname.value,
    })

    await router.push({
      name: ROUTE_NAMES.login,
      query: {
        signup: 'success',
      },
    })
  } catch (error) {
    errorMessage.value = error instanceof ApiClientError ? error.message : t('signup.failed')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
.signup-page {
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
    var(--signup-background-image);
  background-repeat: no-repeat;
  background-size: cover;
  background-position: center;
}

.signup-card {
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

.signup-heading {
  text-align: center;
}

.signup-heading h1 {
  margin: 0;
  color: #f0d7ff;
  font-size: 2.2rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  text-shadow: 0 0 16px rgb(188 107 255 / 0.72);
}

.signup-heading p {
  margin: 8px 0 0;
  color: rgb(219 232 244 / 0.72);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
}

.signup-form {
  margin-top: 30px;
}

.field-group {
  display: block;
  color: rgb(219 232 244 / 0.78);
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0;
}

.field-group + .field-group {
  margin-top: 18px;
}

.field-control {
  height: 50px;
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

.signup-error {
  min-height: 18px;
  margin: 16px 0 0;
  color: #ffd3d3;
  font-size: 0.76rem;
  font-weight: 700;
}

.signup-button,
.back-login-button {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: 4px;
  font-size: 0.86rem;
  font-weight: 900;
  letter-spacing: 0;
}

.signup-button {
  margin-top: 18px;
  color: #08101f;
  background: linear-gradient(135deg, #8ff7ff, #f0d7ff);
  box-shadow: 0 10px 24px rgb(99 242 232 / 0.18);
}

.signup-button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.back-login-button {
  margin-top: 12px;
  color: rgb(219 232 244 / 0.76);
  background: rgb(8 15 34 / 0.72);
  border: 1px solid rgb(206 224 255 / 0.14);
}

@media (max-width: 640px) {
  .signup-page {
    align-items: flex-start;
    min-height: 100vh;
    height: auto;
    padding-top: 72px;
    overflow-y: auto;
  }

  .signup-card {
    width: 100%;
    max-height: none;
    padding: 28px 22px;
  }

  .signup-heading h1 {
    font-size: 1.72rem;
  }
}
</style>
