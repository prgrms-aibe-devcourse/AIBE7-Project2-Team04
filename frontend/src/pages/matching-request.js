import '../matching/matching-request.css'
import { getAccessToken } from '../auth/token-storage.js'
import { navigateTo, regionTree, showToast } from '../main.js'
import {
  MatchingApiError,
  cancelRealtimeMatchRequest,
  createRealtimeMatchRequest,
  decideMatchProposal,
  getCurrentMatchProposal,
  getCurrentRealtimeMatchRequest,
  getLatestMatchResult,
  getPreferredRegion,
} from '../matching/matching-api.js'
import { extractKeywordsFromText, getPersonalityProfile } from '../personality/personality-api.js'
import { API_BASE_URL } from '../config/api.js'

const TAG_GROUPS = [
  {
    name: '대화 방식',
    tags: [
      ['INITIATES_CONVERSATION', '먼저 대화를 시작해요'],
      ['GOOD_LISTENER', '상대 이야기를 잘 들어요'],
      ['FOOD_TALK', '음식 이야기를 좋아해요'],
      ['LIGHT_CHAT', '가벼운 대화를 좋아해요'],
      ['DEEP_TALK', '깊은 대화를 좋아해요'],
      ['COMFORTABLE_SILENCE', '조용한 시간도 편해요'],
    ],
  },
  {
    name: '식사 분위기',
    tags: [
      ['CALM_ATMOSPHERE', '차분한 분위기가 좋아요'],
      ['CHEERFUL_ATMOSPHERE', '유쾌한 분위기가 좋아요'],
      ['ACTIVE_ATMOSPHERE', '활발한 분위기가 좋아요'],
    ],
  },
  {
    name: '식사 습관',
    tags: [
      ['SHARE_DISHES', '여러 메뉴를 나눠 먹어요'],
      ['TAKE_FOOD_PHOTOS', '음식 사진을 찍는 편이에요'],
      ['ENJOY_DESSERT', '디저트까지 함께 즐겨요'],
      ['FOCUS_ON_MEAL', '식사 자체에 집중하는 편이에요'],
    ],
  },
]

const TAG_LABELS = new Map(TAG_GROUPS.flatMap((group) => group.tags))
const TAG_DETAILS = new Map([
  ['INITIATES_CONVERSATION', { icon: 'forum', description: '먼저 말을 걸며 어색함을 풀어요.' }],
  ['GOOD_LISTENER', { icon: 'hearing', description: '상대의 이야기에 귀 기울여요.' }],
  ['FOOD_TALK', { icon: 'restaurant', description: '메뉴와 맛있는 이야기를 나눠요.' }],
  ['LIGHT_CHAT', { icon: 'chat_bubble', description: '가볍고 편하게 대화해요.' }],
  ['DEEP_TALK', { icon: 'psychology', description: '생각과 취향을 깊게 나눠요.' }],
  ['COMFORTABLE_SILENCE', { icon: 'self_improvement', description: '말없이 있어도 편안해요.' }],
  ['CALM_ATMOSPHERE', { icon: 'spa', description: '차분하고 여유로운 식사를 원해요.' }],
  ['CHEERFUL_ATMOSPHERE', { icon: 'sentiment_very_satisfied', description: '밝고 유쾌한 분위기를 좋아해요.' }],
  ['ACTIVE_ATMOSPHERE', { icon: 'celebration', description: '활기차고 에너지 있는 분위기를 좋아해요.' }],
  ['SHARE_DISHES', { icon: 'group_work', description: '여러 메뉴를 함께 나눠 먹어요.' }],
  ['TAKE_FOOD_PHOTOS', { icon: 'photo_camera', description: '맛있는 순간을 사진으로 남겨요.' }],
  ['ENJOY_DESSERT', { icon: 'cake', description: '식사 후 디저트도 함께 즐겨요.' }],
  ['FOCUS_ON_MEAL', { icon: 'restaurant_menu', description: '식사와 맛에 집중하는 편이에요.' }],
])

const FOOD_CATEGORIES = [
  ['KOREAN', '한식', '🍚'],
  ['JAPANESE', '일식', '🍣'],
  ['CHINESE', '중식', '🥟'],
  ['WESTERN', '양식', '🍕'],
  ['SOUTHEAST_ASIAN', '동남아 음식', '🍜'],
  ['SNACK', '분식', '🍢'],
  ['FAST_FOOD', '패스트푸드', '🍔'],
  ['CAFE_DESSERT', '카페·디저트', '☕'],
]

const FOOD_LABELS = new Map(FOOD_CATEGORIES.map(([code, label]) => [code, label]))
const FOOD_ICONS = new Map([
  ['KOREAN', 'rice_bowl'],
  ['JAPANESE', 'ramen_dining'],
  ['CHINESE', 'soup_kitchen'],
  ['WESTERN', 'local_pizza'],
  ['SOUTHEAST_ASIAN', 'set_meal'],
  ['SNACK', 'bakery_dining'],
  ['FAST_FOOD', 'fastfood'],
  ['CAFE_DESSERT', 'local_cafe'],
])
const MATCHING_POLL_INTERVAL = 3000
const DEFAULT_SEARCH_RADIUS = 3000
const EXPANDED_SEARCH_RADIUS = 5000
const MATCHING_FORM_STEPS = [
  {
    label: '언제·어디서',
    title: '언제, 어디서 만날까요?',
    description: '약속할 지역과 시간을 먼저 정해 주세요.',
  },
  {
    label: '무엇을 먹을지',
    title: '무엇을 먹을까요?',
    description: '함께 먹고 싶은 음식 카테고리를 골라 주세요.',
  },
  {
    label: '원하는 상대 성향',
    title: '어떤 성향의 상대와 함께하고 싶나요?',
    description: '내 성향이 아니라, 함께 만나고 싶은 밥친구의 특징을 골라 주세요.',
  },
]

/**
 * 지도에서 확정한 핀과 매칭 조건을 단계별로 제출하고,
 * REST 복구 조회와 STOMP 개인 큐를 함께 사용하는 실시간 매칭 화면입니다.
 */
