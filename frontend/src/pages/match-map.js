import { API_BASE_URL, regionTree, showToast } from '../main.js'
import { getAccessToken } from '../auth/token-storage.js'
import { getCsrfToken } from '../auth/csrf.js'

// Module-level variables for map instances
let mapInstance = null
let markerInstance = null
let lastValidPosition = null
let lastValidSido = ''
let lastValidSigungu = ''
let lastValidDetail = ''
let lastValidRegionCode = ''

export async function renderMatchMapPage(container, isCurrentRoute = () => true) {
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
          const csrfToken = await getCsrfToken()

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
  if (!isCurrentRoute()) return

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

  const initialLocationLabel = `${sido} ${sigungu} ${detail}`.trim()

  container.innerHTML = `
    <main class="preferred-region-page max-w-[1280px] mx-auto w-full px-margin-mobile md:px-margin-desktop py-8 sm:py-10 flex flex-col gap-6">
      <!-- 페이지 헤더 -->
      <div class="flex flex-col lg:flex-row lg:items-end justify-between gap-5 border-b border-outline-variant/30 pb-6">
        <div class="space-y-3">
          <div class="inline-flex items-center gap-2 rounded-full bg-primary-container/10 px-3 py-1.5 text-xs font-bold text-primary">
            <span class="material-symbols-outlined text-base">restaurant</span>
            <span>마주한끼 매칭</span>
          </div>
          <div class="space-y-1.5">
            <h1 class="font-headline text-2xl sm:text-3xl lg:text-4xl font-bold tracking-tight text-brand-navy">어디에서 만날까요?</h1>
            <p class="text-sm sm:text-base leading-relaxed text-secondary">만날 지역을 선택하고 지도에서 약속 위치를 확인해 주세요.</p>
          </div>
          <div class="inline-flex max-w-full items-center gap-2 rounded-full border border-outline-variant/30 bg-white px-3.5 py-2 text-sm shadow-sm">
            <span class="material-symbols-outlined text-base text-primary-container">location_on</span>
            <span class="shrink-0 font-semibold text-secondary">현재 선택 위치</span>
            <span id="text-current-location" class="truncate font-bold text-brand-navy">${initialLocationLabel}</span>
          </div>
        </div>
        <div class="flex items-center gap-2 self-start lg:self-end">
          <button id="btn-map-revoke" class="btn-action-revoke px-3.5 py-2 rounded-full text-sm" aria-label="위치 이용 동의 철회" title="위치 이용 동의 철회">
            <span class="material-symbols-outlined text-lg">no_accounts</span>
            <span>동의 철회</span>
          </button>
          <a href="/" class="btn-secondary px-3.5 py-2 rounded-full text-sm font-semibold flex items-center gap-1">
            <span class="material-symbols-outlined text-lg">home</span>
            <span>홈으로</span>
          </a>
        </div>
      </div>

      <!-- 지역 선택·지도 영역 -->
      <div class="grid w-full gap-5 lg:grid-cols-[minmax(280px,0.82fr)_minmax(0,1.6fr)] lg:items-start">
        <!-- 지역 선택 카드 -->
        <section class="lg:col-span-1 lg:row-span-2 lg:sticky lg:top-28 h-fit flex flex-col gap-4 rounded-[28px] border border-outline-variant/30 bg-white p-5 shadow-soft sm:p-6 relative">
          <div class="flex items-start gap-3">
            <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl bg-primary-container text-sm font-extrabold text-white shadow-sm">1</span>
            <div>
              <h2 class="font-headline text-xl font-bold tracking-tight text-brand-navy">만날 지역을 선택해 주세요</h2>
              <p class="mt-1 text-sm leading-relaxed text-secondary">지역을 고른 뒤 지도에서 정확한 약속 위치를 정할 수 있어요.</p>
            </div>
          </div>
          <div class="mt-2 flex flex-col gap-4">
            <!-- 시·도 커스텀 드롭다운 -->
            <div class="relative w-full dropdown-container" id="map-sido-dropdown-container">
              <div class="mb-1.5 flex items-center justify-between gap-2">
                <span class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">시·도</span>
                <span class="text-xs text-secondary/70">첫 번째 선택</span>
              </div>
              <button id="btn-map-sido-dropdown" type="button" aria-haspopup="listbox" aria-expanded="false" class="preference-dropdown-button w-full min-h-12 rounded-2xl border border-outline-variant/40 bg-surface px-4 text-left text-sm font-semibold text-on-surface shadow-sm transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/30 flex items-center justify-between gap-1 cursor-pointer">
                <span class="preference-dropdown-leading-icon inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-xl bg-primary-container/10 text-primary-container">
                  <span class="material-symbols-outlined text-base">map</span>
                </span>
                <span id="text-map-sido-selected" class="preference-dropdown-value truncate text-secondary">시·도 선택</span>
                <span class="preference-dropdown-icon material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
              </button>
              <ul id="list-map-sido-options" role="listbox" class="absolute left-0 top-full z-50 mt-2 hidden max-h-60 w-full overflow-y-auto rounded-2xl border border-outline-variant/20 bg-white py-1 shadow-lg transition-all duration-150"></ul>
            </div>

            <!-- 시·군·구 커스텀 드롭다운 -->
            <div class="relative w-full dropdown-container" id="map-sigungu-dropdown-container">
              <div class="mb-1.5 flex items-center justify-between gap-2">
                <span class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">시·군·구</span>
                <span class="text-xs text-secondary/70">두 번째 선택</span>
              </div>
              <button id="btn-map-sigungu-dropdown" type="button" aria-haspopup="listbox" aria-expanded="false" class="preference-dropdown-button w-full min-h-12 rounded-2xl border border-outline-variant/40 bg-surface px-4 text-left text-sm font-semibold text-on-surface shadow-sm transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/30 flex items-center justify-between gap-1 cursor-not-allowed opacity-50" disabled>
                <span class="preference-dropdown-leading-icon inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-xl bg-secondary/10 text-secondary">
                  <span class="material-symbols-outlined text-base">explore</span>
                </span>
                <span id="text-map-sigungu-selected" class="preference-dropdown-value truncate text-secondary">시·군·구 선택</span>
                <span class="preference-dropdown-icon material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
              </button>
              <ul id="list-map-sigungu-options" role="listbox" class="absolute left-0 top-full z-50 mt-2 hidden max-h-60 w-full overflow-y-auto rounded-2xl border border-outline-variant/20 bg-white py-1 shadow-lg transition-all duration-150"></ul>
            </div>

            <!-- 행정구(구·군) 커스텀 드롭다운 -->
            <div class="relative w-full dropdown-container hidden" id="map-detail-dropdown-container">
              <div class="mb-1.5 flex items-center justify-between gap-2">
                <span class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">세부 지역</span>
                <span class="text-xs text-secondary/70">마지막 선택</span>
              </div>
              <button id="btn-map-detail-dropdown" type="button" aria-haspopup="listbox" aria-expanded="false" class="preference-dropdown-button w-full min-h-12 rounded-2xl border border-outline-variant/40 bg-surface px-4 text-left text-sm font-semibold text-on-surface shadow-sm transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/30 flex items-center justify-between gap-1 cursor-not-allowed opacity-50" disabled>
                <span class="preference-dropdown-leading-icon inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-xl bg-secondary/10 text-secondary">
                  <span class="material-symbols-outlined text-base">location_city</span>
                </span>
                <span id="text-map-detail-selected" class="preference-dropdown-value truncate text-secondary">구 선택</span>
                <span class="preference-dropdown-icon material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
              </button>
              <ul id="list-map-detail-options" role="listbox" class="absolute left-0 top-full z-50 mt-2 hidden max-h-60 w-full overflow-y-auto rounded-2xl border border-outline-variant/20 bg-white py-1 shadow-lg transition-all duration-150"></ul>
            </div>
          </div>

          <div class="rounded-2xl border border-primary-container/20 bg-brand-ivory p-4">
            <div class="selected-region-summary flex items-center gap-3">
              <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-primary-container shadow-sm">
                <span class="material-symbols-outlined text-lg">pin_drop</span>
              </span>
              <div class="selected-region-summary-content min-w-0">
                <p class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">선택한 약속 지역</p>
                <p id="text-selected-region" class="mt-1 truncate font-headline text-lg font-bold leading-tight text-brand-navy">${initialLocationLabel}</p>
              </div>
            </div>
            <p class="mt-3 text-xs leading-relaxed text-secondary">정확한 주소는 공개하지 않고 선택한 지역을 기준으로 매칭합니다.</p>
          </div>
        </section>

        <!-- 지도 -->
        <section class="min-w-0 lg:col-start-2 lg:row-start-1">
          <div class="relative overflow-hidden rounded-[28px] border border-outline-variant/30 bg-[#eef3f8] shadow-soft">
            <div class="w-full h-[360px] sm:h-[430px] lg:h-[460px]" id="map">
              <!-- 카카오맵이 여기에 렌더링됩니다. -->
            </div>
            <div class="pointer-events-none absolute left-4 top-4 z-20 max-w-[calc(100%-2rem)] rounded-2xl border border-white/70 bg-white/90 px-4 py-3 shadow-lg backdrop-blur-sm">
              <p class="text-[11px] font-bold uppercase tracking-[0.12em] text-secondary">선택한 약속 위치</p>
              <p id="text-map-selected-location" class="mt-1 truncate font-headline text-base font-bold text-brand-navy">${initialLocationLabel}</p>
            </div>
            <div class="pointer-events-none absolute bottom-4 left-4 z-20 inline-flex items-center gap-2 rounded-full bg-brand-navy/90 px-3 py-2 text-xs font-semibold text-white shadow-lg">
              <span class="material-symbols-outlined text-sm text-primary-container">pin_drop</span>
              <span>지도를 움직여 약속 위치를 정해 보세요</span>
            </div>
          </div>

          <!-- 확정 액션 바 -->
          <div class="sticky bottom-4 z-20 mt-4 flex flex-col gap-3 rounded-[24px] border border-outline-variant/30 bg-white/95 p-4 shadow-floating backdrop-blur-sm sm:flex-row sm:items-center sm:justify-between sm:p-5">
            <div class="flex min-w-0 items-start gap-2.5">
              <span class="material-symbols-outlined mt-0.5 shrink-0 text-lg text-primary-container">check_circle</span>
              <p id="map-status-msg" class="text-sm leading-relaxed text-secondary">지도를 움직이거나 클릭해 약속 위치를 정한 뒤 확정해 주세요.</p>
            </div>
            <button id="btn-confirm-location" class="btn-primary inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full px-6 py-2.5 text-sm font-bold shadow-md transition sm:w-auto sm:min-w-[180px] shrink-0">
              <span class="material-symbols-outlined text-base">check_circle</span>
              <span>약속 위치 확정</span>
            </button>
          </div>
        </section>
      </div>
    </main>
  `

  // 동의 철회 이벤트 바인딩
  document.getElementById('btn-map-revoke')?.addEventListener('click', async () => {
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
  const label = `${sido} ${sigungu} ${detail}`.trim()
  ;['#text-current-location', '#text-selected-region', '#text-map-selected-location'].forEach((selector) => {
    const element = document.querySelector(selector)
    if (element) element.textContent = label
  })
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
                  statusMsg.innerHTML = `약속 위치: <strong>${searchSido} ${rawSigungu}</strong> `
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
