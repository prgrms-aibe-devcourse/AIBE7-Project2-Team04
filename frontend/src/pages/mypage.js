import { navigateTo, API_BASE_URL } from '../main.js'

export async function renderMyPage(container) {
  // 비인증 접근 시 웰컴(홈) 페이지로 리다이렉트
  const isLoggedIn = sessionStorage.getItem('project2.isLoggedIn') === 'true'
  if (!isLoggedIn) {
    navigateTo('/')
    return
  }

  container.innerHTML = `
    <main class="max-w-4xl mx-auto px-margin-mobile md:px-margin-desktop py-12 flex-grow">
      <div class="bg-surface-container-lowest rounded-card p-6 md:p-8 shadow-soft border border-outline-variant/20 mb-8">
        <div class="flex flex-col sm:flex-row items-center gap-6 border-b border-outline-variant/20 pb-6 mb-6">
          <div class="w-24 h-24 rounded-full overflow-hidden border-4 border-white shadow-md bg-slate-100 flex items-center justify-center flex-shrink-0">
            <img id="mypage-user-profile-img" src="https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png" alt="프로필 이미지" class="w-full h-full object-cover" />
          </div>
          <div class="text-center sm:text-left space-y-1.5">
            <h1 id="mypage-user-nickname" class="font-headline text-2xl font-bold text-brand-navy">로딩 중...</h1>
            <p id="mypage-user-email" class="text-sm text-secondary">로딩 중...</p>
          </div>
        </div>

        <h2 class="font-headline text-lg font-bold text-brand-navy mb-4">계정 및 식사 설정</h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <!-- 선호 위치 등록 -->
          <div class="bg-surface rounded-2xl p-5 border border-outline-variant/30 flex flex-col justify-between hover:shadow-sm transition-shadow">
            <div class="space-y-1.5 mb-4">
              <div class="flex items-center gap-2 text-primary-container">
                <span class="material-symbols-outlined text-lg">favorite</span>
                <span class="text-sm font-bold">선호 위치 설정</span>
              </div>
              <p class="text-xs text-secondary leading-relaxed">
                매칭을 시작할 나의 주 활동 구역과 선호 위치 핀을 지도에서 설정합니다.
              </p>
            </div>
            <button id="btn-mypage-preferred" class="btn-primary w-full py-2.5 rounded-full text-xs sm:text-sm font-semibold">
              선호 위치 변경
            </button>
          </div>

          <!-- 식사 성향 -->
          <div class="bg-surface rounded-2xl p-5 border border-outline-variant/30 flex flex-col justify-between hover:shadow-sm transition-shadow">
            <div class="space-y-1.5 mb-4">
              <div class="flex items-center gap-2 text-primary-container">
                <span class="material-symbols-outlined text-lg">psychology</span>
                <span class="text-sm font-bold">식사 성향 테스트</span>
              </div>
              <p class="text-xs text-secondary leading-relaxed">
                밥친구를 만날 때 어색함 없는 식사 템포와 대화 스타일 취향을 다시 테스트합니다.
              </p>
            </div>
            <button id="btn-mypage-survey" class="btn-secondary w-full py-2.5 rounded-full text-xs sm:text-sm font-semibold">
              식사 성향 테스트하기
            </button>
          </div>
        </div>

        <!-- 민감 영역 (위치 동의 철회) -->
        <div class="mt-8 border-t border-outline-variant/20 pt-6">
          <h2 class="font-headline text-lg font-bold text-red-600 mb-2">개인정보 파기</h2>
          <p class="text-xs text-secondary leading-relaxed mb-4">
            위치 정보 이용 동의를 철회할 경우, 등록하신 선호 위치 데이터와 대기 중인 모든 실시간 매칭 요청이 즉시 영구 파기됩니다.
          </p>
          <button id="btn-mypage-revoke" class="px-4 py-2.5 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-full text-xs sm:text-sm font-bold transition-colors flex items-center gap-1.5 w-full sm:w-auto justify-center">
            <span class="material-symbols-outlined text-sm">no_accounts</span>
            <span>위치동의 철회</span>
          </button>
        </div>
      </div>
    </main>
  `

  // 유저 프로필 상세 정보 조회
  try {
    const res = await fetch(`${API_BASE_URL}/users/me`, { credentials: 'include' })
    const body = await res.json()
    if (body.success && body.data) {
      document.querySelector('#mypage-user-nickname').textContent = body.data.nickname
      document.querySelector('#mypage-user-email').textContent = body.data.email
      if (body.data.profileImageUrl) {
        document.querySelector('#mypage-user-profile-img').src = body.data.profileImageUrl
      }
    }
  } catch (err) {
    console.error('마이페이지 프로필 로드 실패', err)
  }

  // 선호 위치 변경 바인딩
  document.querySelector('#btn-mypage-preferred').addEventListener('click', () => {
    navigateTo('/map?mode=preferred')
  })

  // 성향 테스트 바인딩
  document.querySelector('#btn-mypage-survey').addEventListener('click', () => {
    navigateTo('/personality/survey')
  })

  // 위치 동의 철회 바인딩
  document.querySelector('#btn-mypage-revoke').addEventListener('click', async () => {
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
          navigateTo('/mypage')
        } else {
          alert('동의 철회 처리에 실패했습니다.')
        }
      } catch (err) {
        alert('오류가 발생했습니다: ' + err.message)
      }
    }
  })
}