export async function renderMatchingRequestPage(container) {
  if (!getAccessToken()) {
    navigateTo('/')
    return
  }

  if (typeof window.__matchingRequestCleanup === 'function') {
    window.__matchingRequestCleanup()
  }

  let disposed = false
  let pollTimer = null
  let tickerTimer = null
  let reconnectTimer = null
  let stompClient = null
  let proposalSubscription = null
  let resultSubscription = null
  let isRefreshing = false
  let lastErrorToastMessage = ''

  const queryLocation = readLocationFromQuery()
  const defaultDesiredTimeSlot = getDefaultDateTimeValue()
  const state = {
    initialLoading: true,
    locationError: '',
    errorMessage: '',
    noticeMessage: '',
    mode: 'form',
    formStep: 1,
    personalityProfileCompleted: false,
    radiusExpansionOffer: false,
    radiusExpansionUsed: false,
    location: queryLocation,
    foodCategory: 'KOREAN',
    desiredTimeSlot: defaultDesiredTimeSlot,
    scheduleCalendarMonth: getMonthValueFromDateTime(defaultDesiredTimeSlot),
    isScheduleDateModalOpen: false,
    scheduleDraftDate: '',
    isScheduleTimeModalOpen: false,
    scheduleDraftTime: '',
    locationName: queryLocation.locationName || '',
    searchRadius: DEFAULT_SEARCH_RADIUS,
    selectedTags: new Set(),
    personalityGroupIndex: 0,
    desiredPersonalityText: '',
    desiredExtractedKeywords: [],
    isExtractingDesiredKeywords: false,
    currentRequest: null,
    currentProposal: null,
    latestResult: null,
    hasSubmittedRequest: false,
    hasRequestDetails: false,
    isSubmitting: false,
    isCancelling: false,
    isDeciding: false,
    expiryHandled: false,
  }

  const cleanup = () => {
    disposed = true
    document.body.classList.remove('matching-time-modal-open')
    if (pollTimer) clearInterval(pollTimer)
    if (tickerTimer) clearInterval(tickerTimer)
    if (reconnectTimer) clearTimeout(reconnectTimer)
    pollTimer = null
    tickerTimer = null
    reconnectTimer = null
    disconnectRealtime()
    if (window.__matchingRequestCleanup === cleanup) {
      delete window.__matchingRequestCleanup
    }
  }

  window.__matchingRequestCleanup = cleanup
  render()

  try {
    await Promise.all([loadLocation(), loadPersonalityProfile()])
    if (disposed) return
    state.initialLoading = false
    render()
    await refreshFlow({ checkResult: true })
    if (disposed) return
    startPolling()
    connectRealtime()
  } catch (error) {
    if (disposed) return
    state.initialLoading = false
    if (isUnauthorized(error)) {
      navigateTo('/')
      return
    }
    state.locationError = getUserErrorMessage(error, '매칭 화면을 준비하지 못했습니다. 네트워크 상태를 확인해 주세요.')
    render()
  }

  async function loadLocation() {
    let location = enrichLocation(state.location)
    if (!isCompleteLocation(location)) {
      const preferredRegion = await getPreferredRegion()
      if (!preferredRegion?.locationServiceConsent || !preferredRegion.regionCode) {
        throw new MatchingApiError('매칭을 시작하려면 먼저 선호 활동지역과 위치 이용 동의를 설정해 주세요.', {
          status: 403,
          code: 'MATCHING_005',
        })
      }
      location = enrichLocation({
        regionCode: preferredRegion.regionCode,
        regionName: preferredRegion.regionName,
      })
    }

    if (!isCompleteLocation(location)) {
      throw new MatchingApiError('선택한 지역의 지도 위치를 확인하지 못했습니다.')
    }
    state.location = location
  }

  async function loadPersonalityProfile() {
    try {
      const profile = await getPersonalityProfile()
      state.personalityProfileCompleted = profile?.completed === true
        || profile?.onboardingStatus === 'COMPLETED'
    } catch (error) {
      if (isUnauthorized(error)) throw error
      // 성향 안내 카드는 조회 실패 시에도 기본 노출해 매칭 진입을 막지 않습니다.
      state.personalityProfileCompleted = false
    }
  }

  async function refreshFlow({ checkResult = false } = {}) {
    if (disposed || isRefreshing) return
    if (!checkResult && !state.hasSubmittedRequest && !state.currentRequest && !state.currentProposal) return

    isRefreshing = true
    try {
      const [requestResult, proposalResult] = await Promise.allSettled([
        getCurrentRealtimeMatchRequest(),
        getCurrentMatchProposal(),
      ])
      if (disposed) return

      const requestFound = requestResult.status === 'fulfilled'
      const proposalFound = proposalResult.status === 'fulfilled'
      const requestNotFound = isNotFound(requestResult.reason)
      const proposalNotFound = isNotFound(proposalResult.reason)
      const proposalClosed = proposalNotFound || proposalResult.reason?.status === 409

      if (requestFound) {
        state.currentRequest = requestResult.value
        state.hasSubmittedRequest = true
        state.radiusExpansionOffer = false
      } else if (requestNotFound) {
        state.currentRequest = null
      } else if (isUnauthorized(requestResult.reason)) {
        navigateTo('/')
        return
      } else if (checkResult) {
        state.errorMessage = getUserErrorMessage(requestResult.reason, '현재 매칭 상태를 확인하지 못했습니다.')
      }

      if (proposalFound) {
        if (proposalResult.value?.status === 'PENDING') {
          if (state.currentProposal?.proposalId !== proposalResult.value.proposalId) {
            state.expiryHandled = false
          }
          state.currentProposal = proposalResult.value
          state.hasSubmittedRequest = true
          state.radiusExpansionOffer = false
        } else {
          state.currentProposal = null
        }
      } else if (proposalNotFound) {
        state.currentProposal = null
      } else if (isUnauthorized(proposalResult.reason)) {
        navigateTo('/')
        return
      } else if (proposalResult.reason?.status === 409) {
        state.currentProposal = null
        if (!state.noticeMessage) {
          state.noticeMessage = '후보 제안의 응답 시간이 끝났습니다. 다시 매칭을 탐색합니다.'
        }
      } else if (checkResult && !state.errorMessage) {
        state.errorMessage = getUserErrorMessage(proposalResult.reason, '후보 제안을 확인하지 못했습니다.')
      }

      if (requestNotFound && proposalClosed && (checkResult || state.hasSubmittedRequest)) {
        const latestResult = await getLatestResultSafely()
        if (latestResult && latestResult.status === 'MATCHED') {
          applyMatchResult(latestResult)
          return
        }
        if (state.hasSubmittedRequest) {
          state.hasSubmittedRequest = false
          state.radiusExpansionOffer = !state.radiusExpansionUsed
            && Number(state.searchRadius) === DEFAULT_SEARCH_RADIUS
          state.noticeMessage = state.radiusExpansionOffer
            ? ''
            : '주변에 후보를 찾지 못했어요. 조건을 바꿔 다시 시작해 보세요.'
        }
      }

      syncMode()
      render()
    } catch (error) {
      if (disposed) return
      if (isUnauthorized(error)) {
        navigateTo('/')
        return
      }
      if (checkResult) state.errorMessage = getUserErrorMessage(error, '매칭 상태를 확인하지 못했습니다.')
      render()
    } finally {
      isRefreshing = false
    }
  }

  async function getLatestResultSafely() {
    try {
      return await getLatestMatchResult()
    } catch (error) {
      if (isUnauthorized(error)) {
        navigateTo('/')
        return null
      }
      if (!isNotFound(error) && !state.noticeMessage) {
        state.noticeMessage = getUserErrorMessage(error, '최근 매칭 결과를 확인하지 못했습니다.')
      }
      return null
    }
  }

  function startPolling() {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = setInterval(() => {
      refreshFlow()
    }, MATCHING_POLL_INTERVAL)
  }

  function connectRealtime() {
    if (disposed || stompClient || !window.SockJS || !window.Stomp) {
      return
    }

    const socket = new window.SockJS(`${API_BASE_URL}/ws-chat`)
    stompClient = window.Stomp.over(socket)
    stompClient.debug = () => {}
    stompClient.connect({}, () => {
      if (disposed) return
      proposalSubscription = stompClient.subscribe('/user/queue/match-proposal', (message) => {
        const payload = parseMessage(message)
        if (payload) handleProposal(payload)
      })
      resultSubscription = stompClient.subscribe('/user/queue/match-result', (message) => {
        const payload = parseMessage(message)
        if (payload) handleMatchResult(payload)
      })
      refreshFlow()
    }, () => {
      stompClient = null
      scheduleReconnect()
    })
  }

  function scheduleReconnect() {
    if (disposed || reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connectRealtime()
    }, 5000)
  }

  function disconnectRealtime() {
    try {
      if (proposalSubscription) proposalSubscription.unsubscribe()
      if (resultSubscription) resultSubscription.unsubscribe()
    } catch {
      // 페이지를 떠날 때 이미 종료된 구독은 무시합니다.
    }
    proposalSubscription = null
    resultSubscription = null
    if (stompClient) {
      try {
        stompClient.disconnect(() => {})
      } catch {
        // 페이지를 떠날 때 이미 닫힌 WebSocket은 무시합니다.
      }
    }
    stompClient = null
  }

  function handleProposal(payload) {
    if (disposed || !payload?.proposalId || payload.status !== 'PENDING') return
    state.currentProposal = payload
    state.currentRequest = state.currentRequest || {
      status: 'CONFIRMING',
      expiresAt: payload.expiresAt,
    }
    state.latestResult = null
    state.hasSubmittedRequest = true
    state.radiusExpansionOffer = false
    state.expiryHandled = false
    state.noticeMessage = ''
    syncMode()
    render()
  }

  function handleMatchResult(payload) {
    if (disposed || !payload?.matchId || !payload?.chatRoomId) return
    applyMatchResult(payload)
  }

  function applyMatchResult(payload) {
    state.latestResult = payload
    sessionStorage.setItem('project2.latestMatchResult', JSON.stringify(payload))
    state.currentRequest = null
    state.currentProposal = null
    state.hasSubmittedRequest = false
    state.radiusExpansionOffer = false
    state.mode = 'matched'
    state.noticeMessage = ''
    render()
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (state.isSubmitting) return
    syncFormState()

    if (state.formStep < MATCHING_FORM_STEPS.length) {
      handleNextStep()
      return
    }

    const validationMessage = validateForm()
    if (validationMessage) {
      const invalidStep = findFirstInvalidStep()
      if (invalidStep) state.formStep = invalidStep.step
      state.errorMessage = validationMessage
      render()
      focusMatchingStep()
      return
    }

    await submitMatchingRequest()
  }

  async function submitMatchingRequest({
    noticeMessage = '매칭 요청이 등록되었습니다. 잘 맞는 밥친구를 찾고 있어요.',
    radiusExpansion = false,
  } = {}) {
    if (state.isSubmitting) return

    state.isSubmitting = true
    state.errorMessage = ''
    state.radiusExpansionOffer = false
    render()

    try {
      const response = await createRealtimeMatchRequest({
        foodCategory: state.foodCategory,
        desiredTimeSlot: toInstant(state.desiredTimeSlot),
        regionCode: state.location.regionCode,
        regionName: state.location.regionName,
        locationName: state.locationName.trim() || null,
        latitude: state.location.latitude,
        longitude: state.location.longitude,
        searchRadius: Number(state.searchRadius),
        desiredPersonalityTags: Array.from(state.selectedTags),
        desiredPersonalityText: state.desiredPersonalityText.trim() || null,
      })
      if (disposed) return
      state.currentRequest = response
      state.currentProposal = null
      state.latestResult = null
      state.hasSubmittedRequest = true
      state.hasRequestDetails = true
      state.radiusExpansionUsed = radiusExpansion
      state.noticeMessage = noticeMessage
      syncMode()
      render()
      await refreshFlow()
    } catch (error) {
      if (disposed) return
      if (isUnauthorized(error)) {
        navigateTo('/')
        return
      }
      state.errorMessage = getUserErrorMessage(error, '매칭 요청을 등록하지 못했습니다.')
      if (error.code === 'MATCHING_003') {
        await refreshFlow({ checkResult: true })
      } else {
        render()
      }
    } finally {
      if (!disposed) {
        state.isSubmitting = false
        render()
      }
    }
  }

  async function handleExpandRadius() {
    if (state.isSubmitting) return
    syncFormState()
    state.searchRadius = EXPANDED_SEARCH_RADIUS
    state.errorMessage = ''
    state.noticeMessage = '탐색 반경을 5km로 넓혀 다시 찾아볼게요.'

    const validationMessage = validateForm()
    if (validationMessage) {
      const invalidStep = findFirstInvalidStep()
      if (invalidStep) state.formStep = invalidStep.step
      state.errorMessage = validationMessage
      render()
      focusMatchingStep()
      return
    }

    await submitMatchingRequest({
      noticeMessage: '탐색 반경을 5km로 넓혀 다시 찾아볼게요.',
      radiusExpansion: true,
    })
  }

  function handleCancelRadiusExpansion() {
    if (state.isSubmitting) return
    state.radiusExpansionOffer = false
    state.radiusExpansionUsed = false
    state.searchRadius = DEFAULT_SEARCH_RADIUS
    state.hasRequestDetails = false
    state.formStep = 1
    state.errorMessage = ''
    state.noticeMessage = '매칭 요청을 취소했습니다. 조건을 바꿔 다시 시작할 수 있어요.'
    render()
    focusMatchingStep()
  }

  async function handleCancel() {
    if (state.isCancelling || !state.currentRequest?.requestId) return
    if (!window.confirm('현재 매칭 요청을 취소할까요?')) return

    state.isCancelling = true
    state.errorMessage = ''
    render()
    try {
      await cancelRealtimeMatchRequest(state.currentRequest.requestId)
      if (disposed) return
      state.currentRequest = null
      state.currentProposal = null
      state.hasSubmittedRequest = false
      state.hasRequestDetails = false
      state.mode = 'form'
      state.formStep = 1
      state.radiusExpansionOffer = false
      state.radiusExpansionUsed = false
      state.searchRadius = DEFAULT_SEARCH_RADIUS
      state.noticeMessage = '매칭 요청을 취소했습니다. 조건을 바꿔 다시 시작할 수 있어요.'
    } catch (error) {
      if (disposed) return
      if (isUnauthorized(error)) {
        navigateTo('/')
        return
      }
      state.errorMessage = getUserErrorMessage(error, '매칭 요청을 취소하지 못했습니다.')
      await refreshFlow({ checkResult: true })
    } finally {
      if (!disposed) {
        state.isCancelling = false
        render()
      }
    }
  }

  async function handleDecision(decision) {
    const proposal = state.currentProposal
    if (state.isDeciding || !proposal?.proposalId || proposal.myDecision !== 'PENDING') return

    state.isDeciding = true
    state.errorMessage = ''
    render()
    try {
      const updatedProposal = await decideMatchProposal(proposal.proposalId, decision)
      if (disposed) return

      if (updatedProposal?.status === 'MATCHED') {
        state.noticeMessage = '양쪽 모두 수락했습니다. 매칭 결과를 확인하고 있어요.'
        await refreshFlow({ checkResult: true })
      } else if (decision === 'ACCEPT') {
        state.currentProposal = updatedProposal
        state.noticeMessage = '수락을 완료했습니다. 상대방의 응답을 기다리고 있어요.'
        syncMode()
        render()
      } else {
        state.currentProposal = null
        state.noticeMessage = '이번 후보 제안을 거절했습니다. 다시 매칭을 탐색합니다.'
        await refreshFlow()
      }
    } catch (error) {
      if (disposed) return
      if (isUnauthorized(error)) {
        navigateTo('/')
        return
      }
      state.currentProposal = null
      state.noticeMessage = error.status === 409
        ? '응답 시간이 끝났거나 다른 매칭이 먼저 확정되었습니다. 현재 상태를 다시 확인합니다.'
        : ''
      state.errorMessage = getUserErrorMessage(error, '후보 제안에 응답하지 못했습니다.')
      await refreshFlow({ checkResult: true })
    } finally {
      if (!disposed) {
        state.isDeciding = false
        render()
      }
    }
  }

  function startNewRequest() {
    sessionStorage.removeItem('project2.latestMatchResult')
    document.documentElement.classList.remove('has-cached-chat')
    state.mode = 'form'
    state.formStep = 1
    state.latestResult = null
    state.currentRequest = null
    state.currentProposal = null
    state.hasSubmittedRequest = false
    state.hasRequestDetails = false
    state.radiusExpansionOffer = false
    state.radiusExpansionUsed = false
    state.errorMessage = ''
    state.noticeMessage = ''
    state.desiredTimeSlot = getDefaultDateTimeValue()
    state.scheduleCalendarMonth = getMonthValueFromDateTime(state.desiredTimeSlot)
    state.isScheduleDateModalOpen = false
    state.scheduleDraftDate = ''
    state.isScheduleTimeModalOpen = false
    state.scheduleDraftTime = ''
    state.searchRadius = DEFAULT_SEARCH_RADIUS
    state.personalityGroupIndex = 0
    render()
  }

  function syncFormState() {
    const form = container.querySelector('#matching-request-form')
    if (!form) return
    const foodCategory = form.querySelector('[name="foodCategory"]')
    const desiredTimeSlot = form.querySelector('[name="desiredTimeSlot"]')
    const desiredTimeSlotTime = form.querySelector('[name="desiredTimeSlotTime"]')
    const locationName = form.querySelector('[name="locationName"]')
    const desiredPersonalityText = form.querySelector('[name="desiredPersonalityText"]')
    if (foodCategory) state.foodCategory = foodCategory.value || state.foodCategory
    if (desiredTimeSlot) state.desiredTimeSlot = desiredTimeSlot.value || state.desiredTimeSlot
    if (desiredTimeSlotTime?.value) {
      const { date } = splitDateTimeValue(state.desiredTimeSlot)
      if (date) state.desiredTimeSlot = `${date}T${desiredTimeSlotTime.value}`
    }
    if (locationName) state.locationName = locationName.value || ''
    if (desiredPersonalityText) state.desiredPersonalityText = desiredPersonalityText.value || ''
  }

  function handleNextStep() {
    if (state.formStep >= MATCHING_FORM_STEPS.length) return
    syncFormState()
    const validationMessage = validateStep(state.formStep)
    if (validationMessage) {
      state.errorMessage = validationMessage
      render()
      focusMatchingStep()
      return
    }
    state.errorMessage = ''
    state.formStep += 1
    render()
    focusMatchingStep()
  }

  function handlePreviousStep() {
    if (state.formStep <= 1) return
    syncFormState()
    state.errorMessage = ''
    state.formStep -= 1
    render()
    focusMatchingStep()
  }

  function focusMatchingStep() {
    window.setTimeout(() => {
      const heading = container.querySelector('#matching-step-title')
      if (!heading) return
      heading.focus({ preventScroll: true })
      heading.scrollIntoView({ block: 'start', behavior: 'smooth' })
    }, 0)
  }

  function validateStep(step) {
    if (step === 1) {
      if (!isCompleteLocation(state.location)) {
        return '지도에서 약속 위치를 먼저 확정해 주세요.'
      }
      if (!state.desiredTimeSlot || Number.isNaN(new Date(state.desiredTimeSlot).getTime())) {
        return '희망 식사 일시를 입력해 주세요.'
      }
      if (new Date(state.desiredTimeSlot).getTime() <= Date.now()) {
        return '희망 식사 일시는 현재보다 이후로 선택해 주세요.'
      }
      if (![DEFAULT_SEARCH_RADIUS, EXPANDED_SEARCH_RADIUS].includes(Number(state.searchRadius))) {
        return '탐색 반경은 기본 3km 또는 확장 5km만 사용할 수 있어요.'
      }
      return ''
    }

    if (step === 2) {
      return FOOD_LABELS.has(state.foodCategory) ? '' : '식사 카테고리를 선택해 주세요.'
    }

    if (state.selectedTags.size < 3 || state.selectedTags.size > 5) {
      return '희망 상대 성향 태그를 3개 이상 5개 이하로 선택해 주세요.'
    }
    if (Array.from(state.selectedTags).some((tag) => !TAG_LABELS.has(tag))) {
      return '지원하지 않는 성향 태그가 포함되어 있습니다. 다시 선택해 주세요.'
    }
    if (state.desiredPersonalityText.length > 300) {
      return '희망 상대 설명은 300자 이하로 입력해 주세요.'
    }
    return ''
  }

  function findFirstInvalidStep() {
    for (let step = 1; step <= MATCHING_FORM_STEPS.length; step += 1) {
      const message = validateStep(step)
      if (message) return { step, message }
    }
    return null
  }

  function validateForm() {
    return findFirstInvalidStep()?.message || ''
  }

  function render() {
    if (disposed) return
    document.body.classList.toggle('matching-time-modal-open', state.isScheduleDateModalOpen || state.isScheduleTimeModalOpen)
    if (state.errorMessage && state.errorMessage !== lastErrorToastMessage) {
      lastErrorToastMessage = state.errorMessage
      showToast(state.errorMessage, { type: 'error' })
    } else if (!state.errorMessage) {
      lastErrorToastMessage = ''
    }
    if (state.initialLoading) {
      container.innerHTML = renderLoading()
      return
    }

    const content = state.locationError
      ? renderLocationError()
      : state.mode === 'matched'
        ? renderMatchedResult()
        : state.mode === 'proposal'
          ? renderProposal()
          : state.mode === 'waiting' || state.mode === 'confirming'
            ? renderWaiting()
            : renderRequestForm()

    container.innerHTML = `
      <main class="matching-page flex-grow px-4 py-8 sm:px-6 lg:px-8">
        <div class="mx-auto w-full max-w-5xl">
          <header class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <a href="/" class="mb-3 inline-flex items-center gap-1 text-xs font-bold text-secondary transition-colors hover:text-primary-container">
                <span class="material-symbols-outlined text-base">arrow_back</span>
                홈으로
              </a>
              <h1 class="font-headline text-3xl font-extrabold tracking-tight text-brand-navy sm:text-4xl">오늘의 밥친구를 찾아볼까요?</h1>
              <p class="mt-2 max-w-2xl text-sm leading-6 text-secondary sm:text-base">몇 가지 조건을 차례로 알려주면, 서로 편안하게 마주 앉을 수 있는 상대를 찾아드려요.</p>
            </div>
          </header>

          ${content}

          <p class="mt-6 text-center text-xs leading-5 text-secondary">
            음식 카테고리는 일반적인 선호 정보입니다. 알레르기·식단 제한 적합성을 보장하지 않으니 식사 전 상대방과 꼭 직접 확인해 주세요.
          </p>
        </div>
      </main>
    `
    bindEvents()
    syncTicker()
  }

  function renderLoading() {
    return `
      <main class="matching-page flex-grow px-4 py-8 sm:px-6 lg:px-8">
        <div class="mx-auto w-full max-w-5xl">
          <div class="matching-card rounded-[28px] bg-white p-6 sm:p-10">
            <div class="matching-skeleton mb-4 h-4 w-32 rounded-full"></div>
            <div class="matching-skeleton mb-3 h-10 w-3/4 rounded-xl"></div>
            <div class="matching-skeleton mb-10 h-4 w-full max-w-xl rounded-full"></div>
            <div class="grid gap-5 lg:grid-cols-[1.1fr_0.9fr]">
              <div class="matching-skeleton h-56 rounded-3xl"></div>
              <div class="matching-skeleton h-56 rounded-3xl"></div>
            </div>
          </div>
        </div>
      </main>
    `
  }

  function renderLocationError() {
    return `
      <section class="matching-card rounded-[28px] bg-white p-7 text-center sm:p-12">
        <div class="mx-auto mb-5 grid h-16 w-16 place-items-center rounded-full bg-primary-container/10 text-primary-container">
          <span class="material-symbols-outlined text-3xl">location_on</span>
        </div>
        <h2 class="font-headline text-2xl font-extrabold text-brand-navy">활동지역 설정이 필요해요</h2>
        <p class="mx-auto mt-3 max-w-md text-sm leading-6 text-secondary">${escapeHtml(state.locationError || '매칭에 사용할 선호 활동지역을 설정해 주세요.')}</p>
        <div class="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
          <a href="/map?mode=preferred" class="btn-primary inline-flex min-h-12 items-center justify-center gap-2 rounded-full px-6 text-sm font-bold shadow-md">
            <span class="material-symbols-outlined text-lg">edit_location</span>
            활동지역 설정하기
          </a>
          <a href="/" class="btn-secondary inline-flex min-h-12 items-center justify-center rounded-full px-6 text-sm font-bold">홈으로</a>
        </div>
      </section>
    `
  }

  function renderRequestForm() {
    const step = state.formStep
    const stepMeta = MATCHING_FORM_STEPS[step - 1]
    const progress = Math.round((step / MATCHING_FORM_STEPS.length) * 100)
    const stepContent = step === 1
      ? renderScheduleStep()
      : step === 2
        ? renderFoodStep()
        : renderPersonalityStep()

    return `
      <section class="matching-card matching-form-card mx-auto max-w-3xl rounded-[28px] bg-white p-5 sm:p-8" aria-labelledby="matching-form-title">
        ${state.radiusExpansionOffer ? `
          <div class="matching-radius-offer mb-7 rounded-2xl border border-primary-container/30 bg-primary-container/10 p-4 text-left sm:p-5" role="status" aria-live="polite">
            <div class="flex items-start gap-3">
              <span class="material-symbols-outlined shrink-0 rounded-full bg-white p-2 text-primary-container shadow-sm">search</span>
              <div class="min-w-0">
                <p class="text-sm font-extrabold text-brand-navy">주변에 후보가 없어요.</p>
                <p class="mt-1 text-sm leading-6 text-secondary">탐색 반경을 넓혀볼까요?</p>
              </div>
            </div>
            <div class="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-end">
              <button id="btn-expand-radius" type="button" ${state.isSubmitting ? 'disabled' : ''} class="btn-primary inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-extrabold shadow-md disabled:cursor-not-allowed disabled:opacity-60">
                <span class="material-symbols-outlined text-lg">north_east</span>
                네
              </button>
              <button id="btn-cancel-radius-expansion" type="button" ${state.isSubmitting ? 'disabled' : ''} class="btn-secondary inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-60">
                <span class="material-symbols-outlined text-lg">close</span>
                매칭 취소
              </button>
            </div>
          </div>
        ` : ''}
        <div class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div class="flex items-center gap-2">
              <span class="inline-flex items-center rounded-md bg-primary-container/15 px-2.5 py-1 text-xs font-extrabold tracking-wide text-primary">STEP ${step} / ${MATCHING_FORM_STEPS.length}</span>
              <span class="hidden text-xs font-semibold text-secondary sm:inline">매칭 조건 설정</span>
            </div>
            <h2 id="matching-form-title" class="sr-only">매칭 조건 설정</h2>
          </div>
        </div>

        <div class="matching-stepper" aria-label="매칭 조건 진행 단계">
          ${MATCHING_FORM_STEPS.map((item, index) => {
            const stepNumber = index + 1
            const isCurrent = stepNumber === step
            const isComplete = stepNumber < step
            return `
              <div class="matching-stepper-item ${isCurrent ? 'is-current' : ''} ${isComplete ? 'is-complete' : ''}" ${isCurrent ? 'aria-current="step"' : ''}>
                <span class="matching-stepper-node">${isComplete ? '<span class="material-symbols-outlined text-sm">check</span>' : stepNumber}</span>
                <span class="matching-stepper-label">${item.label}</span>
              </div>
            `
          }).join('')}
        </div>
        <div class="matching-step-progress" role="progressbar" aria-label="매칭 조건 진행률" aria-valuemin="1" aria-valuemax="${MATCHING_FORM_STEPS.length}" aria-valuenow="${step}" aria-valuetext="${step}단계: ${stepMeta.label}">
          <span style="width: ${progress}%"></span>
        </div>

        <div class="mb-7 mt-6 scroll-mt-28" aria-live="polite">
          <p class="text-xs font-extrabold tracking-[0.12em] text-primary-container">${stepMeta.label}</p>
          <h3 id="matching-step-title" tabindex="-1" class="mt-1 font-headline text-2xl font-extrabold tracking-tight text-brand-navy sm:text-3xl">${stepMeta.title}</h3>
          <p class="mt-2 text-sm leading-6 text-secondary">${stepMeta.description}</p>
        </div>

        ${step > 1 ? `
          <div class="matching-selection-summary mb-6 flex flex-wrap gap-2" aria-label="앞서 선택한 조건">
            ${renderSummaryChip('location_on', state.location.regionName)}
            ${renderSummaryChip('schedule', formatDateTime(state.desiredTimeSlot))}
            ${step > 2 ? renderSummaryChip('restaurant', FOOD_LABELS.get(state.foodCategory) || '음식 선택') : ''}
          </div>
        ` : ''}

        <form id="matching-request-form" class="space-y-7">
          ${stepContent}

          <div class="matching-form-actions mt-8 flex flex-col-reverse gap-3 border-t border-outline-variant/30 pt-6 sm:flex-row sm:items-center sm:justify-between">
            ${step > 1
              ? '<button id="btn-matching-prev" type="button" class="btn-secondary inline-flex min-h-12 items-center justify-center gap-2 rounded-full px-5 text-sm font-bold"><span class="material-symbols-outlined text-lg">arrow_back</span>이전</button>'
              : '<span class="hidden text-xs font-semibold text-secondary sm:block"></span>'}
            <div class="flex flex-col-reverse gap-3 sm:flex-row sm:items-center">
              ${step < MATCHING_FORM_STEPS.length
                ? '<button id="btn-matching-next" type="button" class="btn-primary inline-flex min-h-12 items-center justify-center gap-2 rounded-full px-6 text-sm font-extrabold shadow-md"><span>다음 단계</span><span class="material-symbols-outlined text-lg">arrow_forward</span></button>'
                : `<button id="btn-submit-matching-request" type="submit" ${state.isSubmitting ? 'disabled' : ''} class="btn-primary inline-flex min-h-14 items-center justify-center gap-2 rounded-full px-6 text-sm font-extrabold shadow-glow-primary disabled:cursor-not-allowed disabled:opacity-60">
                    ${state.isSubmitting ? '<span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent"></span><span>매칭을 준비하고 있어요…</span>' : '<span class="material-symbols-outlined">local_dining</span><span>이 조건으로 매칭 시작하기</span>'}
                  </button>`}
            </div>
          </div>
        </form>
      </section>
    `

    function renderScheduleStep() {
      return `
        <div class="space-y-6">
          <section class="matching-location-summary rounded-3xl border border-outline-variant/40 bg-white p-4 sm:p-5" aria-labelledby="matching-location-title">
            <div class="flex items-start justify-between gap-4">
              <div class="flex min-w-0 items-start gap-3">
                <span class="material-symbols-outlined rounded-full bg-primary-container/10 p-2 text-primary-container shadow-sm">location_on</span>
                <div class="min-w-0">
                  <p class="text-xs font-semibold text-secondary">선택한 약속 지역</p>
                  <p id="matching-location-title" class="mt-1 truncate text-base font-extrabold text-brand-navy">${escapeHtml(state.location.regionName)}</p>
                </div>
              </div>
              <a href="${escapeHtml(buildMapHref(state.location))}" class="inline-flex shrink-0 items-center gap-1 rounded-full bg-primary-container/10 px-3 py-2 text-xs font-bold text-primary-container shadow-sm hover:bg-primary-container/20" aria-label="지도에서 약속 지역 다시 선택">
                <span class="material-symbols-outlined text-base">map</span>
                수정
              </a>
            </div>
          </section>

          <div class="grid gap-5">
            <div class="block">
              <span class="mb-2 block text-sm font-bold text-brand-navy">희망 식사 일시 <span class="text-primary-container">*</span></span>
              ${renderSchedulePicker(state)}
            </div>
          </div>
        </div>
      `
    }

    function renderFoodStep() {
      return `
        <div class="space-y-6">
          <section class="matching-food-picker rounded-3xl border border-outline-variant/40 bg-white p-4 sm:p-5" aria-labelledby="matching-food-title">
            <div class="flex items-start gap-3">
              <span class="material-symbols-outlined rounded-full bg-primary-container/10 p-2 text-primary-container shadow-sm">restaurant</span>
              <div class="min-w-0">
                <p class="text-xs font-semibold text-secondary">오늘의 메뉴 방향</p>
                <h4 id="matching-food-title" class="mt-1 text-base font-extrabold text-brand-navy">무엇을 먹을지 골라볼까요?</h4>
                <p class="mt-1 text-sm leading-6 text-secondary">정밀한 메뉴보다 함께 고를 음식의 큰 방향만 알려주면 돼요.</p>
              </div>
            </div>

            <fieldset class="mt-5" aria-describedby="matching-food-help">
              <legend class="sr-only">음식 카테고리</legend>
              <div class="matching-food-grid">
                ${FOOD_CATEGORIES.map(([code, label]) => {
                  const isSelected = state.foodCategory === code
                  return `
                    <button type="button" data-food-category="${code}" aria-pressed="${isSelected ? 'true' : 'false'}" class="matching-food-option ${isSelected ? 'is-selected' : ''}">
                      <span class="matching-food-option-icon material-symbols-outlined">${FOOD_ICONS.get(code) || 'restaurant'}</span>
                      <span class="matching-food-option-label">${escapeHtml(label)}</span>
                      <span class="matching-food-option-check material-symbols-outlined" aria-hidden="true">${isSelected ? 'check_circle' : ''}</span>
                    </button>
                  `
                }).join('')}
              </div>
            </fieldset>
            <p id="matching-food-help" class="mt-4 text-xs leading-5 text-secondary">알레르기나 식단 제한은 매칭 후 서로 직접 확인해 주세요.</p>
            <input type="hidden" name="foodCategory" value="${escapeHtml(state.foodCategory)}" />
          </section>
        </div>
      `
    }

    function renderPersonalityStep() {
      return `
        <div class="space-y-7">
          ${renderPersonalityPicker(state)}

          <label class="block">
            <div class="flex items-end justify-between gap-3">
              <span class="text-sm font-bold text-brand-navy">희망 상대에게 바라는 점 <span class="font-normal text-slate-400">(선택)</span></span>
              <span id="desired-personality-count" class="text-xs font-semibold text-secondary">${state.desiredPersonalityText.length} / 300</span>
            </div>
            <textarea class="matching-control mt-2 min-h-32 w-full resize-y rounded-xl px-3.5 py-3 text-sm leading-6" name="desiredPersonalityText" maxlength="300" aria-describedby="matching-personality-help" placeholder="예: 주말에 풋살이나 축구하는 걸 좋아하고, 인스타그램 맛집 탐방이나 사진 촬영을 즐기는 분이면 좋겠어요.">${escapeHtml(state.desiredPersonalityText)}</textarea>
            
            <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
              <span id="matching-personality-help" class="text-xs leading-5 text-secondary">입력한 내용은 매칭 기준으로만 사용하며, 상대에게 원문이 공개되지 않아요.</span>
              <button id="btn-extract-desired-keywords" type="button" ${state.isExtractingDesiredKeywords ? 'disabled' : ''} class="inline-flex items-center gap-1.5 rounded-xl bg-primary-container px-3.5 py-2 text-xs font-extrabold text-white transition hover:bg-primary-container/90 shadow-xs disabled:cursor-not-allowed disabled:opacity-50">
                <span class="material-symbols-outlined text-sm">auto_awesome</span>
                <span>${state.isExtractingDesiredKeywords ? 'AI 키워드 추출 중…' : '✨ AI 희망 키워드 태그 추출하기'}</span>
              </button>
            </div>

            ${state.desiredExtractedKeywords.length > 0 ? `
              <div class="mt-3 rounded-2xl bg-primary-container/10 p-3.5 border border-primary-container/20 space-y-2">
                <div class="flex items-center gap-1.5 text-xs font-extrabold text-primary">
                  <span class="material-symbols-outlined text-sm">auto_awesome</span>
                  <span>AI 추출 희망 키워드 태그</span>
                </div>
                <div class="flex flex-wrap gap-2">
                  ${state.desiredExtractedKeywords.map(kw => `<span class="inline-flex items-center rounded-full bg-white px-3 py-1 text-xs font-extrabold text-primary border border-primary-container/30 shadow-2xs">#${escapeHtml(kw)}</span>`).join('')}
                </div>
              </div>
            ` : ''}
          </label>

          ${!state.personalityProfileCompleted ? `
            <aside class="rounded-2xl border border-brand-navy/10 bg-brand-navy p-4 text-white sm:p-5">
              <div class="flex items-start gap-3">
                <span class="material-symbols-outlined text-primary-container">auto_awesome</span>
                <div>
                  <h3 class="text-sm font-extrabold">성향 정보가 없어도 괜찮아요</h3>
                  <p class="mt-1.5 text-xs leading-5 text-white/75">성향을 설정하지 않은 상대와도 위치·시간·음식 조건을 기준으로 기본 매칭을 진행할 수 있어요.</p>
                  <a href="/personality/survey" class="mt-3 inline-flex items-center gap-1 text-xs font-bold text-primary-container hover:underline">
                    성향 설정 둘러보기
                    <span class="material-symbols-outlined text-sm">arrow_forward</span>
                  </a>
                </div>
              </div>
            </aside>
          ` : ''}
        </div>
      `
    }
  }

  function renderWaiting() {
    const isConfirming = state.mode === 'confirming'
    const title = isConfirming ? '프로필을 확인하고 있어요' : '딱 맞는 밥친구를 찾고 있어요'
    const description = isConfirming
      ? '후보 프로필을 불러오는 중입니다. 잠시만 기다려 주세요.'
      : '선택한 위치와 식사 조건이 맞는 상대를 탐색 중이에요. 후보가 제안되면 바로 알려드릴게요.'
    const remaining = formatRemaining(state.currentRequest?.expiresAt)

    return `
      <section class="matching-card matching-waiting-card rounded-[28px] bg-white px-5 py-8 text-center sm:px-10 sm:py-12" aria-live="polite">
        <div class="matching-waiting-scene mx-auto" aria-hidden="true">
          <div class="matching-waiting-person is-left">
            <span class="material-symbols-outlined">person</span>
          </div>
          <div class="matching-waiting-path is-left"><i></i><i></i><i></i></div>
          <div class="matching-waiting-table">
            <span class="material-symbols-outlined">restaurant</span>
          </div>
          <div class="matching-waiting-path is-right"><i></i><i></i><i></i></div>
          <div class="matching-waiting-person is-right">
            <span class="material-symbols-outlined">person</span>
          </div>
        </div>
        <h2 class="mt-7 font-headline text-2xl font-extrabold tracking-tight text-brand-navy sm:text-3xl">${title}</h2>
        <p class="mx-auto mt-3 max-w-lg text-sm leading-6 text-secondary">${description}</p>

        <div class="matching-waiting-summary mx-auto mt-8 grid max-w-2xl text-left sm:grid-cols-3" role="list" aria-label="선택한 매칭 조건">
          ${renderSummaryItem('location_on', '약속 위치', state.location.regionName)}
          ${renderSummaryItem('schedule', '식사 일시', state.hasRequestDetails ? formatDateTime(state.desiredTimeSlot) : '저장된 요청 조건')}
          ${renderSummaryItem('restaurant', '음식', state.hasRequestDetails ? (FOOD_LABELS.get(state.foodCategory) || '선택한 음식') : '저장된 요청 조건')}
        </div>

        <div class="mx-auto mt-8 max-w-xl">
          <div class="matching-search-flow" role="progressbar" aria-label="매칭 상대 탐색 중">
            <span></span><span></span><span></span><span></span><span></span>
          </div>
          <p class="mt-3 text-sm font-bold text-brand-navy">조건이 잘 맞는 상대를 살펴보고 있어요</p>
          <div class="mt-1 flex items-center justify-center gap-1.5 text-xs font-semibold text-secondary">
            <span class="material-symbols-outlined text-base text-primary-container" aria-hidden="true">schedule</span>
            <span data-matching-remaining>${remaining ? `남은 시간 ${remaining}` : '상태 확인 중'}</span>
          </div>
        </div>

        <div class="mx-auto mt-7 flex max-w-xl items-center justify-center gap-1.5 text-xs leading-5 text-secondary">
          <span class="material-symbols-outlined text-base text-primary-container" aria-hidden="true">verified_user</span>
          <span>매칭이 완료되면 공개 프로필만 안전하게 보여드려요.</span>
        </div>
        <button id="btn-cancel-matching" type="button" ${state.isCancelling ? 'disabled' : ''} class="btn-secondary mt-7 inline-flex min-h-12 items-center justify-center gap-2 rounded-full border-error/40 px-6 text-sm font-bold text-error hover:bg-error/5 disabled:cursor-not-allowed disabled:opacity-60">
          ${state.isCancelling ? '<span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-error border-t-transparent"></span>취소 중…' : '<span class="material-symbols-outlined text-lg">close</span>매칭 요청 취소'}
        </button>
      </section>
    `
  }

  function renderProposal() {
    const proposal = state.currentProposal
    const partner = proposal?.partner || {}
    const profileImageUrl = safeImageUrl(partner.profileImageUrl)
    const myDecision = proposal?.myDecision || 'PENDING'
    const canDecide = myDecision === 'PENDING' && !state.isDeciding
    const score = proposal?.compatibilityScore
    const matchedTags = Array.isArray(proposal?.matchedTags) ? proposal.matchedTags : []
    const reasons = Array.isArray(proposal?.compatibilityReasons) ? proposal.compatibilityReasons : []
    const styleTags = Array.from(partner.styleTags || [])
    const initial = escapeHtml((partner.nickname || '밥').trim().charAt(0) || '밥')

    return `
      <section class="matching-card rounded-[28px] bg-white p-5 sm:p-8" aria-live="polite">
        <div class="flex flex-col gap-3 border-b border-outline-variant/30 pb-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <span class="inline-flex items-center gap-1.5 rounded-full bg-success/15 px-3 py-1.5 text-xs font-extrabold text-brand-navy">
              <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-success"></span>
              프로필 확인
            </span>
            <h2 class="mt-3 font-headline text-2xl font-extrabold tracking-tight text-brand-navy">이런 밥친구를 찾았어요</h2>
            <p class="mt-1 text-sm text-secondary">응답 시간 안에 수락 또는 거절을 선택해 주세요.</p>
          </div>
          <div class="rounded-2xl bg-primary-container/10 px-4 py-3 text-left sm:text-right">
            <p class="text-xs font-bold text-secondary">남은 응답 시간</p>
            <p data-matching-remaining class="mt-1 font-headline text-xl font-extrabold tabular-nums text-primary">${formatRemaining(proposal?.expiresAt) || '확인 중'}</p>
          </div>
        </div>

        <div class="mt-6 grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
          <div class="flex flex-col items-center justify-center rounded-3xl bg-surface-container-low px-5 py-7 text-center">
            <div class="matching-avatar relative h-28 w-28 overflow-hidden rounded-full border-4 border-white bg-surface-dim shadow-floating" data-matching-avatar>
              <span class="matching-avatar-fallback">${initial}</span>
              ${profileImageUrl ? `<img src="${escapeHtml(profileImageUrl)}" alt="${escapeHtml(partner.nickname || '밥친구')} 프로필 사진" class="absolute inset-0 h-full w-full object-cover" data-matching-avatar-image />` : ''}
            </div>
            <h3 class="mt-4 font-headline text-xl font-extrabold text-brand-navy">${escapeHtml(partner.nickname || '닉네임을 불러오는 중')}</h3>
            <p class="mt-3 min-h-12 max-w-sm text-sm leading-6 text-secondary">${escapeHtml(partner.description || '아직 공개 자기소개를 작성하지 않았어요.')}</p>
            ${styleTags.length > 0 ? `
              <div class="mt-5 flex flex-wrap justify-center gap-2">
                ${styleTags.map((tag) => `<span class="rounded-full bg-white px-3 py-1.5 text-xs font-bold text-brand-navy shadow-sm">#${escapeHtml(tagLabel(tag))}</span>`).join('')}
              </div>
            ` : '<p class="mt-5 rounded-full bg-white px-3 py-2 text-xs font-semibold text-secondary">공개된 성향 태그가 없어요</p>'}
          </div>

          <div class="flex flex-col justify-center">
            <div class="flex items-center justify-between gap-3 rounded-2xl border border-outline-variant/50 bg-white px-4 py-4 shadow-sm">
              <div>
                <p class="text-xs font-bold text-secondary">이번 매칭 호환도</p>
                <p class="mt-1 text-sm font-semibold text-brand-navy">${score == null ? '기본 조건을 중심으로 매칭했어요' : '서로의 선호 성향을 비교했어요'}</p>
              </div>
              <div class="font-headline text-3xl font-extrabold text-primary">${score == null ? '기본' : `${escapeHtml(score)}%`}</div>
            </div>

            ${matchedTags.length > 0 ? `
              <div class="mt-5">
                <p class="text-xs font-extrabold tracking-wide text-secondary">잘 맞는 성향</p>
                <div class="mt-2 flex flex-wrap gap-2">
                  ${matchedTags.map((tag) => `<span class="rounded-full bg-primary-container/10 px-3 py-1.5 text-xs font-bold text-primary">${escapeHtml(tagLabel(tag))}</span>`).join('')}
                </div>
              </div>
            ` : ''}

            <div class="mt-5 rounded-2xl bg-surface-container-low p-4">
              <p class="text-xs font-extrabold tracking-wide text-secondary">호환 사유</p>
              ${reasons.length > 0
                ? `<ul class="mt-2 space-y-2 text-sm leading-5 text-brand-navy">${reasons.map((reason) => `<li class="flex items-start gap-2"><span class="material-symbols-outlined mt-0.5 text-base text-success">check_circle</span><span>${escapeHtml(reason)}</span></li>`).join('')}</ul>`
                : '<p class="mt-2 text-sm leading-5 text-secondary">위치·시간 등 기본 조건을 바탕으로 제안된 후보예요.</p>'}
            </div>

            <div class="mt-6 rounded-2xl border border-outline-variant/40 bg-white px-4 py-3 text-xs leading-5 text-secondary">
              <span class="font-bold text-brand-navy">응답 안내:</span> ${myDecision === 'ACCEPTED' ? '수락을 완료했습니다. 상대방의 응답 결과를 기다리고 있어요.' : '결정하기 전에는 상대방의 수락 여부를 알 수 없습니다.'}
            </div>

            <div class="mt-6 grid gap-3 sm:grid-cols-2">
              <button id="btn-accept-proposal" type="button" ${canDecide ? '' : 'disabled'} class="btn-primary inline-flex min-h-12 items-center justify-center gap-2 rounded-full text-sm font-extrabold shadow-md disabled:cursor-not-allowed disabled:opacity-50">
                ${state.isDeciding ? '<span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>처리 중…' : '<span class="material-symbols-outlined text-lg">check</span>수락하기'}
              </button>
              <button id="btn-reject-proposal" type="button" ${canDecide ? '' : 'disabled'} class="btn-secondary inline-flex min-h-12 items-center justify-center gap-2 rounded-full text-sm font-extrabold disabled:cursor-not-allowed disabled:opacity-50">
                <span class="material-symbols-outlined text-lg">close</span>거절하기
              </button>
            </div>
          </div>
        </div>
      </section>
    `
  }

  function renderMatchedResult() {
    const result = state.latestResult || {}
    const partner = result.partner || {}
    const compatibility = result.compatibility || {}
    const profileImageUrl = safeImageUrl(partner.profileImageUrl)
    const initial = escapeHtml((partner.nickname || '밥').trim().charAt(0) || '밥')

    return `
      <section class="matching-card rounded-[28px] bg-white p-6 text-center sm:p-12" aria-live="polite">
        <div class="mx-auto grid h-20 w-20 place-items-center rounded-full bg-success/15 text-success">
          <span class="material-symbols-outlined text-4xl">celebration</span>
        </div>
        <span class="mt-6 inline-flex rounded-full bg-success/15 px-3 py-1.5 text-xs font-extrabold text-brand-navy">매칭 완료</span>
        <h2 class="mt-4 font-headline text-3xl font-extrabold tracking-tight text-brand-navy">따뜻한 한 끼가 연결됐어요!</h2>
        <p class="mx-auto mt-3 max-w-lg text-sm leading-6 text-secondary">이제 서비스 안에서 약속을 조율해 보세요. 서로의 연락처와 정밀 위치는 공개되지 않습니다.</p>

        <div class="mx-auto mt-8 flex max-w-md items-center gap-4 rounded-3xl bg-surface-container-low p-4 text-left">
          <div class="matching-avatar relative h-16 w-16 shrink-0 overflow-hidden rounded-full border-2 border-white bg-surface-dim shadow-sm" data-matching-avatar>
            <span class="matching-avatar-fallback text-xl">${initial}</span>
            ${profileImageUrl ? `<img src="${escapeHtml(profileImageUrl)}" alt="${escapeHtml(partner.nickname || '밥친구')} 프로필 사진" class="absolute inset-0 h-full w-full object-cover" data-matching-avatar-image />` : ''}
          </div>
          <div class="min-w-0">
            <p class="text-xs font-bold text-secondary">나의 밥친구</p>
            <p class="mt-1 truncate font-headline text-lg font-extrabold text-brand-navy">${escapeHtml(partner.nickname || '밥친구')}</p>
          </div>
          ${compatibility.score != null ? `<span class="ml-auto shrink-0 rounded-full bg-primary-container/10 px-3 py-1.5 text-sm font-extrabold text-primary">${escapeHtml(compatibility.score)}%</span>` : ''}
        </div>

        ${Array.isArray(compatibility.reasons) && compatibility.reasons.length > 0 ? `
          <div class="mx-auto mt-5 max-w-md text-left">
            <p class="text-xs font-extrabold tracking-wide text-secondary">매칭 이유</p>
            <ul class="mt-2 space-y-1 text-sm text-brand-navy">${compatibility.reasons.slice(0, 3).map((reason) => `<li class="flex items-start gap-2"><span class="material-symbols-outlined mt-0.5 text-base text-success">check_circle</span><span>${escapeHtml(reason)}</span></li>`).join('')}</ul>
          </div>
        ` : ''}

        <div class="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
          <button id="btn-open-matched-chat" type="button" class="btn-primary inline-flex min-h-13 items-center justify-center gap-2 rounded-full px-7 text-sm font-extrabold shadow-glow-primary">
            <span class="material-symbols-outlined">chat</span>
            채팅방 입장하기
          </button>
          <button id="btn-new-matching-request" type="button" class="btn-secondary inline-flex min-h-13 items-center justify-center gap-2 rounded-full px-7 text-sm font-extrabold">
            <span class="material-symbols-outlined">restart_alt</span>
            새 매칭 요청
          </button>
        </div>
      </section>
    `
  }

  function selectScheduleDate(dateValue) {
    if (!isDateSelectable(dateValue)) return
    state.scheduleDraftDate = dateValue
    state.scheduleCalendarMonth = getMonthValueFromDateTime(`${dateValue}T00:00`)
    state.errorMessage = ''
    render()
  }

  function openScheduleDateModal() {
    const { date } = splitDateTimeValue(state.desiredTimeSlot)
    state.scheduleDraftDate = date
    state.scheduleCalendarMonth = getMonthValueFromDateTime(`${date}T00:00`)
    state.scheduleDraftTime = ''
    state.isScheduleDateModalOpen = true
    state.isScheduleTimeModalOpen = false
    render()
    window.setTimeout(() => {
      container.querySelector('#matching-schedule-date-close')?.focus({ preventScroll: true })
    }, 0)
  }

  function closeScheduleDateModal() {
    state.isScheduleDateModalOpen = false
    state.scheduleDraftDate = ''
    render()
    window.setTimeout(() => {
      container.querySelector('#btn-open-schedule-date')?.focus({ preventScroll: true })
    }, 0)
  }

  function confirmScheduleDate() {
    const dateValue = state.scheduleDraftDate
    if (!dateValue || !isDateSelectable(dateValue)) return
    const current = splitDateTimeValue(state.desiredTimeSlot)
    const timeValue = isTimeAvailable(dateValue, current.time)
      ? current.time
      : getDefaultTimeForDate(dateValue)
    state.desiredTimeSlot = `${dateValue}T${timeValue}`
    state.scheduleCalendarMonth = getMonthValueFromDateTime(state.desiredTimeSlot)
    state.isScheduleDateModalOpen = false
    state.scheduleDraftDate = ''
    state.errorMessage = ''
    render()
  }

  function openScheduleTimeModal() {
    const { time } = splitDateTimeValue(state.desiredTimeSlot)
    state.scheduleDraftTime = time
    state.scheduleDraftDate = ''
    state.isScheduleDateModalOpen = false
    state.isScheduleTimeModalOpen = true
    render()
    window.setTimeout(() => {
      container.querySelector('#matching-schedule-time-close')?.focus({ preventScroll: true })
    }, 0)
  }

  function closeScheduleTimeModal() {
    state.isScheduleTimeModalOpen = false
    state.scheduleDraftTime = ''
    render()
    window.setTimeout(() => {
      container.querySelector('#btn-open-schedule-time')?.focus({ preventScroll: true })
    }, 0)
  }

  function selectScheduleTime(timeValue) {
    const { date } = splitDateTimeValue(state.desiredTimeSlot)
    if (!date || !isTimeAvailable(date, timeValue)) return
    state.scheduleDraftTime = timeValue
    render()
  }

  function confirmScheduleTime() {
    const { date } = splitDateTimeValue(state.desiredTimeSlot)
    if (!date || !isTimeAvailable(date, state.scheduleDraftTime)) return
    state.desiredTimeSlot = `${date}T${state.scheduleDraftTime}`
    state.isScheduleTimeModalOpen = false
    state.scheduleDraftTime = ''
    state.errorMessage = ''
    render()
  }

  function bindEvents() {
    container.querySelector('#matching-request-form')?.addEventListener('submit', handleSubmit)
    container.querySelector('#btn-matching-next')?.addEventListener('click', handleNextStep)
    container.querySelector('#btn-matching-prev')?.addEventListener('click', handlePreviousStep)
    container.querySelectorAll('[data-food-category]').forEach((button) => {
      button.addEventListener('click', () => {
        const category = button.getAttribute('data-food-category')
        if (!FOOD_LABELS.has(category)) return
        state.foodCategory = category
        state.errorMessage = ''
        render()
      })
    })
    container.querySelector('[name="desiredTimeSlot"]')?.addEventListener('input', (event) => {
      state.desiredTimeSlot = event.target.value
    })
    container.querySelector('#btn-open-schedule-date')?.addEventListener('click', openScheduleDateModal)
    container.querySelector('#btn-open-schedule-time')?.addEventListener('click', openScheduleTimeModal)
    container.querySelectorAll('[data-schedule-date]').forEach((button) => {
      button.addEventListener('click', () => {
        const dateValue = button.getAttribute('data-schedule-date')
        if (dateValue) selectScheduleDate(dateValue)
      })
    })
    container.querySelectorAll('[data-schedule-time]').forEach((button) => {
      button.addEventListener('click', () => {
        const timeValue = button.getAttribute('data-schedule-time')
        if (timeValue) selectScheduleTime(timeValue)
      })
    })
    container.querySelector('#matching-calendar-prev')?.addEventListener('click', () => {
      state.scheduleCalendarMonth = shiftMonthValue(state.scheduleCalendarMonth, -1)
      render()
    })
    container.querySelector('#matching-calendar-next')?.addEventListener('click', () => {
      const maximumMonth = getMonthValueFromDateTime(`${getMaximumDateValue()}T00:00`)
      if (state.scheduleCalendarMonth >= maximumMonth) return
      state.scheduleCalendarMonth = shiftMonthValue(state.scheduleCalendarMonth, 1)
      render()
    })
    container.querySelector('[name="desiredTimeSlotTime"]')?.addEventListener('change', (event) => {
      const timeValue = event.target.value
      if (!timeValue) return
      state.scheduleDraftTime = timeValue
      render()
    })
    container.querySelector('#matching-schedule-time-close')?.addEventListener('click', closeScheduleTimeModal)
    container.querySelector('#matching-schedule-time-cancel')?.addEventListener('click', closeScheduleTimeModal)
    container.querySelector('#matching-schedule-time-confirm')?.addEventListener('click', confirmScheduleTime)
    container.querySelector('#matching-schedule-time-backdrop')?.addEventListener('click', closeScheduleTimeModal)
    container.querySelector('#matching-schedule-time-modal')?.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') closeScheduleTimeModal()
    })
    container.querySelector('#matching-schedule-date-close')?.addEventListener('click', closeScheduleDateModal)
    container.querySelector('#matching-schedule-date-cancel')?.addEventListener('click', closeScheduleDateModal)
    container.querySelector('#matching-schedule-date-confirm')?.addEventListener('click', confirmScheduleDate)
    container.querySelector('#matching-schedule-date-backdrop')?.addEventListener('click', closeScheduleDateModal)
    container.querySelector('#matching-schedule-date-modal')?.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') closeScheduleDateModal()
    })
    container.querySelector('[name="locationName"]')?.addEventListener('input', (event) => {
      state.locationName = event.target.value
    })
    container.querySelector('[name="desiredPersonalityText"]')?.addEventListener('input', (event) => {
      state.desiredPersonalityText = event.target.value.slice(0, 300)
      const count = container.querySelector('#desired-personality-count')
      if (count) count.textContent = `${state.desiredPersonalityText.length} / 300`
    })

    container.querySelectorAll('[data-personality-group]').forEach((button) => {
      button.addEventListener('click', () => {
        const groupIndex = Number(button.getAttribute('data-personality-group'))
        if (!Number.isInteger(groupIndex) || groupIndex < 0 || groupIndex >= TAG_GROUPS.length) return
        state.personalityGroupIndex = groupIndex
        state.errorMessage = ''
        render()
      })
    })

    container.querySelectorAll('[data-matching-tag]').forEach((button) => {
      button.addEventListener('click', () => {
        const tag = button.getAttribute('data-matching-tag')
        if (!TAG_LABELS.has(tag)) {
          state.errorMessage = '지원하지 않는 성향 태그입니다.'
        } else if (state.selectedTags.has(tag)) {
          state.selectedTags.delete(tag)
          state.errorMessage = ''
        } else if (state.selectedTags.size >= 5) {
          state.errorMessage = '희망 상대 성향 태그는 최대 5개까지 선택할 수 있습니다.'
        } else {
          state.selectedTags.add(tag)
          state.errorMessage = ''
        }
        render()
      })
    })

    async function handleExtractDesiredKeywords() {
      const text = (state.desiredPersonalityText || '').trim()
      if (!text) {
        showToast('희망 상대에게 바라는 점 텍스트를 먼저 입력해 주세요.')
        return
      }
      state.isExtractingDesiredKeywords = true
      render()
      try {
        const res = await extractKeywordsFromText({ selfDescription: text })
        const keywordsList = res?.keywords || (Array.isArray(res) ? res : [])
        state.desiredExtractedKeywords = keywordsList
        if (state.desiredExtractedKeywords.length === 0) {
          showToast('키워드 태그를 추출하지 못했습니다.')
        } else {
          showToast('✨ AI 희망 키워드 태그 추출이 완료되었습니다!')
        }
      } catch (err) {
        showToast(err.message || 'AI 키워드 추출에 실패했습니다.')
      } finally {
        state.isExtractingDesiredKeywords = false
        render()
      }
    }

    container.querySelector('#btn-extract-desired-keywords')?.addEventListener('click', handleExtractDesiredKeywords)
    container.querySelector('#btn-cancel-matching')?.addEventListener('click', handleCancel)
    container.querySelector('#btn-expand-radius')?.addEventListener('click', handleExpandRadius)
    container.querySelector('#btn-cancel-radius-expansion')?.addEventListener('click', handleCancelRadiusExpansion)
    container.querySelector('#btn-accept-proposal')?.addEventListener('click', () => handleDecision('ACCEPT'))
    container.querySelector('#btn-reject-proposal')?.addEventListener('click', () => handleDecision('REJECT'))
    container.querySelector('#btn-new-matching-request')?.addEventListener('click', startNewRequest)
    container.querySelector('#btn-open-matched-chat')?.addEventListener('click', () => {
      if (state.latestResult?.chatRoomId) {
        navigateTo(`/chat?roomId=${encodeURIComponent(state.latestResult.chatRoomId)}`)
      }
    })

    container.querySelectorAll('[data-matching-avatar-image]').forEach((image) => {
      image.addEventListener('error', () => {
        image.classList.add('is-hidden')
      }, { once: true })
    })
  }

  function syncMode() {
    if (state.latestResult) {
      state.mode = 'matched'
      return
    }
    if (state.currentProposal?.status === 'PENDING') {
      state.mode = 'proposal'
      return
    }
    if (state.currentRequest?.status === 'CONFIRMING') {
      state.mode = 'confirming'
      return
    }
    if (state.currentRequest?.status === 'WAITING') {
      state.mode = 'waiting'
      return
    }
    state.mode = 'form'
  }

  function syncTicker() {
    const shouldTick = state.mode === 'waiting' || state.mode === 'confirming' || state.mode === 'proposal'
    if (!shouldTick) {
      if (tickerTimer) clearInterval(tickerTimer)
      tickerTimer = null
      return
    }
    if (tickerTimer) return
    tickerTimer = setInterval(() => {
      if (disposed) return
      const expiresAt = state.currentProposal?.expiresAt || state.currentRequest?.expiresAt
      if (expiresAt && remainingMilliseconds(expiresAt) <= 0 && state.mode === 'proposal' && !state.expiryHandled) {
        state.expiryHandled = true
        state.noticeMessage = '응답 시간이 만료되었습니다. 다시 매칭을 탐색할 수 있어요.'
        void refreshFlow({ checkResult: true })
        return
      }
      updateTickerText()
    }, 1000)
  }

  function updateTickerText() {
    const ticker = container.querySelector('[data-matching-remaining]')
    if (!ticker) return
    const expiresAt = state.currentProposal?.expiresAt || state.currentRequest?.expiresAt
    const remaining = formatRemaining(expiresAt)
    ticker.textContent = state.mode === 'proposal'
      ? remaining || '확인 중'
      : remaining ? `남은 시간 ${remaining}` : '상태 확인 중'
  }

}

