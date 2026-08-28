import { defineConfig, loadEnv } from 'vite'
import path from 'path'

export default defineConfig(({ mode }) => {
  // 프로젝트 루트 디렉토리의 .env 파일을 로드합니다.
  const env = loadEnv(mode, path.resolve(import.meta.dirname, '..'), '')

  // 백엔드와 공유하는 환경변수명을 프론트엔드 빌드용 VITE_ 변수에 매핑합니다.
  process.env.VITE_KAKAO_MAP_API_KEY = env.KAKAO_MAP_JAVASCRIPT_KEY || ''

  return {
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
        '/users': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/regions': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/ws-chat': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
        },
      },
    },
  }
})
