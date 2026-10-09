<script setup>
import { onMounted, ref, watch } from 'vue'
import { fetchPublishedArticle } from '@/api/article'
import { fetchSiteContent } from '@/api/site'
import PageShell from '@/components/PageShell.vue'
import { formatDateTime, splitTags } from '@/utils/format'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  id: { type: [String, Number], required: true },
})

const brand = ref('张文龙')
const loading = ref(true)
const error = ref('')
const article = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  article.value = null
  try {
    const [content, detail] = await Promise.all([
      fetchSiteContent().catch(() => ({})),
      fetchPublishedArticle(props.id),
    ])
    brand.value = content['site.brand'] || brand.value
    article.value = detail
  } catch (e) {
    error.value = e.message || '文章不存在或未发布'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <PageShell :brand="brand" back-to="/articles" back-label="返回近期文章">
    <p v-if="loading" class="text-muted-foreground py-12">加载中…</p>
    <p v-else-if="error" class="text-muted-foreground py-12">{{ error }}</p>

    <article v-else-if="article" class="content-article">
      <header class="content-header">
        <div class="content-meta">
          <span>{{ formatDateTime(article.publishedAt) }}</span>
          <span>·</span>
          <span>{{ article.viewCount ?? 0 }} 次阅读</span>
        </div>
        <h1 class="content-title">{{ article.title }}</h1>
        <p v-if="article.summary" class="content-summary">{{ article.summary }}</p>
        <div v-if="splitTags(article.tags).length" class="content-tags">
          <span v-for="tag in splitTags(article.tags)" :key="tag" class="tag tag-muted">{{ tag }}</span>
        </div>
      </header>
      <div class="content-body md-body" v-html="renderMarkdown(article.content)"></div>
    </article>
  </PageShell>
</template>
