import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  cacheDir: '.vite-cache',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
