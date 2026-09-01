import { getPersonalityProfile, upsertPersonalityProfile, skipPersonalityProfile, updateFoodPreferences, getFoodPreferences, suggestPersonalityTags } from '../personality/personality-api.js'
import { navigateTo, showToast } from '../main.js'

// V1 기본 스타일 차원 및 옵션 정의
const DIMENSIONS = [
  {
    key: 'CONVERSATION_LEVEL',
    title: '대화 분위기',
    description: '식사 중 대화는 어느 정도가 편하신가요?',
    icon: 'chat',
    options: [
      { value: 1, label: '조용한 식사', desc: '대화는 필요한 만큼만, 조용하고 편안하게', badge: '낮음' },
      { value: 3, label: '적당한 대화', desc: '상황과 분위기에 맞춰 자연스럽고 편안하게', badge: '중간' },
      { value: 5, label: '활발한 대화', desc: '다양한 주제로 이야기꽃을 피우며 즐겁게', badge: '높음' },
    ],
  },
  {
    key: 'MEAL_PACE',
    title: '식사 속도',
    description: '평소 음식을 드시는 속도는 어떤가요?',
    icon: 'timer',
    options: [
      { value: 1, label: '여유로운 식사', desc: '맛을 음미하며 천천히 대화와 함께', badge: '느림' },
      { value: 3, label: '보통 속도', desc: '너무 빠르지도 느리지도 않은 적당한 속도', badge: '보통' },
      { value: 5, label: '빠른 식사', desc: '시간을 효율적으로 쓰며 깔끔하고 신속하게', badge: '빠름' },
    ],
  },
  {
    key: 'PLANNING_STYLE',
    title: '약속 스타일',
    description: '식사 약속을 잡을 때 어떤 방식을 선호하시나요?',
    icon: 'calendar_month',
    options: [
      { value: 1, label: '즉흥적인 만남', desc: '당일 기분과 상황에 맞춰 유동적으로', badge: '즉흥적' },
      { value: 3, label: '유연한 조율', desc: '대략적인 틀을 잡고 편하게 맞춰가기', badge: '유연함' },
      { value: 5, label: '철저한 계획', desc: '시간·장소·메뉴를 사전에 꼼꼼하게 확정', badge: '계획적' },
    ],
  },
  {
    key: 'NOVELTY_PREFERENCE',
    title: '메뉴 탐색 스타일',
    description: '식당이나 메뉴를 고를 때의 성향은 어떠신가요?',
    icon: 'explore',
    options: [
      { value: 1, label: '익숙한 단골 메뉴', desc: '실패 없는 검증된 익숙한 맛 선호', badge: '안정적' },
      { value: 3, label: '균형 있는 선택', desc: '익숙함과 새로운 시도의 적절한 조화', badge: '균형' },
      { value: 5, label: '새로운 맛집 탐방', desc: '새로운 음식과 트렌디한 핫플레이스 도전', badge: '도전적' },
    ],
  },
]

// V1 세부 스타일 태그 목록
const STYLE_TAG_GROUPS = [
  {
    groupName: '대화 방식',
    tags: [
      { code: 'INITIATES_CONVERSATION', label: '먼저 대화를 시작해요' },
      { code: 'GOOD_LISTENER', label: '상대 이야기를 잘 들어요' },
      { code: 'FOOD_TALK', label: '음식 이야기를 좋아해요' },
      { code: 'LIGHT_CHAT', label: '가벼운 대화를 좋아해요' },
      { code: 'DEEP_TALK', label: '깊은 대화를 좋아해요' },
      { code: 'COMFORTABLE_SILENCE', label: '조용한 시간도 편해요' },
    ],
  },
  {
    groupName: '식사 분위기',
    tags: [
      { code: 'CALM_ATMOSPHERE', label: '차분한 분위기가 좋아요' },
      { code: 'CHEERFUL_ATMOSPHERE', label: '유쾌한 분위기가 좋아요' },
      { code: 'ACTIVE_ATMOSPHERE', label: '활발한 분위기가 좋아요' },
    ],
  },
  {
    groupName: '식사 습관',
    tags: [
      { code: 'SHARE_DISHES', label: '여러 메뉴를 나눠 먹어요' },
      { code: 'TAKE_FOOD_PHOTOS', label: '음식 사진을 찍는 편이에요' },
      { code: 'ENJOY_DESSERT', label: '디저트까지 함께 즐겨요' },
      { code: 'FOCUS_ON_MEAL', label: '식사 자체에 집중하는 편이에요' },
    ],
  },
]

// V1 선호 음식 카테고리 목록
const FOOD_CATEGORIES = [
  { code: 'KOREAN', label: '한식', icon: 'rice_bowl', emoji: '🍚' },
  { code: 'JAPANESE', label: '일식', icon: 'set_meal', emoji: '🍣' },
  { code: 'CHINESE', label: '중식', icon: 'ramen_dining', emoji: '🥟' },
  { code: 'WESTERN', label: '양식', icon: 'local_pizza', emoji: '🍕' },
  { code: 'SOUTHEAST_ASIAN', label: '동남아 음식', icon: 'dinner_dining', emoji: '🍜' },
  { code: 'SNACK', label: '분식', icon: 'bakery_dining', emoji: '🍢' },
  { code: 'FAST_FOOD', label: '패스트푸드', icon: 'fastfood', emoji: '🍔' },
  { code: 'CAFE_DESSERT', label: '카페·디저트', icon: 'cake', emoji: '☕' },
]

