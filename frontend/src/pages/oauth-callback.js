import { exchangeOAuthCode } from '../auth/auth-api.js'

const ERROR_MESSAGES = {
  AUTH_001: '소셜 로그인에 실패했습니다. 다시 시도해 주세요.',
  AUTH_004: '소셜 계정에서 인증된 이메일을 제공해야 서비스에 가입할 수 있습니다.',
  AUTH_005: '동일한 이메일로 가입된 다른 로그인 방식의 계정이 있습니다.',
}

export async function renderOAuthCallback(container) {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const error = params.get('error')

  window.history.replaceState({}, document.title, '/oauth/callback')
  renderStatus(container, '소셜 로그인을 확인하고 있습니다.', '잠시만 기다려 주세요.', true)

  if (error) {
    renderFailure(container, ERROR_MESSAGES[error] || ERROR_MESSAGES.AUTH_001)
    return
  }

  if (!code) {
    renderFailure(container, '로그인 인증 코드가 없습니다. 처음부터 다시 시도해 주세요.')
    return
  }

  try {
    const result = await exchangeOAuthCode(code)
    const nextPath = result.profileSetupRequired ? '/profile/setup' : '/'
    renderSuccess(container, result.profileSetupRequired, nextPath)
  } catch (exchangeError) {
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

function renderSuccess(container, profileSetupRequired, nextPath) {
  renderStatus(
    container,
    '로그인이 완료되었습니다.',
    profileSetupRequired ? '서비스 이용 전에 프로필을 완성해 주세요.' : '마주한끼를 시작할 준비가 되었습니다.',
  )

  const link = document.createElement('a')
  link.className = 'primary-link'
  link.href = nextPath
  link.textContent = profileSetupRequired ? '프로필 설정하기' : '홈으로 이동'
  container.querySelector('.auth-card').append(link)
}

function renderFailure(container, message) {
  renderStatus(container, '로그인을 완료하지 못했습니다.', message)

  const link = document.createElement('a')
  link.className = 'secondary-link'
  link.href = '/'
  link.textContent = '로그인 화면으로 돌아가기'
  container.querySelector('.auth-card').append(link)
}
