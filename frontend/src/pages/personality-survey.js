import { getPersonalityProfile, upsertPersonalityProfile, skipPersonalityProfile, updateFoodPreferences, getFoodPreferences } from '../personality/personality-api.js'
import { navigateTo } from '../main.js'

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

export async function renderPersonalitySurvey(container) {
  let currentStep = 1
  const answers = {
    CONVERSATION_LEVEL: null,
    MEAL_PACE: null,
    PLANNING_STYLE: null,
    NOVELTY_PREFERENCE: null,
  }
  const selectedTags = new Set()
  const selectedFoods = new Set()
  let isSubmitting = false
  let errorMessage = ''

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
      if (Array.isArray(data.styleTags)) {
        data.styleTags.forEach((t) => selectedTags.add(t))
        needsUpdate = true
      }
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
    container.innerHTML = `
      <main class="min-h-[calc(100vh-88px)] py-8 px-4 sm:px-6 lg:px-8 bg-gradient-to-b from-[#FAF7F2] via-white to-[#FAF7F2] flex flex-col items-center">
        <!-- Container (shadcn Card style) -->
        <div class="w-full max-w-3xl bg-white border border-slate-200/80 rounded-2xl shadow-soft p-6 sm:p-10 transition-all">
          
          <!-- Stepper Header -->
          <div class="mb-8">
            <div class="flex items-center justify-between gap-4 mb-4">
              <div class="flex items-center gap-2">
                <span class="inline-flex items-center justify-center px-2.5 py-1 rounded-md text-xs font-bold tracking-wide uppercase bg-primary-container/15 text-primary">
                  Step ${currentStep} / 3
                </span>
                <span class="text-xs text-slate-500 font-medium hidden sm:inline-block">식사 스타일 맞춤 설정</span>
              </div>
              <button id="btn-survey-skip" type="button" class="text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors px-3 py-1.5 rounded-lg hover:bg-slate-100 flex items-center gap-1">
                <span>다음에 하기 (건너뛰기)</span>
                <span class="material-symbols-outlined text-sm">chevron_right</span>
              </button>
            </div>

            <!-- Progress Bar -->
            <div class="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
              <div class="bg-primary-container h-full transition-all duration-300 ease-out" style="width: ${(currentStep / 3) * 100}%;"></div>
            </div>

            <!-- Step Title -->
            <div class="mt-6 text-center sm:text-left">
              <h1 class="text-2xl sm:text-3xl font-extrabold text-brand-navy tracking-tight font-headline">
                ${getStepTitle(currentStep)}
              </h1>
              <p class="text-sm text-slate-600 mt-1.5">
                ${getStepDescription(currentStep)}
              </p>
            </div>
          </div>

          <!-- Error Alert Banner (shadcn Alert) -->
          <div id="survey-alert" class="${errorMessage ? 'block' : 'hidden'} mb-6 p-4 rounded-xl border border-red-200 bg-red-50/80 text-red-700 text-sm flex items-start gap-3">
            <span class="material-symbols-outlined text-red-500 text-lg flex-shrink-0 mt-0.5">error</span>
            <div class="flex-1 font-medium" id="survey-alert-text">${errorMessage}</div>
          </div>

          <!-- Step Content -->
          <div id="step-content" class="mb-10">
            ${renderStepContent(currentStep)}
          </div>

          <!-- Footer Actions (shadcn Buttons) -->
          <div class="pt-6 border-t border-slate-100 flex items-center justify-between gap-3">
            <button
              id="btn-survey-prev"
              type="button"
              class="px-5 py-2.5 rounded-xl border border-slate-200 text-slate-700 font-semibold text-sm hover:bg-slate-50 active:scale-98 transition-all flex items-center gap-1.5 ${currentStep === 1 ? 'invisible' : ''}"
            >
              <span class="material-symbols-outlined text-base">arrow_back</span>
              <span>이전</span>
            </button>

            <div class="flex items-center gap-3">
              ${
                currentStep < 3
                  ? `
                <button
                  id="btn-survey-next"
                  type="button"
                  class="px-6 py-2.5 rounded-xl bg-primary-container text-white font-bold text-sm shadow-sm hover:bg-primary hover:shadow active:scale-98 transition-all flex items-center gap-2"
                >
                  <span>다음 단계</span>
                  <span class="material-symbols-outlined text-base">arrow_forward</span>
                </button>
              `
                  : `
                <button
                  id="btn-survey-submit"
                  type="button"
                  ${isSubmitting ? 'disabled' : ''}
                  class="px-7 py-2.5 rounded-xl bg-primary-container text-white font-bold text-sm shadow-md hover:bg-primary hover:shadow-lg active:scale-98 transition-all flex items-center gap-2 ${isSubmitting ? 'opacity-60 cursor-not-allowed' : ''}"
                >
                  ${
                    isSubmitting
                      ? '<span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>'
                      : '<span class="material-symbols-outlined text-base">check_circle</span>'
                  }
                  <span>완료하고 시작하기</span>
                </button>
              `
              }
            </div>
          </div>
        </div>
      </main>
    `

    bindEvents()
  }

  function getStepTitle(step) {
    if (step === 1) return '1. 기본 식사 스타일'
    if (step === 2) return '2. 세부 스타일 태그'
    return '3. 선호 음식 카테고리'
  }

  function getStepDescription(step) {
    if (step === 1) return '편안한 식사를 위해 대화, 속도, 계획, 탐색 4가지 선호도를 선택해 주세요.'
    if (step === 2) return '나의 식사 성향과 분위기를 나타내는 키워드를 최대 5개까지 선택해 주세요.'
    return '자주 즐기거나 선호하는 음식 카테고리를 최대 5개까지 선택해 주세요.'
  }

  function renderStepContent(step) {
    if (step === 1) return renderStep1()
    if (step === 2) return renderStep2()
    return renderStep3()
  }

  // Step 1: 4개 차원 단일 선택 카드 (shadcn Radio Card Group)
  function renderStep1() {
    return `
      <div class="space-y-8">
        ${DIMENSIONS.map((dim, idx) => {
          const currentVal = answers[dim.key]
          return `
            <div class="space-y-3">
              <div class="flex items-center gap-2">
                <span class="w-6 h-6 rounded-full bg-slate-100 text-slate-700 text-xs font-bold flex items-center justify-center">${idx + 1}</span>
                <h3 class="text-base font-bold text-brand-navy">${dim.title}</h3>
                <span class="text-xs text-slate-500 font-normal">(${dim.description})</span>
              </div>

              <!-- Options Grid -->
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                ${dim.options
                  .map((opt) => {
                    const isSelected = currentVal === opt.value
                    return `
                    <div
                      data-dim="${dim.key}"
                      data-val="${opt.value}"
                      class="survey-dim-card cursor-pointer rounded-xl p-4 border transition-all select-none flex flex-col justify-between ${
                        isSelected
                          ? 'border-primary bg-primary-container/5 ring-2 ring-primary-container/20 shadow-sm'
                          : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50/70'
                      }"
                    >
                      <div class="flex items-start justify-between gap-2 mb-2">
                        <span class="text-sm font-bold ${isSelected ? 'text-primary' : 'text-brand-navy'}">${opt.label}</span>
                        <span class="inline-flex items-center justify-center w-5 h-5 rounded-full border text-xs transition-colors ${
                          isSelected ? 'border-primary bg-primary text-white' : 'border-slate-300 bg-white'
                        }">
                          ${isSelected ? '<span class="material-symbols-outlined text-[14px]">check</span>' : ''}
                        </span>
                      </div>
                      <p class="text-xs text-slate-500 leading-relaxed">${opt.desc}</p>
                    </div>
                  `
                  })
                  .join('')}
              </div>
            </div>
          `
        }).join('')}
      </div>
    `
  }

  // Step 2: 세부 스타일 태그 (최대 5개 선택, shadcn Badge Grid)
  function renderStep2() {
    const count = selectedTags.size
    return `
      <div class="space-y-6">
        <!-- Tag Count Header -->
        <div class="flex items-center justify-between bg-slate-50 border border-slate-200/70 rounded-xl px-4 py-3">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-primary-container text-lg">local_offer</span>
            <span class="text-sm font-semibold text-brand-navy">키워드 선택</span>
          </div>
          <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold ${
            count > 0 && count <= 5 ? 'bg-primary-container/15 text-primary' : 'bg-slate-200 text-slate-600'
          }">
            <span>선택됨:</span>
            <span class="font-extrabold">${count}</span>
            <span>/ 5</span>
          </div>
        </div>

        ${STYLE_TAG_GROUPS.map((group) => `
          <div class="space-y-3">
            <h4 class="text-xs font-bold text-slate-500 tracking-wide uppercase">${group.groupName}</h4>
            <div class="flex flex-wrap gap-2.5">
              ${group.tags
                .map((tag) => {
                  const isSelected = selectedTags.has(tag.code)
                  return `
                  <button
                    type="button"
                    data-tag="${tag.code}"
                    class="survey-tag-badge px-3.5 py-2 rounded-xl text-xs sm:text-sm font-medium border transition-all flex items-center gap-1.5 ${
                      isSelected
                        ? 'bg-primary-container text-white border-primary-container shadow-sm font-semibold'
                        : 'bg-white text-slate-700 border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                    }"
                  >
                    <span>${tag.label}</span>
                    ${isSelected ? '<span class="material-symbols-outlined text-sm">check</span>' : ''}
                  </button>
                `
                })
                .join('')}
            </div>
          </div>
        `).join('')}
      </div>
    `
  }

  // Step 3: 선호 음식 카테고리 (최대 5개 선택, shadcn Card Grid)
  function renderStep3() {
    const count = selectedFoods.size
    return `
      <div class="space-y-6">
        <!-- Food Count Header -->
        <div class="flex items-center justify-between bg-slate-50 border border-slate-200/70 rounded-xl px-4 py-3">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-primary-container text-lg">restaurant</span>
            <span class="text-sm font-semibold text-brand-navy">음식 카테고리 선택</span>
          </div>
          <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold ${
            count > 0 && count <= 5 ? 'bg-primary-container/15 text-primary' : 'bg-slate-200 text-slate-600'
          }">
            <span>선택됨:</span>
            <span class="font-extrabold">${count}</span>
            <span>/ 5</span>
          </div>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3.5">
          ${FOOD_CATEGORIES.map((food) => {
            const isSelected = selectedFoods.has(food.code)
            return `
              <div
                data-food="${food.code}"
                class="survey-food-card cursor-pointer rounded-xl p-4 border transition-all select-none flex flex-col items-center text-center gap-2.5 ${
                  isSelected
                    ? 'border-primary bg-primary-container/5 ring-2 ring-primary-container/20 shadow-sm'
                    : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50/70'
                }"
              >
                <div class="text-3xl sm:text-4xl select-none mb-1">${food.emoji}</div>
                <span class="text-sm font-bold ${isSelected ? 'text-primary' : 'text-brand-navy'}">${food.label}</span>
                <span class="inline-flex items-center justify-center w-5 h-5 rounded-full border text-xs transition-colors ${
                  isSelected ? 'border-primary bg-primary text-white' : 'border-slate-200 bg-white'
                }">
                  ${isSelected ? '<span class="material-symbols-outlined text-[13px]">check</span>' : ''}
                </span>
              </div>
            `
          }).join('')}
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
          navigateTo('/')
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
          errorMessage = `[${unselected.title}] 항목을 선택해 주세요.`
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

function renderCompletionView(container) {
  container.innerHTML = `
    <main class="page-shell">
      <section class="auth-card status-card animate-fade-in" aria-live="polite">
        <div class="brand-mark" aria-hidden="true">
          <span class="material-symbols-outlined text-3xl">celebration</span>
        </div>
        <p class="eyebrow">ONBOARDING COMPLETED</p>
        <h1 class="status-title text-2xl font-bold text-brand-navy">식사 스타일 설정 완료!</h1>
        <p class="description status-description text-slate-600 mt-2">
          선택하신 식사 스타일과 선호 음식을 기반으로 딱 맞는 밥친구를 찾아드립니다.
        </p>
        <div class="mt-6 flex flex-col gap-2.5">
          <a class="primary-link" href="/">홈으로 이동하기</a>
        </div>
      </section>
    </main>
  `
}
