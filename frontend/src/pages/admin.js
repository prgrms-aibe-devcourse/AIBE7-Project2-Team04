import { navigateTo } from '../main.js'

const API_BASE_URL = 'http://localhost:8080'

export async function renderAdminPage(container) {
  container.innerHTML = `
    <main class="w-full max-w-6xl mx-auto my-6 px-4 flex flex-col min-h-[600px] gap-6">
      <!-- Title -->
      <div class="flex items-center justify-between border-b border-slate-200 pb-4">
        <div>
          <h2 class="text-2xl font-extrabold text-brand-navy">🛠️ 관리자 신고 센터</h2>
          <p class="text-xs text-secondary mt-1">접수된 이용자 신고 건들을 모니터링하고 부적절한 회원을 즉각 제재합니다.</p>
        </div>
      </div>

      <!-- Report List Wrapper -->
      <div class="w-full bg-white border border-outline-variant/30 rounded-card p-6 shadow-soft flex flex-col gap-4">
        <h3 class="text-sm font-bold text-slate-700 flex items-center gap-1.5 select-none">
          <span class="material-symbols-outlined text-base">list_alt</span>
          <span>신고 접수 목록</span>
        </h3>
        <div id="admin-reports-container" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="col-span-full text-center text-sm text-secondary py-12">신고 내역을 불러오는 중...</div>
        </div>
      </div>
    </main>
  `

  const loadReports = async () => {
    const reportListContainer = document.querySelector('#admin-reports-container')
    if (!reportListContainer) return

    try {
      const resp = await fetch(`${API_BASE_URL}/admin/reports`, { credentials: 'include' })
      if (!resp.ok) {
        if (resp.status === 403) {
          alert('관리자 권한이 없습니다. 메인 페이지로 이동합니다.')
          navigateTo('/')
          return
        }
        throw new Error('신고 목록을 가져오지 못했습니다.')
      }

      const body = await resp.json()
      const reports = body.success && Array.isArray(body.data) ? body.data : []

      if (reports.length === 0) {
        reportListContainer.innerHTML = '<p class="col-span-full text-sm text-secondary text-center py-12">현재 미해결된 신고 내역이 없습니다.</p>'
        return
      }

      reportListContainer.innerHTML = reports.map(item => `
        <div class="bg-slate-50 border border-slate-200/60 rounded-2xl p-5 flex flex-col gap-3.5 shadow-sm hover:shadow-md transition-shadow">
          <div class="flex justify-between items-start">
            <span class="text-xs px-2.5 py-1 font-bold rounded-full bg-red-50 text-red-600 border border-red-100">${escapeHtml(item.categoryDescription)}</span>
            <span class="text-[11px] text-slate-400">${new Date(item.createdAt).toLocaleString('ko-KR')}</span>
          </div>

          <div class="flex flex-col gap-1.5 my-1 text-sm text-slate-700">
            <div class="flex items-center gap-1.5">
              <span class="font-bold text-brand-navy">신고자:</span>
              <span class="bg-blue-50 text-blue-700 px-2 py-0.5 rounded-lg text-xs font-semibold">${escapeHtml(item.reporterNickname)}</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span class="font-bold text-brand-navy">대상자(피신고자):</span>
              <span class="bg-red-50 text-red-700 px-2 py-0.5 rounded-lg text-xs font-semibold">${escapeHtml(item.reportedUserNickname)}</span>
            </div>
            <p class="mt-2 text-xs font-medium text-slate-600 bg-white border border-slate-200/50 p-3 rounded-xl min-h-[60px] whitespace-pre-wrap">${escapeHtml(item.reason)}</p>
          </div>

          <div class="flex gap-2 border-t border-slate-200/50 pt-3 mt-1">
            <button class="btn-view-chat flex-1 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold rounded-xl transition-colors flex items-center justify-center gap-1.5" data-report-id="${item.id}">
              <span class="material-symbols-outlined text-sm">forum</span>
              <span>채팅 내역 확인</span>
            </button>
            <button class="btn-warn-user py-2 px-3 bg-amber-50 hover:bg-amber-100 text-amber-600 text-xs font-bold rounded-xl transition-colors" data-user-id="${item.reportedUserId}" data-report-id="${item.id}">
              경고
            </button>
            <button class="btn-ban-user py-2 px-3 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-xl transition-colors" data-user-id="${item.reportedUserId}" data-report-id="${item.id}">
              정지
            </button>
            <button class="btn-dismiss-report py-2 px-3 bg-white hover:bg-slate-100 text-slate-500 border border-slate-200 text-xs font-bold rounded-xl transition-colors" data-report-id="${item.id}" aria-label="신고 기각">×</button>
          </div>
        </div>
      `).join('')

      // 버튼 리스너 바인딩
      reportListContainer.querySelectorAll('.btn-view-chat').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const reportId = e.currentTarget.getAttribute('data-report-id')
          const report = reports.find(r => String(r.id) === String(reportId))
          openChatHistoryModal(reportId, report)
        })
      })

      reportListContainer.querySelectorAll('.btn-warn-user').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const userId = e.currentTarget.getAttribute('data-user-id')
          const reportId = e.currentTarget.getAttribute('data-report-id')
          handleReportAction(reportId, 'warn')
        })
      })

      reportListContainer.querySelectorAll('.btn-ban-user').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const userId = e.currentTarget.getAttribute('data-user-id')
          const reportId = e.currentTarget.getAttribute('data-report-id')
          handleReportAction(reportId, 'ban')
        })
      })

      reportListContainer.querySelectorAll('.btn-dismiss-report').forEach(btn => {
        btn.addEventListener('click', (e) => {
          dismissReport(e.currentTarget.getAttribute('data-report-id'))
        })
      })

    } catch (err) {
      reportListContainer.innerHTML = `<p class="col-span-full text-sm text-red-600 text-center py-12">에러 발생: ${err.message}</p>`
    }
  }

  const handleReportAction = async (reportId, actionType) => {
    const actionLabel = actionType === 'ban' ? '영구 정지' : '경고(3회시 정지)';
    if (!confirm(`정말로 해당 회원을 ${actionLabel} 처리하시겠습니까?`)) return

    try {
      const csrfResp = await fetch('/auth/csrf', { credentials: 'include' })
      if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
      const readCookie = (name) => {
        const prefix = `${encodeURIComponent(name)}=`
        const cookie = document.cookie.split('; ').find(item => item.startsWith(prefix))
        return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
      }
      const csrfToken = readCookie('XSRF-TOKEN')

      const resp = await fetch(`${API_BASE_URL}/admin/reports/${reportId}/handle/${actionType}`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'X-XSRF-TOKEN': csrfToken
        }
      })

      if (resp.ok) {
        alert(`성공적으로 회원을 ${actionLabel} 하였습니다.`)
        loadReports()
      } else {
        const body = await resp.json()
        alert(body?.error?.message || `${actionLabel} 처리에 실패했습니다.`)
      }
    } catch (err) {
      alert('처리 중 오류가 발생했습니다: ' + err.message)
    }
  }

  const dismissReport = async (reportId) => {
    if (!confirm('이 신고를 기각하고 목록에서 제거하시겠습니까?')) return

    try {
      const csrfResp = await fetch('/auth/csrf', { credentials: 'include' })
      if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
      const readCookie = (name) => {
        const prefix = `${encodeURIComponent(name)}=`
        const cookie = document.cookie.split('; ').find(item => item.startsWith(prefix))
        return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
      }
      const resp = await fetch(`${API_BASE_URL}/admin/reports/${reportId}/dismiss`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'X-XSRF-TOKEN': readCookie('XSRF-TOKEN') }
      })
      if (!resp.ok) throw new Error('신고 기각 처리에 실패했습니다.')
      await loadReports()
    } catch (err) {
      alert('신고 기각 중 오류가 발생했습니다: ' + err.message)
    }
  }

  const openChatHistoryModal = async (reportId, reportInfo) => {
    const existModal = document.querySelector('#admin-chat-modal')
    if (existModal) existModal.remove()

    const modalHtml = `
      <div id="admin-chat-modal" class="fixed inset-0 bg-slate-900/50 flex items-center justify-center z-50 p-4">
        <div class="bg-[#BACEE0] rounded-2xl w-full max-w-lg h-[600px] shadow-2xl flex flex-col overflow-hidden">
          <!-- Modal Header -->
          <div class="bg-white border-b border-slate-200 p-4 flex items-center justify-between">
            <div>
              <h3 class="font-bold text-brand-navy text-sm sm:text-base">채팅 내역 조회 (수사 모드)</h3>
              <p class="text-[10px] text-secondary mt-0.5">신고자: ${escapeHtml(reportInfo.reporterNickname)} | 대상: ${escapeHtml(reportInfo.reportedUserNickname)}</p>
            </div>
            <button id="btn-close-chat-modal" class="text-slate-400 hover:text-slate-600 flex items-center">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>

          <!-- Chat List Area -->
          <div id="admin-chat-box" class="flex-grow overflow-y-auto p-4 space-y-4">
            <div class="text-center text-xs text-slate-500 py-12">채팅 내역을 가져오는 중...</div>
          </div>
        </div>
      </div>
    `
    document.body.insertAdjacentHTML('beforeend', modalHtml)

    const modal = document.querySelector('#admin-chat-modal')
    modal.querySelector('#btn-close-chat-modal').addEventListener('click', () => modal.remove())

    const chatBox = modal.querySelector('#admin-chat-box')

    try {
      const resp = await fetch(`${API_BASE_URL}/admin/reports/${reportId}/chat-messages`, { credentials: 'include' })
      if (!resp.ok) throw new Error('대화 내용을 가져올 수 없습니다.')
      
      const body = await resp.json()
      const messages = body.success && Array.isArray(body.data) ? body.data : []

      if (messages.length === 0) {
        chatBox.innerHTML = '<div class="text-center text-xs text-slate-500 py-12 bg-white/40 rounded-xl max-w-xs mx-auto">기록된 채팅 내역이 없습니다.</div>'
        return
      }

      chatBox.innerHTML = ''
      messages.forEach(msg => {
        const isReporter = msg.sender === reportInfo.reporterId;
        const senderNickname = isReporter ? reportInfo.reporterNickname : reportInfo.reportedUserNickname;

        const row = document.createElement('div')
        if (isReporter) {
          row.className = 'flex justify-end items-end gap-1.5 mb-1.5 w-full'
          row.innerHTML = `
            <div class="bg-[#FEE500] text-black text-xs p-2.5 rounded-l-2xl rounded-br-2xl max-w-[75%] shadow-sm whitespace-pre-wrap break-words border border-[#E4CE00]/30">${escapeHtml(msg.message)}</div>
            <span class="text-[9px] text-slate-700/60 font-semibold select-none">${escapeHtml(senderNickname)}</span>
          `
        } else {
          row.className = 'flex items-start gap-2.5 mb-1.5 w-full'
          row.innerHTML = `
            <div class="w-8 h-8 rounded-full bg-white flex items-center justify-center text-brand-navy font-bold text-xs shadow-sm">${escapeHtml(senderNickname.trim().charAt(0))}</div>
            <div class="flex flex-col">
              <span class="text-[10px] text-slate-800 mb-0.5 font-semibold">${escapeHtml(senderNickname)}</span>
              <div class="bg-white text-black text-xs p-2.5 rounded-r-2xl rounded-bl-2xl max-w-[75%] shadow-sm whitespace-pre-wrap break-words border border-slate-200">${escapeHtml(msg.message)}</div>
            </div>
          `
        }
        chatBox.appendChild(row)
      })
      chatBox.scrollTop = chatBox.scrollHeight

    } catch (err) {
      chatBox.innerHTML = `<div class="text-center text-xs text-red-600 py-12 bg-white/40 rounded-xl max-w-xs mx-auto">오류 발생: ${err.message}</div>`
    }
  }

  const escapeHtml = (value) => String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')

  await loadReports()
}
