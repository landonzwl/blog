<script setup>
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createArticle,
  deleteArticle,
  fetchAdminArticle,
  fetchAdminArticles,
  updateArticle,
  updateArticleStatus,
} from '@/api/article'
import { formatDateTime } from '@/utils/format'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const editingId = ref(null)
const message = ref('')

const form = reactive({
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
  tags: '',
  status: 1,
})

function resetForm() {
  editingId.value = null
  form.title = ''
  form.summary = ''
  form.content = ''
  form.coverUrl = ''
  form.tags = ''
  form.status = 1
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchAdminArticles(1, 50)
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onEdit(id) {
  message.value = ''
  try {
    const detail = await fetchAdminArticle(id)
    editingId.value = id
    form.title = detail.title || ''
    form.summary = detail.summary || ''
    form.content = detail.content || ''
    form.coverUrl = detail.coverUrl || ''
    form.tags = detail.tags || ''
    form.status = detail.status ?? 1
  } catch (e) {
    message.value = e.message
  }
}

async function onSubmit() {
  message.value = ''
  const payload = {
    title: form.title,
    summary: form.summary || null,
    content: form.content,
    coverUrl: form.coverUrl || null,
    tags: form.tags || null,
    status: Number(form.status),
  }
  try {
    if (editingId.value) {
      await updateArticle(editingId.value, payload)
      message.value = '已更新'
    } else {
      await createArticle(payload)
      message.value = '已创建'
    }
    resetForm()
    await loadList()
  } catch (e) {
    message.value = e.message
  }
}

async function onToggleStatus(item) {
  const next = item.status === 1 ? 0 : 1
  try {
    await updateArticleStatus(item.id, next)
    await loadList()
  } catch (e) {
    message.value = e.message
  }
}

async function onDelete(id) {
  if (!confirm('确认逻辑删除该文章？')) return
  try {
    await deleteArticle(id)
    if (editingId.value === id) resetForm()
    await loadList()
  } catch (e) {
    message.value = e.message
  }
}

onMounted(loadList)
</script>

<template>
  <div class="admin-page">
    <header class="admin-header">
      <div>
        <h1>文章管理</h1>
        <p class="admin-sub">P2 简易后台 · JWT 鉴权将在 P3 接入</p>
      </div>
      <RouterLink to="/" class="admin-link">返回前台</RouterLink>
    </header>

    <p v-if="message" class="admin-msg">{{ message }}</p>
    <p v-if="error" class="admin-msg error">{{ error }}</p>

    <section class="admin-card">
      <h2>{{ editingId ? `编辑 #${editingId}` : '新建文章' }}</h2>
      <form class="admin-form" @submit.prevent="onSubmit">
        <label>
          标题
          <input v-model="form.title" required maxlength="200" />
        </label>
        <label>
          摘要
          <input v-model="form.summary" maxlength="500" />
        </label>
        <label>
          标签（逗号分隔）
          <input v-model="form.tags" maxlength="200" placeholder="SpringBoot,Docker" />
        </label>
        <label>
          封面 URL
          <input v-model="form.coverUrl" maxlength="500" />
        </label>
        <label>
          状态
          <select v-model.number="form.status">
            <option :value="0">草稿</option>
            <option :value="1">已发布</option>
          </select>
        </label>
        <label class="full">
          正文
          <textarea v-model="form.content" required rows="10" />
        </label>
        <div class="admin-actions">
          <button type="submit" class="btn btn-primary">{{ editingId ? '保存' : '创建' }}</button>
          <button v-if="editingId" type="button" class="btn btn-outline" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-card">
      <h2>文章列表（{{ total }}）</h2>
      <p v-if="loading">加载中…</p>
      <table v-else class="admin-table">
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
          <tr v-for="item in list" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.status === 1 ? '已发布' : '草稿' }}</td>
            <td>{{ item.viewCount }}</td>
            <td>{{ formatDateTime(item.updateTime) }}</td>
            <td class="ops">
              <button type="button" @click="onEdit(item.id)">编辑</button>
              <button type="button" @click="onToggleStatus(item)">
                {{ item.status === 1 ? '转草稿' : '发布' }}
              </button>
              <button type="button" class="danger" @click="onDelete(item.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1.25rem 4rem;
}
.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.admin-header h1 {
  font-size: 1.5rem;
  font-weight: 700;
}
.admin-sub {
  color: var(--color-muted-foreground);
  font-size: 0.875rem;
  margin-top: 0.25rem;
}
.admin-link {
  color: var(--color-primary);
  font-size: 0.875rem;
}
.admin-msg {
  margin-bottom: 1rem;
  font-size: 0.875rem;
  color: var(--color-primary);
}
.admin-msg.error {
  color: #b42318;
}
.admin-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1.25rem;
  margin-bottom: 1.25rem;
}
.admin-card h2 {
  font-size: 1.125rem;
  font-weight: 600;
  margin-bottom: 1rem;
}
.admin-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1rem;
}
.admin-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.875rem;
  color: var(--color-muted-foreground);
}
.admin-form .full {
  grid-column: 1 / -1;
}
.admin-form input,
.admin-form select,
.admin-form textarea {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.5rem 0.75rem;
  background: var(--color-background);
  color: var(--color-foreground);
  font: inherit;
}
.admin-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 0.75rem;
  margin-top: 0.5rem;
}
.admin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}
.admin-table th,
.admin-table td {
  border-bottom: 1px solid var(--color-border);
  padding: 0.65rem 0.4rem;
  text-align: left;
  vertical-align: top;
}
.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.ops button {
  border: 1px solid var(--color-border);
  background: transparent;
  border-radius: 4px;
  padding: 0.2rem 0.45rem;
  cursor: pointer;
  font-size: 0.75rem;
  color: var(--color-foreground);
}
.ops button.danger {
  color: #b42318;
}
@media (max-width: 720px) {
  .admin-form {
    grid-template-columns: 1fr;
  }
}
</style>
