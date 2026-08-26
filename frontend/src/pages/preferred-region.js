import { API_BASE_URL, regionTree } from '../main.js'
import { getAccessToken } from '../auth/token-storage.js'

export async function renderPreferredRegionPage(container) {
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

  let initialLat = 37.5662
  let initialLng = 126.9016
  let initialLocName = '서울특별시 마포구'

  try {
    const resp = await fetch(`${API_BASE_URL}/users/me/preferred-region`, { credentials: 'include' })
    if (resp.ok) {
      const body = await resp.json()
      if (body && body.success && body.data) {
        const pref = body.data
        const parts = pref.regionName.split(' ')
        const s = parts[0]
        const sig = parts[1]
        const d = parts[2] || ''

        let node = regionTree[s]?.[sig]
        if (node) {
          const target = d ? node[d] : node
          if (target) {
            initialLat = target.lat
            initialLng = target.lng
            initialLocName = pref.regionName
          }
        }
      }
    }
  } catch (e) {
    console.error('선호지역 조회 오류:', e)
  }

  container.innerHTML = `
    <main class="max-w-[1440px] mx-auto px-margin-mobile md:px-margin-desktop py-8 flex flex-col gap-6 w-full">
      <!-- 타이틀 바 -->
      <div class="flex items-center justify-between border-b border-outline-variant/30 pb-4">
        <div>
          <h1 class="font-headline text-2xl font-bold text-brand-navy">선호위치 등록</h1>
          <p class="text-sm text-secondary">지정한 선호위치: <span id="text-current-location" class="font-bold text-primary-container">${initialLocName}</span></p>
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
        <!-- 주소 입력 바 -->
        <div class="flex flex-col sm:flex-row items-center gap-3 bg-surface-container-lowest p-3 rounded-card shadow-soft border border-outline-variant/30 relative">
          <div class="relative w-full sm:flex-1">
            <input id="input-preferred-address" type="text" placeholder="예: 서울 마포구 백범로 35" class="w-full bg-surface border border-outline-variant/40 text-on-surface rounded-full py-2.5 pl-10 pr-4 focus:ring-2 focus:ring-primary-container focus:border-primary-container text-sm font-medium" />
            <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-secondary text-xl pointer-events-none">search</span>
          </div>
          <button id="btn-search-address" class="w-full sm:w-auto btn-primary py-2.5 px-6 rounded-full text-sm font-bold flex items-center justify-center gap-2 shrink-0">
            <span>주소 검색</span>
          </button>
        </div>

        <!-- 지도 -->
        <div class="w-full bg-white border border-outline-variant/30 rounded-card shadow-soft overflow-hidden" id="map" style="height: 450px; min-height: 450px;">
          <!-- 카카오맵이 여기에 렌더링됩니다. -->
        </div>

        <!-- 하단 액션 바 -->
        <div class="flex flex-col sm:flex-row justify-between items-center bg-white p-4 border border-outline-variant/30 rounded-card shadow-soft gap-4">
          <p id="map-status-msg" class="text-sm text-secondary">주소를 입력하거나 지도의 핀을 드래그하여 자주 활동하시는 선호위치를 확정해 주세요.</p>
          <button id="btn-confirm-location" class="btn-primary py-2.5 px-6 rounded-full text-sm font-bold flex items-center gap-2 shadow-md shrink-0">
            <span class="material-symbols-outlined text-sm">favorite</span>
            <span>선호위치 등록</span>
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

  const initPreferredLocationMap = (initLat, initLng) => {
    const checkKakao = setInterval(() => {
      if (window.kakao && window.kakao.maps) {
        clearInterval(checkKakao)

        window.kakao.maps.load(() => {
          const mapContainer = document.getElementById('map')
          if (!mapContainer) return

          const options = {
            center: new window.kakao.maps.LatLng(initLat, initLng),
            level: 5
          }
          const map = new window.kakao.maps.Map(mapContainer, options)
          const marker = new window.kakao.maps.Marker({
            position: new window.kakao.maps.LatLng(initLat, initLng),
            draggable: true
          })
          marker.setMap(map)

          // Force map relayout after layout rendering is complete to avoid off-center issue
          setTimeout(() => {
            map.relayout()
            map.setCenter(new window.kakao.maps.LatLng(initLat, initLng))
          }, 150)

          const geocoder = new window.kakao.maps.services.Geocoder()
          let currentPosition = marker.getPosition()
          let currentRegionCode = ''
          let currentRegionName = ''

          function readCookie(name) {
            const prefix = `${encodeURIComponent(name)}=`
            const cookie = document.cookie
              .split('; ')
              .find((item) => item.startsWith(prefix))
            return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
          }

          const updateUIForPosition = (position) => {
            currentPosition = position
            geocoder.coord2RegionCode(position.getLng(), position.getLat(), (result, status) => {
              if (status === window.kakao.maps.services.Status.OK) {
                const regionInfo = result.find(r => r.region_type === 'H')
                if (!regionInfo) return

                const newSido = regionInfo.region_1depth_name
                const rawSigungu = regionInfo.region_2depth_name

                let searchSido = newSido
                if (newSido === '광주광역시' || newSido === '전라남도') {
                  searchSido = '전남광주통합특별시'
                }

                const spaceIdx = rawSigungu.indexOf(' ')
                let parentSigungu = rawSigungu
                let childDetail = ''
                if (spaceIdx > 0) {
                  parentSigungu = rawSigungu.substring(0, spaceIdx)
                  childDetail = rawSigungu.substring(spaceIdx + 1)
                }

                const sidoNode = regionTree[searchSido]
                const sigunguNode = sidoNode ? sidoNode[parentSigungu] : null

                let matchedRegion = null
                let finalDetail = ''
                if (sigunguNode) {
                  if (childDetail && sigunguNode[childDetail]) {
                    matchedRegion = sigunguNode[childDetail]
                    finalDetail = childDetail
                  } else if (!childDetail && sigunguNode.code) {
                    matchedRegion = sigunguNode
                  }
                }

                if (matchedRegion) {
                  currentRegionCode = matchedRegion.code
                  currentRegionName = matchedRegion.fullName

                  const labelLoc = document.getElementById('text-current-location')
                  if (labelLoc) {
                    labelLoc.textContent = matchedRegion.fullName
                  }
                  const statusMsg = document.getElementById('map-status-msg')
                  if (statusMsg) {
                    statusMsg.innerHTML = `선택된 선호지역: <strong>${matchedRegion.fullName}</strong> (위도: ${position.getLat().toFixed(4)}, 경도: ${position.getLng().toFixed(4)})`
                  }
                } else {
                  marker.setPosition(currentPosition)
                  alert(`선택하신 위치(${newSido} ${rawSigungu})는 현재 서비스 지원 지역이 아닙니다.`)
                }
              }
            })
          }

          updateUIForPosition(currentPosition)

          window.kakao.maps.event.addListener(map, 'click', (mouseEvent) => {
            const clickedPos = mouseEvent.latLng
            marker.setPosition(clickedPos)
            updateUIForPosition(clickedPos)
          })

          window.kakao.maps.event.addListener(marker, 'dragend', () => {
            updateUIForPosition(marker.getPosition())
          })

          const addressInput = document.getElementById('input-preferred-address')
          const searchBtn = document.getElementById('btn-search-address')

          const executeAddressSearch = () => {
            const address = addressInput.value.trim()
            if (!address) {
              alert('검색할 주소를 입력해 주세요.')
              return
            }

            geocoder.addressSearch(address, (result, status) => {
              if (status === window.kakao.maps.services.Status.OK) {
                const coords = new window.kakao.maps.LatLng(result[0].y, result[0].x)
                map.relayout() // Force relayout to update container dimensions
                map.setCenter(coords)
                marker.setPosition(coords)
                updateUIForPosition(coords)
              } else {
                alert('입력하신 주소를 찾을 수 없습니다. 정확한 도로명/지번 주소를 입력해 주세요.')
              }
            })
          }

          searchBtn?.addEventListener('click', executeAddressSearch)
          addressInput?.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
              executeAddressSearch()
            }
          })

          const confirmBtn = document.getElementById('btn-confirm-location')
          confirmBtn?.addEventListener('click', async () => {
            if (!currentRegionCode || !currentRegionName) {
              alert('선택된 유효한 행정구역이 없습니다. 지도를 클릭하거나 주소를 검색해 주세요.')
              return
            }

            try {
              const csrfResp = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' })
              if (!csrfResp.ok) throw new Error('CSRF 토큰 발급에 실패했습니다.')
              const csrfToken = readCookie('XSRF-TOKEN')

              const resp = await fetch(`${API_BASE_URL}/users/me/preferred-region`, {
                method: 'PUT',
                credentials: 'include',
                headers: {
                  'Content-Type': 'application/json',
                  'X-XSRF-TOKEN': csrfToken
                },
                body: JSON.stringify({
                  regionCode: currentRegionCode,
                  regionName: currentRegionName,
                  locationServiceConsent: true
                })
              })

              if (resp.ok) {
                alert('선호위치 등록이 성공적으로 완료되었습니다!')
                window.location.assign('/')
              } else {
                const body = await resp.json()
                alert(body?.error?.message || '선호위치 등록에 실패했습니다.')
              }
            } catch (err) {
              alert(err.message || '선호위치 등록 중 오류가 발생했습니다.')
            }
          })
        })
      }
    }, 50)
  }

  initPreferredLocationMap(initialLat, initialLng)
}
