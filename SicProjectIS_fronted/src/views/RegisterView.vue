<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const realName = ref('')
const phone = ref('')
const email = ref('')
const loading = ref(false)
const errorMessage = ref('')
const passwordsMatch = computed(() => password.value === confirmPassword.value)
const canSubmit = computed(
  () => username.value.trim() && password.value.length >= 6 && passwordsMatch.value,
)

async function submit() {
  if (!canSubmit.value || loading.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await auth.register({
      username: username.value.trim(),
      password: password.value,
      realName: realName.value.trim(),
      deptId: null,
      phone: phone.value.trim(),
      email: email.value.trim(),
    })
    router.push('/dashboard')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : 'Unable to create account'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <p class="eyebrow">SIC Project IS</p>
      <h1>Create Account</h1>
      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>Username</span>
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          <span>Password</span>
          <input v-model="password" type="password" autocomplete="new-password" />
        </label>
        <label>
          <span>Confirm password</span>
          <input v-model="confirmPassword" type="password" autocomplete="new-password" />
        </label>
        <label>
          <span>Real name</span>
          <input v-model="realName" autocomplete="name" />
        </label>
        <label>
          <span>Phone</span>
          <input v-model="phone" autocomplete="tel" />
        </label>
        <label>
          <span>Email</span>
          <input v-model="email" type="email" autocomplete="email" />
        </label>
        <p v-if="password && password.length < 6" class="form-hint">Password must be at least 6 characters.</p>
        <p v-if="confirmPassword && !passwordsMatch" class="form-error">Passwords do not match.</p>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <button type="submit" :disabled="!canSubmit || loading">
          {{ loading ? 'Creating...' : 'Create account' }}
        </button>
      </form>
      <p class="auth-switch">
        Already have an account?
        <RouterLink to="/login">Sign in</RouterLink>
      </p>
    </section>
  </main>
</template>
