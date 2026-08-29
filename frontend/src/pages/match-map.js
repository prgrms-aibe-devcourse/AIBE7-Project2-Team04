import { API_BASE_URL, regionTree, showToast } from '../main.js'
import { getAccessToken } from '../auth/token-storage.js'

// Module-level variables for map instances
let mapInstance = null
let markerInstance = null
let lastValidPosition = null
let lastValidSido = ''
let lastValidSigungu = ''
let lastValidDetail = ''
let lastValidRegionCode = ''

export async function renderMatchMapPage(container) {
  let preferredRegion = null

  // 위치 기반 서비스 이용 동의 여부 체크
  const checkLocationConsent = async () => {
    const token = getAccessToken()
    if (!token) {
      window.location.assign('/')
      return false
    }

    let consented = false
    try {
      const resp = await fetch(`${API_BASE_URL}/users/me/preferred-region`, { credentials: 'include' })
      if (resp.ok) {
        const body = await resp.json()
        if (body && body.success && body.data && body.data.locationServiceConsent) {
          consented = true
          preferredRegion = body.data
        }
      }
    } catch (e) {
      console.error('위치 동의 여부 확인 실패:', e)
    }

    if (consented) return true

    // 미동의 → body에 모달 동적 삽입
    return new Promise((resolve) => {
      const overlay = document.createElement('div')
      overlay.id = 'location-consent-modal'
      overlay.style.cssText = 'display:grid; position:fixed; inset:0; place-items:center; background:rgba(0,0,0,0.5); z-index:9999;'
      const panel = document.createElement('div')
      panel.style.cssText = 'background:#fff; border-radius:24px; padding:32px; max-width:420px; width:90%; box-shadow:0 8px 32px rgba(0,0,0,0.18);'
      panel.innerHTML = `
        <h2 style="font-size:1.2rem; font-weight:700; color:#1e2c4a; margin-bottom:8px;">위치 기반 서비스 이용 동의</h2>
        <p style="font-size:0.875rem; color:#505e7f; margin-bottom:20px; line-height:1.6;">
          마주한끼 서비스는 밥친구 매칭을 위해 선호 활동 지역 정보를 수집·이용합니다.<br/>동의하지 않으면 서비스를 이용할 수 없습니다.
        </p>
        <label style="display:flex; align-items:center; gap:8px; font-size:0.875rem; font-weight:600; color:#1e2c4a; cursor:pointer; margin-bottom:24px;">
          <input id="chk-location-consent" type="checkbox" style="width:16px; height:16px; cursor:pointer;" />
          위치 기반 서비스 이용에 동의합니다. (필수)
        </label>
        <div style="display:flex; gap:12px;">
          <button id="btn-cancel-location-consent" style="flex:1; padding:12px; border-radius:999px; border:1px solid #e1bfb8; background:#fff; font-size:0.875rem; font-weight:600; cursor:pointer; color:#505e7f;">취소</button>
          <button id="btn-submit-location-consent" disabled style="flex:1; padding:12px; border-radius:999px; border:none; background:#ff6b4a; color:#fff; font-size:0.875rem; font-weight:700; cursor:not-allowed; opacity:0.5;">동의하고 시작</button>
        </div>
      `
      overlay.appendChild(panel)
      document.body.appendChild(overlay)

      const chkConsent = panel.querySelector('#chk-location-consent')
      const btnSubmit = panel.querySelector('#btn-submit-location-consent')
      const btnCancel = panel.querySelector('#btn-cancel-location-consent')

      chkConsent.addEventListener('change', () => {
        btnSubmit.disabled = !chkConsent.checked
        btnSubmit.style.opacity = chkConsent.checked ? '1' : '0.5'
        btnSubmit.style.cursor = chkConsent.checked ? 'pointer' : 'not-allowed'
      })

      btnCancel.addEventListener('click', () => {
        overlay.remove()
        window.location.assign('/')
      })

      btnSubmit.addEventListener('click', async () => {
        try {
          const csrfResp = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' })
          if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
          const prefix = encodeURIComponent('XSRF-TOKEN') + '='
          const csrfCookie = document.cookie.split('; ').find(c => c.startsWith(prefix))
          const csrfToken = csrfCookie ? decodeURIComponent(csrfCookie.slice(prefix.length)) : null

          const resp = await fetch(`${API_BASE_URL}/users/me/preferred-region`, {
            method: 'PUT',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken },
            body: JSON.stringify({ regionCode: '11440', regionName: '서울특별시 마포구', locationServiceConsent: true })
          })

          if (resp.ok) {
            overlay.remove()
            resolve(true)
          } else {
            alert('동의 처리에 실패했습니다. 다시 시도해 주세요.')
          }
        } catch (err) {
          alert('오류가 발생했습니다: ' + err.message)
        }
      })
    })
  }

  await checkLocationConsent()

  const params = new URLSearchParams(window.location.search)
  
  let defaultLat = 37.5662
  let defaultLng = 126.9016
  let defaultName = '마포구'
  let defaultSido = '서울특별시'
  let defaultSigungu = '마포구'

  if (preferredRegion && preferredRegion.regionName) {
    const parts = preferredRegion.regionName.split(' ')
    const s = parts[0]
    const sig = parts[1]
    const d = parts[2] || ''

    let node = regionTree[s]?.[sig]
    if (node) {
      const target = d ? node[d] : node
      if (target) {
        defaultLat = target.lat
        defaultLng = target.lng
        defaultName = d || sig
        defaultSido = s
        defaultSigungu = sig
      }
    }
  }

  const lat = parseFloat(params.get('lat')) || defaultLat
  const lng = parseFloat(params.get('lng')) || defaultLng
  const name = params.get('name') || defaultName
  let sido = params.get('sido') || defaultSido
  if (sido === '광주광역시' || sido === '전라남도') {
    sido = '전남광주통합특별시'
  }
  const sigungu = params.get('sigungu') || defaultSigungu

  // If name is sub-district, then sigungu is parent city (e.g. 성남시), detail is name (e.g. 분당구)
  const detail = name !== sigungu ? name : ''

  container.innerHTML = `
    <main class="max-w-[1440px] mx-auto px-margin-mobile md:px-margin-desktop py-8 flex flex-col gap-6 w-full">
      <!-- 타이틀 바 -->
      <div class="flex items-center justify-between border-b border-outline-variant/30 pb-4">
        <div>
          <h1 class="font-headline text-2xl font-bold text-brand-navy">마주한끼 찾기</h1>
          <p class="text-sm text-secondary">지정한 위치: <span id="text-current-location" class="font-bold text-primary-container">${sido} ${sigungu} ${detail}</span></p>
        </div>
        <div class="flex items-center gap-2">
          <button id="btn-map-revoke" class="btn-secondary px-4 py-2 rounded-full text-sm font-semibold text-error hover:bg-error/10 hover:text-error border-error/30 flex items-center gap-1">
            <span class="material-symbols-outlined text-lg">no_accounts</span>
            <span>동의 철회</span>
          </button>
          <a href="/" class="btn-secondary px-4 py-2 rounded-full text-sm font-semibold flex items-center gap-1">
            <span class="material-symbols-outlined text-lg">home</span>
            <span>홈으로</span>
          </a>
        </div>
      </div>

      <!-- 지도 및 컨트롤 영역 -->
      <div class="w-full flex flex-col gap-4">
        <!-- 필터 바 (시도 / 시군구 / 구 선택) -->
        <div class="flex flex-col sm:flex-row items-center gap-3 bg-surface-container-lowest p-3 rounded-card shadow-soft border border-outline-variant/30 relative">
          <!-- 시·도 커스텀 드롭다운 -->
          <div class="relative w-full sm:flex-1 dropdown-container" id="map-sido-dropdown-container">
            <button id="btn-map-sido-dropdown" type="button" class="w-full bg-surface border border-outline-variant/40 text-on-surface rounded-full py-2.5 pl-10 pr-4 focus:ring-2 focus:ring-primary-container focus:border-primary-container text-sm font-medium text-left flex items-center justify-between cursor-pointer">
              <span id="text-map-sido-selected" class="truncate text-secondary">시·도 선택</span>
              <span class="material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
            </button>
            <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-secondary text-xl pointer-events-none">map</span>
            <ul id="list-map-sido-options" class="absolute left-0 top-full mt-2 w-full bg-white border border-outline-variant/20 rounded-2xl shadow-lg max-h-60 overflow-y-auto z-50 hidden transition-all duration-150 py-1">
            </ul>
          </div>

          <!-- 시·군·구 커스텀 드롭다운 -->
          <div class="relative w-full sm:flex-1 dropdown-container" id="map-sigungu-dropdown-container">
            <button id="btn-map-sigungu-dropdown" type="button" class="w-full bg-surface border border-outline-variant/40 text-on-surface rounded-full py-2.5 pl-10 pr-4 focus:ring-2 focus:ring-primary-container focus:border-primary-container text-sm font-medium text-left flex items-center justify-between cursor-not-allowed opacity-50" disabled>
              <span id="text-map-sigungu-selected" class="truncate text-secondary">시·군·구 선택</span>
              <span class="material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
            </button>
            <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-secondary text-xl pointer-events-none">explore</span>
            <ul id="list-map-sigungu-options" class="absolute left-0 top-full mt-2 w-full bg-white border border-outline-variant/20 rounded-2xl shadow-lg max-h-60 overflow-y-auto z-50 hidden transition-all duration-150 py-1">
            </ul>
          </div>

          <!-- 행정구(구·군) 커스텀 드롭다운 (상세구 존재 시 자동 활성화) -->
          <div class="relative w-full sm:flex-1 dropdown-container hidden" id="map-detail-dropdown-container">
            <button id="btn-map-detail-dropdown" type="button" class="w-full bg-surface border border-outline-variant/40 text-on-surface rounded-full py-2.5 pl-10 pr-4 focus:ring-2 focus:ring-primary-container focus:border-primary-container text-sm font-medium text-left flex items-center justify-between cursor-not-allowed opacity-50" disabled>
              <span id="text-map-detail-selected" class="truncate text-secondary">구 선택</span>
              <span class="material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
            </button>
            <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-secondary text-xl pointer-events-none">location_city</span>
            <ul id="list-map-detail-options" class="absolute left-0 top-full mt-2 w-full bg-white border border-outline-variant/20 rounded-2xl shadow-lg max-h-60 overflow-y-auto z-50 hidden transition-all duration-150 py-1">
            </ul>
          </div>
        </div>

        <!-- 지도 -->
        <div class="w-full bg-white border border-outline-variant/30 rounded-card shadow-soft overflow-hidden" id="map" style="height: 450px; min-height: 450px;">
          <!-- 카카오맵이 여기에 렌더링됩니다. -->
        </div>

        <!-- 하단 액션 바 -->
        <div class="flex flex-col sm:flex-row justify-between items-center bg-white p-4 border border-outline-variant/30 rounded-card shadow-soft gap-4">
          <p id="map-status-msg" class="text-sm text-secondary">지도의 핀을 마우스로 드래그하거나 지도를 클릭하여 원하시는 상세 약속 위치를 잡아주세요.</p>
          <button id="btn-confirm-location" class="btn-primary py-2.5 px-6 rounded-full text-sm font-bold flex items-center gap-2 shadow-md shrink-0">
            <span class="material-symbols-outlined text-sm">check_circle</span>
            <span>약속 위치 확정</span>
          </button>
        </div>
      </div>
    </main>
  `

  // 동의 철회 이벤트 바인딩
  document.getElementById('btn-map-revoke')?.addEventListener('click', async () => {
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
          window.location.assign('/')
        } else {
          alert('동의 철회 처리에 실패했습니다.')
        }
      } catch (err) {
        alert('오류가 발생했습니다: ' + err.message)
      }
    }
  })

  // dropdown references
  const btnSido = document.querySelector('#btn-map-sido-dropdown')
  const textSido = document.querySelector('#text-map-sido-selected')
  const listSido = document.querySelector('#list-map-sido-options')

  const btnSigungu = document.querySelector('#btn-map-sigungu-dropdown')
  const textSigungu = document.querySelector('#text-map-sigungu-selected')
  const listSigungu = document.querySelector('#list-map-sigungu-options')

  const btnDetail = document.querySelector('#btn-map-detail-dropdown')
  const textDetail = document.querySelector('#text-map-detail-selected')
  const listDetail = document.querySelector('#list-map-detail-options')
  const detailContainer = document.querySelector('#map-detail-dropdown-container')

  window.updateMapSidoText = (val) => {
    lastValidSido = val
    if (textSido) {
      textSido.textContent = val
      textSido.classList.remove('text-secondary')
      textSido.classList.add('text-on-surface', 'font-semibold')
    }
  }

  window.updateMapSigunguText = (val) => {
    lastValidSigungu = val
    if (textSigungu) {
      textSigungu.textContent = val
      textSigungu.classList.remove('text-secondary')
      textSigungu.classList.add('text-on-surface', 'font-semibold')
    }
  }

  window.updateMapDetailText = (val) => {
    lastValidDetail = val
    if (textDetail) {
      textDetail.textContent = val || '구 선택'
      if (val) {
        textDetail.classList.remove('text-secondary')
        textDetail.classList.add('text-on-surface', 'font-semibold')
      } else {
        textDetail.classList.remove('text-on-surface', 'font-semibold')
        textDetail.classList.add('text-secondary')
      }
    }
  }

  const selectSido = (s) => {
    window.updateMapSidoText(s)
    window.updateMapSigunguText('시·군·구 선택')
    window.updateMapDetailText('')
    if (detailContainer) detailContainer.classList.add('hidden')
    
    if (btnSigungu) {
      btnSigungu.disabled = false
      btnSigungu.classList.remove('cursor-not-allowed', 'opacity-50')
      btnSigungu.classList.add('cursor-pointer')
    }
    window.populateMapSigunguOptions(s)
    listSido?.classList.add('hidden')
  }

  const selectSigungu = (sig) => {
    window.updateMapSigunguText(sig)
    listSigungu?.classList.add('hidden')
    
    const node = regionTree[lastValidSido][sig]
    const hasDetail = node && !node.code

    if (hasDetail) {
      if (detailContainer) detailContainer.classList.remove('hidden')
      if (btnDetail) {
        btnDetail.disabled = false
        btnDetail.classList.remove('cursor-not-allowed', 'opacity-50')
        btnDetail.classList.add('cursor-pointer')
      }
      window.updateMapDetailText('')
      window.populateMapDetailOptions(sig)
    } else {
      if (detailContainer) detailContainer.classList.add('hidden')
      window.updateMapDetailText('')
      
      if (node && mapInstance && markerInstance) {
        const moveLatLng = new window.kakao.maps.LatLng(node.lat, node.lng)
        mapInstance.setCenter(moveLatLng)
        markerInstance.setPosition(moveLatLng)
        
        lastValidPosition = moveLatLng
        lastValidRegionCode = node.code

        updateCurrentLocationLabel(lastValidSido, sig, '')
      }
    }
  }

  const selectDetail = (det) => {
    window.updateMapDetailText(det)
    listDetail?.classList.add('hidden')

    const node = regionTree[lastValidSido][lastValidSigungu]
    const targetRegion = node ? node[det] : null

    if (targetRegion && mapInstance && markerInstance) {
      const moveLatLng = new window.kakao.maps.LatLng(targetRegion.lat, targetRegion.lng)
      mapInstance.setCenter(moveLatLng)
      markerInstance.setPosition(moveLatLng)
      
      lastValidPosition = moveLatLng
      lastValidRegionCode = targetRegion.code

      updateCurrentLocationLabel(lastValidSido, lastValidSigungu, det)
    }
  }

  // Global references for coordinate-based synchronization
  window.populateMapSigunguOptions = (s) => {
    if (!listSigungu || !regionTree[s]) return
    listSigungu.innerHTML = ''
    Object.keys(regionTree[s]).forEach(sigunguName => {
      const li = document.createElement('li')
      li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
      li.textContent = sigunguName
      li.addEventListener('click', () => selectSigungu(sigunguName))
      listSigungu.appendChild(li)
    })
  }

  window.populateMapDetailOptions = (sig) => {
    if (!listDetail) return
    listDetail.innerHTML = ''
    const node = regionTree[lastValidSido][sig]
    if (node) {
      Object.keys(node).forEach(detName => {
        const li = document.createElement('li')
        li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
        li.textContent = detName
        li.addEventListener('click', () => selectDetail(detName))
        listDetail.appendChild(li)
      })
    }
  }

  // Populate initial options
  if (listSido) {
    listSido.innerHTML = ''
    Object.keys(regionTree).forEach(sidoName => {
      const li = document.createElement('li')
      li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
      li.textContent = sidoName
      li.addEventListener('click', () => selectSido(sidoName))
      listSido.appendChild(li)
    })
  }

  // Set initial selected options
  if (sido) {
    window.updateMapSidoText(sido)
    if (btnSigungu) {
      btnSigungu.disabled = false
      btnSigungu.classList.remove('cursor-not-allowed', 'opacity-50')
      btnSigungu.classList.add('cursor-pointer')
    }
    window.populateMapSigunguOptions(sido)
  }
  if (sigungu) {
    window.updateMapSigunguText(sigungu)
    
    const node = regionTree[sido][sigungu]
    const hasDetail = node && !node.code
    if (hasDetail) {
      if (detailContainer) detailContainer.classList.remove('hidden')
      if (btnDetail) {
        btnDetail.disabled = false
        btnDetail.classList.remove('cursor-not-allowed', 'opacity-50')
        btnDetail.classList.add('cursor-pointer')
      }
      window.populateMapDetailOptions(sigungu)
      if (detail) {
        window.updateMapDetailText(detail)
      }
    }
  }

  btnSido?.addEventListener('click', (e) => {
    e.stopPropagation()
    listSido?.classList.toggle('hidden')
    listSigungu?.classList.add('hidden')
    listDetail?.classList.add('hidden')
  })

  btnSigungu?.addEventListener('click', (e) => {
    e.stopPropagation()
    if (lastValidSido) {
      listSigungu?.classList.toggle('hidden')
      listSido?.classList.add('hidden')
      listDetail?.classList.add('hidden')
    }
  })

  btnDetail?.addEventListener('click', (e) => {
    e.stopPropagation()
    if (lastValidSido && lastValidSigungu) {
      listDetail?.classList.toggle('hidden')
      listSido?.classList.add('hidden')
      listSigungu?.classList.add('hidden')
    }
  })

  document.addEventListener('click', () => {
    listSido?.classList.add('hidden')
    listSigungu?.classList.add('hidden')
    listDetail?.classList.add('hidden')
  })

  // Initialize Map
  initKakaoMap(lat, lng, name, sido, sigungu, detail)
}

