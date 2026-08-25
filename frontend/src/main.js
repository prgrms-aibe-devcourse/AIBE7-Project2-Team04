import './style.css'
import { startKakaoLogin } from './auth/auth-api.js'
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
  const btnHeroMatch = document.querySelector('#btn-hero-match')
  const btnCtaStart = document.querySelector('#btn-cta-start')
  const btnPreviewJoin = document.querySelector('#btn-preview-join')
  const districtSelect = document.querySelector('#hero-district-select')

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
    document.querySelector('#btn-logout')?.addEventListener('click', () => {
      clearAccessToken()
      window.location.reload()
    })
  } else if (btnHeaderLogin) {
    btnHeaderLogin.addEventListener('click', startKakaoLogin)
  }

  const handleMatchStart = () => {
    const selectedDistrict = districtSelect ? districtSelect.value : ''
    if (!token) {
      startKakaoLogin()
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
      startKakaoLogin()
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      districtSelect?.focus()
    }
  })
  btnPreviewJoin?.addEventListener('click', () => {
    if (!token) {
      startKakaoLogin()
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
