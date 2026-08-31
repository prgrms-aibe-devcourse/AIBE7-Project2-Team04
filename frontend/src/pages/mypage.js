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
          
          <!-- 프로필 이미지 컨테이너 (카메라 아이콘 탑재) -->
          <div class="relative group">
            <div class="w-24 h-24 rounded-full overflow-hidden border-4 border-white shadow-md bg-slate-100 flex items-center justify-center flex-shrink-0">
              <img id="mypage-user-profile-img" src="https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png" alt="프로필 이미지" class="w-full h-full object-cover" />
            </div>
            <!-- 카메라 수정 버튼 -->
            <button id="btn-edit-profile-img" class="absolute -bottom-1 -right-1 bg-[#ae3115] text-white p-2 rounded-full shadow-md hover:scale-105 transition-transform flex items-center justify-center border border-white" aria-label="프로필 사진 수정" title="프로필 사진 수정">
              <span class="material-symbols-outlined text-sm">photo_camera</span>
            </button>
            <input type="file" id="input-profile-file" class="hidden" accept="image/*" />
          </div>

          <div class="text-center sm:text-left space-y-1.5 flex-grow">
            <!-- 닉네임 수정 컴포넌트 -->
            <div class="flex items-center justify-center sm:justify-start gap-2.5">
              <div id="nickname-display-wrapper" class="flex items-center gap-2">
                <h1 id="mypage-user-nickname" class="font-headline text-2xl font-bold text-brand-navy">로딩 중...</h1>
                <button id="btn-edit-nickname" class="text-secondary hover:text-primary-container transition-colors flex items-center justify-center" title="닉네임 수정">
                  <span class="material-symbols-outlined text-lg">edit</span>
                </button>
              </div>
              <div id="nickname-edit-wrapper" class="hidden flex items-center gap-2">
                <input type="text" id="input-edit-nickname" class="form-input rounded-xl px-3 py-1.5 text-sm border border-slate-300 focus:border-[#ae3115] focus:ring-[#ae3115] w-48" maxlength="50" />
                <button id="btn-save-nickname" class="bg-emerald-600 text-white p-1.5 rounded-lg shadow-sm hover:bg-opacity-90 flex items-center justify-center" title="저장">
                  <span class="material-symbols-outlined text-sm">check</span>
                </button>
                <button id="btn-cancel-nickname" class="bg-slate-100 text-slate-600 p-1.5 rounded-lg shadow-sm hover:bg-slate-200 flex items-center justify-center" title="취소">
                  <span class="material-symbols-outlined text-sm">close</span>
                </button>
              </div>
            </div>
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

        <!-- 매칭 히스토리 -->
        <div class="mt-8 border-t border-outline-variant/20 pt-6">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="font-headline text-lg font-bold text-brand-navy">매칭 히스토리</h2>
              <p class="text-xs text-secondary mt-1">그동안 성사된 매칭을 확인할 수 있습니다.</p>
            </div>
            <span class="material-symbols-outlined text-primary-container">history</span>
          </div>
          <div id="mypage-match-history" class="space-y-3">
            <p class="text-sm text-secondary text-center py-6">매칭 이력을 불러오는 중...</p>
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

        <!-- 계정 탈퇴 -->
        <div class="mt-8 border-t border-outline-variant/20 pt-6">
          <h2 class="font-headline text-lg font-bold text-red-600 mb-2">계정 탈퇴</h2>
          <p class="text-xs text-secondary leading-relaxed mb-4">
            탈퇴 시 회원 정보 및 서비스 이용 기록이 모두 파기되며, 이 작업은 되돌릴 수 없습니다.
          </p>
          <button id="btn-mypage-withdraw" class="px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-full text-xs sm:text-sm font-bold transition-colors flex items-center gap-1.5 w-full sm:w-auto justify-center">
            <span class="material-symbols-outlined text-sm">delete_forever</span>
            <span>계정 탈퇴하기</span>
          </button>
        </div>
      </div>
    </main>
  `

  let cachedUserId = null
  let cachedNickname = ''

  // 유저 프로필 상세 정보 조회
  const loadProfile = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/users/me`, { credentials: 'include' })
      const body = await res.json()
      if (body.success && body.data) {
        cachedUserId = body.data.userId
        cachedNickname = body.data.nickname
        
        document.querySelector('#mypage-user-nickname').textContent = body.data.nickname
        document.querySelector('#mypage-user-email').textContent = body.data.email
        if (body.data.profileImageUrl) {
          document.querySelector('#mypage-user-profile-img').src = body.data.profileImageUrl
        }
      }
    } catch (err) {
      console.error('마이페이지 프로필 로드 실패', err)
    }
  }

  await loadProfile()

  const loadMatchHistory = async () => {
    const historyContainer = document.querySelector('#mypage-match-history')
    if (!historyContainer) return

    try {
      const response = await fetch(`${API_BASE_URL}/matches/history`, { credentials: 'include' })
      const body = await response.json()
      const history = body.success && Array.isArray(body.data) ? body.data : []
      if (history.length === 0) {
        historyContainer.innerHTML = '<p class="text-sm text-secondary text-center py-6">아직 매칭 이력이 없습니다.</p>'
        return
      }

      const statusLabel = { MATCHED: '진행 중', COMPLETED: '만남 완료', CANCELLED: '취소됨' }
      historyContainer.innerHTML = history.map(item => `
        <div class="bg-surface rounded-2xl p-4 border border-outline-variant/30 flex items-center gap-3">
          <div class="w-12 h-12 rounded-full overflow-hidden bg-slate-100 flex items-center justify-center flex-shrink-0">
            ${item.partnerProfileImageUrl
              ? `<img src="${escapeHtml(item.partnerProfileImageUrl)}" alt="${escapeHtml(item.partnerNickname || '상대방')} 프로필" class="w-full h-full object-cover" />`
              : `<span class="font-bold text-brand-navy">${escapeHtml((item.partnerNickname || '밥').trim().charAt(0) || '밥')}</span>`}
          </div>
          <div class="min-w-0 flex-grow">
            <div class="flex items-center gap-2">
              <span class="font-bold text-brand-navy truncate">${escapeHtml(item.partnerNickname || '상대방')}</span>
              <span class="text-[11px] px-2 py-0.5 rounded-full bg-slate-100 text-secondary">${statusLabel[item.status] || item.status}</span>
            </div>
            <p class="text-xs text-secondary mt-1 truncate">${escapeHtml(item.regionName || '')} · ${escapeHtml(item.foodCategory || '')}</p>
          </div>
          <time class="text-[11px] text-secondary whitespace-nowrap">${formatHistoryDate(item.matchedAt)}</time>
        </div>
      `).join('')
    } catch (error) {
      console.error('매칭 이력 조회 실패', error)
      historyContainer.innerHTML = '<p class="text-sm text-secondary text-center py-6">매칭 이력을 불러오지 못했습니다.</p>'
    }
  }

  const escapeHtml = (value) => String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')

  const formatHistoryDate = (value) => {
    if (!value) return '-'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('ko-KR')
  }

  await loadMatchHistory()

  // 닉네임 수정 모드 전환 제어
  const btnEditNickname = document.querySelector('#btn-edit-nickname')
  const btnCancelNickname = document.querySelector('#btn-cancel-nickname')
  const btnSaveNickname = document.querySelector('#btn-save-nickname')
  const nicknameDisplayWrapper = document.querySelector('#nickname-display-wrapper')
  const nicknameEditWrapper = document.querySelector('#nickname-edit-wrapper')
  const inputEditNickname = document.querySelector('#input-edit-nickname')

  btnEditNickname?.addEventListener('click', () => {
    inputEditNickname.value = cachedNickname
    nicknameDisplayWrapper.classList.add('hidden')
    nicknameEditWrapper.classList.remove('hidden')
    inputEditNickname.focus()
  })

  btnCancelNickname?.addEventListener('click', () => {
    nicknameDisplayWrapper.classList.remove('hidden')
    nicknameEditWrapper.classList.add('hidden')
  })

  btnSaveNickname?.addEventListener('click', async () => {
    const nextNickname = inputEditNickname.value.trim()
    if (!nextNickname || nextNickname.length < 2) {
      alert('닉네임은 2자 이상 입력해주세요.')
      return
    }

    try {
      const csrfResp = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' })
      if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
      const readCookie = (name) => {
        const prefix = `${encodeURIComponent(name)}=`
        const cookie = document.cookie.split('; ').find(item => item.startsWith(prefix))
        return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
      }
      const csrfToken = readCookie('XSRF-TOKEN')

      const resp = await fetch(`${API_BASE_URL}/users/me`, {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrfToken
        },
        body: JSON.stringify({ nickname: nextNickname })
      })

      if (resp.ok) {
        const body = await resp.json()
        if (body.success && body.data) {
          cachedNickname = body.data.nickname
          document.querySelector('#mypage-user-nickname').textContent = cachedNickname
          
          // 헤더 닉네임 실시간 동기화
          const headerNickname = document.querySelector('#header-user-nickname')
          if (headerNickname) headerNickname.textContent = cachedNickname
          
          nicknameDisplayWrapper.classList.remove('hidden')
          nicknameEditWrapper.classList.add('hidden')
          alert('닉네임이 성공적으로 변경되었습니다.')
        }
      } else {
        const body = await resp.json()
        alert(body?.error?.message || '닉네임 변경에 실패했습니다.')
      }
    } catch (err) {
      alert('오류가 발생했습니다: ' + err.message)
    }
  })

  // 프로필 이미지 수정 및 백엔드 업로드 연동
  const btnEditProfileImg = document.querySelector('#btn-edit-profile-img')
  const inputProfileFile = document.querySelector('#input-profile-file')

  btnEditProfileImg?.addEventListener('click', () => {
    inputProfileFile?.click()
  })

  inputProfileFile?.addEventListener('change', async (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    if (!file.type.startsWith('image/')) {
      alert('이미지 파일만 업로드할 수 있습니다.')
      return
    }

    if (file.size > 5 * 1024 * 1024) {
      alert('프로필 사진은 최대 5MB까지만 업로드할 수 있습니다.')
      return
    }

    try {
      const formData = new FormData()
      formData.append('file', file)

      const csrfResp = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' })
      if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
      const readCookie = (name) => {
        const prefix = `${encodeURIComponent(name)}=`
        const cookie = document.cookie.split('; ').find(item => item.startsWith(prefix))
        return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
      }
      const csrfToken = readCookie('XSRF-TOKEN')

      const profileUploadResp = await fetch(`${API_BASE_URL}/users/me/profile-image`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'X-XSRF-TOKEN': csrfToken
        },
        body: formData
      })

      if (profileUploadResp.ok) {
        const body = await profileUploadResp.json()
        if (body.success && body.data) {
          const newUrl = body.data.profileImageUrl
          document.querySelector('#mypage-user-profile-img').src = newUrl
          
          // 헤더 프로필 이미지 동기화
          const headerProfileImg = document.querySelector('#header-user-profile-img')
          if (headerProfileImg) headerProfileImg.src = newUrl
          
          alert('프로필 사진이 성공적으로 변경되었습니다.')
        }
      } else {
        const body = await profileUploadResp.json()
        alert(body?.error?.message || '프로필 사진 저장에 실패했습니다.')
      }
    } catch (err) {
      alert('프로필 변경 중 오류 발생: ' + err.message)
    } finally {
      inputProfileFile.value = ''
    }
  })

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

  // 계정 탈퇴 바인딩
  document.querySelector('#btn-mypage-withdraw')?.addEventListener('click', async () => {
    if (confirm('정말로 마주한끼를 탈퇴하시겠습니까?\n탈퇴 시 모든 데이터가 파기되며 되돌릴 수 없습니다.')) {
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

        const resp = await fetch(`${API_BASE_URL}/users/me`, {
          method: 'DELETE',
          credentials: 'include',
          headers: {
            'X-XSRF-TOKEN': csrfToken
          }
        })

        if (resp.ok) {
          alert('계정이 성공적으로 탈퇴 처리되었습니다. 그동안 서비스를 이용해 주셔서 감사합니다.')
          sessionStorage.removeItem('project2.isLoggedIn')
          sessionStorage.removeItem('project2.latestMatchResult')
          navigateTo('/')
          window.location.reload()
        } else {
          alert('계정 탈퇴 처리에 실패했습니다.')
        }
      } catch (err) {
        alert('오류가 발생했습니다: ' + err.message)
      }
    }
  })
}
