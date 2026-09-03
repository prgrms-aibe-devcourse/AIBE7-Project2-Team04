import { getAccessToken, clearAccessToken } from '../../auth/token-storage.js'
import { logout } from '../../auth/auth-api.js'
import { API_BASE_URL } from '../../config/api.js'
import { navigateTo, showToast } from '../../main.js'

let isHeaderInitialized = false

/**
 * 헤더 컴포넌트 이벤트 초기화 (최초 1회 실행)
 */
export function initHeader() {
  if (isHeaderInitialized) return
  isHeaderInitialized = true

  // 1. 브랜드 로고 클릭 이벤트 (진행 중인 매칭 정리 후 이동)
  const brandHomeLink = document.querySelector('#brand-home-link')
  if (brandHomeLink) {
    brandHomeLink.addEventListener('click', async (event) => {
      event.preventDefault()
      if (sessionStorage.getItem('project2.isLoggedIn') !== 'true' || !getAccessToken()) {
        window.location.assign('/')
        return
      }
      if (brandHomeLink.dataset.cleanupPending === 'true') return
      brandHomeLink.dataset.cleanupPending = 'true'
      try {
        const { cancelCurrentRealtimeMatchState } = await import('../../matching/matching-api.js')
        await cancelCurrentRealtimeMatchState()
        window.location.assign('/')
      } catch (error) {
        if (error?.status === 404) {
          window.location.assign('/')
          return
        }
        showToast(error?.message || '진행 중인 매칭을 정리하지 못했습니다.', { type: 'error' })
      } finally {
        delete brandHomeLink.dataset.cleanupPending
      }
    })
  }

  // 2. 프로필 토글 메뉴 제어
  const btnProfileMenu = document.querySelector('#btn-profile-menu')
  const profileDropdown = document.querySelector('#profile-dropdown')
  if (btnProfileMenu && profileDropdown) {
    btnProfileMenu.addEventListener('click', (e) => {
      e.stopPropagation()
      profileDropdown.classList.toggle('hidden')
    })

    document.addEventListener('click', (e) => {
      if (!btnProfileMenu.contains(e.target) && !profileDropdown.contains(e.target)) {
        profileDropdown.classList.add('hidden')
      }
    })
  }

  // 3. 마이페이지 / 관리자페이지 이동 버튼
  const btnGoMypage = document.querySelector('#btn-go-mypage')
  if (btnGoMypage) {
    btnGoMypage.addEventListener('click', (e) => {
      e.preventDefault()
      profileDropdown?.classList.add('hidden')
      navigateTo('/mypage')
    })
  }

  // 4. 로그아웃 버튼
  const btnDropdownLogout = document.querySelector('#btn-dropdown-logout')
  if (btnDropdownLogout) {
    btnDropdownLogout.addEventListener('click', async (e) => {
      e.preventDefault()
      profileDropdown?.classList.add('hidden')
      try {
        await logout()
      } catch (error) {
        console.warn('로그아웃 요청 실패 (로컬 세션은 정리합니다):', error)
      } finally {
        clearAccessToken()
        navigateTo('/')
      }
    })
  }

  // 5. 헤더 로그인 / 시작하기 모달 바인딩
  const btnHeaderLogin = document.querySelector('#btn-header-login')
  const btnHeaderStart = document.querySelector('#btn-header-start')

  btnHeaderLogin?.addEventListener('click', () => openAuthModal(false))
  btnHeaderStart?.addEventListener('click', () => openAuthModal(true))
}

/**
 * 로그인/회원가입 모달 열기
 */
export function openAuthModal(isSignup = false) {
  const authModal = document.querySelector('#auth-modal')
  const loginForm = document.querySelector('#form-local-login')
  const signupForm = document.querySelector('#form-local-signup')
  const modalTitle = document.querySelector('#modal-title')
  const modalDesc = document.querySelector('#modal-desc')

  if (isSignup) {
    loginForm?.classList.add('hidden')
    signupForm?.classList.remove('hidden')
    if (modalTitle) modalTitle.textContent = '이메일 회원가입'
    if (modalDesc) modalDesc.textContent = '간단한 가입으로 나만의 1:1 밥친구를 찾아보세요.'
  } else {
    loginForm?.classList.remove('hidden')
    signupForm?.classList.add('hidden')
    if (modalTitle) modalTitle.textContent = '마주한끼 시작하기'
    if (modalDesc) modalDesc.textContent = '혼밥 말고 따뜻한 한 끼를 함께할 친구를 만나보세요.'
  }

  if (authModal) {
    authModal.style.display = 'grid'
    authModal.offsetHeight
    authModal.classList.add('is-open')
    authModal.setAttribute('aria-hidden', 'false')
  }
}

/**
 * 페이지 이동 시 헤더 상태 동기화 (유저 정보, 매칭중/매칭없음 뱃지)
 */
