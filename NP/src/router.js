import { createRouter, createWebHistory } from 'vue-router'
import { homePath, npSession } from './npSession'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/admin' },
    { path: '/admin', component: () => import('./NpErpView.vue') },
    { path: '/staff', component: () => import('./NpStaffView.vue') },
    { path: '/visitor', component: () => import('./NpVisitorView.vue') },
  ],
})

router.beforeEach((to) => {
  if (!npSession.token) return true
  const target = homePath()
  if (to.path !== target) return target
})

export default router
