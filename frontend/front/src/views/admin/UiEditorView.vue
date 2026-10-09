<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createArticle,
  deleteArticle,
  fetchAdminArticle,
  fetchAdminArticles,
  updateArticle,
} from '@/api/article'
import {
  createProject,
  deleteProject,
  fetchAdminProject,
  fetchAdminProjects,
  updateProject,
} from '@/api/project'
import { fetchAdminSiteContent, saveAdminSiteContent } from '@/api/site'
import { formatDateTime } from '@/utils/format'

const tab = ref('site')
const message = ref('')
const error = ref('')

const siteItems = ref([])
const siteSaving = ref(false)

const projects = ref([])
const projectEditingId = ref(null)
const projectForm = reactive({
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
  tag: '',
  dateLabel: '',
  appKey: '',
  status: 1,
  sortOrder: 0,
})

const articles = ref([])
const articleEditingId = ref(null)
const articleForm = reactive({
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
  tags: '',
  status: 1,
})

/** 与首页从上到下模块顺序一致 */
const GROUP_ORDER = ['nav', 'hero', 'about', 'projects', 'articles', 'contact', 'site']
const GROUP_LABELS = {
  nav: '1. 导航',
  hero: '2. 首屏 Hero',
  about: '3. 关于我',
  projects: '4. 精选作品文案',
  articles: '5. 近期文章文案',
  contact: '6. 联系我',
  site: '7. 页脚',
}

const orderedSiteGroups = computed(() => {
  const map = {}
  for (const item of siteItems.value) {
    const g = item.groupName || 'other'
    if (!map[g]) map[g] = []
    map[g].push(item)
  }
  for (const g of Object.keys(map)) {
    map[g].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || (a.id || 0) - (b.id || 0))
  }
  const result = []
  for (const g of GROUP_ORDER) {
    if (map[g]?.length) {
      result.push({ group: g, label: GROUP_LABELS[g] || g, items: map[g] })
      delete map[g]
    }
  }
  for (const g of Object.keys(map)) {
    result.push({ group: g, label: g, items: map[g] })
  }
  return result
})

function flash(msg, isError = false) {
  message.value = isError ? '' : msg
  error.value = isError ? msg : ''
}

async function loadSite() {
  siteItems.value = (await fetchAdminSiteContent()) || []
}

async function loadProjects() {
  projects.value = (await fetchAdminProjects()) || []
}

async function loadArticles() {
  const data = await fetchAdminArticles(1, 50)
  articles.value = data?.list || []
}

async function loadAll() {
  error.value = ''
  try {
    await Promise.all([loadSite(), loadProjects(), loadArticles()])
  } catch (e) {
    flash(e.message || '加载失败', true)
  }
}

async function saveSite() {
  siteSaving.value = true
  flash('')
  try {
    const payload = siteItems.value.map((item) => ({
      contentKey: item.contentKey,
      contentValue: item.contentValue ?? '',
    }))
    await saveAdminSiteContent(payload)
    flash('站点文案已保存')
  } catch (e) {
    flash(e.message || '保存失败', true)
  } finally {
    siteSaving.value = false
  }
}

function resetProjectForm() {
  projectEditingId.value = null
  projectForm.title = ''
  projectForm.summary = ''
  projectForm.content = '作品详情页占位内容，后续再完善。'
  projectForm.coverUrl = ''
  projectForm.tag = ''
  projectForm.dateLabel = ''
  projectForm.appKey = ''
  projectForm.status = 1
  projectForm.sortOrder = 0
}

async function onEditProject(id) {
  flash('')
  try {
    const detail = await fetchAdminProject(id)
    projectEditingId.value = id
    projectForm.title = detail.title || ''
    projectForm.summary = detail.summary || ''
    projectForm.content = detail.content || ''
    projectForm.coverUrl = detail.coverUrl || ''
    projectForm.tag = detail.tag || ''
    projectForm.dateLabel = detail.dateLabel || ''
    projectForm.appKey = detail.appKey || ''
    projectForm.status = detail.status ?? 1
    projectForm.sortOrder = detail.sortOrder ?? 0
    tab.value = 'projects'
  } catch (e) {
    flash(e.message, true)
  }
}