const SURVEY_STEPS = [
  { label: '식사 스타일', icon: 'tune' },
  { label: '대화 키워드', icon: 'forum' },
  { label: '선호 음식', icon: 'restaurant' },
]

function renderSurveyStepper(currentStep) {
  return `
    <div class="flex items-start" role="list">
      ${SURVEY_STEPS.map((step, index) => {
        const stepNumber = index + 1
        const isCurrent = currentStep === stepNumber
        const isCompleted = currentStep > stepNumber
        return `
          <div class="flex min-w-0 flex-1 items-start last:flex-none" role="listitem">
            <div class="flex min-w-0 flex-col items-center gap-2">
              <span
                class="inline-flex h-10 w-10 items-center justify-center rounded-full border-2 transition-colors ${
                  isCompleted
                    ? 'border-primary-container bg-primary-container text-white'
                    : isCurrent
                      ? 'border-primary-container bg-white text-primary-container shadow-[0_0_0_5px_rgba(255,107,74,0.14)]'
                      : 'border-slate-200 bg-white text-slate-400'
                }"
                ${isCurrent ? 'aria-current="step"' : ''}
              >
                <span class="material-symbols-outlined text-lg" aria-hidden="true">${isCompleted ? 'check' : step.icon}</span>
              </span>
              <span class="max-w-[5rem] text-center text-[11px] font-bold leading-tight ${
                isCurrent ? 'text-primary' : isCompleted ? 'text-brand-navy' : 'text-slate-400'
              }">${step.label}</span>
            </div>
            ${
              index < SURVEY_STEPS.length - 1
                ? `<span class="mx-2 mt-5 h-0.5 flex-1 rounded-full ${isCompleted ? 'bg-primary-container' : 'bg-slate-200'}" aria-hidden="true"></span>`
                : ''
            }
          </div>
        `
      }).join('')}
    </div>
  `
}

function renderSelectionChips(labels, emptyMessage = '아직 선택한 항목이 없어요.') {
  if (!labels.length) {
    return `<span class="text-xs font-medium text-slate-400">${emptyMessage}</span>`
  }

  return labels
    .map(
      (label) => `
        <span class="inline-flex items-center gap-1 rounded-full border border-primary-container/20 bg-primary-container/10 px-2.5 py-1 text-xs font-semibold text-primary">
          <span class="material-symbols-outlined text-sm" aria-hidden="true">check</span>
          ${escapeHtml(label)}
        </span>
      `,
    )
    .join('')
}

function renderSummaryChipsForDark(labels, emptyMessage = '선택 없음') {
  if (!labels.length) {
    return `<span class="text-xs font-medium text-white/50">${emptyMessage}</span>`
  }

  const visibleLabels = labels.slice(0, 3)
  const remainingCount = labels.length - visibleLabels.length
  return `${visibleLabels
    .map((label) => `<span class="rounded-full bg-white/15 px-2 py-1 text-[10px] font-bold text-white">${escapeHtml(label)}</span>`)
    .join('')}${remainingCount > 0 ? `<span class="rounded-full bg-white/15 px-2 py-1 text-[10px] font-bold text-white">+${remainingCount}</span>` : ''}`
}

function scoreToAnswerValue(score) {
  if (score === null || score === undefined || score === '') return null
  const numericScore = Number(score)
  if (!Number.isFinite(numericScore)) return null
  if (numericScore <= 25) return 1
  if (numericScore >= 75) return 5
  return 3
}

