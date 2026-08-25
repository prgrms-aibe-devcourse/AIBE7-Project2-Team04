# 마주한끼 (Maju-Hankki) Frontend Design System

> **Design System Name:** Warm Table (Digital Hospitality)  
> **Service Name:** 마주한끼 (Maju-Hankki)  
> **Key Slogan:** 마주 앉아 나누는 따뜻한 한 끼, 혼밥 말고 마주한끼 (Find Your 1:1 Dining Companion)  
> **Reference:** Stitch MCP `Bab-Chingu Dining Companion` (`projects/17669164501791521432`)

---

## 1. 개요 및 디자인 철학 (Brand & Philosophy)

**'마주한끼 (Maju-Hankki)'**의 디자인 시스템 **'Warm Table'**은 서로 마주 보고 따뜻한 밥 한 끼를 나누는 디지털 인터랙션과 오프라인 식사의 연결을 지향하는 **"디지털 환대(Digital Hospitality)"**를 핵심 가치로 삼습니다.

- **브랜드 성격 (Brand Personality):**
  - **Inviting (다정한):** 처음 방문한 사용자도 마주 앉아 환영받는 느낌을 주는 포근한 분위기
  - **Trustworthy (신뢰할 수 있는):** 1:1 오프라인 만남의 불안감을 해소하는 안정감 있고 투명한 정보 구조
  - **Whimsical (유쾌하고 위트 있는):** 밥그릇의 모락모락 피어나는 김, 마주 보고 웃는 캐릭터 일러스트 등 귀엽고 친근한 비주얼 터치
- **비주얼 스타일 (Visual Style):**
  - **Soft Modernism & Tactile Minimalism:** 차가운 디지털 느낌을 배제하고, 부드러운 곡선(Ultra-soft shapes), 따뜻한 아이보리 톤의 캔버스, 만지고 싶은 촉각적 피드백을 결합
- **감성 목표 (Emotional Response):**
  - **"Safe Excitement"**: 새로운 밥친구와의 만남이 어색하거나 두렵지 않고, 마주 앉아 맛있는 한 끼를 함께 나눌 기대감과 안도감을 전달합니다.

---

## 2. 컬러 시스템 (Color Palette & Tokens)

Warm Table 컬러 팔레트는 높은 대비와 따뜻한 조화를 이루는 3가지 앵커 컬러와 감성적인 보조/시맨틱 컬러로 구성됩니다.

### 2.1 핵심 앵커 컬러 (Core Palette)

| 토큰명 | Hex | 역할 및 사용 가이드 |
|---|---|---|
| **Warm Ivory** | `#FAF7F2` | **기본 배경(Canvas).** 차가운 순백색을 대체하여 눈의 피로를 덜고 집밥 같은 아늑한 식탁 무드를 조성 |
| **Coral Orange** | `#FF6B4A` | **주요 액션(Primary Accent).** 주요 CTA 버튼, 활성 상태, 하이라이트 뱃지, 매칭 애니메이션 펄스 링에 사용 |
| **Midnight Navy** | `#1E2C4A` (`#0B1C30`) | **신뢰 기반 텍스트(Foundation).** 헤드라인, 주요 텍스트, 구조적 라인, 보조 버튼 테두리에 사용하여 가독성과 신뢰감 부여 |

### 2.2 시맨틱 & 보조 컬러 (Semantic & Accent Colors)

| 구분 | Hex | 설명 |
|---|---|---|
| **Soft Sage Green (Success)** | `#82A67D` | 매칭 성공, 인증 완료, 긍정적 상태 |
| **Muted Terracotta (Error)** | `#BA1A1A` / `#D65A41` | 경고, 오류 메시지, 취소 (따뜻한 스펙트럼 유지) |
| **Warm Yellow (Rating)** | `#FACC15` | 별점, 후기 평점 표시 |
| **Secondary Slate** | `#505E7F` | 보조 설명 텍스트, 비활성 아이콘 |

### 2.3 서피스 & 머티리얼 토큰 (Tonal Surfaces)

| 토큰명 | Hex Code | 용도 |
|---|---|---|
| `surface` | `#F8F9FF` / `#FAF7F2` | 기본 페이지 배경 |
| `surface-container-lowest` | `#FFFFFF` | 메인 콘텐츠 카드, 입력 폼 배경 |
| `surface-container-low` | `#EFF4FF` | 섹션 래퍼, 보조 컨테이너 배경 |
| `surface-container` | `#E5EEFF` | 구분 영역, 푸터 배경 |
| `surface-container-high` | `#DCE9FF` | 호버 상태 및 강조 박스 |
| `surface-dim` | `#CBDBF5` | 아바타 플레이스홀더, 태그 배경 |
| `outline` | `#8D716A` | 구분선 및 테두리 |
| `outline-variant` | `#E1BFB8` | 연한 인풋/카드 테두리 |

