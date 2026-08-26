import './style.css'
import { startKakaoLogin, startGoogleLogin, login, logout, signUp } from './auth/auth-api.js'
import { clearAccessToken, getAccessToken } from './auth/token-storage.js'
import { renderOAuthCallback } from './pages/oauth-callback.js'
import { renderPersonalitySurvey } from './pages/personality-survey.js'
import { getPersonalityProfile } from './personality/personality-api.js'

const app = document.querySelector('#app')
const initialLandingHTML = app ? app.innerHTML : ''

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
let regionData = {}

// Client-side SPA Router
export function navigateTo(path) {
  if (window.location.pathname !== path) {
    window.history.pushState({}, '', path)
  }
  renderCurrentRoute()
}

window.addEventListener('popstate', () => {
  renderCurrentRoute()
})

// Intercept internal anchor link clicks for smooth SPA transitions
document.addEventListener('click', (e) => {
  const link = e.target.closest('a')
  if (!link || link.target === '_blank' || link.hasAttribute('download')) return

  const href = link.getAttribute('href')
  if (!href) return

  // External links
  if (href.startsWith('http://') || href.startsWith('https://') || href.startsWith('mailto:')) return

  // Anchor links on same page
  if (href.startsWith('#')) {
    if (window.location.pathname !== '/') {
      e.preventDefault()
      navigateTo(`/${href}`)
    }
    return
  }

  // Anchor links with leading slash
  if (href.startsWith('/#')) {
    if (window.location.pathname !== '/') {
      e.preventDefault()
      navigateTo(href)
    }
    return
  }

  // Internal path navigation
  if (href.startsWith('/')) {
    e.preventDefault()
    navigateTo(href)
  }
})

function renderCurrentRoute() {
  initCommonHeader()
  const path = window.location.pathname

  if (path === '/oauth/callback') {
    renderOAuthCallback(app)
  } else if (path === '/personality/survey') {
    renderPersonalitySurvey(app)
  } else if (path === '/profile/setup') {
    renderProfileSetup(app)
  } else if (path === '/map') {
    renderMapPage(app)
  } else {
    if (app) app.innerHTML = initialLandingHTML
    initLandingPage()
  }
}

// Initial render
renderCurrentRoute()

function openAuthModal(isSignup = false) {
  const authModal = document.querySelector('#auth-modal')
  const formLogin = document.querySelector('#form-local-login')
  const formSignup = document.querySelector('#form-local-signup')
  const modalTitle = document.querySelector('#modal-title')
  const modalDesc = document.querySelector('#modal-desc')
  const loginErrorMsg = document.querySelector('#login-error-msg')
  const signupErrorMsg = document.querySelector('#signup-error-msg')

  if (isSignup) {
    formLogin?.classList.add('hidden')
    formSignup?.classList.remove('hidden')
    if (modalTitle) modalTitle.textContent = '이메일 회원가입'
    if (modalDesc) modalDesc.textContent = '간단한 가입으로 나만의 1:1 밥친구를 찾아보세요.'
    if (signupErrorMsg) signupErrorMsg.style.display = 'none'
  } else {
    formLogin?.classList.remove('hidden')
    formSignup?.classList.add('hidden')
    if (modalTitle) modalTitle.textContent = '마주한끼 시작하기'
    if (modalDesc) modalDesc.textContent = '혼밥 말고 따뜻한 한 끼를 함께할 친구를 만나보세요.'
    if (loginErrorMsg) loginErrorMsg.style.display = 'none'
  }
  authModal?.classList.remove('hidden')
  requestAnimationFrame(() => {
    authModal?.classList.add('is-open')
    authModal?.setAttribute('aria-hidden', 'false')
  })
}

function closeAuthModal() {
  const authModal = document.querySelector('#auth-modal')
  const loginErrorMsg = document.querySelector('#login-error-msg')
  const signupErrorMsg = document.querySelector('#signup-error-msg')

  authModal?.classList.remove('is-open')
  authModal?.setAttribute('aria-hidden', 'true')
  if (loginErrorMsg) loginErrorMsg.style.display = 'none'
  if (signupErrorMsg) signupErrorMsg.style.display = 'none'
  setTimeout(() => {
    if (!authModal?.classList.contains('is-open')) {
      authModal?.classList.add('hidden')
    }
  }, 250)
}

