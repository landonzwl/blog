<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowLeft, Moon, Sun } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'

defineProps({
  brand: { type: String, default: '张文龙' },
  backTo: { type: String, default: '/' },
  backLabel: { type: String, default: '返回' },
})

const { theme, toggle: toggleTheme } = useTheme()
const isDark = computed(() => theme.value === 'dark')
</script>

<template>
  <div class="content-page">
    <nav class="nav scrolled">
      <div class="nav-inner">
        <RouterLink to="/" class="nav-logo">{{ brand }}</RouterLink>
        <div class="nav-actions">
          <button class="icon-btn" aria-label="切换主题" @click="toggleTheme">
            <Sun v-if="isDark" class="icon" />
            <Moon v-else class="icon" />
          </button>
        </div>
      </div>
    </nav>

    <main class="container content-main">
      <RouterLink :to="backTo" class="content-back">
        <ArrowLeft class="icon" />
        {{ backLabel }}
      </RouterLink>
      <slot />
    </main>
  </div>
</template>
