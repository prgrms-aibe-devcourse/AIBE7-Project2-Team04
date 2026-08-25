const LOGGED_IN_FLAG = 'project2.isLoggedIn'

export function saveAccessToken(accessToken) {
  // 토큰을 저장하는 대신 로그인 성공 플래그를 세션스토리지에 저장합니다.
  sessionStorage.setItem(LOGGED_IN_FLAG, 'true')
}

export function getAccessToken() {
  // JS가 HttpOnly 쿠키를 읽을 수 없으므로, 로그인 플래그의 존재 여부로 더미 값을 반환하여 라우팅 호환성을 유지합니다.
  return sessionStorage.getItem(LOGGED_IN_FLAG) === 'true' ? 'authenticated' : null
}

export function clearAccessToken() {
  // 세션스토리지를 비워 로그아웃 상태로 전환합니다.
  sessionStorage.removeItem(LOGGED_IN_FLAG)
}
