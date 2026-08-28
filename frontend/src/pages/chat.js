import { getAccessToken } from '../auth/token-storage.js'
import { navigateTo } from '../main.js'

/**
 * 채팅 테스트 페이지.
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
    <main style="max-width:600px; margin:40px auto; padding:0 16px;">
      <h2>채팅 테스트</h2>
      <p><button id="btn-back" style="cursor:pointer;">← 뒤로가기</button></p>
      <hr>

      <div>
        <b>로그인 유저 ID:</b> <span id="disp-user-id">불러오는 중...</span><br>
        <b>채팅방 ID:</b>       <span id="disp-room-id">-</span><br>
        <b>연결 상태:</b>       <span id="disp-status">미연결</span>
      </div>
      <hr>

      <div>
        <label>채팅방 ID: <input type="number" id="room-id-input" value="1" min="1" style="width:80px;"></label>
        <button id="btn-connect">채팅방 입장</button>
        <button id="btn-disconnect" disabled>연결 끊기</button>
      </div>
      <br>

      <div>
        <input type="text" id="msg-input" placeholder="메시지 입력" style="width:300px;" disabled>
        <button id="btn-send" disabled>전송</button>
      </div>
      <br>

      <b>채팅 내역</b>
      <div id="chat-box" style="border:1px solid #aaa; height:300px; overflow-y:scroll; padding:8px; background:#fafafa;"></div>
    </main>
  `

  // ── 상태 변수 ──────────────────────────────────────────────────────────────
  let stompClient  = null
  let subscription = null
  let myUserId     = null

  // ── 현재 유저 ID 조회 (/users/me) ──────────────────────────────────────────
  fetch('/users/me', { credentials: 'include' })
    .then(r => r.json())
    .then(body => {
      if (body.success && body.data) {
        myUserId = body.data.userId
        document.getElementById('disp-user-id').textContent = myUserId
      } else {
        document.getElementById('disp-user-id').textContent = '(조회 실패)'
      }
    })
    .catch(() => {
      document.getElementById('disp-user-id').textContent = '(조회 실패)'
    })

  // ── 로그 출력 ──────────────────────────────────────────────────────────────
  function log(text, color) {
    const box  = document.getElementById('chat-box')
    const line = document.createElement('div')
    if (color) line.style.color = color
    line.textContent = `[${new Date().toLocaleTimeString()}] ${text}`
    box.appendChild(line)
    box.scrollTop = box.scrollHeight
  }

  // ── CONNECT + SUBSCRIBE ────────────────────────────────────────────────────
  function connect() {
    const roomId = document.getElementById('room-id-input').value
    document.getElementById('disp-room-id').textContent = roomId
    document.getElementById('disp-status').textContent  = '연결 중...'

    // SockJS + STOMP (CDN 전역 객체 사용)
    const SockJS = window.SockJS
    const Stomp  = window.Stomp

    if (!SockJS || !Stomp) {
      log('SockJS / STOMP 라이브러리가 로드되지 않았습니다.', 'red')
      return
    }

    const socket = new SockJS('/ws-chat')
    stompClient  = Stomp.over(socket)
    stompClient.debug = null

    // CONNECT: 쿠키 기반 인증 (HttpOnly JWT 쿠키는 브라우저가 자동 전송)
    // 서버의 ChatSubscriptionInterceptor 가 CONNECT 프레임 헤더를 읽음
    stompClient.connect({}, function () {
      log('연결 성공!', 'green')
      document.getElementById('disp-status').textContent = '연결됨'
      document.getElementById('btn-connect').disabled    = true
      document.getElementById('btn-disconnect').disabled = false

      // SUBSCRIBE
      subscription = stompClient.subscribe(`/topic/chat/${roomId}`, function (msg) {
        const body   = JSON.parse(msg.body)
        const isMine = body.sender === myUserId
        log(
          (isMine ? '▶ 나' : `◀ ${body.sender}`) + ': ' + body.message,
          isMine ? 'blue' : 'black'
        )
      })

      log(`채팅방 ${roomId} 입장 완료.`)
      document.getElementById('msg-input').disabled = false
      document.getElementById('btn-send').disabled  = false
    }, function (err) {
      log('연결 실패: ' + err, 'red')
      document.getElementById('disp-status').textContent = '연결 실패'
    })
  }

  // ── DISCONNECT ─────────────────────────────────────────────────────────────
  function disconnect() {
    if (stompClient) {
      if (subscription) subscription.unsubscribe()
      stompClient.disconnect(() => {
        log('연결 해제됨.', 'gray')
        resetUI()
      })
    }
  }

  // ── SEND ───────────────────────────────────────────────────────────────────
  function sendMessage() {
    const roomId  = document.getElementById('room-id-input').value
    const message = document.getElementById('msg-input').value.trim()
    if (!message || !stompClient) return

    stompClient.send(
      `/app/chat/${roomId}/send`,
      {},
      JSON.stringify({ message })
    )
    document.getElementById('msg-input').value = ''
  }

  // ── UI 초기화 ──────────────────────────────────────────────────────────────
  function resetUI() {
    document.getElementById('disp-status').textContent  = '미연결'
    document.getElementById('disp-room-id').textContent = '-'
    document.getElementById('btn-connect').disabled     = false
    document.getElementById('btn-disconnect').disabled  = true
    document.getElementById('msg-input').disabled       = true
    document.getElementById('btn-send').disabled        = true
    stompClient  = null
    subscription = null
  }

  // ── 이벤트 바인딩 ──────────────────────────────────────────────────────────
  document.getElementById('btn-back').addEventListener('click', () => navigateTo('/'))
  document.getElementById('btn-connect').addEventListener('click', connect)
  document.getElementById('btn-disconnect').addEventListener('click', disconnect)
  document.getElementById('btn-send').addEventListener('click', sendMessage)
  document.getElementById('msg-input').addEventListener('keydown', e => {
    if (e.key === 'Enter') sendMessage()
  })
}
