<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  ArrowUp,
  ArrowUpRight,
  Mail,
  MapPin,
  Menu,
  Moon,
  Phone,
  Sun,
  X,
} from 'lucide-vue-next'
import { fetchPublishedArticles } from '@/api/article'
import { fetchPublishedProjects } from '@/api/project'
import { fetchSiteContent } from '@/api/site'
import { useTheme } from '@/composables/useTheme'
import { firstTag, formatYearMonth } from '@/utils/format'

const { theme, toggle: toggleTheme } = useTheme()
const mobileOpen = ref(false)
const scrolled = ref(false)
const showBackTop = ref(false)

const site = ref({})
const projects = ref([])
const articles = ref([])
const loading = ref(true)
const error = ref('')

const isDark = computed(() => theme.value === 'dark')

function t(key, fallback = '') {
  const value = site.value?.[key]
  return value == null || value === '' ? fallback : value
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [content, projectList, articlePage] = await Promise.all([
      fetchSiteContent(),
      fetchPublishedProjects(),
      fetchPublishedArticles(1, 10),
    ])
    site.value = content || {}
    projects.value = projectList || []
    articles.value = articlePage?.list || []
  } catch (e) {
    error.value = e.message || '首页加载失败'
  } finally {
    loading.value = false
    await nextTick()
    initReveal()
  }
}

function onScroll() {
  scrolled.value = window.scrollY > 8
  showBackTop.value = window.scrollY > 400
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

let revealObserver

function initReveal() {
  if (revealObserver) revealObserver.disconnect()
  const targets = document.querySelectorAll('.reveal, .project-card, .article-item, .contact-item')
  if (!targets.length) return

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    targets.forEach((el) => el.classList.add('is-visible'))
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.12, rootMargin: '0px 0px -60px 0px' },
  )
  targets.forEach((el) => revealObserver.observe(el))
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  loadPage()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (revealObserver) revealObserver.disconnect()
})
</script>