function readLocationFromQuery() {
  const params = new URLSearchParams(window.location.search)
  return {
    regionCode: params.get('regionCode') || '',
    regionName: params.get('regionName') || '',
    locationName: params.get('locationName') || '',
    latitude: toFiniteNumber(params.get('lat') || params.get('latitude')),
    longitude: toFiniteNumber(params.get('lng') || params.get('longitude')),
  }
}

function enrichLocation(location) {
  const result = { ...location }
  let region = result.regionCode ? findRegionByCode(result.regionCode) : null
  if (!region && result.regionName) region = findRegionByName(result.regionName)
  if (region) {
    result.regionCode = result.regionCode || region.code
    result.regionName = result.regionName || region.fullName
    if (!Number.isFinite(result.latitude)) result.latitude = region.lat
    if (!Number.isFinite(result.longitude)) result.longitude = region.lng
  }
  if (!result.regionName && result.regionCode) result.regionName = result.regionCode
  return result
}

function findRegionByCode(code) {
  for (const sido of Object.values(regionTree)) {
    for (const sigungu of Object.values(sido)) {
      if (sigungu?.code === code) return sigungu
      for (const detail of Object.values(sigungu || {})) {
        if (detail?.code === code) return detail
      }
    }
  }
  return null
}

function findRegionByName(name) {
  for (const sido of Object.values(regionTree)) {
    for (const sigungu of Object.values(sido)) {
      if (sigungu?.fullName === name) return sigungu
      for (const detail of Object.values(sigungu || {})) {
        if (detail?.fullName === name) return detail
      }
    }
  }
  return null
}

