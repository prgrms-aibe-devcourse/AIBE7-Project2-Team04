import { API_BASE_URL } from '../config/api.js'
import { getCsrfToken } from '../auth/csrf.js'

/**
 * 매칭 API 오류를 화면에서 상태 코드와 함께 처리하기 위한 오류 타입입니다.
 */
export class MatchingApiError extends Error {
  constructor(message, { status = 0, code = '' } = {}) {
    super(message)
    this.name = 'MatchingApiError'
    this.status = status
    this.code = code
  }
}
export async function getPreferredRegion() {
  const response = await fetch(`${API_BASE_URL}/users/me/preferred-region`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, '선호 활동지역을 불러오지 못했습니다.')
}

export async function createRealtimeMatchRequest(payload) {
  const csrfToken = await ensureCsrfToken()
  const response = await fetch(`${API_BASE_URL}/matches/realtime/requests`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify(payload),
  })
  return readResponse(response, '매칭 요청을 등록하지 못했습니다.')
}

export async function getCurrentRealtimeMatchRequest() {
  const response = await fetch(`${API_BASE_URL}/matches/realtime/requests/me`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, '현재 매칭 요청을 불러오지 못했습니다.')
}

export async function cancelRealtimeMatchRequest(requestId) {
  const csrfToken = await ensureCsrfToken()
  const response = await fetch(`${API_BASE_URL}/matches/realtime/requests/${encodeURIComponent(requestId)}`, {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    keepalive: true,
  })
  return readResponse(response, '매칭 요청을 취소하지 못했습니다.')
}

export async function getCurrentMatchProposal() {
  const response = await fetch(`${API_BASE_URL}/matches/realtime/proposals/current`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, '현재 후보 제안을 불러오지 못했습니다.')
}

export async function decideMatchProposal(proposalId, decision) {
  const csrfToken = await ensureCsrfToken()
  const response = await fetch(`${API_BASE_URL}/matches/realtime/proposals/${encodeURIComponent(proposalId)}/decision`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify({ decision }),
    keepalive: true,
  })
  return readResponse(response, '후보 제안 응답을 저장하지 못했습니다.')
}

export async function cancelCurrentRealtimeMatchState() {
  let currentRequest = null
  try {
    currentRequest = await getCurrentRealtimeMatchRequest()
  } catch (error) {
    if (error?.status !== 404) throw error
  }

  if (currentRequest?.requestId
      && ['WAITING', 'CONFIRMING'].includes(currentRequest.status)) {
    await cancelRealtimeMatchRequest(currentRequest.requestId)
    return true
  }

  let currentProposal = null
  try {
    currentProposal = await getCurrentMatchProposal()
  } catch (error) {
    if (error?.status !== 404) throw error
  }

  if (currentProposal?.proposalId && currentProposal.status === 'PENDING') {
    await decideMatchProposal(currentProposal.proposalId, 'REJECT')
    return true
  }
  return false
}

export async function getLatestMatchResult() {
  const response = await fetch(`${API_BASE_URL}/matches/realtime/results/latest`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, '최근 매칭 결과를 불러오지 못했습니다.')
}

async function ensureCsrfToken() {
  try {
    return await getCsrfToken()
  } catch (error) {
    throw new MatchingApiError(error.message || 'CSRF 토큰을 발급받지 못했습니다.')
  }
}

async function readResponse(response, fallbackMessage) {
  const body = await readJson(response)
  if (response.ok && body?.success) {
    return body.data
  }

  throw new MatchingApiError(
    body?.error?.message || fallbackMessage,
    {
      status: response.status,
      code: body?.error?.code || '',
    },
  )
}

async function readJson(response) {
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) return null

  try {
    return await response.json()
  } catch {
    return null
  }
}