function updateCurrentLocationLabel(sido, sigungu, detail) {
  const textLocation = document.querySelector('#text-current-location')
  if (textLocation) {
    textLocation.textContent = `${sido} ${sigungu} ${detail}`.trim()
  }
}

function initKakaoMap(lat, lng, name, sido, sigungu, detail) {
  const checkKakao = setInterval(() => {
    if (window.kakao && window.kakao.maps) {
      clearInterval(checkKakao)
      
      window.kakao.maps.load(() => {
        const container = document.getElementById('map')
        if (!container) return

        const options = {
          center: new window.kakao.maps.LatLng(lat, lng),
          level: 4
        }
        
        const map = new window.kakao.maps.Map(container, options)
        const markerPosition = new window.kakao.maps.LatLng(lat, lng)
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          draggable: false
        })
        
        marker.setMap(map)

        // Store map and marker globally inside the module
        mapInstance = map
        markerInstance = marker
        lastValidPosition = markerPosition
        lastValidSido = sido
        lastValidSigungu = sigungu
        lastValidDetail = detail

        // Find initial region code
        const sidoNode = regionTree[sido]
        const sigunguNode = sidoNode ? sidoNode[sigungu] : null
        let initialRegion = null
        if (sigunguNode) {
          if (detail) {
            initialRegion = sigunguNode[detail]
          } else {
            initialRegion = sigunguNode
          }
        }
        if (initialRegion) {
          lastValidRegionCode = initialRegion.code
        }

        const geocoder = new window.kakao.maps.services.Geocoder()

        const handlePinMove = (position) => {
          geocoder.coord2RegionCode(position.getLng(), position.getLat(), (result, status) => {
            if (status === window.kakao.maps.services.Status.OK) {
              const regionInfo = result.find(r => r.region_type === 'H')
              if (!regionInfo) return

              const newSido = regionInfo.region_1depth_name
              let rawSigungu = regionInfo.region_2depth_name

              let searchSido = newSido

              // Split rawSigungu (e.g. "성남시 분당구" -> parent: "성남시", detail: "분당구")
              const spaceIdx = rawSigungu.indexOf(' ')
              let parentSigungu = rawSigungu
              let childDetail = ''
              if (spaceIdx > 0) {
                parentSigungu = rawSigungu.substring(0, spaceIdx)
                childDetail = rawSigungu.substring(spaceIdx + 1)
              }

              // Check if supported in our regionTree
              const sidoNode = regionTree[searchSido]
              const sigunguNode = sidoNode ? sidoNode[parentSigungu] : null
              
              let matchedRegion = null
              if (sigunguNode) {
                if (childDetail) {
                  matchedRegion = sigunguNode[childDetail]
                } else {
                  if (sigunguNode.code) {
                    matchedRegion = sigunguNode
                  }
                }
              }

              if (matchedRegion) {
                // Supported region: Update dropdown filters and labels automatically
                window.updateMapSidoText(searchSido)
                window.populateMapSigunguOptions(searchSido)
                window.updateMapSigunguText(parentSigungu)

                const detailContainer = document.querySelector('#map-detail-dropdown-container')
                if (childDetail) {
                  if (detailContainer) detailContainer.classList.remove('hidden')
                  window.populateMapDetailOptions(parentSigungu)
                  window.updateMapDetailText(childDetail)
                } else {
                  if (detailContainer) detailContainer.classList.add('hidden')
                  window.updateMapDetailText('')
                }

                lastValidPosition = position
                lastValidSido = searchSido
                lastValidSigungu = parentSigungu
                lastValidDetail = childDetail
                lastValidRegionCode = matchedRegion.code

                updateCurrentLocationLabel(searchSido, parentSigungu, childDetail)

                const statusMsg = document.querySelector('#map-status-msg')
                if (statusMsg) {
                  statusMsg.innerHTML = `핀 위치: <strong>${searchSido} ${rawSigungu}</strong> (위도: ${position.getLat().toFixed(4)}, 경도: ${position.getLng().toFixed(4)})`
                }
              } else {
                // Unsupported region: toast warning and revert pin position
                showToast(`'${newSido} ${rawSigungu}'은 아직 서비스를 지원하지 않는 지역입니다.`);
                marker.setPosition(lastValidPosition)
              }
            }
          })
        }
        
        // 지도가 움직일 때 마커의 위치를 지도의 정중앙에 고정
        window.kakao.maps.event.addListener(map, 'center_changed', () => {
          marker.setPosition(map.getCenter())
        })

        // 지도 드래그가 끝났을 때만 행정동 변환 API(handlePinMove) 호출하여 자원 절약
        window.kakao.maps.event.addListener(map, 'dragend', () => {
          handlePinMove(map.getCenter())
        })

        // Map click event
        window.kakao.maps.event.addListener(map, 'click', (mouseEvent) => {
          const latlng = mouseEvent.latLng
          map.panTo(latlng) // 지도를 클릭한 위치로 부드럽게 이동
          handlePinMove(latlng)
        })

        // Confirm button event
        document.querySelector('#btn-confirm-location')?.addEventListener('click', () => {
          if (!lastValidRegionCode || !lastValidPosition) {
            alert('유효한 지역과 핀 위치를 지정해 주세요.')
            return
          }

          const query = new URLSearchParams({
            regionCode: lastValidRegionCode,
            regionName: `${lastValidSido} ${lastValidSigungu} ${lastValidDetail}`.trim(),
            lat: String(lastValidPosition.getLat()),
            lng: String(lastValidPosition.getLng()),
            sido: lastValidSido,
            sigungu: lastValidSigungu,
            name: lastValidDetail || lastValidSigungu,
          })
          window.location.assign(`/matching/request?${query.toString()}`)
        })
      })
    }
  }, 100)
}
