<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import '@np/np.css'
import { npEmbedRole } from '@np/npState'
import { enterRole, npSession } from '@np/npSession'
import NpErpView from '@np/NpErpView.vue'
import NpStaffView from '@np/NpStaffView.vue'
import NpVisitorView from '@np/NpVisitorView.vue'

const ready = ref(false)
const error = ref('')

const view = computed(() => {
  if (npSession.role === 'staff') return NpStaffView
  if (npSession.role === 'visitor') return NpVisitorView
  return NpErpView
})

onMounted(async () => {
  error.value = await enterRole('admin')
  ready.value = true
})

watch(() => npSession.role, (role) => {
  npEmbedRole.value = npSession.token ? role : null
}, { immediate: true })

onUnmounted(() => {
  npEmbedRole.value = null
})
</script>

<template>
  <p v-if="error">{{ error }}</p>
  <component :is="view" v-else-if="ready && npSession.token" />
</template>
