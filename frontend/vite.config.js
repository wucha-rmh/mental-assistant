import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:1236',
        changeOrigin: true
      },
      // 本地上传的图片（backend/uploads）由后端提供，同源代理过去
      '/files': {
        target: 'http://localhost:1236',
        changeOrigin: true
      }
    }
  }
})
