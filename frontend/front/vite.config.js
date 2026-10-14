import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@np': fileURLToPath(new URL('../../NP/src', import.meta.url)),
      vue: fileURLToPath(new URL('./node_modules/vue', import.meta.url)),
      'vue-router': fileURLToPath(new URL('./node_modules/vue-router', import.meta.url)),
    },
  },
  server: {
    host: true,
    open: false,
    proxy: {
      '/rag': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/rag/, ''),
      },
      '/api/chat': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/api/tickets': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/api/auth': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/api/admin/tickets': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/api/admin/knowledge': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/api/np': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
