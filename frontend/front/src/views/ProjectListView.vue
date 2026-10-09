<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchPublishedProjects } from '@/api/project'
import { fetchSiteContent } from '@/api/site'
import PageShell from '@/components/PageShell.vue'

const brand = ref('张文龙')
const title = ref('精选作品')
const eyebrow = ref('Selected Work')
const subtitle = ref('')
const loading = ref(true)
const error = ref('')
const projects = ref([])

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [content, list] = await Promise.all([
      fetchSiteContent().catch(() => ({})),
      fetchPublishedProjects(),
    ])
    brand.value = content['site.brand'] || brand.value
    title.value = content['projects.title'] || title.value
    eyebrow.value = content['projects.eyebrow'] || eyebrow.value
    subtitle.value = content['projects.sub'] || ''
    projects.value = list || []
  } catch (e) {
    error.value = e.message || '作品加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PageShell :brand="brand" back-to="/" back-label="返回首页">
    <header class="content-header">
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1 class="content-title">{{ title }}</h1>
      <p v-if="subtitle" class="content-summary">{{ subtitle }}</p>
    </header>

    <p v-if="loading" class="text-muted-foreground py-8">加载中…</p>
    <p v-else-if="error" class="text-muted-foreground py-8">{{ error }}</p>
    <p v-else-if="!projects.length" class="text-muted-foreground py-8">暂无作品</p>

    <div v-else class="list-projects-grid">
      <RouterLink
        v-for="item in projects"
        :key="item.id"
        :to="`/projects/${item.id}`"
        class="project-card is-visible"
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
  </PageShell>
</template>

<style scoped>
.list-projects-grid {
  display: grid;
  gap: 1.25rem;
  margin-top: 0.5rem;
}
@media (min-width: 640px) {
  .list-projects-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (min-width: 960px) {
  .list-projects-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
