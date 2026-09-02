import { exchangeOAuthCode } from '../auth/auth-api.js'
import { getPersonalityProfile } from '../personality/personality-api.js'
import { navigateTo } from '../main.js'

const ERROR_MESSAGES = {
  AUTH_001: '소셜 로그인에 실패했습니다. 다시 시도해 주세요.',
  AUTH_004: '소셜 계정에서 인증된 이메일을 제공해야 서비스에 가입할 수 있습니다.',
  AUTH_005: '동일한 이메일로 가입된 다른 로그인 방식의 계정이 있습니다.',
}

export async function renderOAuthCallback(container) {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const error = params.get('error')

  renderStatus(container, '소셜 로그인을 확인하고 있습니다.', '잠시만 기다려 주세요.', true)

  if (error) {
    window.history.replaceState({}, document.title, '/oauth/callback')
    renderFailure(container, ERROR_MESSAGES[error] || ERROR_MESSAGES.AUTH_001)
    return
  }

  if (!code) {
    window.history.replaceState({}, document.title, '/oauth/callback')
    renderFailure(container, '로그인 인증 코드가 없습니다. 처음부터 다시 시도해 주세요.')
    return
  }

  try {
    const result = await exchangeOAuthCode(code)
    let nextPath = '/'
    if (result.profileSetupRequired) {
      nextPath = '/profile/setup'
    } else {
    try {
      const personality = await getPersonalityProfile()
      if (personality?.onboardingStatus === 'NOT_STARTED') {
        alert('🎉 환영합니다!\n나와 잘 맞는 밥친구를 찾기 위해 먼저 식사 성향을 설정해 주세요.')
        nextPath = '/personality/survey'
      }
    } catch (profileError) {
      console.warn('온보딩 상태 확인 중 오류:', profileError)
    }
    }

    // 로그인 완료 창을 거치지 않고 바로 메인 페이지 또는 성향 설정 화면으로 즉시 이동
    navigateTo(nextPath)
  } catch (exchangeError) {
    window.history.replaceState({}, document.title, '/oauth/callback')
    renderFailure(container, exchangeError.message)
  }
}

function renderStatus(container, title, description, busy = false) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card status-card" aria-live="polite" ${busy ? 'aria-busy="true"' : ''}>
        <div class="status-icon ${busy ? 'is-loading' : ''}" aria-hidden="true">
          ${busy ? '' : '<span class="material-symbols-outlined text-3xl">check_circle</span>'}
        </div>
        <h1 class="status-title"></h1>
        <p class="description status-description"></p>
      </section>
    </main>
  `
  container.querySelector('.status-title').textContent = title
  container.querySelector('.status-description').textContent = description
}

function renderFailure(container, message) {
  renderStatus(container, '로그인을 완료하지 못했습니다.', message)

  const link = document.createElement('a')
  link.className = 'secondary-link'
  link.href = '/'
  link.textContent = '로그인 화면으로 돌아가기'
  container.querySelector('.auth-card').append(link)
}