function isCompleteLocation(location) {
  return /^\d{5}$/.test(location?.regionCode || '')
    && Number.isFinite(location?.latitude)
    && Number.isFinite(location?.longitude)
}

function buildMapHref(location) {
  if (!isCompleteLocation(location)) return '/map?mode=preferred'
  const params = new URLSearchParams({
    lat: String(location.latitude),
    lng: String(location.longitude),
    name: location.regionName.split(' ').at(-1) || '',
    sido: location.regionName.split(' ')[0] || '',
    sigungu: location.regionName.split(' ')[1] || '',
    regionCode: location.regionCode,
  })
  return `/map?${params.toString()}`
}

function renderSchedulePicker(state) {
  const { date: selectedDate, time: selectedTime } = splitDateTimeValue(state.desiredTimeSlot)
  const selectedDateLabel = formatScheduleDate(selectedDate)
  const selectedTimeLabel = formatTimeLabel(selectedTime)

  return `
    <div class="matching-schedule-picker mt-3 rounded-3xl border border-outline-variant/40 bg-white p-4 sm:p-5">
      <div class="matching-schedule-fields mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_1px_minmax(0,1fr)] lg:items-stretch lg:gap-6">
        <section class="matching-date-panel">
          <div class="flex items-center gap-2">
            <div>
              <p class="text-sm font-extrabold text-brand-navy">식사 날짜</p>
              <p class="mt-0.5 text-xs text-secondary">버튼을 눌러 만날 날짜를 선택해 보세요.</p>
            </div>
          </div>
          <button id="btn-open-schedule-date" type="button" class="matching-date-trigger mt-4 flex w-full items-center justify-between gap-3 rounded-2xl border border-primary-container/30 bg-primary-container/8 px-4 py-3.5 text-left" aria-haspopup="dialog" aria-expanded="${state.isScheduleDateModalOpen ? 'true' : 'false'}">
            <span class="min-w-0">
              <span class="block text-[11px] font-extrabold tracking-wide text-secondary">선택한 날짜</span>
              <span class="mt-1 block truncate text-lg font-extrabold text-brand-navy">${escapeHtml(selectedDateLabel)}</span>
            </span>
            <span class="matching-date-trigger-icon material-symbols-outlined shrink-0 rounded-full bg-white p-2 text-primary-container shadow-sm">chevron_right</span>
          </button>
          ${state.isScheduleDateModalOpen ? renderScheduleDateModal(state, selectedDate) : ''}
        </section>

        <div class="matching-schedule-divider" aria-hidden="true"></div>

        <section class="matching-time-panel">
          <div class="flex items-center gap-2">
            <div>
              <p class="text-sm font-extrabold text-brand-navy">식사 시간</p>
              <p class="mt-0.5 text-xs text-secondary">버튼을 눌러 가능한 시간을 확인해 보세요.</p>
            </div>
          </div>
          <button id="btn-open-schedule-time" type="button" class="matching-time-trigger mt-4 flex w-full items-center justify-between gap-3 rounded-2xl border border-primary-container/30 bg-primary-container/8 px-4 py-3.5 text-left" aria-haspopup="dialog" aria-expanded="${state.isScheduleTimeModalOpen ? 'true' : 'false'}">
            <span class="min-w-0">
              <span class="block text-[11px] font-extrabold tracking-wide text-secondary">선택한 시간</span>
              <span class="mt-1 block truncate text-lg font-extrabold text-brand-navy">${escapeHtml(selectedTimeLabel)}</span>
            </span>
            <span class="matching-time-trigger-icon material-symbols-outlined shrink-0 rounded-full bg-white p-2 text-primary-container shadow-sm">chevron_right</span>
          </button>
          ${state.isScheduleTimeModalOpen ? renderScheduleTimeModal(state, selectedDate) : ''}
        </section>
      </div>
      <input type="hidden" name="desiredTimeSlot" value="${escapeHtml(state.desiredTimeSlot)}" />
    </div>
  `
}

