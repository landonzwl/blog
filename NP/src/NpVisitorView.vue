<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import NpFrame from './NpFrame.vue'
import { deptName, loadVisitor, npState, setDept, statusText } from './npState'

const menus = [{ key: 'board', label: '任务公示' }]
const loading = ref(true)
const listPage = ref(1)
const pageSize = 10
const query = reactive({ keyword: '', status: 'all', deptId: 'east' })
const applied = reactive({ keyword: '', status: 'all', deptId: 'east' })

onMounted(async () => {
  await loadVisitor()
  setDept(applied.deptId)
  loading.value = false
})

const filtered = computed(() => {
  const source = applied.deptId === 'all'
    ? npState.publicTasks
    : npState.publicTasks.filter((item) => item.deptId === applied.deptId)
  return source.filter((item) => {
    if (applied.status !== 'all' && item.status !== applied.status) return false
    if (applied.keyword && !item.name.includes(applied.keyword)) return false
    return true
  })
})
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const pageNumbers = computed(() => {
  const end = Math.min(pageCount.value, Math.max(listPage.value, 1) + 2)
  const from = Math.max(1, end - 4)
  const list = []
  for (let i = from; i <= Math.min(pageCount.value, from + 4); i += 1) list.push(i)
  return list
})
const pageRows = computed(() => {
  const start = (listPage.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})

function tagClass(status) {
  if (status === 'published') return 'np-tag np-tag-published'
  if (status === 'running') return 'np-tag np-tag-running'
  if (status === 'done') return 'np-tag np-tag-done'
  return 'np-tag'
}

function namesOf(item) {
  return item.assigneeNames && item.assigneeNames.length ? item.assigneeNames.join('、') : '—'
}

function search() {
  applied.keyword = query.keyword.trim()
  applied.status = query.status
  applied.deptId = query.deptId
  setDept(query.deptId)
  listPage.value = 1
}

function resetQuery() {
  query.keyword = ''
  query.status = 'all'
  query.deptId = 'east'
  search()
}
</script>

<template>
  <NpFrame role="visitor" :menus="menus" active="board" user-name="访客">
    <h1 class="np-page-title">任务公示</h1>
    <form class="np-filter" @submit.prevent="search">
      <label>任务名称<input v-model="query.keyword" placeholder="请输入" /></label>
      <label>部门
        <select v-model="query.deptId">
          <option value="all">全部部门</option>
          <option v-for="dept in npState.depts" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
        </select>
      </label>
      <label>状态
        <select v-model="query.status">
          <option value="all">全部</option>
          <option value="published">已发布</option>
          <option value="running">正在执行</option>
          <option value="done">已完成</option>
        </select>
      </label>
      <div class="np-filter-actions">
        <button type="submit" class="np-btn np-btn-primary">查询</button>
        <button type="button" class="np-btn" @click="resetQuery">重置</button>
      </div>
    </form>
    <section class="np-card">
      <div class="np-table-wrap">
        <table class="np-table">
          <thead>
            <tr>
              <th>任务名称</th>
              <th>部门</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>状态</th>
              <th>人员</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading || !pageRows.length" class="np-empty">
              <td colspan="6">{{ loading ? '加载中…' : '暂无数据' }}</td>
            </tr>
            <tr v-for="item in pageRows" :key="item.id">
              <td>{{ item.name }}</td>
              <td>{{ item.deptName || deptName(item.deptId) }}</td>
              <td>{{ item.startText }}</td>
              <td>{{ item.endText }}</td>
              <td><span :class="tagClass(item.status)">{{ statusText(item.status) }}</span></td>
              <td>{{ namesOf(item) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="np-pager">
        <span>共 {{ filtered.length }} 条</span>
        <button type="button" :disabled="listPage === 1" @click="listPage -= 1">上一页</button>
        <button
          v-for="n in pageNumbers"
          :key="n"
          type="button"
          :class="{ 'is-current': n === listPage }"
          @click="listPage = n"
        >{{ n }}</button>
        <button type="button" :disabled="listPage === pageCount" @click="listPage += 1">下一页</button>
      </div>
    </section>
  </NpFrame>
</template>
