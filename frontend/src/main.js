import './style.css'
import { startKakaoLogin, startGoogleLogin, login, logout, signUp } from './auth/auth-api.js'
import { clearAccessToken, getAccessToken } from './auth/token-storage.js'
import { renderOAuthCallback } from './pages/oauth-callback.js'

const app = document.querySelector('#app')

if (window.location.pathname === '/oauth/callback') {
  renderOAuthCallback(app)
} else if (window.location.pathname === '/profile/setup') {
  renderProfileSetup(app)
} else {
  initLandingPage()
}

function initLandingPage() {
  const token = getAccessToken()
  const headerAuth = document.querySelector('#header-auth')
  const btnHeaderLogin = document.querySelector('#btn-header-login')
  const btnHeaderStart = document.querySelector('#btn-header-start')
  const btnHeroMatch = document.querySelector('#btn-hero-match')
  const btnCtaStart = document.querySelector('#btn-cta-start')
  const btnPreviewJoin = document.querySelector('#btn-preview-join')
  const districtSelect = document.querySelector('#hero-district-select')

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
    authModal?.classList.add('is-open')
    authModal?.setAttribute('aria-hidden', 'false')
  }

  const closeAuthModal = () => {
    authModal?.classList.remove('is-open')
    authModal?.setAttribute('aria-hidden', 'true')
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

  // Header Logged-In vs Logged-Out UI
  if (token && headerAuth) {
    headerAuth.innerHTML = `
      <div class="flex items-center gap-3">
        <span class="hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-success/15 text-success text-xs font-bold">
          <span class="w-1.5 h-1.5 rounded-full bg-success"></span>
          로그인 됨
        </span>
        <button id="btn-logout" class="btn-secondary px-4 py-2 rounded-full text-xs sm:text-sm font-semibold">
          로그아웃
        </button>
      </div>
    `
    document.querySelector('#btn-logout')?.addEventListener('click', async () => {
      await logout()
      clearAccessToken()
      window.location.reload()
    })
  } else {
    btnHeaderLogin?.addEventListener('click', () => openAuthModal(false))
    btnHeaderStart?.addEventListener('click', () => openAuthModal(false))
  }

  // Modal Close Events
  btnCloseModal?.addEventListener('click', closeAuthModal)
  authModal?.addEventListener('click', (e) => {
    if (e.target === authModal) {
      closeAuthModal()
    }
  })

  // Switch between Login & Signup
  btnToggleSignup?.addEventListener('click', showSignupForm)
  btnToggleLogin?.addEventListener('click', showLoginForm)

  // Social Login Triggers
  btnModalKakao?.addEventListener('click', startKakaoLogin)
  btnModalGoogle?.addEventListener('click', startGoogleLogin)

  // Local Login Submit
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

  // Local Signup Submit
  formSignup?.addEventListener('submit', async (e) => {
    e.preventDefault()
    const email = document.querySelector('#signup-email')?.value.trim()
    const nickname = document.querySelector('#signup-nickname')?.value.trim()
    const password = document.querySelector('#signup-password')?.value
    if (signupErrorMsg) signupErrorMsg.style.display = 'none'

    try {
      await signUp(email, password, nickname)
      // 회원가입 성공 후 자동 로그인 시도
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

  // Hero Quick Match Button
  const handleMatchStart = () => {
    const selectedDistrict = districtSelect ? districtSelect.value : ''
    if (!token) {
      openAuthModal(false)
      return
    }
    if (selectedDistrict) {
      alert(`[${selectedDistrict}] 지역 마주한끼 매칭 대기열에 참가합니다!`)
    } else {
      alert('활동 지역(구)을 먼저 선택해 주세요.')
      districtSelect?.focus()
    }
  }

  btnHeroMatch?.addEventListener('click', handleMatchStart)
  btnCtaStart?.addEventListener('click', () => {
    if (!token) {
      openAuthModal(false)
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      districtSelect?.focus()
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

function renderProfileSetup(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card" aria-labelledby="profile-title">
        <div class="brand-mark" aria-hidden="true">
          <span class="material-symbols-outlined text-3xl">face</span>
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