// Global Modal Events Setup (Runs once)
function setupModalEvents() {
  const authModal = document.querySelector('#auth-modal')
  const btnCloseModal = document.querySelector('#btn-close-modal')
  const btnModalKakao = document.querySelector('#btn-modal-kakao')
  const btnModalGoogle = document.querySelector('#btn-modal-google')
  const formLogin = document.querySelector('#form-local-login')
  const formSignup = document.querySelector('#form-local-signup')
  const btnToggleSignup = document.querySelector('#btn-toggle-signup')
  const btnToggleLogin = document.querySelector('#btn-toggle-login')
  const loginErrorMsg = document.querySelector('#login-error-msg')
  const signupErrorMsg = document.querySelector('#signup-error-msg')

  btnCloseModal?.addEventListener('click', closeAuthModal)
  authModal?.addEventListener('click', (e) => {
    if (e.target === authModal) {
      closeAuthModal()
    }
  })

  btnToggleSignup?.addEventListener('click', () => openAuthModal(true))
  btnToggleLogin?.addEventListener('click', () => openAuthModal(false))

  btnModalKakao?.addEventListener('click', startKakaoLogin)
  btnModalGoogle?.addEventListener('click', startGoogleLogin)

  formLogin?.addEventListener('submit', async (e) => {
    e.preventDefault()
    const email = document.querySelector('#login-email')?.value.trim()
    const password = document.querySelector('#login-password')?.value
    if (loginErrorMsg) loginErrorMsg.style.display = 'none'

    try {
      await login(email, password)
      closeAuthModal()

      try {
        const profile = await getPersonalityProfile()
        if (profile?.onboardingStatus === 'NOT_STARTED') {
          navigateTo('/personality/survey')
          return
        }
      } catch (profileErr) {
        console.warn('성향 상태 조회 실패:', profileErr)
      }

      renderCurrentRoute()
    } catch (err) {
      if (loginErrorMsg) {
        loginErrorMsg.textContent = err.message || '로그인에 실패했습니다.'
        loginErrorMsg.style.display = 'block'
      }
    }
  })

  formSignup?.addEventListener('submit', async (e) => {
    e.preventDefault()
    const email = document.querySelector('#signup-email')?.value.trim()
    const nickname = document.querySelector('#signup-nickname')?.value.trim()
    const password = document.querySelector('#signup-password')?.value
    if (signupErrorMsg) signupErrorMsg.style.display = 'none'

    try {
      await signUp(email, password, nickname)
      await login(email, password)
      closeAuthModal()
      navigateTo('/personality/survey')
    } catch (err) {
      if (signupErrorMsg) {
        signupErrorMsg.textContent = err.message || '회원가입에 실패했습니다.'
        signupErrorMsg.style.display = 'block'
      }
    }
  })
}

setupModalEvents()