function renderScheduleDateModal(state, selectedDate) {
  const draftDate = state.scheduleDraftDate || selectedDate
  const calendarMonth = normalizeMonthValue(state.scheduleCalendarMonth, draftDate)
  const currentMonth = getMonthValue(new Date())
  const maximumMonth = getMonthValueFromDateTime(`${getMaximumDateValue()}T00:00`)
  const calendarCells = buildCalendarCells(calendarMonth, draftDate)
  const canConfirm = Boolean(draftDate) && isDateSelectable(draftDate)

  return `
    <div id="matching-schedule-date-modal" class="matching-time-modal" role="dialog" aria-modal="true" aria-labelledby="matching-schedule-date-title">
      <button id="matching-schedule-date-backdrop" type="button" class="matching-time-modal-backdrop" tabindex="-1" aria-label="날짜 선택 닫기"></button>
      <div class="matching-time-modal-panel matching-date-modal-panel">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-extrabold tracking-wide text-primary-container">날짜 선택</p>
            <h4 id="matching-schedule-date-title" class="mt-1 font-headline text-xl font-extrabold text-brand-navy">식사 날짜를 골라 주세요</h4>
            <p class="mt-1 text-sm leading-6 text-secondary">오늘부터 일주일 안에서 만날 날짜를 선택해 주세요.</p>
          </div>
          <button id="matching-schedule-date-close" type="button" class="matching-time-modal-close" aria-label="날짜 선택 닫기">
            <span class="material-symbols-outlined text-lg">close</span>
          </button>
        </div>

        <div class="matching-calendar mt-5 rounded-2xl border border-outline-variant/35 bg-white p-3.5 sm:p-4" aria-label="희망 식사 날짜 선택">
          <div class="flex items-center justify-between gap-3">
            <button id="matching-calendar-prev" type="button" ${calendarMonth <= currentMonth ? 'disabled' : ''} class="matching-calendar-nav" aria-label="이전 달">
              <span class="material-symbols-outlined text-lg">chevron_left</span>
            </button>
            <p class="text-sm font-extrabold text-brand-navy">${escapeHtml(formatCalendarMonth(calendarMonth))}</p>
            <button id="matching-calendar-next" type="button" ${calendarMonth >= maximumMonth ? 'disabled' : ''} class="matching-calendar-nav" aria-label="다음 달">
              <span class="material-symbols-outlined text-lg">chevron_right</span>
            </button>
          </div>
          <div class="matching-calendar-weekdays mt-4" aria-hidden="true">
            ${['일', '월', '화', '수', '목', '금', '토'].map((day) => `<span>${day}</span>`).join('')}
          </div>
          <div class="matching-calendar-grid mt-2">
            ${calendarCells.map((cell) => `
              <button
                type="button"
                data-schedule-date="${cell.dateValue}"
                class="matching-calendar-day ${cell.isOutside ? 'is-outside' : ''} ${cell.isToday ? 'is-today' : ''} ${cell.isSelected ? 'is-selected' : ''}"
                ${cell.isDisabled ? 'disabled' : ''}
                aria-pressed="${cell.isSelected ? 'true' : 'false'}"
                aria-label="${escapeHtml(cell.ariaLabel)}"
              >${cell.day}</button>
            `).join('')}
          </div>
        </div>

        <div class="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button id="matching-schedule-date-cancel" type="button" class="btn-secondary inline-flex min-h-11 items-center justify-center rounded-full px-5 text-sm font-bold">취소</button>
          <button id="matching-schedule-date-confirm" type="button" class="btn-primary inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-extrabold shadow-md" ${canConfirm ? '' : 'disabled'}>
            <span class="material-symbols-outlined text-lg">check</span>
            이 날짜로 선택
          </button>
        </div>
      </div>
    </div>
  `
}

