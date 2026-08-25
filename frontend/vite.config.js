import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
