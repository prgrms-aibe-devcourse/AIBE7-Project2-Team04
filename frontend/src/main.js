import './style.css'
import { startKakaoLogin, startGoogleLogin, login, logout, signUp } from './auth/auth-api.js'
import { clearAccessToken, getAccessToken } from './auth/token-storage.js'
import { renderOAuthCallback } from './pages/oauth-callback.js'
import { renderPreferredRegionPage } from './pages/preferred-region.js'
import { renderMatchMapPage } from './pages/match-map.js'
import { renderPersonalitySurvey } from './pages/personality-survey.js'
import { renderChatPage } from './pages/chat.js'

const app = document.querySelector('#app')

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

// 행정구역 트리 (pages/ 모듈과 공유)
export let regionTree = {}

// Toast 헬퍼 (pages/ 모듈과 공유)
export function showToast(message) {
  const existing = document.querySelector('#toast-message')
  if (existing) existing.remove()

  const toast = document.createElement('div')
  toast.id = 'toast-message'
  toast.className = 'fixed bottom-6 left-1/2 -translate-x-1/2 bg-brand-navy text-white px-6 py-3.5 rounded-full text-xs sm:text-sm font-bold shadow-floating z-50 animate-bounce flex items-center gap-2 border border-white/10'
  toast.innerHTML = `
    <span class="material-symbols-outlined text-primary-container text-base sm:text-lg">warning</span>
    <span>${message}</span>
  `
  document.body.appendChild(toast)

  setTimeout(() => {
    toast.classList.add('opacity-0', 'transition-opacity', 'duration-500')
    setTimeout(() => toast.remove(), 500)
  }, 2500)
}