function renderScheduleTimeModal(state, selectedDate) {
  const { time: selectedTime } = splitDateTimeValue(state.desiredTimeSlot)
  const draftTime = state.scheduleDraftTime || selectedTime
  const timeInputMin = selectedDate === toDateValue(new Date()) ? getMinimumTimeValue() : '00:00'
  const canConfirm = Boolean(selectedDate) && isTimeAvailable(selectedDate, draftTime)

  return `
    <div id="matching-schedule-time-modal" class="matching-time-modal" role="dialog" aria-modal="true" aria-labelledby="matching-schedule-time-title">
      <button id="matching-schedule-time-backdrop" type="button" class="matching-time-modal-backdrop" tabindex="-1" aria-label="시간 선택 닫기"></button>
      <div class="matching-time-modal-panel">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-extrabold tracking-wide text-primary-container">${escapeHtml(formatScheduleDate(selectedDate))}</p>
            <h4 id="matching-schedule-time-title" class="mt-1 font-headline text-xl font-extrabold text-brand-navy">식사 시간을 골라 주세요</h4>
            <p class="mt-1 text-sm leading-6 text-secondary">밥친구와 만나기 편한 시간을 선택해 주세요.</p>
          </div>
          <button id="matching-schedule-time-close" type="button" class="matching-time-modal-close" aria-label="시간 선택 닫기">
            <span class="material-symbols-outlined text-lg">close</span>
          </button>
        </div>

        <div class="matching-time-modal-grid mt-5">
          ${buildTimeSlots().map((timeValue) => {
            const isAvailable = Boolean(selectedDate) && isTimeAvailable(selectedDate, timeValue)
            const isSelected = draftTime === timeValue && isAvailable
            return `
              <button type="button" data-schedule-time="${timeValue}" class="matching-time-slot ${isSelected ? 'is-selected' : ''}" ${isAvailable ? '' : 'disabled'} aria-pressed="${isSelected ? 'true' : 'false'}">
                ${escapeHtml(formatTimeLabel(timeValue))}
              </button>
            `
          }).join('')}
        </div>

        <div class="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button id="matching-schedule-time-cancel" type="button" class="btn-secondary inline-flex min-h-11 items-center justify-center rounded-full px-5 text-sm font-bold">취소</button>
          <button id="matching-schedule-time-confirm" type="button" class="btn-primary inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-extrabold shadow-md" ${canConfirm ? '' : 'disabled'}>
            <span class="material-symbols-outlined text-lg">check</span>
            이 시간으로 선택
          </button>
        </div>
      </div>
    </div>
  `
}