---

## 3. 타이포그래피 (Typography System)

명확한 정보 전달(Precision)과 다정한 개성(Personality)을 동시에 전달할 수 있는 계층 구조를 갖춥니다.

### 3.1 폰트 패밀리 (Font Families)
- **Display & Headline:** `Plus Jakarta Sans` (기하학적이며 둥근 마감으로 현대적이고 세련된 인상)
- **Body & Label:** `Be Vietnam Pro` / `Pretendard` (본문 판독성 및 국문 글꼴과의 뛰어난 조화)
- **국문 Fallback:** `Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`

### 3.2 타이포그래피 스케일 (Typography Scales)

| 토큰명 | Font Family | Size | Weight | Line Height | Letter Spacing | 용도 |
|---|---|---|---|---|---|---|
| `headline-xl` | Plus Jakarta Sans | 40px | 700 (Bold) | 48px | -0.02em | 데스크톱 히어로 메인 타이틀 |
| `headline-lg` | Plus Jakarta Sans | 32px | 700 (Bold) | 40px | -0.01em | 섹션 헤더, 큰 타이틀 |
| `headline-lg-mobile` | Plus Jakarta Sans | 28px | 700 (Bold) | 36px | normal | 모바일 히어로 타이틀 |
| `headline-md` | Plus Jakarta Sans | 24px | 600 (SemiBold) | 32px | normal | 카드 타이틀, 모달 헤더 |
| `headline-sm` | Plus Jakarta Sans | 20px | 600 (SemiBold) | 28px | normal | 서브 타이틀, 프로필 이름 |
| `body-lg` | Be Vietnam Pro | 18px | 400 (Regular) | 28px | normal | 리드 문구, 히어로 설명글 |
| `body-md` | Be Vietnam Pro | 16px | 400 (Regular) | 24px | normal | 기본 본문 텍스트, 폼 입력 |
| `label-md` | Be Vietnam Pro | 14px | 600 (SemiBold) | 20px | 0.02em | 버튼 텍스트, 네비게이션 링크, 태그 |
| `label-sm` | Be Vietnam Pro | 12px | 600 (SemiBold) | 16px | 0.02em | 보조 라벨, 날짜/시간 메타데이터 |

---

## 4. 레이아웃 및 간격 (Layout & Spacing)

### 4.1 그리드 시스템
- **Desktop (1024px+):** 12-컬럼 그리드, 최대 너비 `1280px` (`max-w-7xl`), 좌우 마진 `40px` (`margin-desktop`), 거터 `24px`
- **Tablet (768px ~ 1023px):** 8-컬럼 그리드, 좌우 마진 `32px`, 거터 `20px`
- **Mobile (767px 이하):** 4-컬럼 그리드, 좌우 마진 `20px` (`margin-mobile`), 거터 `16px`

### 4.2 간격 규칙 (4px / 8px Incremental Scale)
- `base`: 4px
- `xs`: 8px
- `sm`: 12px
- `md`: 16px
- `lg`: 24px
- `xl`: 32px
- `xxl`: 48px
- `section-py`: 96px (섹션 간 여유로운 상하 여백)
- **정보 밀도 원칙 ("Air over Information"):** 카드 및 콘텐츠 사이에 충분한 여백을 두어 한 번에 하나의 프로필이나 식당 정보에 집중하도록 설계하여 선택 피로도를 줄입니다.

---

## 5. 형태 및 깊이감 (Shapes, Radius & Elevation)

친근함과 안전함을 시각화하기 위해 날카로운 모서리를 배제하고 **울트라 소프트(Ultra-Soft)** 형태 언어를 적용합니다.

### 5.1 곡률 가이드 (Border Radius)
- **Section Cards / Bento Cards:** `24px` (`rounded-card`)
- **Large Modals / Mobile Frame / Promo Containers:** `32px` ~ `40px`
- **Card Images:** `20px` (`rounded-image`)
- **Buttons / Badges / Search Bar:** `rounded-full` (완전한 타원형 필 형태) 또는 `16px` (`rounded-2xl`)
- **Input Fields & Selects:** `rounded-full` 또는 `12px` (`rounded-xl`)
- **Avatars:** `rounded-full` (원형)

### 5.2 엘리베이션 및 그림자 (Elevation & Shadows)
강하고 어두운 그림자 대신, 부드럽고 확산된 톤의 그림자를 사용합니다.

```css
/* 기본 카드 그림자 (Soft Ambient Shadow) */
box-shadow: 0 16px 40px rgba(30, 44, 74, 0.06);

/* 호버 및 액티브 그림자 (Hover Deep Shadow) */
box-shadow: 0 20px 48px rgba(30, 44, 74, 0.10);

/* 프로필 / 모바일 카드 플로팅 그림자 */
box-shadow: 0 24px 60px rgba(30, 44, 74, 0.12);

/* 프라이머리 버튼 글로우 효과 */
box-shadow: 0 8px 24px rgba(255, 107, 74, 0.30);
```

