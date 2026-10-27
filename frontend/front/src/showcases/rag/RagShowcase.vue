<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="rag-showcase">
    <div class="rag-bar">
      <RouterLink to="/projects">返回作品</RouterLink>
      <span class="hint">右上角切换身份</span>
    </div>
    <iframe src="/rag/index.html" title="RAG 智能工单系统"></iframe>
  </div>
</template>

<style scoped>
.rag-showcase {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background:
    radial-gradient(900px 420px at 0% 0%, #c8ebe2 0%, transparent 55%),
    #eef6f3;
}
.rag-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  padding-top: calc(8px + env(safe-area-inset-top, 0px));
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid #d5e4df;
  backdrop-filter: blur(10px);
  font-size: 13px;
  color: #5b726c;
}
.rag-bar a {
  color: #0f8f7b;
  text-decoration: none;
  font-weight: 700;
}
.hint { opacity: 0.9; }
iframe {
  flex: 1;
  width: 100%;
  border: 0;
  min-height: 0;
}
@media (max-width: 640px) {
  .hint { font-size: 12px; }
}
</style>
