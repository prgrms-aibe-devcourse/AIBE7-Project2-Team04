import { getAccessToken } from '../auth/token-storage.js'
import { getCsrfToken } from '../auth/csrf.js'
import { navigateTo } from '../main.js'
import { getLatestMatchResult } from '../matching/matching-api.js'
import { API_BASE_URL } from '../config/api.js'
import './chat.css'

const FOOD_CATEGORY_DETAILS = {
  KOREAN: { label: '한식', groupCode: 'FD6', searchKeywords: ['한식'], categoryKeywords: ['한식'] },
  JAPANESE: {
    label: '일식', groupCode: 'FD6', searchKeywords: ['일식', '일본음식'], categoryKeywords: ['일식', '일본음식'],
  },
  CHINESE: {
    label: '중식', groupCode: 'FD6', searchKeywords: ['중식', '중국요리'], categoryKeywords: ['중식', '중국요리'],
  },
  WESTERN: {
    label: '양식',
    groupCode: 'FD6',
    searchKeywords: ['양식', '이탈리안', '스테이크'],
    categoryKeywords: ['양식', '이탈리안', '프랑스음식', '스테이크,립', '패밀리레스토랑'],
  },
  SOUTHEAST_ASIAN: {
    label: '동남아 음식',
    groupCode: 'FD6',
    searchKeywords: ['동남아 음식', '베트남 음식', '태국 음식', '인도 음식'],
    categoryKeywords: ['동남아음식', '아시아음식', '베트남음식', '태국음식', '인도음식'],
  },
  SNACK: { label: '분식', groupCode: 'FD6', searchKeywords: ['분식'], categoryKeywords: ['분식'] },
  FAST_FOOD: {
    label: '패스트푸드', groupCode: 'FD6', searchKeywords: ['패스트푸드'], categoryKeywords: ['패스트푸드'],
  },
  CAFE_DESSERT: {
    label: '카페·디저트', groupCode: 'CE7', searchKeywords: ['카페', '디저트'], categoryKeywords: ['카페'],
  },
}

