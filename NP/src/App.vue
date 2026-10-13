<script setup>
import { onMounted, ref } from 'vue'
import { ensurePreview, npSession } from './npSession'

const ready = ref(false)
const error = ref('')

onMounted(async () => {
  error.value = await ensurePreview()
  ready.value = true
})
</script>

<template>
  <p v-if="error" class="np-login-error">{{ error }}</p>
  <router-view v-else-if="ready && npSession.token" />
</template>
