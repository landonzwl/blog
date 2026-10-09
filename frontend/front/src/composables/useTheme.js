import { onMounted, ref } from 'vue'

const THEME_KEY = 'zwl-theme'

function getPreferredTheme() {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved) return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

export function useTheme() {
  const theme = ref('light')

  const toggle = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem(THEME_KEY, theme.value)
    applyTheme(theme.value)
  }

  onMounted(() => {
    theme.value = getPreferredTheme()
    applyTheme(theme.value)
  })

  return { theme, toggle }
}
