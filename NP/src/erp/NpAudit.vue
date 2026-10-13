<script setup>
import { onMounted, ref } from 'vue'
import { authHeaders, clearSession } from '../npSession'

const rows = ref([])
const notice = ref('')

async function load() {
  notice.value = ''
  try {
    const response = await fetch('/api/np/admin/audit', { headers: authHeaders(false) })
    const json = await response.json()
    if (response.status === 401) {
      clearSession()
      return
    }
    if (!response.ok || json.code !== 200) {
      notice.value = json.msg || '请求失败'
      return
    }
    rows.value = json.data || []
  } catch (error) {
    notice.value = '无法连接业务服务'
  }
}

onMounted(load)
</script>

<template>
  <section class="np-panel">
    <header class="np-panel-head">
      <h2>操作日志</h2>
      <button type="button" class="np-btn" @click="load">刷新</button>
    </header>
    <p v-if="notice" class="np-alert is-error">{{ notice }}</p>
    <div class="np-table-wrap">
      <table class="np-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>账号</th>
            <th>动作</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!rows.length">
            <td colspan="4" class="np-empty">暂无日志</td>
          </tr>
          <tr v-for="(row, index) in rows" :key="index">
            <td>{{ row.at }}</td>
            <td>{{ row.username }}</td>
            <td>{{ row.action }}</td>
            <td>{{ row.detail }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
