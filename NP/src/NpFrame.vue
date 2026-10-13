<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { npEmbedRole } from './npState'
import { enterRole, homePath, npSession, previewAccounts } from './npSession'

const props = defineProps({
  role: { type: String, required: true },
  menus: { type: Array, required: true },
  active: { type: String, required: true },
  userName: { type: String, default: '' },
})

const emit = defineEmits(['navigate'])

const router = useRouter()
const menuOpen = ref(false)
const siderOpen = ref(false)

const currentLabel = computed(() => props.menus.find((item) => item.key === props.active)?.label || '')
const displayName = computed(() => npSession.name || props.userName || '')

async function switchTo(role) {
  menuOpen.value = false
  if (role === npSession.role) return
  await enterRole(role)
  const target = homePath()
  if (npEmbedRole.value === null && router.resolve(target).matched.length) router.push(target)
}

function onDoc(event) {
  if (!event.target.closest('.np-user')) menuOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', onDoc))
onUnmounted(() => document.removeEventListener('pointerdown', onDoc))
</script>

<template>
  <div class="np-shell">
    <button v-if="siderOpen" type="button" class="np-sider-mask" aria-label="关闭菜单" @click="siderOpen = false" />
    <aside class="np-sider" :class="{ 'is-open': siderOpen }">
      <div class="np-logo">
        <span class="np-logo-mark">班</span>
        <span>排班管理</span>
      </div>
      <nav class="np-menu" aria-label="功能菜单">
        <button
          v-for="item in menus"
          :key="item.key"
          type="button"
          :class="{ 'is-current': item.key === active }"
          :aria-current="item.key === active ? 'page' : undefined"
          @click="emit('navigate', item.key); siderOpen = false"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <template v-if="item.key === 'loads'">
              <path d="M4 19V10M10 19V5M16 19v-7M22 19H2" />
            </template>
            <template v-else-if="item.key === 'mine'">
              <circle cx="12" cy="8" r="3" />
              <path d="M5 19c1.5-3 3.8-4.5 7-4.5S17.5 16 19 19" />
            </template>
            <template v-else-if="item.key === 'blocks'">
              <rect x="4" y="5" width="16" height="15" rx="2" />
              <path d="M8 3v4M16 3v4M4 10h16" />
            </template>
            <template v-else>
              <rect x="4" y="4" width="16" height="16" rx="2" />
              <path d="M8 9h8M8 13h8M8 17h5" />
            </template>
          </svg>
          {{ item.label }}
        </button>
      </nav>
    </aside>
    <div class="np-main">
      <header class="np-topbar">
        <button type="button" class="np-menu-btn" aria-label="打开菜单" @click="siderOpen = true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
        <nav class="np-crumb" aria-label="面包屑">
          <RouterLink v-if="npEmbedRole !== null" to="/projects">精选作品</RouterLink>
          <span v-if="npEmbedRole !== null">/</span>
          <span>排班管理</span>
          <span>/</span>
          <span class="np-crumb-current">{{ currentLabel }}</span>
        </nav>
        <div class="np-user">
          <button type="button" class="np-user-btn" aria-haspopup="menu" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
            <span class="np-avatar">{{ displayName.slice(0, 1) }}</span>
            <span>{{ displayName }}</span>
            <i class="np-caret" />
          </button>
          <div v-if="menuOpen" class="np-dropdown" role="menu">
            <button
              v-for="item in previewAccounts"
              :key="item.role"
              type="button"
              role="menuitem"
              :class="{ 'is-current': item.role === npSession.role }"
              @click="switchTo(item.role)"
            >
              {{ item.role === role && userName ? userName : item.name }}
              <small>{{ item.post }}</small>
            </button>
          </div>
        </div>
      </header>
      <main class="np-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style src="./np.css"></style>
