import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/projects',
      name: 'project-list',
      component: () => import('@/views/ProjectListView.vue'),
    },
    {
      path: '/projects/:id',
      name: 'project-detail',
      component: () => import('@/views/ProjectDetailView.vue'),
      props: true,
    },
    {
      path: '/articles',
      name: 'article-list',
      component: () => import('@/views/ArticleListView.vue'),
    },
    {
      path: '/articles/:id',
      name: 'article-detail',
      component: () => import('@/views/ArticleDetailView.vue'),
      props: true,
    },
    {
      path: '/admin/ui',
      name: 'admin-ui',
      component: () => import('@/views/admin/UiEditorView.vue'),
    },
    {
      path: '/admin/articles',
      name: 'admin-articles',
      component: () => import('@/views/admin/ArticleAdminView.vue'),
    },
  ],
  scrollBehavior(to, _from, saved) {
    if (saved) return saved
    if (to.hash) {
      return { el: to.hash, top: 72, behavior: 'smooth' }
    }
    return { top: 0 }
  },
})

export default router