export async function renderPersonalitySurvey(container) {
  let currentStep = 1
  const answers = {
    CONVERSATION_LEVEL: null,
    MEAL_PACE: null,
    PLANNING_STYLE: null,
    NOVELTY_PREFERENCE: null,
  }
  const selectedTags = new Set()
  const suggestedTags = new Set()
  const selectedFoods = new Set()
  let selfDescription = ''
  let aiAnalysisConsent = false
  let isSuggesting = false
  let isSubmitting = false
  let errorMessage = ''
  let lastErrorToastMessage = ''

  // 초기 뷰 즉시 렌더링 (auth-card 깜빡임 방지)
  updateView()

  try {
    // 기존 설정 여부 비동기 조회 및 복원
    const [profileData, foodData] = await Promise.allSettled([
      getPersonalityProfile(),
      getFoodPreferences(),
    ])

    let needsUpdate = false
    if (profileData.status === 'fulfilled' && profileData.value) {
      const data = profileData.value
      if (data.scores) {
        const scoreByDimension = {
          CONVERSATION_LEVEL: data.scores.conversationLevel,
          MEAL_PACE: data.scores.mealPace,
          PLANNING_STYLE: data.scores.planningStyle,
          NOVELTY_PREFERENCE: data.scores.noveltyPreference,
        }
        Object.entries(scoreByDimension).forEach(([key, score]) => {
          const restoredValue = scoreToAnswerValue(score)
          if (restoredValue !== null) answers[key] = restoredValue
        })
        needsUpdate = true
      }
      if (Array.isArray(data.styleTags)) {
        data.styleTags.forEach((t) => selectedTags.add(t))
        needsUpdate = true
      }
      selfDescription = data.selfDescription || ''
      aiAnalysisConsent = data.aiAnalysisConsent === true
    }

    if (foodData.status === 'fulfilled' && foodData.value) {
      const data = foodData.value
      if (Array.isArray(data.foodCategories)) {
        data.foodCategories.forEach((f) => selectedFoods.add(f))
        needsUpdate = true
      }
    }

    if (needsUpdate) {
      updateView()
    }
  } catch (err) {
    if (err.status === 401) {
      alert('로그인이 필요한 페이지입니다.')
      window.location.assign('/')
      return
    }
  }

  // 메인 UI 렌더러
  function updateView() {
    if (errorMessage && errorMessage !== lastErrorToastMessage) {
      lastErrorToastMessage = errorMessage
      showToast(errorMessage, { type: 'error' })
    } else if (!errorMessage) {
      lastErrorToastMessage = ''
    }

    const answeredCount = Object.values(answers).filter((value) => value !== null).length
    const progressPercent = Math.round((currentStep / SURVEY_STEPS.length) * 100)

    container.innerHTML = `
      <main class="personality-survey-page min-h-[calc(100vh-88px)] px-4 py-8 sm:px-6 sm:py-12">
        <div class="mx-auto w-full max-w-4xl">
          <header class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div class="min-w-0">
              <h1 class="font-headline text-2xl font-extrabold tracking-tight text-brand-navy sm:text-3xl">나와 잘 맞는 식사 스타일을 찾아볼까요?</h1>
              <p class="mt-1.5 text-sm leading-relaxed text-secondary">약 1분이면 마주한끼에서 어울리는 밥친구를 찾는 데 도움이 되는 정보를 완성할 수 있어요.</p>
            </div>
            <button id="btn-survey-skip" type="button" class="inline-flex items-center gap-1 self-start rounded-full border border-outline-variant/40 bg-white px-3.5 py-2 text-xs font-bold text-secondary transition hover:border-outline-variant hover:bg-brand-ivory hover:text-brand-navy sm:self-auto">
              <span>다음에 하기</span>
              <span class="material-symbols-outlined text-base" aria-hidden="true">arrow_forward</span>
            </button>
          </header>

          <section class="personality-survey-card rounded-[28px] border border-outline-variant/30 bg-white p-5 shadow-floating sm:p-8">
            <div class="mb-8 rounded-2xl border border-primary-container/15 bg-brand-ivory/70 p-4 sm:p-5">
              <div class="mb-4 flex items-center justify-between gap-4">
                <div class="flex min-w-0 items-center gap-3">
                  <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-container text-sm font-extrabold text-white">${currentStep}</span>
                  <div class="min-w-0">
                    <p class="truncate text-sm font-extrabold text-brand-navy">${getStepProgressLabel(currentStep)}</p>
                    <p class="mt-0.5 text-xs font-medium text-secondary">총 ${SURVEY_STEPS.length}단계 · ${getStepHint(currentStep, answeredCount)}</p>
                  </div>
                </div>
                <span class="shrink-0 text-sm font-extrabold tabular-nums text-primary">${progressPercent}% <span class="font-semibold text-secondary">완료</span></span>
              </div>

              <div class="h-2 overflow-hidden rounded-full bg-white/80" role="progressbar" aria-label="성향 분석 진행률" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${progressPercent}">
                <div class="h-full rounded-full bg-primary-container transition-all duration-500 ease-out" style="width: ${progressPercent}%;"></div>
              </div>
              <nav class="mt-5" aria-label="성향 분석 단계">
                ${renderSurveyStepper(currentStep)}
              </nav>
            </div>

            <div class="mb-8 max-w-2xl">
              <span class="mb-3 inline-flex items-center gap-1.5 rounded-full bg-primary-container/10 px-3 py-1 text-xs font-extrabold uppercase tracking-[0.12em] text-primary">
                <span class="material-symbols-outlined text-sm" aria-hidden="true">${SURVEY_STEPS[currentStep - 1].icon}</span>
                Step ${currentStep}
              </span>
              <h2 id="survey-step-title" tabindex="-1" class="font-headline text-2xl font-extrabold tracking-tight text-brand-navy sm:text-3xl">${getStepTitle(currentStep)}</h2>
              <p class="mt-2 text-sm leading-relaxed text-secondary sm:text-base">${getStepDescription(currentStep)}</p>
            </div>

            <div id="step-content" aria-labelledby="survey-step-title">
              ${renderStepContent(currentStep)}
            </div>

            <div class="personality-survey-actions mt-8 flex items-center justify-between gap-3 border-t border-slate-100 pt-6">
              <div class="flex min-w-0 flex-1 items-center gap-2">
                ${
                  currentStep > 1
                    ? `<button id="btn-survey-prev" type="button" class="inline-flex shrink-0 items-center gap-1.5 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 active:scale-95"><span class="material-symbols-outlined text-base" aria-hidden="true">arrow_back</span><span>이전</span></button>`
                    : '<span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-container/10 text-primary-container"><span class="material-symbols-outlined text-lg" aria-hidden="true">tips_and_updates</span></span>'
                }
                <span class="hidden truncate text-xs font-semibold text-secondary sm:inline">${getStepHint(currentStep, answeredCount)}</span>
              </div>

              <div class="flex shrink-0">
                ${
                  currentStep < SURVEY_STEPS.length
                    ? `<button id="btn-survey-next" type="button" class="inline-flex items-center gap-2 rounded-xl bg-primary-container px-5 py-2.5 text-sm font-extrabold text-white shadow-glow-primary transition hover:bg-primary hover:shadow-lg active:scale-95 sm:px-6"><span>다음 단계</span><span class="material-symbols-outlined text-base" aria-hidden="true">arrow_forward</span></button>`
                    : `<button id="btn-survey-submit" type="button" ${isSubmitting ? 'disabled' : ''} class="inline-flex items-center gap-2 rounded-xl bg-primary-container px-5 py-2.5 text-sm font-extrabold text-white shadow-glow-primary transition hover:bg-primary hover:shadow-lg active:scale-95 sm:px-6 ${isSubmitting ? 'cursor-not-allowed opacity-60' : ''}">${isSubmitting ? '<span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>' : '<span class="material-symbols-outlined text-base" aria-hidden="true">check_circle</span>'}<span>내 식사 스타일 확인하기</span></button>`
                }
              </div>
            </div>
          </section>
        </div>
      </main>
    `

    bindEvents()
  }

  function getStepTitle(step) {
    if (step === 1) return '함께 먹을 때의 기본 스타일'
    if (step === 2) return '나를 보여주는 식사 키워드'
    return '좋아하는 음식 카테고리'
  }

  function getStepDescription(step) {
    if (step === 1) return '대화, 식사 속도, 약속, 메뉴 탐색에 대한 나의 선호를 골라 주세요.'
    if (step === 2) return '나의 식사 성향과 분위기를 가장 잘 표현하는 키워드를 최대 5개까지 골라 주세요.'
    return '자주 즐기거나 함께 먹고 싶은 음식 카테고리를 최대 5개까지 골라 주세요.'
  }

  function getStepProgressLabel(step) {
    return SURVEY_STEPS[step - 1]?.label || '식사 스타일'
  }

  function getStepHint(step, answeredCount) {
    if (step === 1) return `${answeredCount}/4개 질문에 답했어요`
    if (step === 2) return `${selectedTags.size}/5개 키워드를 선택했어요`
    return selectedFoods.size > 0 ? `${selectedFoods.size}/5개 음식을 선택했어요` : '좋아하는 음식은 선택 사항이에요'
  }

  function renderStepContent(step) {
    if (step === 1) return renderStep1()
    if (step === 2) return renderStep2()
    return renderStep3()
  }

  // Step 1: 4개 차원 단일 선택 카드
  function renderStep1() {
    return `
      <div class="space-y-7">
        ${DIMENSIONS.map((dim) => {
          const currentVal = answers[dim.key]
          return `
            <section class="survey-dimension-group rounded-2xl border border-transparent p-1 transition-colors">
              <div class="mb-3 flex items-start justify-between gap-3">
                <div class="flex min-w-0 items-start gap-3">
                  <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-container/10 text-primary-container">
                    <span class="material-symbols-outlined text-lg" aria-hidden="true">${dim.icon}</span>
                  </span>
                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                      <h3 class="text-base font-extrabold text-brand-navy">${dim.title}</h3>
                    </div>
                    <p class="mt-1 text-xs leading-relaxed text-secondary">${dim.description}</p>
                  </div>
                </div>
                <span class="shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold ${currentVal ? 'bg-primary-container/10 text-primary' : 'bg-slate-100 text-slate-400'}">
                  ${currentVal ? '선택 완료' : '선택 필요'}
                </span>
              </div>

              <div class="grid grid-cols-1 gap-3 sm:grid-cols-3" role="group" aria-label="${dim.title}">
                ${dim.options
                  .map((opt) => {
                    const isSelected = currentVal === opt.value
                    return `
                    <button
                      type="button"
                      data-dim="${dim.key}"
                      data-val="${opt.value}"
                      aria-pressed="${isSelected}"
                      aria-label="${dim.title}: ${opt.label}"
                      class="survey-dim-card group relative flex min-h-[146px] cursor-pointer select-none flex-col justify-between rounded-2xl border p-4 text-left transition-all ${
                        isSelected
                          ? 'border-primary-container bg-primary-container/5 ring-2 ring-primary-container/20 shadow-soft'
                          : 'border-slate-200 bg-white hover:border-primary-container/40 hover:bg-brand-ivory/50'
                      }"
                    >
                      <div class="flex items-start justify-between gap-2">
                        <span class="text-sm font-extrabold ${isSelected ? 'text-primary' : 'text-brand-navy'}">${opt.label}</span>
                        <span class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs transition-colors ${
                          isSelected ? 'border-primary-container bg-primary-container text-white' : 'border-slate-200 bg-white text-transparent'
                        }">
                          <span class="material-symbols-outlined text-[15px]" aria-hidden="true">${isSelected ? 'check' : 'radio_button_unchecked'}</span>
                        </span>
                      </div>
                      <p class="mt-3 pr-2 text-xs leading-relaxed text-secondary">${opt.desc}</p>
                      <span class="mt-4 inline-flex w-fit rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-500 ${isSelected ? 'bg-primary-container/10 text-primary' : ''}">${opt.badge}</span>
                    </button>
                  `
                  })
                  .join('')}
              </div>
            </section>
          `
        }).join('')}
      </div>
    `
  }

  // Step 2: 세부 스타일 태그 (최대 5개 선택)
  function renderStep2() {
    const count = selectedTags.size
    return `
      <div class="space-y-6">
        <div class="rounded-2xl border border-primary-container/15 bg-brand-ivory/60 p-4 sm:p-5">
          <div class="flex items-start justify-between gap-4">
            <div class="flex min-w-0 items-start gap-3">
              <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-container text-white">
                <span class="material-symbols-outlined text-lg" aria-hidden="true">local_offer</span>
              </span>
              <div class="min-w-0">
                <p class="text-sm font-extrabold text-brand-navy">나를 보여주는 키워드</p>
                <p class="mt-1 text-xs leading-relaxed text-secondary">나와 잘 맞는 밥친구에게 보여주고 싶은 특징을 골라 주세요.</p>
              </div>
            </div>
            <span class="inline-flex shrink-0 items-baseline gap-1 rounded-full px-3 py-1.5 text-xs font-bold ${
              count === 5 ? 'bg-primary-container text-white' : count > 0 ? 'bg-primary-container/15 text-primary' : 'bg-white text-slate-500'
            }">
              <span class="text-base font-extrabold tabular-nums">${count}</span><span>/ 5</span>
            </span>
          </div>
          <div class="mt-4 flex min-h-7 flex-wrap items-center gap-2">
            ${renderSelectionChips(Array.from(selectedTags).map(tagLabel), '아직 선택한 키워드가 없어요.')}
          </div>
        </div>

        ${STYLE_TAG_GROUPS.map((group) => `
          <section class="space-y-3">
            <div class="flex items-center gap-2">
              <span class="h-px w-5 bg-primary-container/40"></span>
              <h4 class="text-xs font-extrabold tracking-[0.12em] text-secondary">${group.groupName}</h4>
            </div>
            <div class="flex flex-wrap gap-2">
              ${group.tags
                .map((tag) => {
                  const isSelected = selectedTags.has(tag.code)
                  return `
                  <button
                    type="button"
                    data-tag="${tag.code}"
                    aria-pressed="${isSelected}"
                    class="survey-tag-badge inline-flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-xs font-semibold transition-all sm:text-sm ${
                      isSelected
                        ? 'border-primary-container bg-primary-container text-white shadow-sm'
                        : 'border-slate-200 bg-white text-slate-700 hover:border-primary-container/40 hover:bg-brand-ivory'
                    }"
                  >
                    <span>${tag.label}</span>
                    <span class="material-symbols-outlined text-sm" aria-hidden="true">${isSelected ? 'check' : 'add'}</span>
                  </button>
                `
                })
                .join('')}
            </div>
          </section>
        `).join('')}

        <div class="space-y-4 border-t border-slate-100 pt-6">
          <div class="rounded-2xl border border-slate-200/80 bg-slate-50/70 p-4 sm:p-5">
            <div class="flex items-start gap-3">
              <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-primary-container shadow-sm">
                <span class="material-symbols-outlined text-lg" aria-hidden="true">edit_note</span>
              </span>
              <div class="min-w-0 flex-1">
                <label for="personality-self-description" class="text-sm font-extrabold text-brand-navy">나의 식사 스타일을 직접 소개해 주세요 <span class="font-normal text-slate-400">(선택)</span></label>
                <p class="mt-1 text-xs leading-relaxed text-secondary">조금 더 나다운 모습을 전하고 싶다면 자유롭게 적어 주세요.</p>
              </div>
            </div>
            <textarea id="personality-self-description" maxlength="300" rows="4" class="mt-4 w-full resize-y rounded-xl border border-slate-200 bg-white px-3.5 py-3 text-sm leading-relaxed text-brand-navy outline-none transition focus:border-primary-container focus:ring-2 focus:ring-primary-container/20" placeholder="예: 처음에는 조용하지만 친해지면 대화를 많이 하고, 새로운 맛집을 찾아다니는 편이에요.">${escapeHtml(selfDescription)}</textarea>
            <div class="mt-1.5 flex items-center justify-between text-xs text-slate-400"><span>최대 300자</span><span><span id="self-description-count">${selfDescription.length}</span> / 300</span></div>
          </div>

          <div class="rounded-2xl border border-primary-container/15 bg-primary-container/5 p-4">
            <label class="flex cursor-pointer items-start gap-3">
              <input id="ai-analysis-consent" type="checkbox" ${aiAnalysisConsent ? 'checked' : ''} class="mt-0.5 h-4 w-4 rounded border-slate-300 text-primary-container focus:ring-primary-container">
              <span class="min-w-0 text-xs leading-relaxed text-secondary"><span class="font-extrabold text-brand-navy">AI로 키워드 추천받기</span><br>자유 설명을 AI 태그 추천과 성향 임베딩 생성에 사용하는 데 동의합니다. 동의를 철회하면 자유 설명과 파생 임베딩이 삭제됩니다.</span>
            </label>
            <button id="btn-suggest-tags" type="button" ${isSuggesting ? 'disabled' : ''} class="mt-3 inline-flex items-center gap-1.5 rounded-xl border border-primary px-3.5 py-2 text-xs font-extrabold text-primary transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-50">
              <span class="material-symbols-outlined text-sm" aria-hidden="true">auto_awesome</span>
              <span>${isSuggesting ? '추천 중…' : 'AI 태그 추천받기'}</span>
            </button>
          </div>

          ${suggestedTags.size > 0 ? `
            <div class="rounded-2xl border border-primary-container/20 bg-white p-4 shadow-soft">
              <div class="flex items-start gap-2">
                <span class="material-symbols-outlined text-lg text-primary-container" aria-hidden="true">auto_awesome</span>
                <p class="text-xs leading-relaxed text-secondary">AI가 추천한 키워드예요. 원하는 항목을 눌러 최종 선택에 추가해 주세요.</p>
              </div>
              <div class="mt-3 flex flex-wrap gap-2">
                ${Array.from(suggestedTags).map((code) => `<button type="button" data-suggested-tag="${code}" class="inline-flex items-center gap-1 rounded-full border border-primary-container/30 bg-primary-container/5 px-3 py-1.5 text-xs font-extrabold text-primary transition hover:bg-primary-container/10"><span class="material-symbols-outlined text-sm" aria-hidden="true">add</span>${escapeHtml(tagLabel(code))}</button>`).join('')}
              </div>
            </div>
          ` : ''}
        </div>
      </div>
    `
  }

  // Step 3: 선호 음식 카테고리 (최대 5개 선택)
  function renderStep3() {
    const count = selectedFoods.size
    const answeredCount = Object.values(answers).filter((value) => value !== null).length
    return `
      <div class="space-y-6">
        <div class="rounded-2xl border border-primary-container/15 bg-brand-ivory/60 p-4 sm:p-5">
          <div class="flex items-start justify-between gap-4">
            <div class="flex min-w-0 items-start gap-3">
              <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-container text-white">
                <span class="material-symbols-outlined text-lg" aria-hidden="true">restaurant</span>
              </span>
              <div class="min-w-0">
                <p class="text-sm font-extrabold text-brand-navy">좋아하는 음식</p>
                <p class="mt-1 text-xs leading-relaxed text-secondary">함께 먹고 싶은 음식 취향을 알려주면 더 잘 맞는 제안을 받을 수 있어요.</p>
              </div>
            </div>
            <span class="inline-flex shrink-0 items-baseline gap-1 rounded-full px-3 py-1.5 text-xs font-bold ${
              count === 5 ? 'bg-primary-container text-white' : count > 0 ? 'bg-primary-container/15 text-primary' : 'bg-white text-slate-500'
            }">
              <span class="text-base font-extrabold tabular-nums">${count}</span><span>/ 5</span>
            </span>
          </div>
          <div class="mt-4 flex min-h-7 flex-wrap items-center gap-2">
            ${renderSelectionChips(Array.from(selectedFoods).map(foodLabel), '아직 선택한 음식이 없어요.')}
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-3.5">
          ${FOOD_CATEGORIES.map((food) => {
            const isSelected = selectedFoods.has(food.code)
            return `
              <button
                type="button"
                data-food="${food.code}"
                aria-pressed="${isSelected}"
                aria-label="${food.label}"
                class="survey-food-card group relative flex min-h-[154px] cursor-pointer select-none flex-col items-center justify-center gap-2.5 rounded-2xl border p-4 text-center transition-all ${
                  isSelected
                    ? 'border-primary-container bg-primary-container/5 ring-2 ring-primary-container/20 shadow-soft'
                    : 'border-slate-200 bg-white hover:border-primary-container/40 hover:bg-brand-ivory/50'
                }"
              >
                <span class="absolute right-3 top-3 inline-flex h-6 w-6 items-center justify-center rounded-full border text-xs transition-colors ${
                  isSelected ? 'border-primary-container bg-primary-container text-white' : 'border-slate-200 bg-white text-transparent'
                }">
                  <span class="material-symbols-outlined text-[15px]" aria-hidden="true">${isSelected ? 'check' : 'radio_button_unchecked'}</span>
                </span>
                <span class="mb-1 flex h-14 w-14 items-center justify-center rounded-2xl transition-colors sm:h-16 sm:w-16 ${
                  isSelected
                    ? 'bg-primary-container text-white'
                    : 'bg-primary-container/10 text-primary-container'
                }" aria-hidden="true">
                  <span class="material-symbols-outlined text-3xl sm:text-4xl">${food.icon}</span>
                </span>
                <span class="text-sm font-extrabold ${isSelected ? 'text-primary' : 'text-brand-navy'}">${food.label}</span>
              </button>
            `
          }).join('')}
        </div>

        <div class="rounded-2xl border border-brand-navy/10 bg-brand-navy p-4 text-white sm:p-5">
          <div class="flex items-start gap-3">
            <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white/10 text-primary-container">
              <span class="material-symbols-outlined text-lg" aria-hidden="true">fact_check</span>
            </span>
            <div>
              <h3 class="text-sm font-extrabold">지금까지 선택한 내용</h3>
              <p class="mt-1 text-xs leading-relaxed text-white/70">이 정보는 나와 잘 맞는 밥친구를 찾는 데 활용돼요.</p>
            </div>
          </div>
          <div class="mt-4 grid gap-3 sm:grid-cols-3">
            <div class="rounded-xl bg-white/10 p-3">
              <div class="flex items-center gap-1.5 text-xs font-bold text-white/70"><span class="material-symbols-outlined text-sm text-primary-container" aria-hidden="true">tune</span>식사 스타일</div>
              <p class="mt-2 text-sm font-extrabold">${answeredCount}/4개 응답 완료</p>
            </div>
            <div class="rounded-xl bg-white/10 p-3">
              <div class="flex items-center gap-1.5 text-xs font-bold text-white/70"><span class="material-symbols-outlined text-sm text-primary-container" aria-hidden="true">local_offer</span>식사 키워드</div>
              <div class="mt-2 flex min-h-5 flex-wrap gap-1.5">${renderSummaryChipsForDark(Array.from(selectedTags).map(tagLabel), '선택 없음')}</div>
            </div>
            <div class="rounded-xl bg-white/10 p-3">
              <div class="flex items-center gap-1.5 text-xs font-bold text-white/70"><span class="material-symbols-outlined text-sm text-primary-container" aria-hidden="true">restaurant</span>선호 음식</div>
              <div class="mt-2 flex min-h-5 flex-wrap gap-1.5">${renderSummaryChipsForDark(Array.from(selectedFoods).map(foodLabel), '선택 없음')}</div>
            </div>
          </div>
        </div>
      </div>
    `
  }

  function bindEvents() {
    // Step 1 Radio Cards
    container.querySelectorAll('.survey-dim-card').forEach((el) => {
      el.addEventListener('click', () => {
        const dim = el.getAttribute('data-dim')
        const val = parseInt(el.getAttribute('data-val'), 10)
        answers[dim] = val
        errorMessage = ''
        updateView()
      })
    })

    // Step 2 Tag Badges
    container.querySelectorAll('.survey-tag-badge').forEach((el) => {
      el.addEventListener('click', () => {
        const code = el.getAttribute('data-tag')
        if (selectedTags.has(code)) {
          selectedTags.delete(code)
          errorMessage = ''
        } else {
          if (selectedTags.size >= 5) {
            errorMessage = '세부 스타일 태그는 최대 5개까지만 선택할 수 있습니다.'
            updateView()
            return
          }
          selectedTags.add(code)
          errorMessage = ''
        }
        updateView()
      })
    })

    const descriptionInput = container.querySelector('#personality-self-description')
    descriptionInput?.addEventListener('input', () => {
      selfDescription = descriptionInput.value
      const count = container.querySelector('#self-description-count')
      if (count) count.textContent = String(selfDescription.length)
    })
    container.querySelector('#ai-analysis-consent')?.addEventListener('change', (event) => {
      aiAnalysisConsent = event.target.checked
      if (!aiAnalysisConsent) suggestedTags.clear()
      updateView()
    })
    container.querySelector('#btn-suggest-tags')?.addEventListener('click', async () => {
      if (!aiAnalysisConsent) {
        errorMessage = 'AI 태그 추천을 받으려면 분석 동의가 필요합니다.'
        updateView()
        return
      }
      if (!selfDescription.trim()) {
        errorMessage = '태그 추천을 받을 자유 설명을 입력해 주세요.'
        updateView()
        return
      }
      isSuggesting = true
      errorMessage = ''
      updateView()
      try {
        const result = await suggestPersonalityTags({ selfDescription: selfDescription.trim(), aiAnalysisConsent: true })
        suggestedTags.clear()
        ;(result.suggestedTags || []).forEach((tag) => suggestedTags.add(tag))
        if (!result.available) errorMessage = '현재 AI 추천을 사용할 수 없습니다. 직접 태그를 선택해 주세요.'
      } catch (err) {
        errorMessage = err.message || 'AI 태그 추천에 실패했습니다.'
      } finally {
        isSuggesting = false
        updateView()
      }
    })
    container.querySelectorAll('[data-suggested-tag]').forEach((el) => {
      el.addEventListener('click', () => {
        const code = el.getAttribute('data-suggested-tag')
        if (!selectedTags.has(code) && selectedTags.size >= 5) {
          errorMessage = '세부 스타일 태그는 최대 5개까지만 선택할 수 있습니다.'
        } else {
          selectedTags.add(code)
          suggestedTags.delete(code)
          errorMessage = ''
        }
        updateView()
      })
    })

    // Step 3 Food Cards
    container.querySelectorAll('.survey-food-card').forEach((el) => {
      el.addEventListener('click', () => {
        const code = el.getAttribute('data-food')
        if (selectedFoods.has(code)) {
          selectedFoods.delete(code)
          errorMessage = ''
        } else {
          if (selectedFoods.size >= 5) {
            errorMessage = '음식 카테고리는 최대 5개까지만 선택할 수 있습니다.'
            updateView()
            return
          }
          selectedFoods.add(code)
          errorMessage = ''
        }
        updateView()
      })
    })

    // 건너뛰기 버튼
    container.querySelector('#btn-survey-skip')?.addEventListener('click', async () => {
      if (confirm('온보딩을 건너뛰시겠습니까? 마이페이지에서 언제든 다시 설정할 수 있습니다.')) {
        try {
          await skipPersonalityProfile()
          navigateTo('/mypage')
        } catch (err) {
          alert(err.message || '건너뛰기 요청에 실패했습니다.')
        }
      }
    })

    // 이전 버튼
    container.querySelector('#btn-survey-prev')?.addEventListener('click', () => {
      if (currentStep > 1) {
        currentStep -= 1
        errorMessage = ''
        updateView()
      }
    })

    // 다음 버튼
    container.querySelector('#btn-survey-next')?.addEventListener('click', () => {
      if (currentStep === 1) {
        // Step 1 유효성 검사: 4개 차원 모두 선택되었는지 확인
        const unselected = DIMENSIONS.find((d) => !answers[d.key])
        if (unselected) {
          errorMessage = `${unselected.title} 항목을 선택해 주세요.`
          updateView()
          return
        }
      } else if (currentStep === 2) {
        if (selectedTags.size > 5) {
          errorMessage = '세부 스타일 태그는 최대 5개까지만 선택할 수 있습니다.'
          updateView()
          return
        }
      }
      currentStep += 1
      errorMessage = ''
      updateView()
    })

    // 제출 버튼 (Step 3)
    container.querySelector('#btn-survey-submit')?.addEventListener('click', async () => {
      if (selectedFoods.size > 5) {
        errorMessage = '음식 카테고리는 최대 5개까지만 선택할 수 있습니다.'
        updateView()
        return
      }

      isSubmitting = true
      errorMessage = ''
      updateView()

      try {
        // 1. 성향 프로필 등록/갱신
        const answerPayload = Object.entries(answers).map(([questionCode, value]) => ({
          questionCode,
          value,
        }))

        await upsertPersonalityProfile({
          questionnaireVersion: 'MEAL_PERSONALITY_V1',
          answers: answerPayload,
          styleTags: Array.from(selectedTags),
          selfDescription: aiAnalysisConsent ? selfDescription.trim() || null : null,
          aiAnalysisConsent,
        })

        // 2. 음식 선호 카테고리 갱신
        if (selectedFoods.size > 0) {
          await updateFoodPreferences(Array.from(selectedFoods))
        }

        renderCompletionView(container)
      } catch (err) {
        isSubmitting = false
        errorMessage = err.message || '설정 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
        updateView()
      }
    })
  }

  updateView()
}

