import './style.css'
import { startKakaoLogin } from './auth/auth-api.js'
import { clearAccessToken, getAccessToken } from './auth/token-storage.js'
import { renderOAuthCallback } from './pages/oauth-callback.js'

const app = document.querySelector('#app')

if (window.location.pathname === '/oauth/callback') {
  renderOAuthCallback(app)
} else if (window.location.pathname === '/profile/setup') {
  renderProfileSetup(app)
} else if (getAccessToken()) {
  renderHome(app)
} else {
  renderLogin(app)
}

function renderLogin(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card" aria-labelledby="login-title">
        <div class="brand-mark" aria-hidden="true">P2</div>
        <p class="eyebrow">PROJECT 2</p>
        <h1 id="login-title">함께할 사람을 만나보세요</h1>
        <p class="description">카카오 계정으로 간편하게 시작할 수 있습니다.</p>
        <button id="kakao-login" class="kakao-button" type="button">
          카카오로 계속하기
        </button>
        <p class="notice">로그인하면 서비스 이용약관과 개인정보 처리방침에 동의하게 됩니다.</p>
      </section>
    </main>
  `

  container.querySelector('#kakao-login').addEventListener('click', startKakaoLogin)
}

function renderHome(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card" aria-labelledby="home-title">
        <div class="brand-mark" aria-hidden="true">P2</div>
        <p class="eyebrow">LOGIN COMPLETE</p>
        <h1 id="home-title">로그인되었습니다</h1>
        <p class="description">이제 인증이 필요한 Project2 API를 호출할 수 있습니다.</p>
        <button id="clear-session" class="secondary-link" type="button">이 브라우저의 Access Token 지우기</button>
      </section>
    </main>
  `

  container.querySelector('#clear-session').addEventListener('click', () => {
    clearAccessToken()
    window.location.replace('/')
  })
}

function renderProfileSetup(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card" aria-labelledby="profile-title">
        <p class="eyebrow">PROFILE SETUP</p>
        <h1 id="profile-title">프로필 설정이 필요합니다</h1>
        <p class="description">임시 닉네임이 발급되었습니다. 프로필 수정 API 구현 후 이 화면에 입력 폼을 연결합니다.</p>
        <a class="primary-link" href="/">우선 홈으로 이동</a>
      </section>
    </main>
  `
}
