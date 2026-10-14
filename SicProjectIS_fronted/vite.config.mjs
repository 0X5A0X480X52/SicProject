import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // 浏览器仍请求同源 /api；开发服务器代理到实际 Gateway，兼容默认 8080 与隔离环境端口。
  const env = loadEnv(mode, process.cwd(), 'SIC_')
  return {
    plugins: [vue()],
    cacheDir: '.vite-cache',
    server: {
      proxy: {
        '/api': env.SIC_GATEWAY_PROXY_TARGET || 'http://127.0.0.1:8080',
      },
    },
  }
})