// 행정구역 데이터 로드 (Backend API → regionTree 구성)
const loadRegions = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/regions?level=GU`)
    const body = await response.json()
    if (body.success && body.data) {
      regionTree = {}
      body.data.forEach(r => {
        const parts = r.regionName.split(' ')
        const sido = parts[0]
        const sigungu = parts[1]
        const detail = parts[2] || ''

        if (!regionTree[sido]) {
          regionTree[sido] = {}
        }

        if (detail) {
          if (!regionTree[sido][sigungu]) {
            regionTree[sido][sigungu] = {}
          }
          regionTree[sido][sigungu][detail] = {
            code: r.regionCode,
            name: detail,
            fullName: r.regionName,
            lat: r.centerLatitude,
            lng: r.centerLongitude
          }
        } else {
          regionTree[sido][sigungu] = {
            code: r.regionCode,
            name: sigungu,
            fullName: r.regionName,
            lat: r.centerLatitude,
            lng: r.centerLongitude
          }
        }
      })
    }
  } catch (err) {
    console.error('행정구역 데이터를 가져오는데 실패했습니다.', err)
  }
}

// SPA 라우팅 네비게이션 함수
export function navigateTo(path) {
  if (path === '/') {
    window.location.assign('/')
    return
  }
  window.history.pushState({}, '', path)
  routeApp()
}

// 라우터 분기 로직
const routeApp = async () => {
  const path = window.location.pathname
  const params = new URLSearchParams(window.location.search)

  if (path === '/oauth/callback') {
    renderOAuthCallback(app)
  } else if (path === '/profile/setup') {
    renderProfileSetup(app)
  } else if (path === '/map') {
    if (params.get('mode') === 'preferred') {
      await renderPreferredRegionPage(app)
    } else {
      await renderMatchMapPage(app)
    }
  } else if (path === '/personality/survey') {
    renderPersonalitySurvey(app)
  } else if (path === '/chat') {
    renderChatPage(app)
  } else {
    // 기본 메인 랜딩 페이지
    initLandingPage()
  }
  
  // 헤더 상태 동기화
  initCommonHeader()
}

// 뒤로가기/앞으로가기 처리
window.addEventListener('popstate', routeApp)

// 앱 초기 진입점
const initApp = async () => {
  await loadRegions()
  await routeApp()
}

initApp()

function initLandingPage() {
  const token = getAccessToken()
  const btnHeroMatch = document.querySelector('#btn-hero-match')
  const btnRegisterPreferred = document.querySelector('#btn-register-preferred')
  const btnCtaStart = document.querySelector('#btn-cta-start')
  const btnPreviewJoin = document.querySelector('#btn-preview-join')

  // Auth Modal Elements
  const authModal = document.querySelector('#auth-modal')
  const btnCloseModal = document.querySelector('#btn-close-modal')
  const btnModalKakao = document.querySelector('#btn-modal-kakao')
  const btnModalGoogle = document.querySelector('#btn-modal-google')
  const formLogin = document.querySelector('#form-local-login')
  const formSignup = document.querySelector('#form-local-signup')
  const btnToggleSignup = document.querySelector('#btn-toggle-signup')
  const btnToggleLogin = document.querySelector('#btn-toggle-login')
  const modalTitle = document.querySelector('#modal-title')
  const modalDesc = document.querySelector('#modal-desc')
  const loginErrorMsg = document.querySelector('#login-error-msg')
  const signupErrorMsg = document.querySelector('#signup-error-msg')

  const openAuthModal = (isSignup = false) => {
    if (isSignup) {
      showSignupForm()
    } else {
      showLoginForm()
    }
    if (authModal) {
      authModal.style.display = 'grid'
      authModal.offsetHeight // Force reflow
      authModal.classList.add('is-open')
      authModal.setAttribute('aria-hidden', 'false')
    }
  }

  const closeAuthModal = () => {
    if (authModal) {
      authModal.classList.remove('is-open')
      authModal.setAttribute('aria-hidden', 'true')
      setTimeout(() => {
        authModal.style.display = 'none'
      }, 250)
    }
    if (loginErrorMsg) loginErrorMsg.style.display = 'none'
    if (signupErrorMsg) signupErrorMsg.style.display = 'none'
  }

  const showLoginForm = () => {
    formLogin?.classList.remove('hidden')
    formSignup?.classList.add('hidden')
    if (modalTitle) modalTitle.textContent = '마주한끼 시작하기'
    if (modalDesc) modalDesc.textContent = '혼밥 말고 따뜻한 한 끼를 함께할 친구를 만나보세요.'
    if (loginErrorMsg) loginErrorMsg.style.display = 'none'
  }

  const showSignupForm = () => {
    formLogin?.classList.add('hidden')
    formSignup?.classList.remove('hidden')
    if (modalTitle) modalTitle.textContent = '이메일 회원가입'
    if (modalDesc) modalDesc.textContent = '간단한 가입으로 나만의 1:1 밥친구를 찾아보세요.'
    if (signupErrorMsg) signupErrorMsg.style.display = 'none'
  }

  // 모달 닫기
  btnCloseModal?.addEventListener('click', closeAuthModal)
  authModal?.addEventListener('click', (e) => {
    if (e.target === authModal) {
      closeAuthModal()
    }
  })

  // 로그인 ↔ 회원가입 전환
  btnToggleSignup?.addEventListener('click', showSignupForm)
  btnToggleLogin?.addEventListener('click', showLoginForm)

  // 소셜 로그인
  btnModalKakao?.addEventListener('click', startKakaoLogin)
  btnModalGoogle?.addEventListener('click', startGoogleLogin)

  // 이메일 로그인 제출
  formLogin?.addEventListener('submit', async (e) => {
    e.preventDefault()
    const email = document.querySelector('#login-email')?.value.trim()
    const password = document.querySelector('#login-password')?.value
    if (loginErrorMsg) loginErrorMsg.style.display = 'none'

    try {
      await login(email, password)
      closeAuthModal()
      window.location.reload()
    } catch (err) {
      if (loginErrorMsg) {
        loginErrorMsg.textContent = err.message || '로그인에 실패했습니다.'
        loginErrorMsg.style.display = 'block'
      }
    }
  })

  // 이메일 회원가입 제출
  formSignup?.addEventListener('submit', async (e) => {
    e.preventDefault()
    const email = document.querySelector('#signup-email')?.value.trim()
    const nickname = document.querySelector('#signup-nickname')?.value.trim()
    const password = document.querySelector('#signup-password')?.value
    if (signupErrorMsg) signupErrorMsg.style.display = 'none'

    try {
      await signUp(email, password, nickname)
      await login(email, password)
      closeAuthModal()
      window.location.reload()
    } catch (err) {
      if (signupErrorMsg) {
        signupErrorMsg.textContent = err.message || '회원가입에 실패했습니다.'
        signupErrorMsg.style.display = 'block'
      }
    }
  })

  // 히어로 매칭 시작 버튼
  const handleMatchStart = () => {
    if (!token) {
      openAuthModal(false)
      return
    }
    navigateTo('/map')
  }

  btnHeroMatch?.addEventListener('click', handleMatchStart)

  // 채팅방 가기 버튼: project2.isLoggedIn === 'true' 일 때만 표시
  const btnGoChat = document.querySelector('#btn-go-chat')
  if (sessionStorage.getItem('project2.isLoggedIn') === 'true') {
    btnGoChat?.classList.remove('hidden')
  }
  btnGoChat?.addEventListener('click', () => navigateTo('/chat'))

  if (sessionStorage.getItem('project2.isLoggedIn') === 'true') {
    btnRegisterPreferred?.classList.remove('hidden')
  }
  btnRegisterPreferred?.addEventListener('click', () => {
    navigateTo('/map?mode=preferred')
  })

  btnCtaStart?.addEventListener('click', () => {
    if (!token) {
      openAuthModal(false)
    } else {
      navigateTo('/map')
    }
  })
  btnPreviewJoin?.addEventListener('click', () => {
    if (!token) {
      openAuthModal(false)
    } else {
      alert('민지 님의 식사 테이블에 참가 요청을 보냈습니다!')
    }
  })
}

// 메인 헤더 및 온보딩 연동 처리
function initCommonHeader() {
  const token = getAccessToken()
  const headerAuth = document.querySelector('#header-auth')
  if (!headerAuth) return

  if (token) {
    headerAuth.innerHTML = `
      <div class="flex items-center gap-2 sm:gap-3">
        <a href="/personality/survey" class="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-100 hover:bg-slate-200 text-brand-navy text-xs font-bold transition-colors">
          <span class="material-symbols-outlined text-sm text-primary-container">psychology</span>
          <span>식사 성향</span>
        </a>
        <button id="btn-revoke-location" class="btn-secondary px-3 sm:px-4 py-1.5 sm:py-2 rounded-full text-xs sm:text-sm font-semibold text-error hover:bg-error/10 hover:text-error border-error/30 flex items-center gap-1">
          <span class="material-symbols-outlined text-sm">no_accounts</span>
          <span>위치동의 철회</span>
        </button>
        <button id="btn-logout" class="btn-secondary px-3 sm:px-4 py-1.5 sm:py-2 rounded-full text-xs sm:text-sm font-semibold">
          로그아웃
        </button>
      </div>
    `
    
    // 위치 동의 철회 버튼 바인딩
    document.querySelector('#btn-revoke-location')?.addEventListener('click', async (e) => {
      e.preventDefault()
      e.stopPropagation()
      if (confirm('위치 정보 이용 동의를 철회하시겠습니까?\n철회 시 등록된 선호위치와 대기 중인 모든 매칭 요청이 파기됩니다.')) {
        try {
          const csrfResp = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' })
          if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')

          const readCookie = (name) => {
            const prefix = `${encodeURIComponent(name)}=`
            const cookie = document.cookie
              .split('; ')
              .find((item) => item.startsWith(prefix))
            return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
          }
          const csrfToken = readCookie('XSRF-TOKEN')

          const resp = await fetch(`${API_BASE_URL}/users/me/preferred-region`, {
            method: 'DELETE',
            credentials: 'include',
            headers: {
              'X-XSRF-TOKEN': csrfToken
            }
          })

          if (resp.ok) {
            alert('위치 정보 이용 동의가 철회되고 데이터가 영구 파기되었습니다.')
            navigateTo('/')
          } else {
            alert('동의 철회 처리에 실패했습니다.')
          }
        } catch (err) {
          alert('오류가 발생했습니다: ' + err.message)
        }
      }
    })

    document.querySelector('#btn-logout')?.addEventListener('click', async (e) => {
      e.preventDefault()
      e.stopPropagation()
      try {
        await logout()
      } catch (error) {
        console.warn('로그아웃 요청 실패 (로컬 세션은 정리합니다):', error)
      } finally {
        clearAccessToken()
        navigateTo('/')
      }
    })
  } else {
    headerAuth.innerHTML = `
      <div class="flex items-center gap-3">
        <button id="btn-header-login" class="btn-secondary px-4 py-2 rounded-full text-xs sm:text-sm font-semibold">
          로그인
        </button>
        <button id="btn-header-start" class="btn-primary px-4 py-2 rounded-full text-xs sm:text-sm font-semibold flex items-center gap-1.5 shadow-md">
          <span>시작하기</span>
          <span class="material-symbols-outlined text-base">arrow_forward</span>
        </button>
      </div>
    `
    const openAuthModal = (isSignup = false) => {
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

    document.querySelector('#btn-header-login')?.addEventListener('click', () => openAuthModal(false))
    document.querySelector('#btn-header-start')?.addEventListener('click', () => openAuthModal(false))
  }
}

function renderProfileSetup(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card" aria-labelledby="profile-title">
        <div class="brand-mark" aria-hidden="true">
          <img src="/assets/branding/app-icon-kakao-ivory-128.png" alt="마주한끼 로고" />
        </div>
        <p class="eyebrow">PROFILE SETUP</p>
        <h1 id="profile-title">프로필 설정</h1>
        <p class="description">
          임시 닉네임이 발급되었습니다. 나만의 식사 성향과 취향을 설정하여 완벽한 마주한끼를 만나보세요.
        </p>
        <a class="primary-link" href="/">홈으로 이동하기</a>
      </section>
    </main>
  `
}