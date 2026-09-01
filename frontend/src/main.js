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

const app = document.querySelector('#app')
const initialLandingPageHtml = app?.innerHTML ?? ''

export { API_BASE_URL }

window.addEventListener('project2:match-updated', (event) => {
  const statusElement = document.querySelector('#header-match-status')
  if (!statusElement || typeof event.detail?.isMatching !== 'boolean') return

  if (event.detail.isMatching === false) {
    statusElement.className = 'flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold bg-slate-100 text-slate-500 border border-slate-200'
    statusElement.innerHTML = '<span class="h-1.5 w-1.5 rounded-full bg-slate-400"></span><span>\uB9E4\uCE6D \uC5C6\uC74C</span>'
    return
  }

  statusElement.className = 'flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200'
  statusElement.innerHTML = '<span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span><span>\uD604\uC7AC \uB9E4\uCE6D\uC911</span>'
})

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
    
    // 헤더 상태 동기화
    if (!isCurrentRoute()) return
    initCommonHeader()
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

  // 채팅방 가기 버튼: project2.isLoggedIn === 'true' 이며 매칭이 완료된 경우에만 활성화
  const btnGoChat = document.querySelector('#btn-go-chat')
  if (sessionStorage.getItem('project2.isLoggedIn') === 'true' && btnGoChat) {
    const cachedMatchResult = readCachedMatchResult()

    // 새로고침 혹은 SPA 화면 복원 직후에는 캐시 정보를 우선 활용하여 버튼 깜빡임 최소화
    if (cachedMatchResult && cachedMatchResult.status === 'MATCHED') {
      enableChatButton(btnGoChat, cachedMatchResult.chatRoomId)
      setLandingMatchState(btnHeroMatch, btnGoChat, true)
      document.documentElement.classList.add('has-cached-chat')
    } else {
      btnGoChat.classList.add('hidden', 'chat-state-pending')
      setLandingMatchState(btnHeroMatch, btnGoChat, false)
      document.documentElement.classList.remove('has-cached-chat')
    }

    import('./matching/matching-api.js').then(({ getLatestMatchResult }) => {
      getLatestMatchResult()
        .then((result) => {
          if (result && result.chatRoomId && result.status === 'MATCHED') {
            sessionStorage.setItem('project2.latestMatchResult', JSON.stringify(result))
            enableChatButton(btnGoChat, result.chatRoomId)
            setLandingMatchState(btnHeroMatch, btnGoChat, true)
            document.documentElement.classList.add('has-cached-chat')
          } else {
            // 매칭되지 않았거나 이미 종료(COMPLETED/CANCELLED)된 사용자는 채팅방 버튼을 표시하지 않음
            sessionStorage.removeItem('project2.latestMatchResult')
            btnGoChat.disabled = true
            btnGoChat.classList.add('hidden', 'chat-state-pending')
            setLandingMatchState(btnHeroMatch, btnGoChat, false)
            document.documentElement.classList.remove('has-cached-chat')
          }
        })
        .catch(() => {
          if (!cachedMatchResult || cachedMatchResult.status !== 'MATCHED') {
            btnGoChat.classList.add('hidden', 'chat-state-pending')
            document.documentElement.classList.remove('has-cached-chat')
          }
        })
    })
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

function initCommonHeader() {
  const headerAuth = document.querySelector('#header-auth')
  if (!headerAuth) return

  const isLoggedIn = sessionStorage.getItem('project2.isLoggedIn') === 'true'

  if (isLoggedIn) {
    // 사용자 정보 호출
    const nicknameEl = document.querySelector('#header-user-nickname')
    const profileImgEl = document.querySelector('#header-user-profile-img')
    
    if (nicknameEl && profileImgEl && !nicknameEl.dataset.loaded) {
      nicknameEl.dataset.loaded = 'true'
      fetch(`${API_BASE_URL}/users/me`, { credentials: 'include' })
        .then(res => res.json())
        .then(body => {
          if (body.success && body.data) {
            nicknameEl.textContent = body.data.nickname
            if (body.data.profileImageUrl) {
              profileImgEl.src = body.data.profileImageUrl
            }

            const btnGoMypage = document.querySelector('#btn-go-mypage')
            const matchStatusEl = document.querySelector('#header-match-status')
            if (btnGoMypage) {
              if (body.data.role === 'ADMIN') {
                matchStatusEl?.classList.add('hidden')
                matchStatusEl?.classList.remove('flex')
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
                loadHeaderMatchStatus(matchStatusEl)
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
        .catch(err => console.error('사용자 정보를 가져오는데 실패했습니다.', err))
    }

    // 프로필 드롭다운 메뉴 제어
    const btnProfileMenu = document.querySelector('#btn-profile-menu')
    const profileDropdown = document.querySelector('#profile-dropdown')
    if (btnProfileMenu && profileDropdown && !btnProfileMenu.dataset.bound) {
      btnProfileMenu.dataset.bound = 'true'
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

    // 마이페이지 이동 버튼 바인딩
    const btnGoMypage = document.querySelector('#btn-go-mypage')
    if (btnGoMypage && !btnGoMypage.dataset.bound) {
      btnGoMypage.dataset.bound = 'true'
      btnGoMypage.addEventListener('click', (e) => {
        e.preventDefault()
        profileDropdown?.classList.add('hidden')
        navigateTo('/mypage')
      })
    }

    // 로그아웃 버튼 바인딩
    const btnDropdownLogout = document.querySelector('#btn-dropdown-logout')
    if (btnDropdownLogout && !btnDropdownLogout.dataset.bound) {
      btnDropdownLogout.dataset.bound = 'true'
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
  }

  // 로그인/시작하기 모달 버튼 리스너 바인딩
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

  const btnHeaderLogin = document.querySelector('#btn-header-login')
  if (btnHeaderLogin && !btnHeaderLogin.dataset.bound) {
    btnHeaderLogin.dataset.bound = 'true'
    btnHeaderLogin.addEventListener('click', () => openAuthModal(false))
  }

  const btnHeaderStart = document.querySelector('#btn-header-start')
  if (btnHeaderStart && !btnHeaderStart.dataset.bound) {
    btnHeaderStart.dataset.bound = 'true'
    btnHeaderStart.addEventListener('click', () => openAuthModal(false))
  }
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

async function loadHeaderMatchStatus(statusElement) {
  if (!statusElement) return

  try {
    const { getLatestMatchResult } = await import('./matching/matching-api.js')
    const result = await getLatestMatchResult()
    const isMatching = result?.status === 'MATCHED'

    statusElement.className = `flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold ${
      isMatching
        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
        : 'bg-slate-100 text-slate-500 border border-slate-200'
    }`
    statusElement.innerHTML = `
      <span class="h-1.5 w-1.5 rounded-full ${isMatching ? 'bg-emerald-500' : 'bg-slate-400'}"></span>
      <span>${isMatching ? '현재 매칭중' : '매칭 없음'}</span>
    `
  } catch (error) {
    statusElement.className = 'flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold bg-slate-100 text-slate-500 border border-slate-200'
    statusElement.innerHTML = '<span class="h-1.5 w-1.5 rounded-full bg-slate-400"></span><span>매칭 없음</span>'
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