### 5.3 글래스모피즘 (Glassmorphism)
상단 네비게이션 바 및 플로팅 요소에는 가벼운 반투명 블러 효과를 적용합니다.
- `background: rgba(255, 255, 255, 0.7);`
- `backdrop-filter: blur(20px);`
- `-webkit-backdrop-filter: blur(20px);`

---

## 6. 주요 UI 컴포넌트 가이드 (Component Specs)

### 6.1 상단 네비게이션 (Top Navigation Bar)
- **구조:** 고정형(`fixed top-0`), 높이 약 `88px` (본문 패딩 `pt-[88px]`), 좌우 정렬
- **로고:** 마주한끼 심볼 (마주 앉아 따뜻한 김이 피어오르는 식탁) + `마주한끼 (Maju-Hankki)` 볼드 텍스트
- **메뉴 링크:** 서비스 소개(Matching), 성향 싱크(Vibe Sync), 매칭 미리보기(Preview), 식사 후기(Reviews)
- **액션 버튼:** '로그인' (Secondary Outline Pill) + '카카오로 시작하기' (Primary Coral Pill)

### 6.2 버튼 (Buttons)
1. **Primary Action Button (`btn-primary`):**
   - 배경: `#FF6B4A` (Coral Orange), 글자색: `#FFFFFF`, 볼드 폰트
   - 둥글기: `rounded-full`
   - 호버 인터랙션: `transform: scale(1.02); box-shadow: 0 8px 24px rgba(255, 107, 74, 0.3);`
2. **Secondary Button (`btn-secondary`):**
   - 배경: 투명, 테두리: `2px solid #1E2C4A`, 글자색: `#1E2C4A`
   - 둥글기: `rounded-full`
   - 호버 인터랙션: `transform: scale(1.02); background: rgba(30, 44, 74, 0.05);`
3. **Matching Pulse Button:**
   - 대기열 진입 시 버튼 주위에 퍼지는 펄스 링(`pulse-ring`) 애니메이션 효과 적용

### 6.3 입력 필드 & 셀렉트 박스 (Inputs & Selects)
- **구 스타일 선택 드롭다운:** 백그라운드 `#F8F9FF`, 1px `#E1BFB8` 테두리, 좌측 지도 핀 아이콘 (`location_on`), `rounded-full`
- **포커스 효과:** 테두리 색상이 `#FF6B4A`로 부드럽게 전이되며 2px 포커스 링 생성

### 6.4 칩 & 뱃지 (Chips & Tags)
- **성향 태그 (`.chip`):** `#FF6B4A` 10% 불투명도 배경 (`rgba(255, 107, 74, 0.1)`), 글자색 `#FF6B4A`, `rounded-full`, 1px 얇은 테두리
- **매칭률 뱃지:** `#1E2C4A` 배경, 흰색 텍스트, 하트 아이콘 포함 (`96% Vibe Match`)
- **음식/선호 카테고리 태그:** `bg-surface-dim` (`#CBDBF5`), 12px 볼드 텍스트

### 6.5 카드 컴포넌트 (Cards)
1. **Dining & Feature Card (Bento Grid 형태):**
   - 흰색 서피스(`surface-container-lowest`), `24px` 라운딩, `shadow-soft`
   - 호버 시 `translateY(-4px) scale(1.02)` 부드러운 상승 효과
2. **Buddy Profile Preview Card (프로필 미리보기):**
   - 모바일 프레임 모티프 (`rounded-[32px]`), 상단 노치 디자인
   - 원형 아바타 (화이트 테두리 + 그림자), 거주 지역 및 선호 식사 멘트 박스
   - 직관적인 '식사 함께하기 신청 (Join Table)' CTA 버튼
3. **Review Card:**
   - `#1E2C4A` 네이비 배경의 반전 카드, 따뜻한 옐로우 별점 아이콘, 반투명 화이트 인용 박스

---

## 7. 주요 화면 구성 (Key Screens & Sections)

### 7.1 랜딩 페이지 (Landing Page)
1. **Header (Glass Nav):** 마주한끼 로고, 메뉴, 로그인/카카오 간편 시작
2. **Hero Section:**
   - 카피: *"오늘 저녁, 혼밥 말고 따뜻한 한 끼"*
   - 서브: *"구 단위 지역, 식사 속도, 대화 성향에 맞춰 나에게 딱 맞는 1:1 밥친구를 매칭해 드립니다."*
   - 인터랙션: 빠른 활동 지역(구) 선택 + '마주한끼 찾기' 펄스 CTA 버튼
   - 우측 비주얼: 아늑하게 마주 앉은 식사 일러스트레이션 & 플로팅 "Dinner Match: 2분 내 매칭" 알림 뱃지