function initLandingPage() {
  const token = getAccessToken()
  const btnHeaderLogin = document.querySelector('#btn-header-login')
  const btnHeaderStart = document.querySelector('#btn-header-start')
  const btnHeroMatch = document.querySelector('#btn-hero-match')
  const btnCtaStart = document.querySelector('#btn-cta-start')
  const btnPreviewJoin = document.querySelector('#btn-preview-join')
  const btnSido = document.querySelector('#btn-sido-dropdown')
  const textSido = document.querySelector('#text-sido-selected')
  const listSido = document.querySelector('#list-sido-options')

  const btnSigungu = document.querySelector('#btn-sigungu-dropdown')
  const textSigungu = document.querySelector('#text-sigungu-selected')
  const listSigungu = document.querySelector('#list-sigungu-options')

  let selectedSido = ''
  let selectedSigungu = ''

  if (!token) {
    btnHeaderLogin?.addEventListener('click', () => openAuthModal(false))
    btnHeaderStart?.addEventListener('click', () => openAuthModal(false))
  }

  // Toggle Sido dropdown visibility
  btnSido?.addEventListener('click', (e) => {
    e.stopPropagation()
    listSido?.classList.toggle('hidden')
    listSigungu?.classList.add('hidden')
  })

  // Toggle Sigungu dropdown visibility
  btnSigungu?.addEventListener('click', (e) => {
    e.stopPropagation()
    if (selectedSido) {
      listSigungu?.classList.toggle('hidden')
      listSido?.classList.add('hidden')
    }
  })

  // Close dropdowns when clicking outside
  document.addEventListener('click', () => {
    listSido?.classList.add('hidden')
    listSigungu?.classList.add('hidden')
  })

  // Populate Sido dropdown options
  const populateSidoOptions = () => {
    if (!listSido) return
    listSido.innerHTML = ''
    Object.keys(regionData).forEach((sido) => {
      const li = document.createElement('li')
      li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
      li.textContent = sido
      li.addEventListener('click', () => selectSido(sido))
      listSido.appendChild(li)
    })
  }

  // Handle Sido selection
  const selectSido = (sido) => {
    selectedSido = sido
    if (textSido) {
      textSido.textContent = sido
      textSido.classList.remove('text-secondary')
      textSido.classList.add('text-on-surface', 'font-semibold')
    }

    // Reset Sigungu
    selectedSigungu = ''
    if (textSigungu) {
      textSigungu.textContent = '시·군·구 선택'
      textSigungu.classList.remove('text-on-surface', 'font-semibold')
      textSigungu.classList.add('text-secondary')
    }

    // Enable Sigungu dropdown button
    if (btnSigungu) {
      btnSigungu.disabled = false
      btnSigungu.classList.remove('cursor-not-allowed', 'opacity-50')
      btnSigungu.classList.add('cursor-pointer')
    }

    // Populate Sigungu list
    if (listSigungu && regionData[sido]) {
      listSigungu.innerHTML = ''
      regionData[sido].forEach((region) => {
        const li = document.createElement('li')
        li.className = 'px-4 py-2.5 text-sm hover:bg-primary-container/10 hover:text-primary-container cursor-pointer transition-colors text-on-surface'
        li.textContent = region.name
        li.addEventListener('click', () => selectSigungu(region.name))
        listSigungu.appendChild(li)
      })
    }

    listSido?.classList.add('hidden')
  }

  // Handle Sigungu selection
  const selectSigungu = (sigungu) => {
    selectedSigungu = sigungu
    if (textSigungu) {
      textSigungu.textContent = sigungu
      textSigungu.classList.remove('text-secondary')
      textSigungu.classList.add('text-on-surface', 'font-semibold')
    }
    listSigungu?.classList.add('hidden')
  }

  // Populate Sido dropdown by loading regions dynamically from Backend API
  const loadRegions = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/regions?level=GU`)
      const body = await response.json()
      if (body.success && body.data) {
        regionData = {}
        body.data.forEach((r) => {
          const parts = r.regionName.split(' ')
          const sido = parts[0]
          const sigungu = parts[1] || r.regionName

          if (!regionData[sido]) {
            regionData[sido] = []
          }
          regionData[sido].push({
            name: sigungu,
            lat: r.centerLatitude,
            lng: r.centerLongitude,
          })
        })

        populateSidoOptions()
      }
    } catch (err) {
      console.error('행정구역 데이터를 가져오는데 실패했습니다.', err)
    }
  }

  loadRegions()

  // Hero Quick Match Button
  const handleMatchStart = () => {
    if (!token) {
      openAuthModal(false)
      return
    }
    if (selectedSido && selectedSigungu) {
      const targetList = regionData[selectedSido] || []
      const region = targetList.find((r) => r.name === selectedSigungu)
      if (region) {
        navigateTo(`/map?lat=${region.lat}&lng=${region.lng}&name=${encodeURIComponent(region.name)}`)
      }
    } else {
      alert('활동 지역(시·도 및 시·군·구)을 모두 선택해 주세요.')
      btnSido?.focus()
    }
  }

  btnHeroMatch?.addEventListener('click', handleMatchStart)
  btnCtaStart?.addEventListener('click', () => {
    if (!token) {
      openAuthModal(false)
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      btnSido?.focus()
    }
  })
  btnPreviewJoin?.addEventListener('click', () => {
    if (!token) {
      openAuthModal(false)
    } else {
      alert('민지 님의 식사 테이블에 참가 요청을 보냈습니다!')
    }
  })
}

function initCommonHeader() {
  const token = getAccessToken()
  const headerAuth = document.querySelector('#header-auth')
  if (!headerAuth) return

  if (token) {
    headerAuth.innerHTML = `
      <div class="flex items-center gap-2 sm:gap-3">
        <a href="/personality/survey" class="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-100 hover:bg-slate-200 text-brand-navy text-xs font-bold transition-colors">
          <span class="material-symbols-outlined text-sm text-primary-container">psychology</span>
          <span>식사 성향</span>
        </a>
        <span class="hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-success/15 text-success text-xs font-bold">
          <span class="w-1.5 h-1.5 rounded-full bg-success"></span>
          로그인 됨
        </span>
        <button id="btn-logout" class="btn-secondary px-3 sm:px-4 py-1.5 sm:py-2 rounded-full text-xs sm:text-sm font-semibold">
          로그아웃
        </button>
      </div>
    `
    document.querySelector('#btn-logout')?.addEventListener('click', async (e) => {
      e.preventDefault()
      e.stopPropagation()
      try {
        await logout()
      } catch (error) {
        console.warn('로그아웃 요청 실패 (로컬 세션은 정리합니다):', error)
      } finally {
        clearAccessToken()
        navigateTo('/')
      }
    })
  } else {
    headerAuth.innerHTML = `
      <div class="flex items-center gap-3">
        <button id="btn-header-login" class="btn-secondary px-4 py-2 rounded-full text-xs sm:text-sm font-semibold">
          로그인
        </button>
        <button id="btn-header-start" class="btn-primary px-4 py-2 rounded-full text-xs sm:text-sm font-semibold flex items-center gap-1.5 shadow-md">
          <span>시작하기</span>
          <span class="material-symbols-outlined text-base">arrow_forward</span>
        </button>
      </div>
    `
    document.querySelector('#btn-header-login')?.addEventListener('click', () => openAuthModal(false))
    document.querySelector('#btn-header-start')?.addEventListener('click', () => openAuthModal(false))
  }
}

function renderProfileSetup(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card" aria-labelledby="profile-title">
        <div class="brand-mark" aria-hidden="true">
          <span class="material-symbols-outlined text-3xl">face</span>
        </div>
        <p class="eyebrow">PROFILE SETUP</p>
        <h1 id="profile-title">프로필 설정</h1>
        <p class="description">
          임시 닉네임이 발급되었습니다. 나만의 식사 성향과 취향을 설정하여 완벽한 마주한끼를 만나보세요.
        </p>
        <a class="primary-link" href="/">홈으로 이동하기</a>
      </section>
    </main>
  `
}