function renderPersonalityPicker(state) {
  const requestedGroupIndex = Number(state.personalityGroupIndex)
  const activeGroupIndex = Number.isInteger(requestedGroupIndex)
    ? Math.min(Math.max(requestedGroupIndex, 0), TAG_GROUPS.length - 1)
    : 0
  const activeGroup = TAG_GROUPS[activeGroupIndex]
  const activeGroupSelectedCount = activeGroup.tags.filter(([code]) => state.selectedTags.has(code)).length

  return `
    <section class="matching-personality-picker rounded-3xl border border-outline-variant/40 bg-white p-4 sm:p-5" aria-labelledby="matching-personality-title">
      <div class="matching-personality-heading flex items-start gap-3">
        <span class="matching-personality-heading-icon material-symbols-outlined" aria-hidden="true">person_search</span>
        <div class="min-w-0 flex-1">
          <p class="text-xs font-semibold text-secondary">원하는 밥친구</p>
          <h3 id="matching-personality-title" class="mt-1 text-base font-extrabold text-brand-navy">어떤 성향의 상대와 함께하고 싶나요?</h3>
          <p class="mt-1 text-sm leading-6 text-secondary">내 성향이 아니라, 함께 만나고 싶은 상대의 특징을 3~5개 선택해 주세요.</p>
        </div>
        <span class="matching-personality-total shrink-0" aria-live="polite">상대 성향 ${state.selectedTags.size} / 5</span>
      </div>

      <div class="matching-personality-tabs mt-5" role="tablist" aria-label="상대 성향 카테고리">
        ${TAG_GROUPS.map((group, groupIndex) => {
          const selectedCount = group.tags.filter(([code]) => state.selectedTags.has(code)).length
          const isActive = groupIndex === activeGroupIndex
          const groupLabel = `상대의 ${group.name}`
          return `
            <button id="matching-personality-tab-${groupIndex}" type="button" data-personality-group="${groupIndex}" role="tab" aria-selected="${isActive ? 'true' : 'false'}" aria-controls="matching-personality-panel-${groupIndex}" class="matching-personality-tab ${isActive ? 'is-active' : ''}">
              <span class="matching-personality-tab-label">${escapeHtml(groupLabel)}</span>
              <span class="matching-personality-tab-count ${selectedCount ? 'has-selection' : ''}" aria-label="${selectedCount}개 선택">${selectedCount}</span>
            </button>
          `
        }).join('')}
      </div>

      <div id="matching-personality-panel-${activeGroupIndex}" class="matching-personality-panel mt-5" role="tabpanel" tabindex="0" aria-labelledby="matching-personality-tab-${activeGroupIndex}" aria-describedby="matching-tags-help">
        <div class="matching-personality-panel-heading flex flex-wrap items-end justify-between gap-2">
          <div>
            <p class="text-sm font-extrabold text-brand-navy">상대의 ${escapeHtml(activeGroup.name)}</p>
            <p class="mt-1 text-xs leading-5 text-secondary">이런 상대를 만나고 싶어요. 원하는 모습에 가까운 카드를 골라보세요.</p>
          </div>
          <span class="text-xs font-bold text-secondary">${activeGroupSelectedCount} / ${activeGroup.tags.length} 선택</span>
        </div>

        <div class="matching-personality-grid mt-3">
          ${activeGroup.tags.map(([code, label]) => {
            const isSelected = state.selectedTags.has(code)
            const detail = TAG_DETAILS.get(code) || { icon: 'mood', description: '함께할 식사 분위기를 골라보세요.' }
            return `
              <button type="button" data-matching-tag="${code}" aria-pressed="${isSelected ? 'true' : 'false'}" class="matching-personality-option ${isSelected ? 'is-selected' : ''}">
                <span class="matching-personality-option-icon material-symbols-outlined" aria-hidden="true">${detail.icon}</span>
                <span class="matching-personality-option-copy">
                  <span class="matching-personality-option-label">${escapeHtml(label)}</span>
                  <span class="matching-personality-option-description">${escapeHtml(detail.description)}</span>
                </span>
                <span class="matching-personality-option-check material-symbols-outlined" aria-hidden="true">${isSelected ? 'check_circle' : 'radio_button_unchecked'}</span>
              </button>
            `
          }).join('')}
        </div>
      </div>

      <p id="matching-tags-help" class="mt-4 text-xs leading-5 text-secondary">필수 조건 · 원하는 상대의 성향을 전체 3~5개 선택해 주세요.</p>
    </section>
  `
}