<template>
  <a href="#main" class="skip-link">跳转到主要内容</a>

  <nav id="nav" class="nav" :class="{ scrolled }">
    <div class="nav-inner">
      <a href="#hero" class="nav-logo">{{ t('site.brand', '…') }}</a>
      <div class="nav-links hidden md:flex">
        <a href="#projects">{{ t('nav.projects', '作品') }}</a>
        <a href="#articles">{{ t('nav.articles', '文章') }}</a>
        <a href="#about">{{ t('nav.about', '关于') }}</a>
        <a href="#contact">{{ t('nav.contact', '联系') }}</a>
      </div>
      <div class="nav-actions">
        <button class="icon-btn" aria-label="切换主题" @click="toggleTheme">
          <Sun v-if="isDark" class="icon" />
          <Moon v-else class="icon" />
        </button>
        <button
          class="icon-btn md:hidden"
          aria-label="打开菜单"
          :aria-expanded="mobileOpen"
          @click="mobileOpen = !mobileOpen"
        >
          <X v-if="mobileOpen" class="icon" />
          <Menu v-else class="icon" />
        </button>
      </div>
    </div>
    <div v-show="mobileOpen" class="mobile-menu md:hidden">
      <div class="mobile-menu-inner">
        <a href="#projects" @click="mobileOpen = false">{{ t('nav.projects', '作品') }}</a>
        <a href="#articles" @click="mobileOpen = false">{{ t('nav.articles', '文章') }}</a>
        <a href="#about" @click="mobileOpen = false">{{ t('nav.about', '关于') }}</a>
        <a href="#contact" @click="mobileOpen = false">{{ t('nav.contact', '联系') }}</a>
      </div>
    </div>
  </nav>

  <main id="main">
    <p v-if="error" class="container text-muted-foreground py-8">{{ error }}</p>

    <section id="hero" class="section hero">
      <div class="container">
        <div class="hero-content">
          <p class="eyebrow">{{ t('hero.eyebrow') }}</p>
          <h1 class="hero-title">{{ t('hero.title') }}</h1>
          <p class="hero-subtitle pre-line">{{ t('hero.subtitle') }}</p>
          <p class="hero-desc">{{ t('hero.desc') }}</p>
          <div class="hero-actions">
            <a href="#projects" class="btn btn-primary">
              {{ t('hero.cta_primary', '查看作品') }}
              <ArrowRight class="btn-icon" />
            </a>
            <a href="#contact" class="btn btn-outline">{{ t('hero.cta_secondary', '联系我') }}</a>
          </div>
        </div>
      </div>
    </section>

    <section id="about" class="section reveal">
      <div class="container">
        <div class="grid-12">
          <div class="md:col-span-4">
            <p class="eyebrow">{{ t('about.eyebrow') }}</p>
            <h2 class="section-title">{{ t('about.title') }}</h2>
          </div>
          <div class="md:col-span-8 about-text">
            <p class="lead">{{ t('about.lead') }}</p>
            <p>{{ t('about.p1') }}</p>
            <p>{{ t('about.p2') }}</p>
          </div>
        </div>
      </div>
    </section>

    <section id="projects" class="section section-muted reveal">
      <div class="container">
        <div class="section-header">
          <div>
            <p class="eyebrow">{{ t('projects.eyebrow') }}</p>
            <h2 class="section-title">
              <RouterLink to="/projects" class="section-title-link">{{ t('projects.title') }}</RouterLink>
            </h2>
          </div>
          <div class="section-header-right">
            <p class="section-sub">{{ t('projects.sub') }}</p>
            <RouterLink to="/projects" class="section-more">查看全部</RouterLink>
          </div>
        </div>

        <p v-if="loading" class="text-muted-foreground py-6">加载中…</p>
        <p v-else-if="!projects.length" class="text-muted-foreground py-6">暂无作品</p>

        <div v-else class="projects-grid">
          <RouterLink
            v-for="item in projects"
            :key="item.id"
            :to="`/projects/${item.id}`"
            class="project-card"
          >
            <div class="project-media">
              <img
                v-if="item.coverUrl"
                :src="item.coverUrl"
                :alt="item.title"
                loading="lazy"
              />
            </div>
            <div class="project-body">
              <div class="project-meta">
                <span class="project-date">{{ item.dateLabel }}</span>
                <span v-if="item.tag" class="tag">{{ item.tag }}</span>
              </div>
              <h3 class="project-title">{{ item.title }}</h3>
              <p class="project-desc">{{ item.summary }}</p>
            </div>
          </RouterLink>
        </div>
      </div>
    </section>

    <section id="articles" class="section reveal">
      <div class="container">
        <div class="section-header">
          <div>
            <p class="eyebrow">{{ t('articles.eyebrow') }}</p>
            <h2 class="section-title">
              <RouterLink to="/articles" class="section-title-link">{{ t('articles.title') }}</RouterLink>
            </h2>
          </div>
          <RouterLink to="/articles" class="section-more">查看全部</RouterLink>
        </div>

        <p v-if="loading" class="text-muted-foreground py-8">加载中…</p>
        <p v-else-if="!articles.length" class="text-muted-foreground py-8">暂无文章</p>

        <div v-else class="article-list">
          <RouterLink
            v-for="item in articles"
            :key="item.id"
            :to="`/articles/${item.id}`"
            class="article-item"
          >
            <div class="article-main">
              <div class="article-meta">
                <span class="article-date">{{ formatYearMonth(item.publishedAt) }}</span>
                <span class="tag tag-muted">{{ firstTag(item.tags) }}</span>
              </div>
              <h3 class="article-title">{{ item.title }}</h3>
            </div>
            <ArrowUpRight class="article-arrow" />
          </RouterLink>
        </div>
      </div>
    </section>

    <section id="contact" class="section section-muted reveal">
      <div class="container">
        <div class="grid-12">
          <div class="md:col-span-5">
            <p class="eyebrow">{{ t('contact.eyebrow') }}</p>
            <h2 class="section-title mb-6">{{ t('contact.title') }}</h2>
            <p class="text-muted-foreground leading-relaxed">{{ t('contact.desc') }}</p>
          </div>
          <div class="md:col-span-7 contact-list">
            <a
              v-if="t('contact.email')"
              :href="`mailto:${t('contact.email')}`"
              class="contact-item"
            >
              <div class="contact-icon">
                <Mail class="icon" />
              </div>
              <div>
                <p class="contact-label">{{ t('contact.email_label', '邮箱') }}</p>
                <p class="contact-value">{{ t('contact.email') }}</p>
              </div>
            </a>
            <div v-if="t('contact.phone')" class="contact-item">
              <div class="contact-icon">
                <Phone class="icon" />
              </div>
              <div>
                <p class="contact-label">{{ t('contact.phone_label', '电话') }}</p>
                <p class="contact-value">{{ t('contact.phone') }}</p>
              </div>
            </div>
            <div v-if="t('contact.location')" class="contact-item">
              <div class="contact-icon">
                <MapPin class="icon" />
              </div>
              <div>
                <p class="contact-label">{{ t('contact.location_label', '所在地') }}</p>
                <p class="contact-value">{{ t('contact.location') }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </main>

  <footer id="footer" class="footer">
    <div class="container footer-inner">
      <p class="footer-copy">{{ t('site.footer') }}</p>
      <a href="#hero" class="back-top-link">
        回到顶部
        <ArrowUp class="icon" />
      </a>
    </div>
  </footer>

  <button
    id="back-to-top"
    class="back-to-top"
    :class="{ visible: showBackTop }"
    aria-label="回到顶部"
    @click="scrollToTop"
  >
    <ArrowUp class="icon" />
  </button>
</template>
