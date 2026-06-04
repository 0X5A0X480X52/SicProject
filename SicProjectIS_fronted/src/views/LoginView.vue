<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const username = ref('alice')
const password = ref('password')
const loading = ref(false)
const errorMessage = ref('')
const canSubmit = computed(() => username.value.trim() && password.value)

async function submit() {
  if (!canSubmit.value || loading.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : 'Unable to sign in'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <p class="eyebrow">SIC Project IS</p>
      <h1>Sign in</h1>
      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>Username</span>
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          <span>Password</span>
          <input v-model="password" type="password" autocomplete="current-password" />
        </label>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <button type="submit" :disabled="!canSubmit || loading">
          {{ loading ? 'Signing in...' : 'Sign in' }}
        </button>
      </form>
      <p class="auth-switch">
        No account yet?
        <RouterLink to="/register">Create one</RouterLink>
      </p>
    </section>
  </main>
</template>