const MAX_RESTAURANT_SEARCH_PAGES = 3
const MAX_DISPLAYED_RESTAURANTS = 10
const MAX_RESTAURANTS_PER_CATEGORY = 5
const PRIMARY_RESTAURANT_SEARCH_RADIUS_METERS = 1500
const EXPANDED_RESTAURANT_SEARCH_RADIUS_METERS = 3000

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
      <div class="chat-layout">
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

        <aside class="chat-map-panel" aria-labelledby="chat-map-title">
          <header class="chat-map-header">
            <div>
              <p class="chat-map-eyebrow">약속 위치 맞추기</p>
              <h2 id="chat-map-title">서로 선택한 위치</h2>
            </div>
            <span class="chat-map-header-icon material-symbols-outlined" aria-hidden="true">map</span>
          </header>

          <div class="chat-map-canvas-wrap">
            <div id="chat-location-map" class="chat-map-canvas" aria-label="양쪽 사용자의 희망 매칭 위치 지도"></div>
            <div id="chat-restaurant-legend" class="chat-restaurant-legend is-hidden" aria-label="식당 후보 범례">
              <span class="is-mine">
                <span class="material-symbols-outlined" aria-hidden="true">restaurant</span>
                <span id="chat-my-food-label">내 메뉴</span>
              </span>
              <span class="is-partner">
                <span class="material-symbols-outlined" aria-hidden="true">restaurant</span>
                <span id="chat-partner-food-label">상대 메뉴</span>
              </span>
            </div>
            <div id="chat-restaurant-status" class="chat-restaurant-status is-hidden"></div>
            <div id="chat-map-status" class="chat-map-status">
              <span class="material-symbols-outlined" aria-hidden="true">progress_activity</span>
              <span>희망 위치를 불러오고 있어요.</span>
            </div>
          </div>

          <div class="chat-location-list">
            <article class="chat-location-card is-mine">
              <div>
                <p>내가 선택한 위치</p>
                <strong id="chat-my-location-name">확인 중</strong>
                <span id="chat-my-region-name"></span>
              </div>
            </article>
            <article class="chat-location-card is-partner">
              <div>
                <p><span id="chat-location-partner-name">상대방</span>님이 선택한 위치</p>
                <strong id="chat-partner-location-name">확인 중</strong>
                <span id="chat-partner-region-name"></span>
              </div>
            </article>
          </div>

          <p class="chat-map-notice">
            <span class="material-symbols-outlined" aria-hidden="true">info</span>
            실시간 위치가 아니라 매칭 요청 시 각자 선택한 희망 장소예요.
          </p>
        </aside>
      </div>
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
  let desiredLocations = null
  let locationMap = null
  let activePlaceOverlay = null

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

  function appendPlaceMessage(senderId, nickname, place, isMine, customTime = null) {
    const box = document.getElementById('chat-box')
    if (!box || !isValidSharedPlace(place)) return

    const row = document.createElement('div')
    const timeVal = customTime ? new Date(customTime) : new Date()
    const timeStr = timeVal.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    row.className = `chat-message-row chat-place-message-row ${isMine ? 'is-mine' : 'is-partner'}`

    const card = createPlaceMessageCard(place)
    const time = document.createElement('time')
    time.className = 'chat-message-time'
    time.textContent = timeStr

    if (isMine) {
      row.append(time, card)
    } else {
      const avatar = document.createElement('div')
      avatar.className = 'chat-message-avatar'
      avatar.setAttribute('aria-hidden', 'true')
      const initial = document.createElement('span')
      initial.textContent = nickname ? nickname.trim().charAt(0) : '상'
      avatar.append(initial)
      if (partnerProfileImg) {
        const image = document.createElement('img')
        image.src = partnerProfileImg
        image.alt = ''
        image.addEventListener('error', () => image.remove(), { once: true })
        avatar.append(image)
      }

      const content = document.createElement('div')
      content.className = 'chat-message-content'
      const name = document.createElement('span')
      name.className = 'chat-message-name'
      name.textContent = nickname || '상대방'
      const line = document.createElement('div')
      line.className = 'chat-message-line'
      line.append(card, time)
      content.append(name, line)
      row.append(avatar, content)
    }

    box.appendChild(row)
    box.scrollTop = box.scrollHeight
  }

  function createPlaceMessageCard(place) {
    const card = document.createElement('article')
    card.className = 'chat-place-message-card'

    const eyebrow = document.createElement('span')
    eyebrow.className = 'chat-place-message-eyebrow'
    eyebrow.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true">restaurant</span> 식당 공유'
    const name = document.createElement('strong')
    name.textContent = place.name
    const category = document.createElement('span')
    category.className = 'chat-place-message-category'
    category.textContent = place.category
    const address = document.createElement('p')
    address.textContent = place.address
    const actions = document.createElement('div')
    actions.className = 'chat-place-message-actions'
    const focusButton = document.createElement('button')
    focusButton.type = 'button'
    focusButton.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true">location_on</span> 지도에서 보기'
    focusButton.addEventListener('click', () => focusSharedPlaceOnMap(place))
    actions.append(focusButton)

    const safePlaceUrl = getSafePlaceUrl(place)
    if (safePlaceUrl) {
      const link = document.createElement('a')
      link.href = safePlaceUrl
      link.target = '_blank'
      link.rel = 'noopener noreferrer'
      link.setAttribute('aria-label', `${place.name} 카카오맵에서 열기`)
      link.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true">open_in_new</span>'
      actions.append(link)
    }

    card.append(eyebrow, name, category, address, actions)
    return card
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
              if (msg.messageType === 'PLACE' && msg.place) {
                appendPlaceMessage(msg.senderId, nickname, msg.place, isMine, msg.sentAt)
              } else {
                appendChatMessage(msg.senderId, nickname, msg.content, isMine, msg.sentAt)
              }
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
        if (body.messageType === 'PLACE' && body.place) {
          appendPlaceMessage(body.sender, nickname, body.place, isMine)
        } else {
          appendChatMessage(body.sender, nickname, body.message, isMine)
        }
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

  function sendPlaceMessage(candidate) {
    const roomId = roomIdFromQuery
    const place = candidate?.place
    if (!stompClient || !/^\d+$/.test(roomId || '') || !place?.id) {
      showRestaurantStatus('채팅 연결 후 식당을 공유할 수 있어요.', true)
      return
    }

    const address = place.road_address_name || place.address_name
    const latitude = Number(place.y)
    const longitude = Number(place.x)
    if (!address || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      showRestaurantStatus('공유할 식당 정보가 충분하지 않아요.', true)
      return
    }

    stompClient.send(
      `/app/chat/${roomId}/send`,
      {},
      JSON.stringify({
        roomId: Number(roomId),
        messageType: 'PLACE',
        place: {
          providerPlaceId: String(place.id),
          name: place.place_name,
          category: place.category_name || '음식점',
          address,
          latitude,
          longitude,
        },
      }),
    )
    showRestaurantStatus(`${place.place_name}을(를) 채팅에 공유했어요.`)
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

  function renderDesiredLocations(locations) {
    desiredLocations = locations
    const mine = locations?.mine
    const partner = locations?.partner
    const partnerLabel = document.getElementById('chat-location-partner-name')
    if (partnerLabel) partnerLabel.textContent = partnerNickname

    if (!isValidLocation(mine) || !isValidLocation(partner)) {
      showMapStatus('희망 위치 정보를 확인할 수 없어요.', 'location_off')
      return
    }

    setText('chat-my-location-name', mine.locationName || mine.regionName)
    setText('chat-my-region-name', mine.locationName ? mine.regionName : '')
    setText('chat-partner-location-name', partner.locationName || partner.regionName)
    setText('chat-partner-region-name', partner.locationName ? partner.regionName : '')
    initializeLocationMap(mine, partner)
  }

  function initializeLocationMap(mine, partner) {
    let attempts = 0
    const waitForKakao = setInterval(() => {
      attempts += 1
      if (window.kakao?.maps) {
        clearInterval(waitForKakao)
        window.kakao.maps.load(() => createLocationMap(mine, partner))
        return
      }
      if (attempts >= 50) {
        clearInterval(waitForKakao)
        showMapStatus('지도를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', 'map_off')
      }
    }, 100)
  }

  function createLocationMap(mine, partner) {
    const mapContainer = document.getElementById('chat-location-map')
    if (!mapContainer || !desiredLocations) return

    const kakaoMaps = window.kakao.maps
    const myPosition = new kakaoMaps.LatLng(mine.latitude, mine.longitude)
    const partnerPosition = new kakaoMaps.LatLng(partner.latitude, partner.longitude)
    locationMap = new kakaoMaps.Map(mapContainer, {
      center: myPosition,
      level: 5,
    })

    const positionsOverlap = Math.abs(mine.latitude - partner.latitude) < 0.000001
      && Math.abs(mine.longitude - partner.longitude) < 0.000001
    addLocationMarker(
      locationMap,
      myPosition,
      '나',
      `is-mine ${positionsOverlap ? 'is-overlap-left' : ''}`.trim(),
    )
    addLocationMarker(
      locationMap,
      partnerPosition,
      partnerNickname,
      `is-partner ${positionsOverlap ? 'is-overlap-right' : ''}`.trim(),
    )

    if (positionsOverlap) {
      locationMap.setCenter(myPosition)
      locationMap.setLevel(4)
    } else {
      const bounds = new kakaoMaps.LatLngBounds()
      bounds.extend(myPosition)
      bounds.extend(partnerPosition)
      locationMap.setBounds(bounds, 70, 70, 70, 70)
    }
    locationMap.relayout()
    hideMapStatus()
    searchNearbyRestaurants(mine, partner)
  }

  function addLocationMarker(map, position, label, modifier) {
    const marker = document.createElement('div')
    marker.className = `chat-map-marker ${modifier}`
    const pin = document.createElement('span')
    pin.className = 'chat-map-marker-pin'
    pin.setAttribute('aria-hidden', 'true')
    const copy = document.createElement('span')
    copy.className = 'chat-map-marker-label'
    copy.textContent = label
    marker.append(pin, copy)

    new window.kakao.maps.CustomOverlay({
      map,
      position,
      content: marker,
      xAnchor: 0.5,
      yAnchor: 1,
      zIndex: modifier === 'is-mine' ? 3 : 2,
    })
  }

  async function searchNearbyRestaurants(mine, partner) {
    const mineCategory = FOOD_CATEGORY_DETAILS[mine.foodCategory]
    const partnerCategory = FOOD_CATEGORY_DETAILS[partner.foodCategory]
    if (!mineCategory || !partnerCategory || !window.kakao?.maps?.services?.Places) {
      showRestaurantStatus('주변 식당 후보를 불러올 수 없어요.', true)
      return
    }

    setText('chat-my-food-label', `내 메뉴 · ${mineCategory.label}`)
    setText('chat-partner-food-label', `${partnerNickname}님 메뉴 · ${partnerCategory.label}`)
    document.getElementById('chat-restaurant-legend')?.classList.remove('is-hidden')
    showRestaurantStatus('중간 지점 주변 식당을 찾고 있어요.')

    const midpoint = {
      latitude: (Number(mine.latitude) + Number(partner.latitude)) / 2,
      longitude: (Number(mine.longitude) + Number(partner.longitude)) / 2,
    }
    const center = new window.kakao.maps.LatLng(midpoint.latitude, midpoint.longitude)
    const searches = mine.foodCategory === partner.foodCategory
      ? [{ ...mineCategory, owners: ['mine', 'partner'] }]
      : [
          { ...mineCategory, owners: ['mine'] },
          { ...partnerCategory, owners: ['partner'] },
        ]

    try {
      let appliedSearchRadius = PRIMARY_RESTAURANT_SEARCH_RADIUS_METERS
      let results = await searchRestaurantCandidates(searches, center, appliedSearchRadius)

      if (hasInsufficientCategoryCandidates(results)) {
        appliedSearchRadius = EXPANDED_RESTAURANT_SEARCH_RADIUS_METERS
        results = await searchRestaurantCandidates(searches, center, appliedSearchRadius)
      }

      const candidates = selectRestaurantCandidates(
        results,
        mine,
        partner,
        midpoint,
        appliedSearchRadius,
      )
      if (!candidates.length) {
        showRestaurantStatus('반경 3km 안에서 식당 후보를 찾지 못했어요.', true)
        return
      }

      const bounds = new window.kakao.maps.LatLngBounds()
      bounds.extend(new window.kakao.maps.LatLng(mine.latitude, mine.longitude))
      bounds.extend(new window.kakao.maps.LatLng(partner.latitude, partner.longitude))
      candidates.forEach(candidate => {
        const position = new window.kakao.maps.LatLng(candidate.place.y, candidate.place.x)
        bounds.extend(position)
        addRestaurantMarker(candidate, position)
      })
      locationMap.setBounds(bounds, 80, 80, 80, 80)
      showRestaurantStatus(
        `중간 지점 반경 ${formatSearchRadius(appliedSearchRadius)} · 식당 후보 ${candidates.length}곳`,
      )
    } catch {
      showRestaurantStatus('주변 식당 후보를 불러오지 못했어요.', true)
    }
  }

  function searchRestaurantCandidates(searches, center, searchRadius) {
    return Promise.all(
      searches.map(search => searchCategoryPlaces(search, center, searchRadius)
        .then(places => ({
          search,
          entries: places.map(place => ({ place, owners: search.owners })),
        }))),
    )
  }

  async function searchCategoryPlaces(category, location, searchRadius) {
    const keywordResults = await Promise.all(
      category.searchKeywords.map(keyword => searchKeywordPlaces(category, keyword, location, searchRadius)),
    )
    const placesById = new Map()
    keywordResults.flat().forEach(place => {
      const placeKey = place.id || `${place.x}:${place.y}:${place.place_name}`
      if (!placesById.has(placeKey)) placesById.set(placeKey, place)
    })
    return Array.from(placesById.values())
  }

  function searchKeywordPlaces(category, keyword, location, searchRadius) {
    return new Promise((resolve, reject) => {
      const places = new window.kakao.maps.services.Places()
      const matchedPlaces = []
      const seenPlaceIds = new Set()
      let searchedPageCount = 0

      const handleResult = (result, status, pagination) => {
        if (status === window.kakao.maps.services.Status.OK) {
          searchedPageCount += 1
          result.forEach(place => {
            const placeKey = place.id || `${place.x}:${place.y}:${place.place_name}`
            if (seenPlaceIds.has(placeKey)
                || !matchesFoodCategory(place, category)
                || !hasSufficientPlaceInformation(place)) return
            seenPlaceIds.add(placeKey)
            matchedPlaces.push(place)
          })

          if (searchedPageCount >= MAX_RESTAURANT_SEARCH_PAGES
              || !pagination?.hasNextPage) {
            resolve(matchedPlaces)
            return
          }
          pagination.nextPage()
          return
        }
        if (status === window.kakao.maps.services.Status.ZERO_RESULT) {
          resolve(matchedPlaces)
          return
        }
        reject(new Error('장소 검색에 실패했습니다.'))
      }

      places.keywordSearch(keyword, handleResult, {
        location,
        radius: searchRadius,
        size: 15,
        category_group_code: category.groupCode,
        sort: window.kakao.maps.services.SortBy.ACCURACY,
      })
    })
  }

  function hasInsufficientCategoryCandidates(results) {
    return results.some(result => {
      const targetCount = result.search.owners.length > 1
        ? MAX_DISPLAYED_RESTAURANTS
        : MAX_RESTAURANTS_PER_CATEGORY
      return result.entries.length < targetCount
    })
  }

  function selectRestaurantCandidates(results, mine, partner, midpoint, searchRadius) {
    const perCategoryLimit = results.length === 1
      ? MAX_DISPLAYED_RESTAURANTS
      : MAX_RESTAURANTS_PER_CATEGORY
    const selectedEntries = results.flatMap(result => rankPlaceCandidates(
      result.entries,
      mine,
      partner,
      midpoint,
      searchRadius,
    ).slice(0, perCategoryLimit))

    return rankPlaceCandidates(
      mergePlaceCandidates(selectedEntries),
      mine,
      partner,
      midpoint,
      searchRadius,
    ).slice(0, MAX_DISPLAYED_RESTAURANTS)
  }

  function matchesFoodCategory(place, category) {
    if (!category.categoryKeywords.length) return true
    const categoryName = String(place.category_name || '').replaceAll(' ', '')
    return category.categoryKeywords.some(keyword => categoryName.includes(keyword))
  }

  function hasSufficientPlaceInformation(place) {
    const availableFieldCount = [place.road_address_name, place.phone, place.place_url]
      .filter(value => String(value || '').trim()).length
    return availableFieldCount >= 2
  }

  function rankPlaceCandidates(candidates, mine, partner, midpoint, searchRadius) {
    const distanceBetweenUsers = distanceMeters(mine, partner)
    return candidates
      .map(candidate => {
        const placeLocation = {
          latitude: Number(candidate.place.y),
          longitude: Number(candidate.place.x),
        }
        const midpointDistance = distanceMeters(placeLocation, midpoint)
        const mineDistance = distanceMeters(placeLocation, mine)
        const partnerDistance = distanceMeters(placeLocation, partner)
        const distanceBalanceDifference = Math.abs(mineDistance - partnerDistance)
        const informationFieldCount = [
          candidate.place.road_address_name,
          candidate.place.phone,
          candidate.place.place_url,
        ].filter(value => String(value || '').trim()).length

        const categoryScore = 40
        const midpointScore = Math.max(
          0,
          1 - midpointDistance / searchRadius,
        ) * 30
        const balanceScore = Math.max(
          0,
          1 - distanceBalanceDifference / Math.max(distanceBetweenUsers, searchRadius),
        ) * 20
        const informationScore = (informationFieldCount / 3) * 10

        return {
          ...candidate,
          rankingScore: categoryScore + midpointScore + balanceScore + informationScore,
          midpointDistance,
        }
      })
      .sort((first, second) => second.rankingScore - first.rankingScore
        || first.midpointDistance - second.midpointDistance
        || String(first.place.id || '').localeCompare(String(second.place.id || '')))
  }

  function formatSearchRadius(searchRadius) {
    return `${searchRadius / 1000}km`
  }

  function distanceMeters(first, second) {
    const earthRadiusMeters = 6371000
    const toRadians = degrees => degrees * Math.PI / 180
    const firstLatitude = toRadians(Number(first.latitude))
    const secondLatitude = toRadians(Number(second.latitude))
    const latitudeDifference = secondLatitude - firstLatitude
    const longitudeDifference = toRadians(Number(second.longitude) - Number(first.longitude))
    const haversine = Math.sin(latitudeDifference / 2) ** 2
      + Math.cos(firstLatitude) * Math.cos(secondLatitude)
      * Math.sin(longitudeDifference / 2) ** 2
    const clampedHaversine = Math.min(1, Math.max(0, haversine))
    return earthRadiusMeters * 2
      * Math.atan2(Math.sqrt(clampedHaversine), Math.sqrt(1 - clampedHaversine))
  }

  function mergePlaceCandidates(entries) {
    const placesById = new Map()
    entries.forEach(({ place, owners }) => {
      const key = place.id || `${place.x}:${place.y}:${place.place_name}`
      const existing = placesById.get(key)
      if (existing) {
        owners.forEach(owner => existing.owners.add(owner))
        return
      }
      placesById.set(key, { place, owners: new Set(owners) })
    })
    return Array.from(placesById.values())
  }

  function addRestaurantMarker(candidate, position) {
    const ownership = candidate.owners.size > 1
      ? 'is-shared'
      : candidate.owners.has('mine') ? 'is-mine' : 'is-partner'
    const marker = document.createElement('button')
    marker.type = 'button'
    marker.className = `chat-restaurant-marker ${ownership}`
    marker.setAttribute('aria-label', `${candidate.place.place_name} 상세 보기`)
    marker.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true">restaurant</span>'

    new window.kakao.maps.CustomOverlay({
      map: locationMap,
      position,
      content: marker,
      xAnchor: 0.5,
      yAnchor: 0.5,
      zIndex: 1,
    })

    marker.addEventListener('click', () => showPlaceOverlay(candidate, position))
  }

  function showPlaceOverlay(candidate, position) {
    if (activePlaceOverlay) activePlaceOverlay.setMap(null)

    const content = document.createElement('article')
    content.className = 'chat-place-popover'
    const name = document.createElement('strong')
    name.textContent = candidate.place.place_name
    const category = document.createElement('span')
    category.textContent = candidate.place.category_name || '음식점'
    const address = document.createElement('p')
    address.textContent = candidate.place.road_address_name || candidate.place.address_name || '주소 정보 없음'
    content.append(name, category, address)
    const actions = document.createElement('div')
    actions.className = 'chat-place-popover-actions'
    const shareButton = document.createElement('button')
    shareButton.type = 'button'
    shareButton.innerHTML = '<span class="material-symbols-outlined" aria-hidden="true">maps_ugc</span> 채팅에 공유'
    shareButton.addEventListener('click', () => sendPlaceMessage(candidate))
    actions.append(shareButton)
    if (candidate.place.place_url) {
      const link = document.createElement('a')
      link.href = candidate.place.place_url
      link.target = '_blank'
      link.rel = 'noopener noreferrer'
      link.textContent = '카카오맵에서 보기'
      actions.append(link)
    }
    content.append(actions)

    activePlaceOverlay = new window.kakao.maps.CustomOverlay({
      map: locationMap,
      position,
      content,
      xAnchor: 0.5,
      yAnchor: 1.25,
      zIndex: 10,
    })
  }

  function focusSharedPlaceOnMap(place) {
    if (!locationMap || !isValidSharedPlace(place)) return
    const position = new window.kakao.maps.LatLng(place.latitude, place.longitude)
    locationMap.panTo(position)
    showPlaceOverlay({
      place: {
        id: place.providerPlaceId,
        place_name: place.name,
        category_name: place.category,
        road_address_name: place.address,
        x: String(place.longitude),
        y: String(place.latitude),
        place_url: getSafePlaceUrl(place),
      },
      owners: new Set(),
    }, position)
  }

  function isValidSharedPlace(place) {
    return place
      && /^\d{1,30}$/.test(String(place.providerPlaceId || ''))
      && Boolean(String(place.name || '').trim())
      && Number.isFinite(Number(place.latitude))
      && Number.isFinite(Number(place.longitude))
  }

  function getSafePlaceUrl(place) {
    const providerPlaceId = String(place?.providerPlaceId || '')
    if (!/^\d{1,30}$/.test(providerPlaceId)) return null
    return `https://place.map.kakao.com/${providerPlaceId}`
  }

  function showRestaurantStatus(message, isError = false) {
    const status = document.getElementById('chat-restaurant-status')
    if (!status) return
    status.textContent = message
    status.classList.remove('is-hidden', 'is-error')
    if (isError) status.classList.add('is-error')
  }

  function isValidLocation(location) {
    return location
      && Number.isFinite(Number(location.latitude))
      && Number.isFinite(Number(location.longitude))
      && Math.abs(Number(location.latitude)) <= 90
      && Math.abs(Number(location.longitude)) <= 180
  }

  function setText(id, text) {
    const element = document.getElementById(id)
    if (element) element.textContent = text || ''
  }

  function showMapStatus(message, icon) {
    const status = document.getElementById('chat-map-status')
    if (!status) return
    status.classList.remove('is-hidden')
    const iconElement = status.querySelector('.material-symbols-outlined')
    const messageElement = status.querySelector('span:last-child')
    if (iconElement) iconElement.textContent = icon
    if (messageElement) messageElement.textContent = message
  }

  function hideMapStatus() {
    document.getElementById('chat-map-status')?.classList.add('is-hidden')
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
      renderDesiredLocations(matchResult.desiredLocations)
    }

    // 정보를 다 확보한 상태에서 자동으로 입장 처리 진행
    if (/^\d+$/.test(roomIdFromQuery || '')) {
      connect()
    }
  })
}
