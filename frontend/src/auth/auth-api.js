import { saveAccessToken } from './token-storage.js'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

export function startKakaoLogin() {
  window.location.assign(`${API_BASE_URL}/oauth2/authorization/kakao`)
}

export async function exchangeOAuthCode(code) {
  await issueCsrfToken()
  const csrfToken = readCookie('XSRF-TOKEN')

  if (!csrfToken) {
    throw new Error('CSRF 토큰을 발급받지 못했습니다.')
  }

  const response = await fetch(`${API_BASE_URL}/auth/oauth2/exchange`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify({ code }),
  })
  const body = await readJson(response)

  if (!response.ok || !body?.success) {
    throw new Error(body?.error?.message || '카카오 로그인에 실패했습니다.')
  }

  saveAccessToken(body.data.accessToken)
  return body.data
}

async function issueCsrfToken() {
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, {
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('CSRF 토큰 요청에 실패했습니다.')
  }
}

function readCookie(name) {
  const prefix = `${encodeURIComponent(name)}=`
  const cookie = document.cookie
    .split('; ')
    .find((item) => item.startsWith(prefix))

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

async function readJson(response) {
  const contentType = response.headers.get('content-type') || ''
  return contentType.includes('application/json') ? response.json() : null
}