async function onSubmitProject() {
  flash('')
  const payload = {
    title: projectForm.title,
    summary: projectForm.summary || null,
    content: projectForm.content,
    coverUrl: projectForm.coverUrl || null,
    tag: projectForm.tag || null,
    dateLabel: projectForm.dateLabel || null,
    appKey: projectForm.appKey || null,
    status: Number(projectForm.status),
    sortOrder: Number(projectForm.sortOrder) || 0,
  }
  try {
    if (projectEditingId.value) {
      await updateProject(projectEditingId.value, payload)
      flash('作品已更新')
    } else {
      await createProject(payload)
      flash('作品已创建')
    }
    resetProjectForm()
    await loadProjects()
  } catch (e) {
    flash(e.message, true)
  }
}

async function onDeleteProject(id) {
  if (!confirm('确认删除该作品？')) return
  try {
    await deleteProject(id)
    if (projectEditingId.value === id) resetProjectForm()
    await loadProjects()
    flash('作品已删除')
  } catch (e) {
    flash(e.message, true)
  }
}

function resetArticleForm() {
  articleEditingId.value = null
  articleForm.title = ''
  articleForm.summary = ''
  articleForm.content = '文章详情页占位内容，后续再完善。'
  articleForm.coverUrl = ''
  articleForm.tags = ''
  articleForm.status = 1
}

async function onEditArticle(id) {
  flash('')
  try {
    const detail = await fetchAdminArticle(id)
    articleEditingId.value = id
    articleForm.title = detail.title || ''
    articleForm.summary = detail.summary || ''
    articleForm.content = detail.content || ''
    articleForm.coverUrl = detail.coverUrl || ''
    articleForm.tags = detail.tags || ''
    articleForm.status = detail.status ?? 1
    tab.value = 'articles'
  } catch (e) {
    flash(e.message, true)
  }
}

async function onSubmitArticle() {
  flash('')
  const payload = {
    title: articleForm.title,
    summary: articleForm.summary || null,
    content: articleForm.content,
    coverUrl: articleForm.coverUrl || null,
    tags: articleForm.tags || null,
    status: Number(articleForm.status),
  }
  try {
    if (articleEditingId.value) {
      await updateArticle(articleEditingId.value, payload)
      flash('文章已更新')
    } else {
      await createArticle(payload)
      flash('文章已创建')
    }
    resetArticleForm()
    await loadArticles()
  } catch (e) {
    flash(e.message, true)
  }
}

async function onDeleteArticle(id) {
  if (!confirm('确认删除该文章？')) return
  try {
    await deleteArticle(id)
    if (articleEditingId.value === id) resetArticleForm()
    await loadArticles()
    flash('文章已删除')
  } catch (e) {
    flash(e.message, true)
  }
}

onMounted(() => {
  resetProjectForm()
  resetArticleForm()
  loadAll()
})
</script>

