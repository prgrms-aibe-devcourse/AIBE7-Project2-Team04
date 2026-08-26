const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

/**
 * 내 성향 프로필을 조회합니다.
 * 미완료 사용자도 200 OK와 함께 onboardingStatus, completed 값을 반환합니다.
 */
export async function getPersonalityProfile() {
  const response = await fetch(`${API_BASE_URL}/users/me/personality-profile`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
    },
  })

  const body = await readJson(response)

  if (response.status === 401) {
    const error = new Error('로그인이 필요합니다.')
    error.status = 401
    throw error
  }

  if (!response.ok || !body?.success) {
    const message = body?.error?.message || '성향 프로필을 불러오지 못했습니다.'
    const error = new Error(message)
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }

  return body.data
}

/**
 * 성향 프로필(기본 스타일 카드 및 세부 태그)을 최초 등록하거나 전체 갱신합니다.
 */
export async function upsertPersonalityProfile({ questionnaireVersion = 'MEAL_PERSONALITY_V1', answers, styleTags, selfDescription, aiAnalysisConsent }) {
  const csrfToken = await ensureCsrfToken()

  const response = await fetch(`${API_BASE_URL}/users/me/personality-profile`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      'Accept': 'application/json',
    },
    body: JSON.stringify({
      questionnaireVersion,
      answers,
      styleTags,
      selfDescription,
      aiAnalysisConsent,
    }),
  })

  const body = await readJson(response)

  if (response.status === 401) {
    const error = new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
    error.status = 401
    throw error
  }

  if (!response.ok || !body?.success) {
    const message = body?.error?.message || '성향 프로필 저장에 실패했습니다.'
    const error = new Error(message)
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }

  return body.data
}

/**
 * 자기소개를 분석해 성향 태그를 제안합니다. 제안 결과는 프로필에 자동 저장되지 않습니다.
 */
export async function suggestPersonalityTags({ selfDescription, aiAnalysisConsent }) {
  const csrfToken = await ensureCsrfToken()
  const response = await fetch(`${API_BASE_URL}/users/me/personality-profile/tag-suggestions`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      'Accept': 'application/json',
    },
    body: JSON.stringify({ selfDescription, aiAnalysisConsent }),
  })
  const body = await readJson(response)
  if (response.status === 401) {
    const error = new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
    error.status = 401
    throw error
  }
  if (!response.ok || !body?.success) {
    const error = new Error(body?.error?.message || 'AI 태그 추천에 실패했습니다.')
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }
  return body.data
}

/**
 * 성향 온보딩을 건너뛰고 상태를 SKIPPED로 저장합니다.
 */
export async function skipPersonalityProfile() {
  const csrfToken = await ensureCsrfToken()

  const response = await fetch(`${API_BASE_URL}/users/me/personality-profile/skip`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      'Accept': 'application/json',
    },
  })

  const body = await readJson(response)

  if (response.status === 401) {
    const error = new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
    error.status = 401
    throw error
  }

  if (!response.ok || !body?.success) {
    const message = body?.error?.message || '온보딩 건너뛰기 처리에 실패했습니다.'
    const error = new Error(message)
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }

  return body.data
}

/**
 * 성향 프로필을 초기화합니다.
 */
export async function resetPersonalityProfile() {
  const csrfToken = await ensureCsrfToken()

  const response = await fetch(`${API_BASE_URL}/users/me/personality-profile`, {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
      'Accept': 'application/json',
    },
  })

  const body = await readJson(response)

  if (response.status === 401) {
    const error = new Error('로그인이 필요합니다.')
    error.status = 401
    throw error
  }

  if (!response.ok || !body?.success) {
    const message = body?.error?.message || '성향 프로필 초기화에 실패했습니다.'
    const error = new Error(message)
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }

  return body?.data
}

/**
 * 내 음식 선호 카테고리를 조회합니다.
 */
export async function getFoodPreferences() {
  const response = await fetch(`${API_BASE_URL}/users/me/food-preferences`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
    },
  })

  const body = await readJson(response)

  if (response.status === 401) {
    const error = new Error('로그인이 필요합니다.')
    error.status = 401
    throw error
  }

  if (!response.ok || !body?.success) {
    const message = body?.error?.message || '음식 선호 정보를 불러오지 못했습니다.'
    const error = new Error(message)
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }

  return body.data
}

/**
 * 음식 선호 카테고리(최대 5개)를 전체 갱신합니다.
 */
export async function updateFoodPreferences(foodCategories) {
  const csrfToken = await ensureCsrfToken()

  const response = await fetch(`${API_BASE_URL}/users/me/food-preferences`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      'Accept': 'application/json',
    },
    body: JSON.stringify({
      foodCategories,
    }),
  })

  const body = await readJson(response)

  if (response.status === 401) {
    const error = new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
    error.status = 401
    throw error
  }

  if (!response.ok || !body?.success) {
    const message = body?.error?.message || '음식 선호 저장에 실패했습니다.'
    const error = new Error(message)
    error.status = response.status
    error.code = body?.error?.code
    throw error
  }

  return body.data
}

async function ensureCsrfToken() {
  let token = readCookie('XSRF-TOKEN')
  if (!token) {
    await issueCsrfToken()
    token = readCookie('XSRF-TOKEN')
  }
  if (!token) {
    throw new Error('CSRF 토큰을 발급받지 못했습니다.')
  }
  return token
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