function tagLabel(code) {
  return STYLE_TAG_GROUPS.flatMap((group) => group.tags).find((tag) => tag.code === code)?.label || code
}

function foodLabel(code) {
  return FOOD_CATEGORIES.find((food) => food.code === code)?.label || code
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;',
  })[character])
}

function renderCompletionView(container) {
  container.innerHTML = `
    <main class="personality-survey-page flex min-h-[calc(100vh-88px)] items-center px-4 py-10 sm:px-6">
      <section class="mx-auto w-full max-w-xl rounded-[28px] border border-outline-variant/30 bg-white p-7 text-center shadow-floating sm:p-10" aria-live="polite">
        <div class="mx-auto inline-flex h-20 w-20 items-center justify-center rounded-[26px] bg-primary-container/10 text-primary-container shadow-soft">
          <span class="material-symbols-outlined text-5xl" aria-hidden="true">celebration</span>
        </div>
        <p class="mt-6 text-xs font-extrabold uppercase tracking-[0.16em] text-primary-container">PROFILE READY</p>
        <h1 class="mt-2 font-headline text-2xl font-extrabold tracking-tight text-brand-navy sm:text-3xl">식사 스타일 설정 완료!</h1>
        <p class="mt-3 text-sm leading-relaxed text-secondary sm:text-base">
          선택한 식사 스타일과 선호 음식을 바탕으로<br class="hidden sm:inline" /> 나와 잘 맞는 밥친구를 찾아드릴게요.
        </p>
        <div class="mt-7 rounded-2xl bg-brand-ivory p-4 text-left">
          <div class="flex items-start gap-3">
            <span class="material-symbols-outlined text-xl text-primary-container" aria-hidden="true">favorite</span>
            <p class="text-xs leading-relaxed text-secondary">마이페이지에서 언제든 성향과 선호 음식을 다시 바꿀 수 있어요.</p>
          </div>
        </div>
        <a class="mt-7 inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-primary-container px-5 text-sm font-extrabold text-white shadow-glow-primary transition hover:bg-primary hover:shadow-lg" href="/mypage">
          <span>마이페이지로 이동하기</span>
          <span class="material-symbols-outlined text-base" aria-hidden="true">arrow_forward</span>
        </a>
      </section>
    </main>
  `
}
