<script setup>
import { computed } from 'vue'
import { erpStore } from './model'

const objects = computed(() => (erpStore.meta || []).filter((item) => item.builtin))
</script>

<template>
  <h1 class="np-page-title">业务对象</h1>
  <p class="np-lead">这里列出系统里的业务对象和字段数量。</p>
  <section class="np-card">
    <div class="np-card-bar"><strong>已有对象</strong></div>
    <div class="np-table-wrap">
      <table class="np-table">
        <thead>
          <tr><th>编码</th><th>名称</th><th>模块</th><th>来源</th><th>字段</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in objects" :key="item.code">
            <td>{{ item.code }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.module }}</td>
            <td>内置</td>
            <td>{{ item.fields.length }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