<template>
  <div class="editor-page">
    <header class="editor-header">
      <div>
        <h1>UI 内容编辑</h1>
        <p class="editor-sub">编辑首页文案、精选作品与近期文章，保存后前台立即读取数据库。</p>
      </div>
      <div class="editor-links">
        <RouterLink to="/">前台首页</RouterLink>
      </div>
    </header>

    <p v-if="message" class="editor-msg">{{ message }}</p>
    <p v-if="error" class="editor-msg error">{{ error }}</p>

    <div class="editor-tabs">
      <button type="button" :class="{ active: tab === 'site' }" @click="tab = 'site'">首页文案</button>
      <button type="button" :class="{ active: tab === 'projects' }" @click="tab = 'projects'">精选作品</button>
      <button type="button" :class="{ active: tab === 'articles' }" @click="tab = 'articles'">近期文章</button>
    </div>

    <section v-show="tab === 'site'" class="editor-card">
      <div class="editor-card-head">
        <h2>站点文案</h2>
        <button type="button" class="btn btn-primary" :disabled="siteSaving" @click="saveSite">
          {{ siteSaving ? '保存中…' : '保存全部文案' }}
        </button>
      </div>

      <div v-for="block in orderedSiteGroups" :key="block.group" class="editor-group">
        <h3>{{ block.label }}</h3>
        <label v-for="item in block.items" :key="item.contentKey" class="editor-field">
          <span class="editor-label">
            {{ item.label }}
            <code>{{ item.contentKey }}</code>
          </span>
          <textarea
            v-if="(item.contentValue || '').length > 80 || (item.contentValue || '').includes('\n')"
            v-model="item.contentValue"
            rows="4"
          />
          <input v-else v-model="item.contentValue" />
        </label>
      </div>
    </section>

    <section v-show="tab === 'projects'" class="editor-card">
      <h2>{{ projectEditingId ? `编辑作品 #${projectEditingId}` : '新建作品' }}</h2>
      <form class="editor-form" @submit.prevent="onSubmitProject">
        <label>
          标题
          <input v-model="projectForm.title" required maxlength="200" />
        </label>
        <label>
          标签
          <input v-model="projectForm.tag" maxlength="100" />
        </label>
        <label>
          时间文案
          <input v-model="projectForm.dateLabel" maxlength="100" placeholder="2024.07 ~ 2024.08" />
        </label>
        <label>
          定制页标识
          <input v-model="projectForm.appKey" maxlength="64" placeholder="留空为通用详情，如 rbc" />
        </label>
        <label>
          排序
          <input v-model.number="projectForm.sortOrder" type="number" />
        </label>
        <label>
          封面 URL
          <input v-model="projectForm.coverUrl" maxlength="500" />
        </label>
        <label>
          状态
          <select v-model.number="projectForm.status">
            <option :value="0">草稿</option>
            <option :value="1">已发布</option>
          </select>
        </label>
        <label class="full">
          摘要（首页卡片）
          <textarea v-model="projectForm.summary" rows="3" maxlength="500" />
        </label>
        <label class="full">
          详情正文（详情页，可先占位）
          <textarea v-model="projectForm.content" rows="6" required />
        </label>
        <div class="editor-actions">
          <button type="submit" class="btn btn-primary">
            {{ projectEditingId ? '保存作品' : '创建作品' }}
          </button>
          <button
            v-if="projectEditingId"
            type="button"
            class="btn btn-outline"
            @click="resetProjectForm"
          >
            取消编辑
          </button>
        </div>
      </form>

      <h2 class="mt">作品列表</h2>
      <table class="editor-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>状态</th>
            <th>排序</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in projects" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.status === 1 ? '已发布' : '草稿' }}</td>
            <td>{{ item.sortOrder }}</td>
            <td class="ops">
              <button type="button" @click="onEditProject(item.id)">编辑</button>
              <button type="button" class="danger" @click="onDeleteProject(item.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-show="tab === 'articles'" class="editor-card">
      <h2>{{ articleEditingId ? `编辑文章 #${articleEditingId}` : '新建文章' }}</h2>
      <form class="editor-form" @submit.prevent="onSubmitArticle">
        <label>
          标题
          <input v-model="articleForm.title" required maxlength="200" />
        </label>
        <label>
          标签（逗号分隔）
          <input v-model="articleForm.tags" maxlength="200" placeholder="SpringBoot,Docker" />
        </label>
        <label>
          封面 URL
          <input v-model="articleForm.coverUrl" maxlength="500" />
        </label>
        <label>
          状态
          <select v-model.number="articleForm.status">
            <option :value="0">草稿</option>
            <option :value="1">已发布</option>
          </select>
        </label>
        <label class="full">
          摘要（列表展示）
          <textarea v-model="articleForm.summary" rows="3" maxlength="500" />
        </label>
        <label class="full">
          详情正文（详情页，可先占位）
          <textarea v-model="articleForm.content" rows="6" required />
        </label>
        <div class="editor-actions">
          <button type="submit" class="btn btn-primary">
            {{ articleEditingId ? '保存文章' : '创建文章' }}
          </button>
          <button
            v-if="articleEditingId"
            type="button"
            class="btn btn-outline"
            @click="resetArticleForm"
          >
            取消编辑
          </button>
        </div>
      </form>

      <h2 class="mt">文章列表</h2>
      <table class="editor-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>状态</th>
            <th>浏览</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in articles" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.status === 1 ? '已发布' : '草稿' }}</td>
            <td>{{ item.viewCount ?? 0 }}</td>
            <td>{{ formatDateTime(item.updateTime) }}</td>
            <td class="ops">
              <button type="button" @click="onEditArticle(item.id)">编辑</button>
              <button type="button" class="danger" @click="onDeleteArticle(item.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.editor-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1.25rem 4rem;
}
.editor-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
  margin-bottom: 1.25rem;
}
.editor-header h1 {
  font-size: 1.5rem;
  font-weight: 700;
}
.editor-sub {
  margin-top: 0.35rem;
  color: var(--color-muted-foreground);
  font-size: 0.875rem;
}
.editor-links {
  display: flex;
  gap: 1rem;
  font-size: 0.875rem;
}
.editor-links a {
  color: var(--color-primary);
}
.editor-msg {
  margin-bottom: 1rem;
  color: var(--color-primary);
  font-size: 0.875rem;
}
.editor-msg.error {
  color: #b42318;
}
.editor-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.editor-tabs button {
  border: 1px solid var(--color-border);
  background: transparent;
  border-radius: 999px;
  padding: 0.35rem 0.9rem;
  cursor: pointer;
  color: var(--color-foreground);
}
.editor-tabs button.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-primary-foreground);
}
.editor-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1.25rem;
}
.editor-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}
.editor-card h2 {
  font-size: 1.125rem;
  font-weight: 600;
  margin-bottom: 1rem;
}
.editor-card-head h2 {
  margin-bottom: 0;
}
.editor-group {
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--color-border);
}
.editor-group h3 {
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-muted-foreground);
  margin-bottom: 0.75rem;
}
.editor-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
  font-size: 0.875rem;
}
.editor-label {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: baseline;
  color: var(--color-muted-foreground);
}
.editor-label code {
  font-size: 0.75rem;
  opacity: 0.75;
}
.editor-field input,
.editor-field textarea,
.editor-form input,
.editor-form select,
.editor-form textarea {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.5rem 0.75rem;
  background: var(--color-background);
  color: var(--color-foreground);
  font: inherit;
}
.editor-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1rem;
  margin-bottom: 1.5rem;
}
.editor-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.875rem;
  color: var(--color-muted-foreground);
}
.editor-form .full {
  grid-column: 1 / -1;
}
.editor-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 0.75rem;
}
.mt {
  margin-top: 0.5rem;
}
.editor-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}
.editor-table th,
.editor-table td {
  border-bottom: 1px solid var(--color-border);
  padding: 0.65rem 0.4rem;
  text-align: left;
}
.ops {
  display: flex;
  gap: 0.35rem;
}
.ops button {
  border: 1px solid var(--color-border);
  background: transparent;
  border-radius: 4px;
  padding: 0.2rem 0.45rem;
  cursor: pointer;
  font-size: 0.75rem;
}
.ops button.danger {
  color: #b42318;
}
@media (max-width: 720px) {
  .editor-form {
    grid-template-columns: 1fr;
  }
  .editor-header {
    flex-direction: column;
  }
}
</style>
