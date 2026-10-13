<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { npEmbedRole } from '../npState'
import { enterRole, homePath, npSession, previewAccounts } from '../npSession'
import { menu } from './model'

const props = defineProps({
  active: { type: String, required: true },
})

const emit = defineEmits(['navigate'])

const router = useRouter()
const menuOpen = ref(false)
const siderOpen = ref(false)
const opened = reactive({})

menu.forEach((group) => {
  if (group.type === 'group') opened[group.key] = true
})

const crumb = computed(() => {
  for (const group of menu) {
    if (group.type === 'item' && group.key === props.active) return { group: '', page: group.label }
    const item = group.items?.find((entry) => entry.key === props.active)
    if (item) return { group: group.label, page: item.label }
  }
  return { group: '', page: '' }
})

function go(key) {
  siderOpen.value = false
  emit('navigate', key)
}

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
        <span class="np-logo-mark">企</span>
        <span>企业管理系统</span>
      </div>
      <nav class="np-menu" aria-label="功能菜单">
        <template v-for="group in menu" :key="group.key">
          <button
            v-if="group.type === 'item'"
            type="button"
            :class="{ 'is-current': group.key === active }"
            :aria-current="group.key === active ? 'page' : undefined"
            @click="go(group.key)"
          >
            {{ group.label }}
          </button>
          <div v-else class="np-group">
            <button type="button" class="np-group-btn" :aria-expanded="opened[group.key]" @click="opened[group.key] = !opened[group.key]">
              <span>{{ group.label }}</span>
              <i class="np-fold" :class="{ 'is-open': opened[group.key] }" />
            </button>
            <div v-show="opened[group.key]" class="np-sub">
              <button
                v-for="item in group.items"
                :key="item.key"
                type="button"
                :class="{ 'is-current': item.key === active }"
                :aria-current="item.key === active ? 'page' : undefined"
                @click="go(item.key)"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </template>
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
          <span>企业管理系统</span>
          <template v-if="crumb.group">
            <span>/</span>
            <span>{{ crumb.group }}</span>
          </template>
          <span>/</span>
          <span class="np-crumb-current">{{ crumb.page }}</span>
        </nav>
        <div class="np-user">
          <button type="button" class="np-user-btn" aria-haspopup="menu" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
            <span class="np-avatar">{{ (npSession.name || '管').slice(0, 1) }}</span>
            <span>{{ npSession.name || '管理员' }}</span>
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
              {{ item.name }}
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