3. **Bento Grid Features (서비스 특징 4대 요소):**
   - **Hyper-local Matches:** 구/동 단위 실시간 위치 기반 매칭 카드 (지도 그래픽 + 펄스 인디케이터)
   - **Perfect Vibe Sync:** 96% 성향 싱크 카드 (`#SlowEater`, `#ComfortableSilence`, `#SmallTalkLover` 등)
   - **Curated Dining Spots:** AI 기반 둘만을 위한 맞춤 식당 추천 카드 (스키야키, 파스타 등)
   - **Safe & Trusted Reviews:** 신뢰할 수 있는 마주한끼 후기 및 방명록 평점 카드
4. **Profile Preview Section (매칭 프로필 미리보기):**
   - 식사 속도(빠른 편/천천히), 대화 성향(조용한 편/수다 환영), 선호 메뉴가 한눈에 보이는 카드
   - 신뢰 포인트 체크리스트 (로컬 인증 프로필, 명확한 식사 성향, 원클릭 신청)
5. **Bottom CTA Banner:**
   - 코랄 오렌지 그라데이션 (`from-[#FF6B4A] to-[#ff8c73]`), 도트 패턴 배경
   - *"오늘 저녁 마주 앉아 따뜻한 식탁을 함께할 준비가 되셨나요?"* + 네이비 라운드 버튼
6. **Footer:** 저작권, 이용약관, 개인정보처리방침, 안전 가이드라인

### 7.2 매칭 온보딩 / 성향 테스트 (Personality Setup)
- 식사 속도 (15분 컷 / 보통 30분 / 여유로운 1시간)
- 대화 선호도 (편안한 침묵 / 가벼운 스몰토크 / 활발한 대화)
- 음식 취향 (한식, 일식, 양식, 매운맛 레벨, 채식 여부 등)
- 3단계 내외의 미니멀하고 시각적인 선택 카드 인터페이스

### 7.3 매칭 대기 및 채팅 (Matching & Chat)
- 매칭 중: 부드럽게 회전/확산하는 코랄 링 펄스 애니메이션
- 1:1 채팅: 화이트 버블(상대방, 좌측 정렬) vs 코랄/소프트 틴트 버블(나, 우측 정렬), 약속 시간/식당 확정 카드 고정

---

## 8. Tailwind CSS 설정 및 구현 스니펫

### 8.1 Tailwind Config 확장 (`tailwind.config.js`)

```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx,html}"],
  theme: {
    extend: {
      colors: {
        // Core Palette
        primary: "#ae3115",
        "primary-container": "#ff6b4a", // Coral Orange
        "on-primary": "#ffffff",
        secondary: "#505e7f",
        "on-secondary": "#ffffff",
        "brand-navy": "#1e2c4a",
        "brand-ivory": "#faf7f2",

        // Surface & Containers
        surface: "#f8f9ff",
        "surface-bright": "#f8f9ff",
        "surface-container-lowest": "#ffffff",
        "surface-container-low": "#eff4ff",
        "surface-container": "#e5eeff",
        "surface-container-high": "#dce9ff",
        "surface-container-highest": "#d3e4fe",
        "surface-dim": "#cbdbf5",
        "surface-variant": "#d3e4fe",
        "on-surface": "#0b1c30",
        "on-surface-variant": "#59413c",
        "on-background": "#0b1c30",

        // Lines & Outlines
        outline: "#8d716a",
        "outline-variant": "#e1bfb8",

        // Semantic
        error: "#ba1a1a",
        "error-container": "#ffdad6",
        success: "#82a67d",
      },
      borderRadius: {
        card: "24px",
        image: "20px",
      },
      spacing: {
        base: "4px",
        xs: "8px",
        sm: "12px",
        md: "16px",
        lg: "24px",
        xl: "32px",
        xxl: "48px",
        "margin-mobile": "20px",
        "margin-desktop": "40px",
        gutter: "16px",
        "section-py": "96px",
      },
      fontFamily: {
        headline: ["Plus Jakarta Sans", "Pretendard", "sans-serif"],
        body: ["Be Vietnam Pro", "Pretendard", "sans-serif"],
      },
      boxShadow: {
        soft: "0 16px 40px rgba(30, 44, 74, 0.06)",
        hover: "0 20px 48px rgba(30, 44, 74, 0.10)",
        floating: "0 24px 60px rgba(30, 44, 74, 0.12)",
        "glow-primary": "0 8px 24px rgba(255, 107, 74, 0.30)",
      },
    },
  },
  plugins: [],
};
```
