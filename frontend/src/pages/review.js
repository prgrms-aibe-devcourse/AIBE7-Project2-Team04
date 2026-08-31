import { navigateTo, API_BASE_URL } from '../main.js'

export async function renderReviewPage(container) {
  const params = new URLSearchParams(window.location.search)
  const matchId = params.get('matchId')

  if (!matchId) {
    alert('유효하지 않은 매칭 정보입니다.')
    navigateTo('/mypage')
    return
  }

  let selectedRevisit = null
  let selectedTag = null
  let isSubmitting = false

  container.innerHTML = `
    <main class="min-h-[calc(100vh-88px)] py-12 px-4 sm:px-6 lg:px-8 bg-gradient-to-b from-[#FAF7F2] via-white to-[#FAF7F2] flex flex-col items-center">
      <div class="w-full max-w-xl bg-white border border-slate-200/80 rounded-2xl shadow-soft p-6 sm:p-10">
        
        <!-- Header -->
        <div class="text-center sm:text-left mb-8 border-b border-slate-100 pb-5">
          <div class="inline-flex items-center gap-2 rounded-full bg-primary-container/10 px-3 py-1.5 text-xs font-bold text-primary mb-3">
            <span class="material-symbols-outlined text-base">rate_review</span>
            <span>한끼 후기 남기기</span>
          </div>
          <h1 class="text-2xl font-extrabold text-brand-navy tracking-tight font-headline">
            상대방과의 식사는 어떠셨나요?
          </h1>
          <p class="text-xs text-slate-500 mt-1.5 leading-relaxed">
            남겨주신 의견은 밥친구의 매너 온도(후기 평점)에 반영되며,<br/>더 나은 매칭을 위한 중요한 추천 기준이 됩니다.
          </p>
        </div>

        <!-- 설문 항목 1: 재만남 의향 -->
        <div class="mb-8">
          <label class="block text-sm font-bold text-brand-navy mb-3">
            1. 상대방과 다시 만날 의향이 있으신가요? <span class="text-red-500">*</span>
          </label>
          <div class="grid grid-cols-1 gap-2.5">
            <button type="button" data-revisit="DEFINITELY_AGAIN" class="revisit-card w-full text-left p-4 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all flex items-center justify-between select-none">
              <div>
                <span class="text-sm font-bold text-brand-navy block">😍 꼭 다시 만나고 싶어요</span>
                <span class="text-[11px] text-slate-500 mt-0.5">매우 즐겁고 유쾌한 식사였습니다.</span>
              </div>
              <span class="check-icon material-symbols-outlined text-primary text-xl hidden">check_circle</span>
            </button>
            <button type="button" data-revisit="MAYBE_AGAIN" class="revisit-card w-full text-left p-4 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all flex items-center justify-between select-none">
              <div>
                <span class="text-sm font-bold text-brand-navy block">🙂 기회가 된다면 만나요</span>
                <span class="text-[11px] text-slate-500 mt-0.5">무난하고 편안하게 식사했습니다.</span>
              </div>
              <span class="check-icon material-symbols-outlined text-primary text-xl hidden">check_circle</span>
            </button>
            <button type="button" data-revisit="ENOUGH_FOR_NOW" class="revisit-card w-full text-left p-4 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all flex items-center justify-between select-none">
              <div>
                <span class="text-sm font-bold text-brand-navy block">🤐 이번으로 충분해요</span>
                <span class="text-[11px] text-slate-500 mt-0.5">서로의 스타일이 다소 맞지 않았습니다.</span>
              </div>
              <span class="check-icon material-symbols-outlined text-primary text-xl hidden">check_circle</span>
            </button>
          </div>
        </div>

        <!-- 설문 항목 2: 인상 태그 -->
        <div class="mb-10">
          <div class="flex items-center justify-between mb-3">
            <label class="block text-sm font-bold text-brand-navy">
              2. 상대방의 가장 인상 깊었던 매력은 무엇인가요?
            </label>
            <span class="text-[11px] text-slate-400 font-medium">선택 사항</span>
          </div>
          <div class="grid grid-cols-2 gap-2.5">
            <button type="button" data-tag="PUNCTUAL" class="tag-card p-3 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all text-center flex flex-col items-center gap-1.5 select-none">
              <span class="material-symbols-outlined text-slate-500 text-lg">schedule</span>
              <span class="text-xs font-bold text-brand-navy">⏰ 시간 약속을 잘 지켜요</span>
            </button>
            <button type="button" data-tag="COMFORTABLE_CONVERSATION" class="tag-card p-3 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all text-center flex flex-col items-center gap-1.5 select-none">
              <span class="material-symbols-outlined text-slate-500 text-lg">chat_bubble</span>
              <span class="text-xs font-bold text-brand-navy">💬 편안하게 대화해줘요</span>
            </button>
            <button type="button" data-tag="CONSIDERATE" class="tag-card p-3 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all text-center flex flex-col items-center gap-1.5 select-none">
              <span class="material-symbols-outlined text-slate-500 text-lg">thumb_up</span>
              <span class="text-xs font-bold text-brand-navy">🤝 사려 깊고 매너있어요</span>
            </button>
            <button type="button" data-tag="ACTIVE_PARTICIPATION" class="tag-card p-3 border border-slate-200 rounded-xl hover:border-primary-container hover:bg-slate-50/50 transition-all text-center flex flex-col items-center gap-1.5 select-none">
              <span class="material-symbols-outlined text-slate-500 text-lg">campaign</span>
              <span class="text-xs font-bold text-brand-navy">🔥 대화에 잘 참여해요</span>
            </button>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex items-center gap-3 pt-5 border-t border-slate-100">
          <button id="btn-review-cancel" type="button" class="flex-1 py-3 rounded-xl border border-slate-200 text-slate-700 font-semibold text-sm hover:bg-slate-50 active:scale-98 transition-all text-center">
            취소
          </button>
          <button id="btn-review-submit" type="button" class="flex-1 py-3 rounded-xl bg-primary text-white font-bold text-sm shadow-md hover:bg-opacity-90 active:scale-98 transition-all flex items-center justify-center gap-2">
            <span>후기 제출하기</span>
          </button>
        </div>

      </div>
    </main>
  `

  const revisitCards = container.querySelectorAll('.revisit-card')
  const tagCards = container.querySelectorAll('.tag-card')
  const btnSubmit = container.querySelector('#btn-review-submit')
  const btnCancel = container.querySelector('#btn-review-cancel')

  // 재만남 의향 카드 토글 이벤트
  revisitCards.forEach(card => {
    card.addEventListener('click', () => {
      const val = card.getAttribute('data-revisit')
      selectedRevisit = val

      revisitCards.forEach(c => {
        c.classList.remove('border-primary-container', 'bg-primary-container/5', 'ring-2', 'ring-primary-container/20')
        c.querySelector('.check-icon').classList.add('hidden')
      })

      card.classList.add('border-primary-container', 'bg-primary-container/5', 'ring-2', 'ring-primary-container/20')
      card.querySelector('.check-icon').classList.remove('hidden')
    })
  })

  // 인상 태그 카드 토글 이벤트
  tagCards.forEach(card => {
    card.addEventListener('click', () => {
      const val = card.getAttribute('data-tag')
      
      if (selectedTag === val) {
        selectedTag = null
        card.classList.remove('border-primary-container', 'bg-primary-container/5', 'ring-2', 'ring-primary-container/20')
      } else {
        selectedTag = val
        tagCards.forEach(c => {
          c.classList.remove('border-primary-container', 'bg-primary-container/5', 'ring-2', 'ring-primary-container/20')
        })
        card.classList.add('border-primary-container', 'bg-primary-container/5', 'ring-2', 'ring-primary-container/20')
      }
    })
  })

  // 취소 처리
  btnCancel.addEventListener('click', () => {
    navigateTo('/mypage')
  })

  // 제출 처리
  btnSubmit.addEventListener('click', async () => {
    if (!selectedRevisit) {
      alert('재만남 의향 문항은 필수 선택 항목입니다.')
      return
    }

    if (isSubmitting) return
    isSubmitting = true
    btnSubmit.disabled = true
    btnSubmit.innerHTML = '<span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span> <span>제출 중...</span>'

    try {
      const csrfResp = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' })
      if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
      const readCookie = (name) => {
        const prefix = `${encodeURIComponent(name)}=`
        const cookie = document.cookie.split('; ').find(item => item.startsWith(prefix))
        return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
      }
      const csrfToken = readCookie('XSRF-TOKEN')

      const resp = await fetch(`${API_BASE_URL}/reviews`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrfToken
        },
        body: JSON.stringify({
          matchId: Number(matchId),
          revisitIntention: selectedRevisit,
          impressionTag: selectedTag
        })
      })

      if (resp.ok) {
        alert('소중한 후기가 제출되었습니다!')
        navigateTo('/mypage')
      } else {
        const body = await resp.json()
        alert(body?.error?.message || '후기 제출에 실패했습니다. 이미 후기를 작성하셨거나 기간이 만료되었을 수 있습니다.')
        navigateTo('/mypage')
      }
    } catch (err) {
      alert('오류가 발생했습니다: ' + err.message)
      isSubmitting = false
      btnSubmit.disabled = false
      btnSubmit.innerHTML = '<span>후기 제출하기</span>'
    }
  })
}
