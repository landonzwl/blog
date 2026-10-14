import { defineAsyncComponent } from 'vue'

/** app_key → 异步定制页组件 */
const showcaseRegistry = {
  rbc: defineAsyncComponent(() => import('./rbc/RbcShowcaseView.vue')),
  np: defineAsyncComponent(() => import('./np/NpShowcase.vue')),
  rag: defineAsyncComponent(() => import('./rag/RagShowcase.vue')),
}

export function resolveShowcase(appKey) {
  if (!appKey) return null
  return showcaseRegistry[appKey] || null
}

export function hasShowcase(appKey) {
  return Boolean(appKey && showcaseRegistry[appKey])
}
