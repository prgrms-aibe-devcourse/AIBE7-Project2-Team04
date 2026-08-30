import { API_BASE_URL, regionTree } from '../main.js'
import { getAccessToken } from '../auth/token-storage.js'

export async function renderPreferredRegionPage(container, isCurrentRoute = () => true) {
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
  if (!isCurrentRoute()) return

  let initialLat = 37.5662
  let initialLng = 126.9016
  let initialLocName = '서울특별시 마포구'

  let initialSido = '서울특별시'
  let initialSigungu = '마포구'
  let initialDetail = ''

  try {
    const resp = await fetch(`${API_BASE_URL}/users/me/preferred-region`, { credentials: 'include' })
    if (!isCurrentRoute()) return
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
            initialSido = s
            initialSigungu = sig
            initialDetail = d
          }
        }
      }
    }
  } catch (e) {
    console.error('선호지역 조회 오류:', e)
  }

  if (!isCurrentRoute()) return
  container.innerHTML = `
    <main class="preferred-region-page max-w-[1280px] mx-auto w-full px-margin-mobile md:px-margin-desktop py-8 sm:py-10 flex flex-col gap-6">
      <!-- 페이지 헤더 -->
      <div class="flex flex-col lg:flex-row lg:items-end justify-between gap-5 border-b border-outline-variant/30 pb-6">
        <div class="space-y-3">
          <div class="inline-flex items-center gap-2 rounded-full bg-primary-container/10 px-3 py-1.5 text-xs font-bold text-primary">
            <span class="material-symbols-outlined text-base">location_on</span>
            <span>활동 지역 설정</span>
          </div>
          <div class="space-y-1.5">
            <h1 class="font-headline text-2xl sm:text-3xl lg:text-4xl font-bold tracking-tight text-brand-navy">어디에서 밥친구를 만날까요?</h1>
            <p class="text-sm sm:text-base leading-relaxed text-secondary">만나고 싶은 지역을 선택하면 지도에서 위치를 확인할 수 있어요.</p>
          </div>
          <div class="inline-flex max-w-full items-center gap-2 rounded-full border border-outline-variant/30 bg-white px-3.5 py-2 text-sm shadow-sm">
            <span class="material-symbols-outlined text-base text-primary-container">check_circle</span>
            <span class="shrink-0 font-semibold text-secondary">현재 등록 지역</span>
            <span id="text-current-location" class="truncate font-bold text-brand-navy">${initialLocName}</span>
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
        <!-- 필터 바 (시도 / 시군구 / 구 선택) -->
        <section class="lg:col-span-1 lg:row-span-2 lg:sticky lg:top-28 h-fit flex flex-col gap-4 rounded-[28px] border border-outline-variant/30 bg-white p-5 shadow-soft sm:p-6 relative">
          <div class="flex items-start gap-3">
            <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl bg-primary-container text-sm font-extrabold text-white shadow-sm">1</span>
            <div>
              <h2 class="font-headline text-xl font-bold tracking-tight text-brand-navy">활동 지역을 선택해 주세요</h2>
              <p class="mt-1 text-sm leading-relaxed text-secondary">주소 대신 대략적인 지역만 등록해 안전하게 매칭할 수 있어요.</p>
            </div>
          </div>
          <div class="mt-2 flex flex-col gap-4">
          <!-- 시·도 커스텀 드롭다운 -->
          <div class="relative w-full dropdown-container" id="pref-sido-dropdown-container">
            <div class="mb-1.5 flex items-center justify-between gap-2">
              <span class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">시·도</span>
              <span class="text-xs text-secondary/70">첫 번째 선택</span>
            </div>
            <button id="btn-pref-sido-dropdown" type="button" aria-haspopup="listbox" aria-expanded="false" class="w-full min-h-12 rounded-2xl border border-outline-variant/40 bg-surface px-4 pl-12 text-left text-sm font-semibold text-on-surface shadow-sm transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/30 flex items-center justify-between cursor-pointer">
              <span id="text-pref-sido-selected" class="truncate text-secondary">시·도 선택</span>
              <span class="material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
            </button>
            <span class="pointer-events-none absolute left-4 top-[2.55rem] inline-flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-xl bg-primary-container/10 text-primary-container">
              <span class="material-symbols-outlined text-base">map</span>
            </span>
            <ul id="list-pref-sido-options" role="listbox" class="absolute left-0 top-full z-50 mt-2 hidden max-h-60 w-full overflow-y-auto rounded-2xl border border-outline-variant/20 bg-white py-1 shadow-lg transition-all duration-150">
            </ul>
          </div>

          <!-- 시·군·구 커스텀 드롭다운 -->
          <div class="relative w-full dropdown-container" id="pref-sigungu-dropdown-container">
            <div class="mb-1.5 flex items-center justify-between gap-2">
              <span class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">시·군·구</span>
              <span class="text-xs text-secondary/70">두 번째 선택</span>
            </div>
            <button id="btn-pref-sigungu-dropdown" type="button" aria-haspopup="listbox" aria-expanded="false" class="w-full min-h-12 rounded-2xl border border-outline-variant/40 bg-surface px-4 pl-12 text-left text-sm font-semibold text-on-surface shadow-sm transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/30 flex items-center justify-between cursor-not-allowed opacity-50" disabled>
              <span id="text-pref-sigungu-selected" class="truncate text-secondary">시·군·구 선택</span>
              <span class="material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
            </button>
            <span class="pointer-events-none absolute left-4 top-[2.55rem] inline-flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-xl bg-secondary/10 text-secondary">
              <span class="material-symbols-outlined text-base">explore</span>
            </span>
            <ul id="list-pref-sigungu-options" role="listbox" class="absolute left-0 top-full z-50 mt-2 hidden max-h-60 w-full overflow-y-auto rounded-2xl border border-outline-variant/20 bg-white py-1 shadow-lg transition-all duration-150">
            </ul>
          </div>

          <!-- 행정구(구·군) 커스텀 드롭다운 (상세구 존재 시 자동 활성화) -->
          <div class="relative w-full dropdown-container hidden" id="pref-detail-dropdown-container">
            <div class="mb-1.5 flex items-center justify-between gap-2">
              <span class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">세부 지역</span>
              <span class="text-xs text-secondary/70">마지막 선택</span>
            </div>
            <button id="btn-pref-detail-dropdown" type="button" aria-haspopup="listbox" aria-expanded="false" class="w-full min-h-12 rounded-2xl border border-outline-variant/40 bg-surface px-4 pl-12 text-left text-sm font-semibold text-on-surface shadow-sm transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/30 flex items-center justify-between cursor-not-allowed opacity-50" disabled>
              <span id="text-pref-detail-selected" class="truncate text-secondary">구 선택</span>
              <span class="material-symbols-outlined text-secondary text-lg">arrow_drop_down</span>
            </button>
            <span class="pointer-events-none absolute left-4 top-[2.55rem] inline-flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-xl bg-secondary/10 text-secondary">
              <span class="material-symbols-outlined text-base">location_city</span>
            </span>
            <ul id="list-pref-detail-options" role="listbox" class="absolute left-0 top-full z-50 mt-2 hidden max-h-60 w-full overflow-y-auto rounded-2xl border border-outline-variant/20 bg-white py-1 shadow-lg transition-all duration-150">
            </ul>
          </div>
        </div>

        <div class="rounded-2xl border border-primary-container/20 bg-brand-ivory p-4">
          <div class="flex items-start gap-3">
            <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-primary-container shadow-sm">
              <span class="material-symbols-outlined text-lg">location_on</span>
            </span>
            <div class="min-w-0">
              <p class="text-xs font-bold uppercase tracking-[0.08em] text-secondary">선택한 활동 지역</p>
              <p id="text-selected-region" class="mt-1 truncate font-headline text-lg font-bold text-brand-navy">${initialLocName}</p>
            </div>
          </div>
          <p class="mt-3 text-xs leading-relaxed text-secondary">정확한 주소는 저장하지 않고, 선택한 지역 단위로만 매칭에 활용합니다.</p>
        </div>
      </section>

        <!-- 지도 -->
        <section class="min-w-0 lg:col-start-2 lg:row-start-1">
          <div class="relative overflow-hidden rounded-[28px] border border-outline-variant/30 bg-[#eef3f8] shadow-soft">
            <div class="w-full h-[360px] sm:h-[430px] lg:h-[460px]" id="map">
              <!-- 카카오맵이 여기에 렌더링됩니다. -->
            </div>
            <div class="pointer-events-none absolute left-4 top-4 z-20 max-w-[calc(100%-2rem)] rounded-2xl border border-white/70 bg-white/90 px-4 py-3 shadow-lg backdrop-blur-sm">
              <p class="text-[11px] font-bold uppercase tracking-[0.12em] text-secondary">선택한 지역</p>
              <p id="text-map-selected-location" class="mt-1 truncate font-headline text-base font-bold text-brand-navy">${initialLocName}</p>
            </div>
            <div class="pointer-events-none absolute bottom-4 left-4 z-20 inline-flex items-center gap-2 rounded-full bg-brand-navy/90 px-3 py-2 text-xs font-semibold text-white shadow-lg">
              <span class="material-symbols-outlined text-sm text-primary-container">pin_drop</span>
              <span>대략적인 활동 지역을 보여드려요</span>
            </div>
          </div>

          <!-- 저장 액션 바 -->
          <div class="sticky bottom-4 z-20 mt-4 flex flex-col gap-3 rounded-[24px] border border-outline-variant/30 bg-white/95 p-4 shadow-floating backdrop-blur-sm sm:flex-row sm:items-center sm:justify-between sm:p-5">
            <div class="flex min-w-0 items-start gap-2.5">
              <span class="material-symbols-outlined mt-0.5 shrink-0 text-lg text-primary-container">check_circle</span>
              <p id="map-status-msg" class="text-sm leading-relaxed text-secondary">현재 지역을 확인한 뒤 저장해 주세요.</p>
            </div>
            <button id="btn-confirm-location" class="btn-primary inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full px-6 py-2.5 text-sm font-bold shadow-md transition sm:w-auto sm:min-w-[180px] shrink-0">
              <span class="material-symbols-outlined text-base">favorite</span>
              <span>이 지역으로 저장</span>
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

  // Dropdown References
  const btnSido = document.querySelector('#btn-pref-sido-dropdown')
  const textSido = document.querySelector('#text-pref-sido-selected')
  const listSido = document.querySelector('#list-pref-sido-options')

  const btnSigungu = document.querySelector('#btn-pref-sigungu-dropdown')
  const textSigungu = document.querySelector('#text-pref-sigungu-selected')
  const listSigungu = document.querySelector('#list-pref-sigungu-options')

  const btnDetail = document.querySelector('#btn-pref-detail-dropdown')
  const textDetail = document.querySelector('#text-pref-detail-selected')
  const listDetail = document.querySelector('#list-pref-detail-options')
  const detailContainer = document.querySelector('#pref-detail-dropdown-container')

  let lastValidSido = initialSido
  let lastValidSigungu = initialSigungu
  let lastValidDetail = initialDetail
  let currentRegionCode = ''
  let currentRegionName = initialLocName

  const updateSidoText = (val) => {
    lastValidSido = val
    if (textSido) {
      textSido.textContent = val
      textSido.classList.remove('text-secondary')
      textSido.classList.add('text-on-surface', 'font-semibold')
    }
  }

  const updateSigunguText = (val) => {
    lastValidSigungu = val
    if (textSigungu) {
      textSigungu.textContent = val
      textSigungu.classList.remove('text-secondary')
      textSigungu.classList.add('text-on-surface', 'font-semibold')
    }
  }

  const updateDetailText = (val) => {
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

  const populateSigunguOptions = (s) => {
    if (!listSigungu || !regionTree[s]) return
    listSigungu.innerHTML = ''
    Object.keys(regionTree[s]).forEach(sigunguName => {
      const li = document.createElement('li')
      li.setAttribute('role', 'option')
      li.setAttribute('tabindex', '0')
      li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
      li.textContent = sigunguName
      li.addEventListener('click', () => selectSigungu(sigunguName))
      listSigungu.appendChild(li)
    })
  }

  const populateDetailOptions = (sig) => {
    if (!listDetail) return
    listDetail.innerHTML = ''
    const node = regionTree[lastValidSido][sig]
    if (node) {
      Object.keys(node).forEach(detName => {
        const li = document.createElement('li')
        li.setAttribute('role', 'option')
        li.setAttribute('tabindex', '0')
        li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
        li.textContent = detName
        li.addEventListener('click', () => selectDetail(detName))
        listDetail.appendChild(li)
      })
    }
  }

  // Map Instance references
  let mapInstance = null
  let markerInstance = null

  const updateSelectedLocationLabels = (labelText) => {
    const labelIds = ['text-selected-region', 'text-map-selected-location']
    labelIds.forEach((id) => {
      const element = document.getElementById(id)
      if (element) element.textContent = labelText
    })
  }

  const moveMapToRegion = (lat, lng, labelText) => {
    updateSelectedLocationLabels(labelText)
    if (mapInstance && markerInstance) {
      const moveLatLng = new window.kakao.maps.LatLng(lat, lng)
      mapInstance.setCenter(moveLatLng)
      markerInstance.setPosition(moveLatLng)
      
    }
    const statusMsg = document.getElementById('map-status-msg')
    if (statusMsg) {
      statusMsg.innerHTML = '선택한 지역을 지도에서 확인할 수 있어요: <strong>' + labelText + '</strong>'
    }
  }

  const selectSido = (s) => {
    updateSidoText(s)
    updateSelectedLocationLabels(s + ' 선택 중')
    updateSigunguText('시·군·구 선택')
    updateDetailText('')
    if (detailContainer) detailContainer.classList.add('hidden')
    
    if (btnSigungu) {
      btnSigungu.disabled = false
      btnSigungu.classList.remove('cursor-not-allowed', 'opacity-50')
      btnSigungu.classList.add('cursor-pointer')
    }
    populateSigunguOptions(s)
    listSido?.classList.add('hidden')
    btnSido?.setAttribute('aria-expanded', 'false')
    currentRegionCode = ''
    currentRegionName = ''
  }

  const selectSigungu = (sig) => {
    updateSigunguText(sig)
    listSigungu?.classList.add('hidden')
    btnSigungu?.setAttribute('aria-expanded', 'false')
    
    const node = regionTree[lastValidSido][sig]
    const hasDetail = node && !node.code

    if (hasDetail) {
      updateSelectedLocationLabels(lastValidSido + ' ' + sig + ' 선택 중')
      if (detailContainer) detailContainer.classList.remove('hidden')
      if (btnDetail) {
        btnDetail.disabled = false
        btnDetail.classList.remove('cursor-not-allowed', 'opacity-50')
        btnDetail.classList.add('cursor-pointer')
      }
      updateDetailText('')
      populateDetailOptions(sig)
      currentRegionCode = ''
      currentRegionName = ''
    } else {
      if (detailContainer) detailContainer.classList.add('hidden')
      updateDetailText('')
      
      if (node) {
        currentRegionCode = node.code
        currentRegionName = node.fullName
        moveMapToRegion(node.lat, node.lng, node.fullName)
      }
    }
  }

  const selectDetail = (det) => {
    updateDetailText(det)
    listDetail?.classList.add('hidden')
    btnDetail?.setAttribute('aria-expanded', 'false')

    const node = regionTree[lastValidSido][lastValidSigungu]
    const targetRegion = node ? node[det] : null

    if (targetRegion) {
      currentRegionCode = targetRegion.code
      currentRegionName = targetRegion.fullName
      moveMapToRegion(targetRegion.lat, targetRegion.lng, targetRegion.fullName)
    }
  }

  // Populate initial options
  if (listSido) {
    listSido.innerHTML = ''
    Object.keys(regionTree).forEach(sidoName => {
      const li = document.createElement('li')
      li.setAttribute('role', 'option')
      li.setAttribute('tabindex', '0')
      li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
      li.textContent = sidoName
      li.addEventListener('click', () => selectSido(sidoName))
      listSido.appendChild(li)
    })
  }

  // Setup dropdown values from initial state
  if (initialSido) {
    updateSidoText(initialSido)
    if (btnSigungu) {
      btnSigungu.disabled = false
      btnSigungu.classList.remove('cursor-not-allowed', 'opacity-50')
      btnSigungu.classList.add('cursor-pointer')
    }
    populateSigunguOptions(initialSido)
  }
  if (initialSigungu) {
    updateSigunguText(initialSigungu)
    
    const node = regionTree[initialSido]?.[initialSigungu]
    const hasDetail = node && !node.code
    if (hasDetail) {
      if (detailContainer) detailContainer.classList.remove('hidden')
      if (btnDetail) {
        btnDetail.disabled = false
        btnDetail.classList.remove('cursor-not-allowed', 'opacity-50')
        btnDetail.classList.add('cursor-pointer')
      }
      populateDetailOptions(initialSigungu)
      if (initialDetail) {
        updateDetailText(initialDetail)
      }
    }
    
    if (node) {
      const target = initialDetail ? node[initialDetail] : node
      if (target) {
        currentRegionCode = target.code
      }
    }
  }

  const setDropdownExpanded = (button, list, expanded) => {
    button?.setAttribute('aria-expanded', String(expanded))
    list?.classList.toggle('hidden', !expanded)
  }

  btnSido?.addEventListener('click', (e) => {
    e.stopPropagation()
    const willOpen = listSido?.classList.contains('hidden')
    setDropdownExpanded(btnSido, listSido, willOpen)
    setDropdownExpanded(btnSigungu, listSigungu, false)
    setDropdownExpanded(btnDetail, listDetail, false)
  })

  btnSigungu?.addEventListener('click', (e) => {
    e.stopPropagation()
    if (lastValidSido) {
      const willOpen = listSigungu?.classList.contains('hidden')
      setDropdownExpanded(btnSigungu, listSigungu, willOpen)
      setDropdownExpanded(btnSido, listSido, false)
      setDropdownExpanded(btnDetail, listDetail, false)
    }
  })

  btnDetail?.addEventListener('click', (e) => {
    e.stopPropagation()
    if (lastValidSido && lastValidSigungu) {
      const willOpen = listDetail?.classList.contains('hidden')
      setDropdownExpanded(btnDetail, listDetail, willOpen)
      setDropdownExpanded(btnSido, listSido, false)
      setDropdownExpanded(btnSigungu, listSigungu, false)
    }
  })

  document.addEventListener('click', () => {
    setDropdownExpanded(btnSido, listSido, false)
    setDropdownExpanded(btnSigungu, listSigungu, false)
    setDropdownExpanded(btnDetail, listDetail, false)
  })

  // Map Initialization
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
            draggable: false // 대략적인 위치 지정이므로 핀 드래그 불필요
          })
          marker.setMap(map)
          map.setDraggable(false) // 드래그로 지도 이동 차단
          map.setZoomable(false)    // 휠 스크롤을 통한 지도 줌(확대/축소) 차단

          mapInstance = map
          markerInstance = marker

          // Force map relayout after layout rendering is complete to avoid off-center issue
          setTimeout(() => {
            map.relayout()
            map.setCenter(new window.kakao.maps.LatLng(initLat, initLng))
          }, 150)

          function readCookie(name) {
            const prefix = `${encodeURIComponent(name)}=`
            const cookie = document.cookie
              .split('; ')
              .find((item) => item.startsWith(prefix))
            return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
          }

          const confirmBtn = document.getElementById('btn-confirm-location')
          confirmBtn?.addEventListener('click', async () => {
            if (!currentRegionCode || !currentRegionName) {
              alert('시·도, 시·군·구를 모두 선택해 주세요.')
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