export function updateHeaderStatus() {
  const isLoggedIn = sessionStorage.getItem('project2.isLoggedIn') === 'true'
  document.documentElement.classList.toggle('logged-in', isLoggedIn)
  document.documentElement.classList.toggle('logged-out', !isLoggedIn)

  const nicknameEl = document.querySelector('#header-user-nickname')
  const profileImgEl = document.querySelector('#header-user-profile-img')
  const btnGoMypage = document.querySelector('#btn-go-mypage')

  if (!isLoggedIn) {
    sessionStorage.removeItem('project2.userRole')
    updateHeaderMatchStatusBadge(null)
    return
  }

  // 1. 유저 정보 조회
  if (nicknameEl && profileImgEl) {
    fetch(`${API_BASE_URL}/users/me`, { credentials: 'include' })
      .then(res => res.json())
      .then(body => {
        if (body.success && body.data) {
          nicknameEl.textContent = body.data.nickname
          if (body.data.profileImageUrl) {
            profileImgEl.src = body.data.profileImageUrl
          }
          profileImgEl.onerror = () => {
            profileImgEl.src = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%2394a3b8'><path d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 4c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm0 14c-2.03 0-3.8-1.04-4.84-2.61.03-.99 2.91-1.53 4.84-1.53s4.81.54 4.84 1.53C15.8 18.96 14.03 20 12 20z'/></svg>"
          }

          if (body.data.role === 'ADMIN') {
            sessionStorage.setItem('project2.userRole', 'ADMIN')
            updateHeaderMatchStatusBadge(null)
          } else {
            sessionStorage.removeItem('project2.userRole')
          }

          if (btnGoMypage) {
            if (body.data.role === 'ADMIN') {
              btnGoMypage.innerHTML = `
                <span class="material-symbols-outlined text-sm">admin_panel_settings</span>
                <span>관리자페이지로 가기</span>
              `
              btnGoMypage.onclick = (e) => {
                e.preventDefault()
                document.querySelector('#profile-dropdown')?.classList.add('hidden')
                navigateTo('/admin', { replace: true })
              }
            } else {
              btnGoMypage.innerHTML = `
                <span class="material-symbols-outlined text-sm">person</span>
                <span>마이페이지로 가기</span>
              `
              btnGoMypage.onclick = (e) => {
                e.preventDefault()
                document.querySelector('#profile-dropdown')?.classList.add('hidden')
                navigateTo('/mypage')
              }
            }
          }
        }
      })
      .catch(err => console.error('헤더 사용자 정보를 가져오는데 실패했습니다.', err))
  }

  // 2. 최신 매칭 상태 확인 및 뱃지 업데이트
  import('../../matching/matching-api.js')
    .then(({ getLatestMatchResult }) => getLatestMatchResult())
    .then((result) => {
      if (result && result.chatRoomId && result.status === 'MATCHED') {
        sessionStorage.setItem('project2.latestMatchResult', JSON.stringify(result))
        updateHeaderMatchStatusBadge(result)
      } else {
        sessionStorage.removeItem('project2.latestMatchResult')
        updateHeaderMatchStatusBadge(null)
      }
    })
    .catch(() => {
      sessionStorage.removeItem('project2.latestMatchResult')
      updateHeaderMatchStatusBadge(null)
    })
}

/**
 * 매칭중 / 매칭없음 / 관리자 헤더 뱃지 UI 렌더링
 */
function updateHeaderMatchStatusBadge(result) {
  const badge = document.querySelector('#header-match-status-badge')
  if (!badge) return

  const isAdmin = sessionStorage.getItem('project2.userRole') === 'ADMIN'
  if (isAdmin) {
    badge.className = 'px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-all cursor-default bg-rose-50 text-rose-700 border border-rose-200/80'
    badge.innerHTML = `
      <span class="material-symbols-outlined text-base" aria-hidden="true">admin_panel_settings</span>
      <span>관리자</span>
    `
    badge.title = '관리자 계정'
    badge.onclick = null
    return
  }

  if (result && result.chatRoomId && result.status === 'MATCHED') {
    badge.className = 'px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-all cursor-pointer bg-emerald-50 text-emerald-800 border border-emerald-200/80 hover:bg-emerald-100'
    badge.innerHTML = `
      <span class="material-symbols-outlined text-base" aria-hidden="true">check_circle</span>
      <span>매칭 완료</span>
      <span class="material-symbols-outlined text-sm" aria-hidden="true">arrow_forward</span>
    `
    badge.title = '클릭하면 채팅방으로 이동합니다'
    badge.onclick = (e) => {
      e.preventDefault()
      navigateTo(`/chat?roomId=${result.chatRoomId}`)
    }
  } else {
    badge.className = 'px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1.5 shadow-sm transition-all cursor-default bg-slate-100 text-slate-500 border border-slate-200/80'
    badge.innerHTML = `
      <span class="material-symbols-outlined text-base" aria-hidden="true">person_search</span>
      <span>매칭 없음</span>
    `
    badge.title = '진행 중인 매칭이 없습니다'
    badge.onclick = null
  }
}
