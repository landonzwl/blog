import { createIcons, Menu, Sun, Moon, ArrowRight, ArrowUpRight, ArrowUp, Mail, Phone, MapPin } from 'lucide'

const iconSet = { Menu, Sun, Moon, ArrowRight, ArrowUpRight, ArrowUp, Mail, Phone, MapPin }

/* ============================================================
   图标渲染
   ============================================================ */
function renderIcons() {
  createIcons({ icons: iconSet })
}

/* ============================================================
   主题切换（明 / 暗），持久化到 localStorage
   ============================================================ */
const THEME_KEY = 'zwl-theme'

function getPreferredTheme() {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved) return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(theme) {
  const root = document.documentElement
  if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
}

function initTheme() {
  applyTheme(getPreferredTheme())
  const btn = document.getElementById('theme-toggle')
  if (!btn) return
  btn.addEventListener('click', () => {
    const next = document.documentElement.classList.contains('dark') ? 'light' : 'dark'
    localStorage.setItem(THEME_KEY, next)
    applyTheme(next)
  })
}

/* ============================================================
   移动端菜单
   ============================================================ */
function initMobileMenu() {
  const btn = document.getElementById('mobile-menu-btn')
  const menu = document.getElementById('mobile-menu')
  if (!btn || !menu) return

  const open = () => {
    menu.classList.remove('hidden')
    btn.setAttribute('aria-expanded', 'true')
  }
  const close = () => {
    menu.classList.add('hidden')
    btn.setAttribute('aria-expanded', 'false')
  }
  const toggle = () => (menu.classList.contains('hidden') ? open() : close())

  btn.addEventListener('click', (e) => {
    e.stopPropagation()
    toggle()
  })

  // 点击菜单内链接后关闭
  menu.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', close)
  })

  // 点击菜单外部关闭
  document.addEventListener('click', (e) => {
    if (!menu.classList.contains('hidden') && !menu.contains(e.target) && !btn.contains(e.target)) {
      close()
    }
  })

  // Esc 关闭
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') close()
  })
}

/* ============================================================
   导航栏滚动阴影
   ============================================================ */
function initNavScroll() {
  const nav = document.getElementById('nav')
  if (!nav) return
  const onScroll = () => {
    if (window.scrollY > 8) {
      nav.classList.add('scrolled')
    } else {
      nav.classList.remove('scrolled')
    }
  }
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
}

/* ============================================================
   回到顶部按钮
   ============================================================ */
function initBackToTop() {
  const btn = document.getElementById('back-to-top')
  if (!btn) return
  const onScroll = () => {
    if (window.scrollY > 400) {
      btn.classList.add('visible')
    } else {
      btn.classList.remove('visible')
    }
  }
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })

  btn.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  })
}

/* ============================================================
   滚动进入动画（IntersectionObserver）
   ============================================================ */
function initReveal() {
  const targets = document.querySelectorAll('.reveal, .project-card, .article-item, .contact-item')
  if (!targets.length) return

  const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (prefersReduced) {
    targets.forEach((el) => el.classList.add('is-visible'))
    return
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.12, rootMargin: '0px 0px -60px 0px' }
  )

  targets.forEach((el) => observer.observe(el))
}

/* ============================================================
   导航高亮：根据滚动位置标记当前区块
   ============================================================ */
function initActiveNav() {
  const sections = ['projects', 'articles', 'about', 'contact']
    .map((id) => document.getElementById(id))
    .filter(Boolean)

  const navLinks = document.querySelectorAll('[data-nav-link]')
  if (!sections.length || !navLinks.length) return

  const setActive = (id) => {
    navLinks.forEach((link) => {
      if (link.getAttribute('href') === `#${id}`) {
        link.classList.add('active')
      } else {
        link.classList.remove('active')
      }
    })
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          setActive(entry.target.id)
        }
      })
    },
    { rootMargin: '-45% 0px -50% 0px', threshold: 0 }
  )

  sections.forEach((sec) => observer.observe(sec))
}

/* ============================================================
   平滑滚动补偿（固定导航栏高度）
   ============================================================ */
function initSmoothScroll() {
  document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener('click', (e) => {
      const id = anchor.getAttribute('href')
      if (!id || id === '#') return
      const target = document.querySelector(id)
      if (!target) return
      e.preventDefault()
      const navHeight = document.getElementById('nav')?.offsetHeight || 64
      const top = target.getBoundingClientRect().top + window.scrollY - navHeight + 1
      window.scrollTo({ top, behavior: 'smooth' })
    })
  })
}

/* ============================================================
   启动
   ============================================================ */
function init() {
  renderIcons()
  initTheme()
  initMobileMenu()
  initNavScroll()
  initBackToTop()
  initReveal()
  initActiveNav()
  initSmoothScroll()
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init)
} else {
  init()
}