function splitDateTimeValue(value) {
  const [date = '', time = ''] = String(value || '').split('T')
  return { date, time: time.slice(0, 5) }
}

function parseDateValue(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value || ''))) return null
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day ? date : null
}

function parseMonthValue(value) {
  if (!/^\d{4}-\d{2}$/.test(String(value || ''))) return null
  const [year, month] = value.split('-').map(Number)
  if (month < 1 || month > 12) return null
  return new Date(year, month - 1, 1)
}

function getMonthValue(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}

function getMonthValueFromDateTime(value) {
  const { date } = splitDateTimeValue(value)
  return getMonthValue(parseDateValue(date) || new Date())
}

function normalizeMonthValue(value, fallbackDateValue) {
  const currentMonth = parseMonthValue(getMonthValue(new Date()))
  const maximumMonth = parseMonthValue(getMonthValueFromDateTime(`${getMaximumDateValue()}T00:00`))
  const fallbackMonth = parseMonthValue(getMonthValueFromDateTime(`${fallbackDateValue || ''}T00:00`))
  const candidate = parseMonthValue(value) || fallbackMonth || currentMonth
  if (candidate < currentMonth) return getMonthValue(currentMonth)
  if (candidate > maximumMonth) return getMonthValue(maximumMonth)
  return getMonthValue(candidate)
}

function shiftMonthValue(value, amount) {
  const month = parseMonthValue(value) || new Date()
  month.setMonth(month.getMonth() + amount, 1)
  return getMonthValue(month)
}

function buildCalendarCells(monthValue, selectedDate) {
  const month = parseMonthValue(monthValue) || new Date()
  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1)
  const firstCell = new Date(firstDay)
  firstCell.setDate(firstDay.getDate() - firstDay.getDay())
  const todayValue = toDateValue(new Date())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(firstCell)
    date.setDate(firstCell.getDate() + index)
    const dateValue = toDateValue(date)
    const isOutside = date.getMonth() !== month.getMonth()
    const isToday = dateValue === todayValue
    const isSelected = dateValue === selectedDate
    return {
      dateValue,
      day: date.getDate(),
      isOutside,
      isToday,
      isSelected,
      isDisabled: isOutside || !isDateSelectable(dateValue),
      ariaLabel: `${formatScheduleDate(dateValue)}${isOutside ? ' (다른 달)' : ''}${isToday ? ' (오늘)' : ''}${isDateAfterMaximum(dateValue) ? ' (선택 가능 기간 이후)' : ''}`,
    }
  })
}

function isDateBeforeToday(dateValue) {
  const date = parseDateValue(dateValue)
  const today = parseDateValue(toDateValue(new Date()))
  return !date || date < today
}

function getMaximumDateValue() {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() + 7)
  return toDateValue(date)
}

function isDateAfterMaximum(dateValue) {
  const date = parseDateValue(dateValue)
  const maximumDate = parseDateValue(getMaximumDateValue())
  return !date || date > maximumDate
}

function isDateSelectable(dateValue) {
  return !isDateBeforeToday(dateValue) && !isDateAfterMaximum(dateValue)
}

function buildTimeSlots() {
  const slots = []
  for (let minutes = 10 * 60; minutes <= 21 * 60 + 30; minutes += 30) {
    const hour = Math.floor(minutes / 60)
    const minute = minutes % 60
    slots.push(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`)
  }
  return slots
}

function parseDateTimeValue(value) {
  const { date, time } = splitDateTimeValue(value)
  const dateValue = parseDateValue(date)
  if (!dateValue || !/^\d{2}:\d{2}$/.test(time)) return null
  const [hour, minute] = time.split(':').map(Number)
  if (hour > 23 || minute > 59) return null
  return new Date(dateValue.getFullYear(), dateValue.getMonth(), dateValue.getDate(), hour, minute)
}

function isTimeAvailable(dateValue, timeValue) {
  const candidate = parseDateTimeValue(`${dateValue}T${timeValue}`)
  return Boolean(candidate) && candidate.getTime() > Date.now()
}

function getDefaultTimeForDate(dateValue) {
  if (dateValue !== toDateValue(new Date())) return '12:00'
  const date = new Date(Date.now() + 10 * 60 * 1000)
  date.setSeconds(0, 0)
  date.setMinutes(Math.ceil(date.getMinutes() / 30) * 30)
  if (toDateValue(date) !== dateValue) return '23:59'
  return toTimeValue(date)
}

function getMinimumTimeValue() {
  return toTimeValue(new Date(Date.now() + 5 * 60 * 1000))
}

function toDateValue(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function toTimeValue(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatCalendarMonth(monthValue) {
  const date = parseMonthValue(monthValue)
  return date ? date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long' }) : ''
}

function formatScheduleDate(dateValue) {
  const date = parseDateValue(dateValue)
  return date
    ? date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
    : '날짜를 선택해 주세요'
}

function formatTimeLabel(timeValue) {
  if (!/^\d{2}:\d{2}$/.test(String(timeValue || ''))) return '시간을 선택해 주세요'
  const [hour, minute] = timeValue.split(':').map(Number)
  const date = new Date(2000, 0, 1, hour, minute)
  return date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

function getDefaultDateTimeValue() {
  const date = new Date(Date.now() + 60 * 60 * 1000)
  date.setMinutes(Math.ceil(date.getMinutes() / 30) * 30, 0, 0)
  return toDateTimeValue(date)
}

function toDateTimeValue(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toInstant(dateTimeValue) {
  return new Date(dateTimeValue).toISOString()
}

function formatDateTime(value) {
  if (!value) return '선택한 시간'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '선택한 시간'
  return date.toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function formatRemaining(expiresAt) {
  if (!expiresAt) return ''
  const milliseconds = remainingMilliseconds(expiresAt)
  if (milliseconds <= 0) return '00:00'
  const totalSeconds = Math.floor(milliseconds / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function remainingMilliseconds(expiresAt) {
  const time = new Date(expiresAt).getTime()
  return Number.isNaN(time) ? 0 : time - Date.now()
}

function formatCoordinate(value) {
  return Number.isFinite(value) ? value.toFixed(4) : '-'
}

function renderSummaryItem(icon, label, value) {
  return `
    <div class="matching-waiting-summary-item flex min-w-0 items-center gap-3 px-4 py-3.5" role="listitem">
      <span class="matching-waiting-summary-icon material-symbols-outlined">${icon}</span>
      <div class="min-w-0">
        <p class="text-[11px] font-bold text-secondary">${label}</p>
        <p class="mt-1 truncate text-xs font-extrabold text-brand-navy">${escapeHtml(value || '-')}</p>
      </div>
    </div>
  `
}

function renderSummaryChip(icon, value) {
  return `
    <span class="matching-summary-chip inline-flex min-w-0 items-center gap-1.5 rounded-full bg-surface-container-low px-3 py-1.5 text-xs font-bold text-brand-navy">
      <span class="material-symbols-outlined text-sm text-primary-container">${icon}</span>
      <span class="truncate">${escapeHtml(value || '-')}</span>
    </span>
  `
}

function tagLabel(code) {
  return TAG_LABELS.get(code) || String(code || '').replaceAll('_', ' ').toLowerCase()
}

function safeImageUrl(value) {
  if (!value) return ''
  try {
    const url = new URL(value, window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function parseMessage(message) {
  try {
    const payload = JSON.parse(message.body)
    return payload && typeof payload === 'object' ? payload : null
  } catch {
    return null
  }
}

function toFiniteNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function isNotFound(error) {
  return error?.status === 404
}

function isUnauthorized(error) {
  return error?.status === 401
}

function getUserErrorMessage(error, fallback) {
  return error instanceof MatchingApiError && error.message ? error.message : fallback
}