function renderMapPage(container) {
  const params = new URLSearchParams(window.location.search)
  const lat = parseFloat(params.get('lat')) || 37.5662
  const lng = parseFloat(params.get('lng')) || 126.9016
  const name = params.get('name') || '마포구'

  container.innerHTML = `
    <main class="max-w-[1440px] mx-auto px-margin-mobile md:px-margin-desktop py-8 flex flex-col gap-6 w-full">
      <!-- 타이틀 바 -->
      <div class="flex items-center justify-between border-b border-outline-variant/30 pb-4">
        <div>
          <h1 class="font-headline text-2xl font-bold text-brand-navy">마주한끼 찾기</h1>
          <p class="text-sm text-secondary">선택한 지역: <strong class="text-primary-container">${name}</strong></p>
        </div>
        <a href="/" class="btn-secondary px-4 py-2 rounded-full text-sm font-semibold flex items-center gap-1">
          <span class="material-symbols-outlined text-lg">home</span>
          <span>홈으로</span>
        </a>
      </div>

      <!-- 지도 및 컨트롤 영역 (가로 너비 max-w-[1440px] 제한, 세로 높이 450px 컴팩트화) -->
      <div class="w-full flex flex-col gap-4">
        <div class="w-full bg-white border border-outline-variant/30 rounded-card shadow-soft overflow-hidden" id="map" style="height: 450px; min-height: 450px;">
          <!-- 카카오맵이 여기에 렌더링됩니다. -->
        </div>

        <!-- 하단 액션 바 -->
        <div class="flex justify-end">
          <button id="btn-confirm-location" class="btn-primary py-2.5 px-6 rounded-full text-sm font-bold flex items-center gap-2 shadow-md">
            <span class="material-symbols-outlined text-sm">check_circle</span>
            <span>이 위치로 핀 확정 테스트</span>
          </button>
        </div>
      </div>
    </main>
  `

  initKakaoMap(lat, lng, name)
}

function initKakaoMap(lat, lng, name) {
  const checkKakao = setInterval(() => {
    if (window.kakao && window.kakao.maps) {
      clearInterval(checkKakao)

      window.kakao.maps.load(() => {
        const container = document.getElementById('map')
        if (!container) return

        const options = {
          center: new window.kakao.maps.LatLng(lat, lng),
          level: 4,
        }

        const map = new window.kakao.maps.Map(container, options)

        const markerPosition = new window.kakao.maps.LatLng(lat, lng)
        const marker = new window.kakao.maps.Marker({
          position: markerPosition,
          draggable: true,
        })

        marker.setMap(map)

        window.kakao.maps.event.addListener(marker, 'dragend', () => {
          const position = marker.getPosition()
          console.log(`변경된 위치 좌표: Lat=${position.getLat()}, Lng=${position.getLng()}`)
        })

        window.kakao.maps.event.addListener(map, 'click', (mouseEvent) => {
          const latlng = mouseEvent.latLng
          marker.setPosition(latlng)
          console.log(`클릭한 위치 좌표: Lat=${latlng.getLat()}, Lng=${latlng.getLng()}`)
        })

        document.querySelector('#btn-confirm-location')?.addEventListener('click', () => {
          const finalPos = marker.getPosition()
          alert(`[${name}] 핀 위치 확정!\n위도: ${finalPos.getLat()}\n경도: ${finalPos.getLng()}`)
        })
      })
    }
  }, 100)
}
