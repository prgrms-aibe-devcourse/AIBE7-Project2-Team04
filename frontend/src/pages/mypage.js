import { navigateTo, API_BASE_URL } from '../main.js'
import { getCsrfToken } from '../auth/csrf.js'

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
        <div class="relative flex flex-col sm:flex-row items-center gap-6 border-b border-outline-variant/20 pb-6 mb-6">

          <!-- 프로필 이미지 컨테이너 (카메라 아이콘 탑재) -->
          <div class="relative group">
            <div class="w-24 h-24 rounded-full overflow-hidden border-4 border-white shadow-md bg-slate-100 flex items-center justify-center flex-shrink-0">
              <img id="mypage-user-profile-img" src="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%2394a3b8'><path d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 4c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm0 14c-2.03 0-3.8-1.04-4.84-2.61.03-.99 2.91-1.53 4.84-1.53s4.81.54 4.84 1.53C15.8 18.96 14.03 20 12 20z'/></svg>" alt="프로필 이미지" class="w-full h-full object-cover" onerror="this.onerror=null;this.src='data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' viewBox=\'0 0 24 24\' fill=\'%2394a3b8\'><path d=\'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 4c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm0 14c-2.03 0-3.8-1.04-4.84-2.61.03-.99 2.91-1.53 4.84-1.53s4.81.54 4.84 1.53C15.8 18.96 14.03 20 12 20z\'/></svg>'" />
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
                <span class="text-sm font-bold">식사 성향 설정</span>
              </div>
              <p class="text-xs text-secondary leading-relaxed">
                밥친구를 만날 때 어색함 없는 식사 템포와 대화 스타일 취향을 설정하고 관리합니다.
              </p>
            </div>
            <button id="btn-mypage-survey" class="btn-secondary w-full py-2.5 rounded-full text-xs sm:text-sm font-semibold">
              식사 성향 설정하기
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

        <!-- 계정 및 개인정보 관리 -->
        <section class="mt-8 border-t border-outline-variant/20 pt-6" aria-labelledby="mypage-account-privacy-heading">
          <div class="mb-4">
            <h2 id="mypage-account-privacy-heading" class="font-headline text-lg font-bold text-brand-navy">계정 및 개인정보 관리</h2>
            <p class="text-xs text-secondary mt-1">복구할 수 없는 작업이 포함되어 있으니 처리 전 내용을 확인해주세요.</p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <!-- 개인정보 파기 -->
            <div class="bg-surface rounded-2xl p-5 border border-red-100 flex flex-col justify-between">
              <div class="space-y-1.5 mb-4">
                <h3 class="font-headline text-base font-bold text-red-600">개인정보 파기</h3>
                <p class="text-xs text-secondary leading-relaxed">
                  위치 정보 이용 동의를 철회하면 등록하신 선호 위치 데이터와 대기 중인 모든 실시간 매칭 요청이 즉시 영구 파기됩니다.
                </p>
              </div>
              <button id="btn-mypage-revoke" class="px-4 py-2.5 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-full text-xs sm:text-sm font-bold transition-colors flex items-center gap-1.5 w-full justify-center">
                <span class="material-symbols-outlined text-sm">no_accounts</span>
                <span>위치동의 철회</span>
              </button>
            </div>

            <!-- 계정 탈퇴 -->
            <div class="bg-surface rounded-2xl p-5 border border-red-100 flex flex-col justify-between">
              <div class="space-y-1.5 mb-4">
                <h3 class="font-headline text-base font-bold text-red-600">계정 탈퇴</h3>
                <p class="text-xs text-secondary leading-relaxed">
                  탈퇴 시 회원 정보 및 서비스 이용 기록이 모두 파기되며, 이 작업은 되돌릴 수 없습니다.
                </p>
              </div>
              <button id="btn-mypage-withdraw" class="px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-full text-xs sm:text-sm font-bold transition-colors flex items-center gap-1.5 w-full justify-center">
                <span class="material-symbols-outlined text-sm">delete_forever</span>
                <span>계정 탈퇴하기</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  `

  document.querySelector('#mypage-user-email')?.insertAdjacentHTML('afterend', `
    <div id="mypage-dasi-score" class="mt-3 sm:mt-0 sm:absolute sm:right-0 sm:top-1/2 sm:-translate-y-1/2 rounded-2xl bg-primary-container/10 border border-primary-container/20 px-5 py-3 text-center">
      <p class="text-[11px] font-bold text-secondary">다시한끼 지수</p>
      <p id="mypage-dasi-score-value" class="mt-1 text-xl font-extrabold text-primary">확인 중...</p>
    </div>
  `)

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

  const loadDasiHankkiScore = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/mypage/reviews`, { credentials: 'include' })
      const body = await response.json()
      const scoreElement = document.querySelector('#mypage-dasi-score-value')
      if (!scoreElement || !body.success || !body.data) return

      if (body.data.scoreStatus === 'AVAILABLE' && body.data.dasiHankkiScore != null) {
        scoreElement.textContent = `${body.data.dasiHankkiScore}점`
      } else {
        scoreElement.textContent = body.data.scoreStatus === 'NO_REVIEWS'
          ? '아직 후기가 없어요'
          : '후기가 더 모이면 다시한끼 지수가 공개돼요'
        scoreElement.classList.remove('text-xl')
        scoreElement.classList.add('text-xs')
      }
    } catch (err) {
      console.error('다시한끼 지수 로드 실패', err)
      const scoreElement = document.querySelector('#mypage-dasi-score-value')
      if (scoreElement) scoreElement.textContent = '-'
    }
  }

  await loadDasiHankkiScore()

  let currentHistoryPage = 0

  const loadMatchHistory = async (page = currentHistoryPage) => {
    const historyContainer = document.querySelector('#mypage-match-history')
    if (!historyContainer) return

    try {
      const response = await fetch(`${API_BASE_URL}/matches/history?page=${page}&size=10`, { credentials: 'include' })
      const body = await response.json()
      const pageData = body.success && body.data ? body.data : null
      const history = pageData && Array.isArray(pageData.content) ? pageData.content : []
      if (history.length === 0) {
        historyContainer.innerHTML = '<p class="text-sm text-secondary text-center py-6">아직 매칭 이력이 없습니다.</p>'
        return
      }

      const statusLabel = { MATCHED: '진행 중', COMPLETED: '만남 완료', CANCELLED: '취소됨' }
      historyContainer.innerHTML = history.map(item => {
        const isCompleted = item.status === 'COMPLETED';
        return `
        <div class="bg-surface rounded-2xl p-4 border border-outline-variant/30 flex flex-col gap-3">
          <div class="flex items-center gap-3">
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
          ${isCompleted ? `
          <div class="flex justify-end gap-2 border-t border-outline-variant/10 pt-2.5">
            ${item.reviewed ? `
            <button class="px-3 py-1.5 bg-slate-100 text-slate-400 text-xs font-bold rounded-lg cursor-not-allowed" disabled>
              후기 작성 완료
            </button>
            ` : `
            <button class="btn-review-write px-3 py-1.5 bg-primary-container text-white text-xs font-bold rounded-lg shadow-sm hover:bg-primary transition-colors" data-match-id="${item.matchId}">
              후기 작성
            </button>
            `}
            <button class="btn-report-user px-3 py-1.5 bg-red-50 text-red-600 border border-red-100 text-xs font-bold rounded-lg hover:bg-red-100 transition-colors" data-match-id="${item.matchId}">
              신고하기
            </button>
          </div>
          ` : ''}
        </div>
        `
      }).join('')

      currentHistoryPage = pageData.page
      if (pageData.totalPages > 1) {
        historyContainer.insertAdjacentHTML('beforeend', `
          <div class="flex items-center justify-center gap-3 pt-2">
            <button id="btn-history-prev" class="px-3 py-1.5 rounded-lg border border-outline-variant/30 text-xs font-bold text-secondary disabled:opacity-40" ${pageData.first ? 'disabled' : ''}>이전</button>
            <span class="text-xs text-secondary">${pageData.page + 1} / ${pageData.totalPages}</span>
            <button id="btn-history-next" class="px-3 py-1.5 rounded-lg border border-outline-variant/30 text-xs font-bold text-secondary disabled:opacity-40" ${pageData.last ? 'disabled' : ''}>다음</button>
          </div>
        `)
        document.querySelector('#btn-history-prev')?.addEventListener('click', () => loadMatchHistory(currentHistoryPage - 1))
        document.querySelector('#btn-history-next')?.addEventListener('click', () => loadMatchHistory(currentHistoryPage + 1))
      }

      historyContainer.querySelectorAll('.btn-report-user').forEach(btn => {
        const item = history.find(historyItem => String(historyItem.matchId) === btn.getAttribute('data-match-id'))
        if (item?.reported) {
          btn.disabled = true
          btn.classList.remove('btn-report-user', 'bg-red-50', 'text-red-600', 'border-red-100', 'hover:bg-red-100')
          btn.classList.add('bg-slate-100', 'text-slate-400', 'cursor-not-allowed')
          btn.textContent = '신고 접수 완료'
        }
      })

      historyContainer.querySelectorAll('.btn-review-write').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const matchId = e.currentTarget.getAttribute('data-match-id')
          navigateTo(`/review?matchId=${matchId}`)
        })
      })

      historyContainer.querySelectorAll('.btn-report-user').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const matchId = e.currentTarget.getAttribute('data-match-id')
          openReportModal(matchId)
        })
      })

    } catch (error) {
      console.error('매칭 이력 조회 실패', error)
      historyContainer.innerHTML = '<p class="text-sm text-secondary text-center py-6">매칭 이력을 불러오지 못했습니다.</p>'
    }
  }

  const openReportModal = (matchId) => {
    const existModal = document.querySelector('#report-modal')
    if (existModal) existModal.remove()

    const modalHtml = `
      <div id="report-modal" class="fixed inset-0 bg-slate-900/50 flex items-center justify-center z-50 p-4">
        <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-xl flex flex-col gap-4">
          <div>
            <h3 class="text-lg font-bold text-brand-navy">불량 이용자 신고하기</h3>
            <p class="text-xs text-secondary mt-1">상대방의 불량한 행동이나 매칭 수칙 위반 사항을 신고해주세요. 관리자가 검토 후 제재 조치를 취합니다.</p>
          </div>
          
          <div class="flex flex-col gap-2">
            <label class="text-xs font-bold text-slate-700">신고 유형</label>
            <select id="report-category" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary-container">
              <option value="NO_SHOW">약속 불이행 및 노쇼 (No-Show)</option>
              <option value="ABUSE">욕설 및 비방, 부적절한 대화</option>
              <option value="SPAM">광고 및 스팸 홍보</option>
              <option value="MISINFORMATION">허위 사실 유포</option>
            </select>
          </div>

          <div class="flex flex-col gap-2">
            <label class="text-xs font-bold text-slate-700">신고 사유 상세</label>
            <textarea id="report-reason" rows="4" placeholder="상세한 신고 사유를 입력해주세요. 어드민이 확인을 위해 관련 대화 내역을 검토할 수 있습니다." class="w-full bg-slate-50 border border-slate-200 rounded-xl p-3 text-sm outline-none focus:ring-2 focus:ring-primary-container resize-none"></textarea>
          </div>

          <div class="flex justify-end gap-2 mt-2">
            <button id="btn-close-report" class="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50 transition-colors">취소</button>
            <button id="btn-submit-report" class="px-4 py-2 bg-red-600 text-white rounded-xl text-xs font-bold hover:bg-red-700 transition-colors shadow-sm">신고 전송</button>
          </div>
        </div>
      </div>
    `
    document.body.insertAdjacentHTML('beforeend', modalHtml)

    const modal = document.querySelector('#report-modal')
    const btnClose = modal.querySelector('#btn-close-report')
    const btnSubmit = modal.querySelector('#btn-submit-report')

    btnClose.addEventListener('click', () => modal.remove())

    btnSubmit.addEventListener('click', async () => {
      const category = modal.querySelector('#report-category').value
      const reason = modal.querySelector('#report-reason').value.trim()

      if (!reason) {
        alert('신고 사유를 구체적으로 적어주세요.')
        return
      }

      try {
        const csrfToken = await getCsrfToken()

        const resp = await fetch(`${API_BASE_URL}/reports`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken
          },
          body: JSON.stringify({
            matchId: Number(matchId),
            category,
            reason
          })
        })

        if (resp.ok) {
          alert('신고가 성공적으로 접수되었습니다. 소중한 의견 감사합니다.')
          modal.remove()
          await loadMatchHistory(currentHistoryPage)
        } else {
          const body = await resp.json()
          alert(body?.error?.message || '신고 접수에 실패했습니다.')
        }
      } catch (err) {
        alert('신고 중 오류 발생: ' + err.message)
      }
    })
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
      const csrfToken = await getCsrfToken()

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

      const csrfToken = await getCsrfToken()

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
        const csrfToken = await getCsrfToken()

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
        const csrfToken = await getCsrfToken()

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
