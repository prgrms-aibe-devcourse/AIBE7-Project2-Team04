import { getAccessToken } from '../auth/token-storage.js'
import { getCsrfToken } from '../auth/csrf.js'
import { navigateTo } from '../main.js'
import { getLatestMatchResult } from '../matching/matching-api.js'
import { API_BASE_URL } from '../config/api.js'
import './chat.css'

/**
 * 마주한끼 테마의 1:1 채팅 페이지
 * - project2.isLoggedIn 이 true 인 경우에만 접근 가능
 * - STOMP CONNECT 헤더에 JWT 쿠키를 포함 (HttpOnly 쿠키는 브라우저가 자동 전송)
 * - /app/chat/{roomId}/send 로 전송, /topic/chat/{roomId} 구독
 */
export function renderChatPage(container) {
  // 로그인 여부 확인
  if (!getAccessToken()) {
    navigateTo('/')
    return
  }

  container.innerHTML = `
    <main class="chat-page">
      <section class="chat-shell" aria-labelledby="chat-partner-name">
        <header class="chat-header">
          <button id="btn-back" class="chat-icon-button" type="button" aria-label="채팅방 나가기">
            <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
          </button>

          <div class="chat-partner">
            <div class="chat-partner-avatar" aria-hidden="true">
              <span id="chat-partner-initial">상</span>
              <img id="chat-partner-image" class="is-hidden" alt="" />
            </div>
            <div class="chat-partner-copy">
              <h1 id="chat-partner-name">밥친구와의 대화</h1>
            </div>
          </div>

          <button id="btn-end-match" class="chat-end-button" type="button">
            <span class="material-symbols-outlined" aria-hidden="true">logout</span>
            <span>매칭 종료</span>
          </button>
        </header>

        <div id="chat-box" class="chat-messages" role="log" aria-live="polite" aria-label="채팅 메시지">
          <div class="chat-system-message">
            <span class="material-symbols-outlined" aria-hidden="true">waving_hand</span>
            <span>따뜻한 인사로 대화를 시작해 보세요.</span>
          </div>
        </div>

        <div class="chat-composer">
          <input type="text" id="msg-input" placeholder="메시지를 입력하세요" disabled aria-label="메시지 입력" />
          <button id="btn-send" type="button" disabled aria-label="메시지 전송">
            <span class="material-symbols-outlined" aria-hidden="true">send</span>
          </button>
        </div>
      </section>
    </main>
  `

  let roomIdFromQuery = new URLSearchParams(window.location.search).get('roomId')
  // ── 상태 변수 ──────────────────────────────────────────────────────────────
  let stompClient  = null
  let subscription = null
  let connectedRoomId = null
  let myUserId     = null
  let partnerNickname = '상대방'
  let partnerUserId = null
  let partnerProfileImg = null
  let matchId      = null

  // ── 메시지 말풍선 렌더링 ───────────────────────────────────────────────────
  function appendChatMessage(senderId, nickname, message, isMine, customTime = null) {
    const box = document.getElementById('chat-box')
    if (!box) return

    const row = document.createElement('div')
    const timeVal = customTime ? new Date(customTime) : new Date()
    const timeStr = timeVal.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    
    row.className = `chat-message-row ${isMine ? 'is-mine' : 'is-partner'}`

    if (isMine) {
      row.innerHTML = `
        <time class="chat-message-time">${timeStr}</time>
        <div class="chat-message-bubble">${escapeHtml(message)}</div>
      `
    } else {
      const initial = escapeHtml(nickname ? nickname.trim().charAt(0) : '상')

      row.innerHTML = `
        <div class="chat-message-avatar" aria-hidden="true">
          <span>${initial}</span>
          ${partnerProfileImg ? `<img data-chat-avatar-image src="${escapeHtml(partnerProfileImg)}" alt="" />` : ''}
        </div>
        <div class="chat-message-content">
          <span class="chat-message-name">${escapeHtml(nickname || '상대방')}</span>
          <div class="chat-message-line">
            <div class="chat-message-bubble">${escapeHtml(message)}</div>
            <time class="chat-message-time">${timeStr}</time>
          </div>
        </div>
      `

      row.querySelector('[data-chat-avatar-image]')?.addEventListener('error', (event) => {
        event.currentTarget.remove()
      }, { once: true })
    }

    box.appendChild(row)
    box.scrollTop = box.scrollHeight
  }

  function appendSystemMessage(text, color = 'slate-600') {
    const box = document.getElementById('chat-box')
    if (!box) return

    const line = document.createElement('div')
    const tone = color.startsWith('red') ? 'is-error' : color.startsWith('green') ? 'is-success' : ''
    line.className = `chat-system-message ${tone}`.trim()
    line.textContent = text
    box.appendChild(line)
    box.scrollTop = box.scrollHeight
  }

  function escapeHtml(str) {
    if (!str) return ''
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;')
  }

  // ── CONNECT + SUBSCRIBE ────────────────────────────────────────────────────
  function connect() {
    const roomId = roomIdFromQuery
    if (!/^\d+$/.test(roomId || '')) return
    connectedRoomId = roomId
    setConnectionStatus('연결 중…', 'connecting')

    const SockJS = window.SockJS
    const Stomp  = window.Stomp

    if (!SockJS || !Stomp) {
      appendSystemMessage('SockJS / STOMP 라이브러리가 로드되지 않았습니다.', 'red-700')
      return
    }

    const socket = new SockJS(`${API_BASE_URL}/ws-chat`)
    stompClient  = Stomp.over(socket)
    stompClient.debug = null

    stompClient.connect({}, function () {
      appendSystemMessage('채팅방 연결 성공!', 'green-700')
      setConnectionStatus('대화할 수 있어요', 'connected')

      const btnEndMatch = document.getElementById('btn-end-match')
      if (btnEndMatch) btnEndMatch.disabled = false

      // 1. 이전 대화 내역 불러오기
      fetch(`${API_BASE_URL}/chatrooms/${roomId}/messages?size=50`, { credentials: 'include' })
        .then(r => r.json())
        .then(body => {
          if (body.success && body.data && body.data.content) {
            const box = document.getElementById('chat-box')
            if (box) box.innerHTML = '' // 초기 환영 메시지 삭제

            body.data.content.forEach(msg => {
              const isMine = msg.senderId === myUserId
              const nickname = isMine ? '나' : (msg.senderId === partnerUserId ? partnerNickname : '상대방')
              appendChatMessage(msg.senderId, nickname, msg.content, isMine, msg.sentAt)
            })
            appendSystemMessage('이전 대화 내역을 불러왔습니다.')
          }
        })
        .catch(err => {
          console.warn('이전 대화 내역 조회 실패:', err)
        })

      // 2. SUBSCRIBE (실시간 메시지 구독)
      subscription = stompClient.subscribe(`/topic/chat/${roomId}`, function (msg) {
        const body   = JSON.parse(msg.body)

        if (body.message === '[SYSTEM] MATCH_CLOSED') {
          const senderIdStr = String(body.sender || '').toLowerCase().trim();
          const myUserIdStr = String(myUserId || '').toLowerCase().trim();

          console.log('[DEBUG] MATCH_CLOSED event:', { senderIdStr, myUserIdStr });

          if (senderIdStr !== myUserIdStr) {
            alert('상대방이 매칭을 종료하여 대화가 종료되었습니다.')
            sessionStorage.removeItem('project2.latestMatchResult')
            document.documentElement.classList.remove('has-cached-chat')
            navigateTo('/')
          } else {
            // 당사자 리다이렉트 동기화
            navigateTo('/')
          }
          return
        }

        const isMine = body.sender === myUserId
        const nickname = isMine ? '나' : (body.sender === partnerUserId ? partnerNickname : '상대방')
        appendChatMessage(body.sender, nickname, body.message, isMine)
      })

      appendSystemMessage(`채팅방 ${roomId}번에 입장하였습니다.`)
      document.getElementById('msg-input').disabled = false
      document.getElementById('btn-send').disabled  = false
    }, function (err) {
      appendSystemMessage('연결 실패: ' + err, 'red-700')
      setConnectionStatus('연결 실패', 'error')
    })
  }

  // ── DISCONNECT ─────────────────────────────────────────────────────────────
  function disconnect() {
    if (stompClient) {
      if (subscription) subscription.unsubscribe()
      stompClient.disconnect(() => {
        appendSystemMessage('채팅방 연결이 종료되었습니다.', 'slate-500')
        resetUI()
      })
    }
  }

  // ── SEND ───────────────────────────────────────────────────────────────────
  function sendMessage() {
    const roomId  = roomIdFromQuery
    const message = document.getElementById('msg-input').value.trim()
    if (!message || !stompClient) return

    stompClient.send(
      `/app/chat/${roomId}/send`,
      {},
      JSON.stringify({ roomId: Number(roomId), message })
    )
    document.getElementById('msg-input').value = ''
  }

  // ── UI 초기화 ──────────────────────────────────────────────────────────────
  function resetUI() {
    setConnectionStatus('미연결')
    const btnEndMatch = document.getElementById('btn-end-match')
    if (btnEndMatch) btnEndMatch.disabled = false
    document.getElementById('msg-input').disabled       = true
    document.getElementById('btn-send').disabled        = true
    stompClient  = null
    subscription = null
  }

  function setConnectionStatus(text, state = '') {
    const status = document.getElementById('disp-status')
    const indicator = document.getElementById('connection-indicator')
    if (status) status.textContent = text
    if (indicator) indicator.className = `chat-connection-dot ${state ? `is-${state}` : ''}`.trim()
  }

  function updatePartnerHeader() {
    const name = document.getElementById('chat-partner-name')
    const initial = document.getElementById('chat-partner-initial')
    const image = document.getElementById('chat-partner-image')
    if (name) name.textContent = `${partnerNickname}님과의 대화`
    if (initial) initial.textContent = partnerNickname.trim().charAt(0) || '상'
    if (!image || !partnerProfileImg) return

    image.src = partnerProfileImg
    image.classList.remove('is-hidden')
    image.addEventListener('error', () => image.classList.add('is-hidden'), { once: true })
  }

  // ── 이벤트 바인딩 ──────────────────────────────────────────────────────────
  document.getElementById('btn-back').addEventListener('click', () => navigateTo('/'))
  document.getElementById('btn-send').addEventListener('click', sendMessage)
  document.getElementById('msg-input').addEventListener('keydown', e => {
    if (e.key === 'Enter') sendMessage()
  })

  // 매칭 종료 이벤트 바인딩
  document.getElementById('btn-end-match')?.addEventListener('click', async () => {
    const targetRoomId = roomIdFromQuery
    if (!matchId && !targetRoomId) {
      alert('매칭 정보 또는 채팅방 정보를 식별할 수 없습니다.')
      return
    }
    if (!confirm('정말로 매칭을 종료하시겠습니까?\n종료 시 상대방과의 채팅방도 함께 폐쇄됩니다.')) return

    try {
      const csrfToken = await getCsrfToken()

      const url = matchId
        ? `${API_BASE_URL}/matches/${matchId}/end`
        : `${API_BASE_URL}/matches/chatroom/${targetRoomId}/end`

      const resp = await fetch(url, {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'X-XSRF-TOKEN': csrfToken
        }
      })

      if (resp.ok) {
        sessionStorage.removeItem('project2.latestMatchResult')
        document.documentElement.classList.remove('has-cached-chat')
        alert('매칭이 성공적으로 종료되었습니다.')
        navigateTo('/')
      } else {
        const body = await resp.json()
        alert(body?.error?.message || '매칭 종료 처리에 실패했습니다.')
      }
    } catch (err) {
      alert('오류가 발생했습니다: ' + err.message)
    }
  })

  // ── 사용자 정보 및 매칭 파트너 정보 먼저 가져오기 ──────────────────────────
  Promise.all([
    fetch(`${API_BASE_URL}/users/me`, { credentials: 'include' })
      .then(r => r.json())
      .catch(() => null),
    getLatestMatchResult()
      .catch(() => null)
  ]).then(([userBody, matchResult]) => {
    if (userBody && userBody.success && userBody.data) {
      myUserId = userBody.data.userId
    }

    if (matchResult && matchResult.partner) {
      matchId = matchResult.matchId
      partnerNickname = matchResult.partner.nickname || '상대방'
      partnerUserId = matchResult.partner.userId
      partnerProfileImg = matchResult.partner.profileImageUrl
      updatePartnerHeader()
    }

    // 정보를 다 확보한 상태에서 자동으로 입장 처리 진행
    if (/^\d+$/.test(roomIdFromQuery || '')) {
      connect()
    }
  })
}
