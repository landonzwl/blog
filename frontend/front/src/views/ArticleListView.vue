<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowUpRight } from 'lucide-vue-next'
import { fetchPublishedArticles } from '@/api/article'
import { fetchSiteContent } from '@/api/site'
import PageShell from '@/components/PageShell.vue'
import { firstTag, formatYearMonth } from '@/utils/format'

const brand = ref('张文龙')
const title = ref('近期文章')
const eyebrow = ref('Writing')
const loading = ref(true)
const error = ref('')
const articles = ref([])

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [content, data] = await Promise.all([
      fetchSiteContent().catch(() => ({})),
      fetchPublishedArticles(1, 50),
    ])
    brand.value = content['site.brand'] || brand.value
    title.value = content['articles.title'] || title.value
    eyebrow.value = content['articles.eyebrow'] || eyebrow.value
    articles.value = data?.list || []
  } catch (e) {
    error.value = e.message || '文章加载失败'
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
    </header>

    <p v-if="loading" class="text-muted-foreground py-8">加载中…</p>
    <p v-else-if="error" class="text-muted-foreground py-8">{{ error }}</p>
    <p v-else-if="!articles.length" class="text-muted-foreground py-8">暂无文章</p>

    <div v-else class="article-list content-list">
      <RouterLink
        v-for="item in articles"
        :key="item.id"
        :to="`/articles/${item.id}`"
        class="article-item is-visible"
      >
        <div class="article-main">
          <div class="article-meta">
            <span class="article-date">{{ formatYearMonth(item.publishedAt) }}</span>
            <span class="tag tag-muted">{{ firstTag(item.tags) }}</span>
          </div>
          <h3 class="article-title">{{ item.title }}</h3>
          <p v-if="item.summary" class="list-item-summary">{{ item.summary }}</p>
        </div>
        <ArrowUpRight class="article-arrow" />
      </RouterLink>
    </div>
  </PageShell>
</template>

<style scoped>
.list-item-summary {
  margin-top: 0.4rem;
  font-size: 0.875rem;
  color: var(--color-muted-foreground);
  line-height: 1.6;
}
</style>
