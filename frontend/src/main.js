import './style.css'
import { startKakaoLogin, startGoogleLogin, login, logout, signUp, installAuthFetchInterceptor, restoreAuthSession } from './auth/auth-api.js'
import { clearAccessToken, getAccessToken } from './auth/token-storage.js'
import { renderOAuthCallback } from './pages/oauth-callback.js'
import { renderPreferredRegionPage } from './pages/preferred-region.js'
import { renderMatchMapPage } from './pages/match-map.js'
import { renderPersonalitySurvey } from './pages/personality-survey.js'
import { renderChatPage } from './pages/chat.js'
import { renderMatchingRequestPage } from './pages/matching-request.js'
import { API_BASE_URL } from './config/api.js'
import { initHeader, updateHeaderStatus, openAuthModal } from './components/header/header.js'

const app = document.querySelector('#app')
const initialLandingPageHtml = app?.innerHTML ?? ''

export { API_BASE_URL, openAuthModal }

// 행정구역 트리 (pages/ 모듈과 공유)
export let regionTree = {}

// Toast 헬퍼 (pages/ 모듈과 공유)
export function showToast(message, { type = 'info', duration = 3500 } = {}) {
  const existing = document.querySelector('#toast-message')
  if (existing) existing.remove()

  const isError = type === 'error'
  const toast = document.createElement('div')
  toast.id = 'toast-message'
  toast.setAttribute('role', isError ? 'alert' : 'status')
  toast.setAttribute('aria-live', isError ? 'assertive' : 'polite')
  toast.className = [
    'fixed bottom-5 right-5 z-50 w-[calc(100%-2.5rem)] max-w-sm',
    'flex items-start gap-3 rounded-2xl border px-4 py-3.5 text-sm font-semibold shadow-floating',
    'toast-enter',
    isError ? 'border-red-200 bg-red-50 text-red-700' : 'border-white/10 bg-brand-navy text-white',
  ].join(' ')
  toast.innerHTML = `
    <span class="material-symbols-outlined shrink-0 text-base sm:text-lg ${isError ? 'text-red-500' : 'text-primary-container'}" aria-hidden="true">${isError ? 'error' : 'warning'}</span>
    <span data-toast-message class="min-w-0 flex-1 break-words"></span>
    <button type="button" data-toast-close class="-mr-1 -mt-1 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-current/70 transition-colors hover:bg-black/5 hover:text-current focus:outline-none focus:ring-2 focus:ring-current/30" aria-label="알림 닫기">
      <span class="material-symbols-outlined text-base" aria-hidden="true">close</span>
    </button>
  `
  toast.querySelector('[data-toast-message]').textContent = String(message ?? '')
  document.body.appendChild(toast)

  const dismiss = () => {
    if (!toast.isConnected || toast.classList.contains('toast-leaving')) return
    toast.classList.remove('toast-enter')
    toast.classList.add('toast-leaving')
    window.setTimeout(() => toast.remove(), 320)
  }

  toast.querySelector('[data-toast-close]')?.addEventListener('click', dismiss, { once: true })
  window.setTimeout(dismiss, duration)
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

// 비동기 행정구역 로드 프로미스
const regionsPromise = loadRegions()
let activeRouteId = 0

// SPA 라우팅 네비게이션 함수
export function navigateTo(path, options = {}) {
  if (path === '/') {
    window.location.assign('/')
    return
  }
  const { replace = false } = options
  const updateHistory = replace ? window.history.replaceState : window.history.pushState
  updateHistory.call(window.history, {}, '', path)
  routeApp()
}

// 라우터 분기 로직
const routeApp = async () => {
  const routeId = ++activeRouteId
  const isCurrentRoute = () => routeId === activeRouteId
  if (typeof window.__matchingRequestCleanup === 'function') {
    const cleanup = window.__matchingRequestCleanup
    delete window.__matchingRequestCleanup
    cleanup()
  }

  const path = window.location.pathname
  const params = new URLSearchParams(window.location.search)

  const header = document.querySelector('header')
  const footer = document.querySelector('footer')

  if (path === '/oauth/callback') {
    header?.classList.add('hidden')
    footer?.classList.add('hidden')
    document.body.classList.remove('pt-[88px]')
    renderOAuthCallback(app)
  } else {
    header?.classList.remove('hidden')
    footer?.classList.remove('hidden')
    document.body.classList.add('pt-[88px]')

    if (path === '/profile/setup') {
      renderProfileSetup(app)
    } else if (path === '/map') {
      await regionsPromise // 지도 관련 페이지 진입 시에만 데이터를 기다림
      if (!isCurrentRoute()) return
      if (params.get('mode') === 'preferred') {
        await renderPreferredRegionPage(app, isCurrentRoute)
      } else {
        await renderMatchMapPage(app, isCurrentRoute)
      }
    } else if (path === '/personality/survey') {
      renderPersonalitySurvey(app)
    } else if (path === '/chat') {
      renderChatPage(app)
    } else if (path === '/matching/request') {
      await regionsPromise // 매칭 요청 페이지 진입 시에만 데이터를 기다림
      await renderMatchingRequestPage(app)
    } else if (path === '/mypage') {
      const { renderMyPage } = await import('./pages/mypage.js')
      if (!isCurrentRoute()) return
      await renderMyPage(app)
    } else if (path === '/admin') {
      const { renderAdminPage } = await import('./pages/admin.js')
      if (!isCurrentRoute()) return
      await renderAdminPage(app)
    } else if (path === '/review') {
      const { renderReviewPage } = await import('./pages/review.js')
      if (!isCurrentRoute()) return
      await renderReviewPage(app)
    } else {
      // 기본 메인 랜딩 페이지
      initLandingPage()
      showPendingLoginMessage()
    }
    
    // 헤더 상태 동기화 (모든 SPA 페이지 공통 적용)
    if (!isCurrentRoute()) return
    updateHeaderStatus()
  }

  // 라우팅이 완료되면 임시 감춤 상태 해제
  if (!isCurrentRoute()) return
  document.documentElement.classList.remove('route-loading')
  document.documentElement.classList.remove('is-oauth-callback')
}

// 뒤로가기/앞으로가기 처리
window.addEventListener('popstate', routeApp)

// 앱 초기 진입점
const initApp = async () => {
  installAuthFetchInterceptor()
  await restoreAuthSession()
  initHeader()
  routeApp() // 즉시 메인 페이지 및 헤더 상태를 렌더링
  await regionsPromise
}

initApp()

function initLandingPage() {
  // 다른 SPA 페이지가 #app 내용을 교체했으므로 루트 진입 시 랜딩 화면을 복원한다.
  if (!app.querySelector('.default-landing-content')) {
    app.innerHTML = initialLandingPageHtml
  }

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

      try {
        const { getPersonalityProfile } = await import('./personality/personality-api.js')
        const profile = await getPersonalityProfile()
        if (profile?.onboardingStatus === 'NOT_STARTED') {
          alert('🎉 환영합니다!\n나와 잘 맞는 밥친구를 찾기 위해 먼저 식사 성향을 설정해 주세요.')
          navigateTo('/personality/survey')
          return
        }
      } catch {
        // 성향 확인 중 실패 시 기본 진행
      }

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
      alert('🎉 회원가입을 축하합니다!\n나와 잘 맞는 밥친구를 찾기 위해 먼저 식사 성향을 설정해 주세요.')
      navigateTo('/personality/survey')
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

  // 채팅방 가기 버튼 및 헤더 매칭 상태 뱃지 업데이트
  const btnGoChat = document.querySelector('#btn-go-chat')
  const isLoggedIn = sessionStorage.getItem('project2.isLoggedIn') === 'true'

  if (isLoggedIn) {
    const cachedMatchResult = readCachedMatchResult()

    if (cachedMatchResult && cachedMatchResult.status === 'MATCHED') {
      if (btnGoChat) {
        enableChatButton(btnGoChat, cachedMatchResult.chatRoomId)
        setLandingMatchState(btnHeroMatch, btnGoChat, true)
      }
      document.documentElement.classList.add('has-cached-chat')
      updateHeaderMatchStatusBadge(cachedMatchResult)
    } else {
      if (btnGoChat) {
        btnGoChat.classList.add('hidden', 'chat-state-pending')
        setLandingMatchState(btnHeroMatch, btnGoChat, false)
      }
      document.documentElement.classList.remove('has-cached-chat')
      updateHeaderMatchStatusBadge(null)
    }

    import('./matching/matching-api.js').then(({ getLatestMatchResult }) => {
      getLatestMatchResult()
        .then((result) => {
          if (result && result.chatRoomId && result.status === 'MATCHED') {
            sessionStorage.setItem('project2.latestMatchResult', JSON.stringify(result))
            if (btnGoChat) {
              enableChatButton(btnGoChat, result.chatRoomId)
              setLandingMatchState(btnHeroMatch, btnGoChat, true)
            }
            document.documentElement.classList.add('has-cached-chat')
            updateHeaderMatchStatusBadge(result)
          } else {
            sessionStorage.removeItem('project2.latestMatchResult')
            if (btnGoChat) {
              btnGoChat.disabled = true
              btnGoChat.classList.add('hidden', 'chat-state-pending')
              setLandingMatchState(btnHeroMatch, btnGoChat, false)
            }
            document.documentElement.classList.remove('has-cached-chat')
            updateHeaderMatchStatusBadge(null)
          }
        })
        .catch(() => {
          sessionStorage.removeItem('project2.latestMatchResult')
          if (btnGoChat) {
            btnGoChat.disabled = true
            btnGoChat.classList.add('hidden', 'chat-state-pending')
            setLandingMatchState(btnHeroMatch, btnGoChat, false)
          }
          document.documentElement.classList.remove('has-cached-chat')
          updateHeaderMatchStatusBadge(null)
        })
    })
  }
}

function updateHeaderMatchStatusBadge(result) {
  const badge = document.querySelector('#header-match-status-badge')
  if (!badge) return

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

function readCachedMatchResult() {
  try {
    const cached = JSON.parse(sessionStorage.getItem('project2.latestMatchResult') || 'null')
    return cached?.chatRoomId ? cached : null
  } catch {
    sessionStorage.removeItem('project2.latestMatchResult')
    return null
  }
}

function enableChatButton(button, chatRoomId) {
  button.disabled = false
  button.classList.remove('opacity-50', 'cursor-not-allowed', 'hidden', 'chat-state-pending')
  button.title = ''
  if (button.dataset.chatRoomId === String(chatRoomId)) return
  button.dataset.chatRoomId = String(chatRoomId)
  button.addEventListener('click', () => navigateTo(`/chat?roomId=${chatRoomId}`))
}

// 메인 헤더 및 온보딩 연동 처리
function showPendingLoginMessage() {
  const message = sessionStorage.getItem('project2.loginMessage')
  if (!message) return

  sessionStorage.removeItem('project2.loginMessage')
  showToast(message)
}

function setLandingMatchState(matchButton, chatButton, isMatching) {
  if (!matchButton || !chatButton) return

  if (isMatching) {
    matchButton.classList.add('hidden')
    chatButton.classList.add('flex-1')
  } else {
    matchButton.classList.remove('hidden')
    chatButton.classList.remove('flex-1')
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
