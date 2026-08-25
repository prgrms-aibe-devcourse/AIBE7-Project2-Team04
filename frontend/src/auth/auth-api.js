import { saveAccessToken } from './token-storage.js'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

/**
 * 카카오 소셜 로그인 화면으로 브라우저를 이동시킵니다.
 */
export function startKakaoLogin() {
  window.location.assign(`${API_BASE_URL}/oauth2/authorization/kakao`)
}

/**
 * 구글 소셜 로그인 화면으로 브라우저를 이동시킵니다.
 */
export function startGoogleLogin() {
  window.location.assign(`${API_BASE_URL}/oauth2/authorization/google`)
}

/**
 * 일반(로컬) 이메일 회원가입을 수행합니다.
 */
export async function signUp(email, password, nickname) {
  await issueCsrfToken()
  const csrfToken = readCookie('XSRF-TOKEN')

  if (!csrfToken) {
    throw new Error('CSRF 토큰을 발급받지 못했습니다.')
  }

  const response = await fetch(`${API_BASE_URL}/auth/signup`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify({ email, password, nickname }),
  })
  const body = await readJson(response)

  if (!response.ok || !body?.success) {
    throw new Error(body?.error?.message || '회원가입에 실패했습니다.')
  }

  return body.data
}

/**
 * 일반(로컬) 이메일 로그인을 수행합니다.
 */
export async function login(email, password) {
  await issueCsrfToken()
  const csrfToken = readCookie('XSRF-TOKEN')

  if (!csrfToken) {
    throw new Error('CSRF 토큰을 발급받지 못했습니다.')
  }

  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    credentials: 'include', // HttpOnly 쿠키 저장을 위해 필수
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify({ email, password }),
  })
  const body = await readJson(response)

  if (!response.ok || !body?.success) {
    throw new Error(body?.error?.message || '로그인에 실패했습니다. 이메일과 비밀번호를 확인해 주세요.')
  }

  // 로그인 플래그를 세션스토리지에 저장합니다.
  saveAccessToken(body.data?.accessToken)
  return body.data
}

/**
 * 로그아웃을 수행하고 브라우저 세션을 지웁니다.
 */
export async function logout() {
  await issueCsrfToken()
  const csrfToken = readCookie('XSRF-TOKEN')

  if (!csrfToken) {
    throw new Error('CSRF 토큰을 발급받지 못했습니다.')
  }

  const response = await fetch(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    credentials: 'include', // 세션 쿠키 삭제를 위해 필수
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
  })
  const body = await readJson(response)

  if (!response.ok || !body?.success) {
    throw new Error(body?.error?.message || '로그아웃에 실패했습니다.')
  }
}

/**
 * 일회용 인가코드를 백엔드 Access/Refresh 토큰 쿠키로 교환합니다.
 */
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
    throw new Error(body?.error?.message || '로그인에 실패했습니다.')
  }

  saveAccessToken(body.data?.accessToken)
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
