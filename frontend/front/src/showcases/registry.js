import { defineAsyncComponent } from 'vue'

/** app_key → 异步定制页组件 */
const showcaseRegistry = {
  rbc: defineAsyncComponent(() => import('./rbc/RbcShowcaseView.vue')),
}

export function resolveShowcase(appKey) {
  if (!appKey) return null
  return showcaseRegistry[appKey] || null
}

export function hasShowcase(appKey) {
  return Boolean(appKey && showcaseRegistry[appKey])
}
