<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="rag-showcase">
    <div class="rag-bar">
      <RouterLink to="/projects">返回精选作品</RouterLink>
      <span>右上角切换身份。管理员可进入管理后台。</span>
    </div>
    <iframe src="/rag/index.html" title="RAG 智能工单系统"></iframe>
  </div>
</template>

<style scoped>
.rag-showcase {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f4f6fa;
}
.rag-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e8f0;
  font-size: 13px;
  color: #64748b;
}
.rag-bar a {
  color: #2563eb;
  text-decoration: none;
  font-weight: 600;
}
iframe {
  flex: 1;
  width: 100%;
  border: 0;
}
</style>
