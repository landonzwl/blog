<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { fetchPublishedProject } from '@/api/project'
import { fetchSiteContent } from '@/api/site'
import PageShell from '@/components/PageShell.vue'

const props = defineProps({
  id: { type: [String, Number], required: true },
})

const brand = ref('张文龙')
const loading = ref(true)
const error = ref('')
const project = ref(null)

const metaLine = computed(() => {
  if (!project.value) return ''
  const parts = [project.value.dateLabel, project.value.tag].filter(Boolean)
  return parts.join(' · ')
})

async function load() {
  loading.value = true
  error.value = ''
  project.value = null
  try {
    const [content, detail] = await Promise.all([
      fetchSiteContent().catch(() => ({})),
      fetchPublishedProject(props.id),
    ])
    brand.value = content['site.brand'] || '张文龙'
    project.value = detail
  } catch (e) {
    error.value = e.message || '作品不存在或未发布'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <PageShell :brand="brand" back-to="/projects" back-label="返回精选作品">
    <p v-if="loading" class="text-muted-foreground py-12">加载中…</p>
    <p v-else-if="error" class="text-muted-foreground py-12">{{ error }}</p>

    <article v-else-if="project" class="content-article">
      <header class="content-header">
        <p v-if="metaLine" class="content-meta">{{ metaLine }}</p>
        <h1 class="content-title">{{ project.title }}</h1>
        <p v-if="project.summary" class="content-summary">{{ project.summary }}</p>
        <div v-if="project.coverUrl" class="content-cover">
          <img :src="project.coverUrl" :alt="project.title" />
        </div>
      </header>
      <div class="content-body">{{ project.content }}</div>
    </article>
  </PageShell>
</template>
