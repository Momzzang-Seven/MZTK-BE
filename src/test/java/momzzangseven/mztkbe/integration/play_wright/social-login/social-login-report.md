# 소셜 로그인 Playwright E2E 테스트 보고서

## 개요

| 항목 | 내용 |
|---|---|
| 테스트 파일 | `social-login.spec.ts` |
| 테스트 도구 | Playwright v1.50+ (TypeScript) |
| 테스트 대상 | Kakao / Google OAuth 전체 인증 플로우 |
| 백엔드 서버 | `http://localhost:8080` |
| 실행 일자 | 2026-02-27 |
| 실행자 | raewookang |

---

## 테스트 목적

이 테스트는 기존 단위 테스트(`AuthControllerTest`, `KakaoAuthenticationStrategyTest` 등)와 E2E 테스트(`AuthTokenLifecycleE2ETest`)에서 **MockBean으로 처리했던 외부 OAuth API 호출**을 실제 환경에서 검증합니다.

### 커버 대상

| 계층 | 기존 테스트 커버 여부 | 이 테스트의 역할 |
|---|---|---|
| `KakaoApiAdapter.getAccessToken()` | ❌ MockBean 처리 | ✅ 실제 Kakao 토큰 엔드포인트 호출 검증 |
| `KakaoApiAdapter.getUserInfo()` | ❌ MockBean 처리 | ✅ 실제 Kakao 사용자 정보 엔드포인트 호출 검증 |
| `GoogleApiAdapter.exchangeToken()` | ❌ MockBean 처리 | ✅ 실제 Google 토큰 엔드포인트 호출 검증 |
| `GoogleApiAdapter.getUserInfo()` | ❌ MockBean 처리 | ✅ 실제 Google 사용자 정보 엔드포인트 호출 검증 |
| `Set-Cookie: refreshToken` 쿠키 속성 | ✅ AuthControllerTest | ✅ 실제 HTTP 응답에서 재검증 |
| `POST /auth/stepup` (소셜 재인증) | ✅ AuthControllerTest | ✅ 실제 신선한 authorization_code 로 재검증 |

---

## 테스트 케이스 및 실행 결과

### Kakao 소셜 로그인 E2E

#### TC-K-01: 카카오 동의 화면을 통해 authorization_code 를 획득한다

**목적**: Playwright 브라우저가 Kakao OAuth 동의 화면을 자동으로 통과하고, redirect_uri 로 전달되는 `authorization_code` 를 인터셉트한다.

**시나리오**:
1. `https://kauth.kakao.com/oauth/authorize?...` 로 네비게이션
2. 이메일/비밀번호 입력 및 로그인
3. 동의 화면 허용 (이미 동의한 경우 자동 스킵)
4. `http://localhost:3000/callback?code=xxx` 로의 리다이렉트를 Playwright route intercept 로 차단
5. URL 파라미터에서 `code` 추출

**검증 항목**:
- `code` 값이 비어 있지 않음
- `code` 길이 > 0

**실행 결과**:

| 실행 일시 | 결과 | 획득한 code 길이 | 소요 시간 | 비고 |
|---|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 길이 --> | <!-- ms --> | <!-- 메모 --> |

---

#### TC-K-02: POST /auth/login (KAKAO) 가 200 과 토큰을 반환한다

**목적**: TC-K-01 에서 획득한 `authorization_code` 로 백엔드 `/auth/login` 을 호출하여, 서버가 Kakao 토큰 엔드포인트와 사용자 정보 엔드포인트를 실제로 호출하고 JWT를 발급하는지 검증한다.

**요청**:
```http
POST /auth/login
Content-Type: application/json

{
  "provider": "KAKAO",
  "authorizationCode": "<TC-K-01에서 획득한 code>",
  "redirectUri": "http://localhost:3000/callback"
}
```

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `200` | <!-- 기입 --> |
| `body.success` | `true` | <!-- 기입 --> |
| `body.data.accessToken` | 비어 있지 않은 JWT 문자열 | <!-- 기입 --> |
| `Set-Cookie` 헤더 존재 | 있음 | <!-- 기입 --> |
| `refreshToken=` | 쿠키에 포함 | <!-- 기입 --> |
| `HttpOnly` | 쿠키 속성에 포함 | <!-- 기입 --> |
| `Path=/auth` | 쿠키 속성에 포함 | <!-- 기입 --> |
| `SameSite=Strict` | 쿠키 속성에 포함 | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | HTTP 상태코드 | accessToken 앞 20자 | Set-Cookie 원문 | 소요 시간 |
|---|---|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 상태코드 --> | <!-- 토큰 앞부분 --> | <!-- 쿠키 헤더 --> | <!-- ms --> |

---

#### TC-K-03: POST /auth/reissue 가 새 accessToken 과 새 refreshToken 쿠키를 반환한다

**목적**: TC-K-02 에서 발급된 `refreshToken` 쿠키로 토큰 재발급을 호출하여, 토큰 로테이션이 정상 작동하는지 검증한다.

**요청**:
```http
POST /auth/reissue
Cookie: refreshToken=<TC-K-02에서 획득한 refreshToken 값>
```

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `200` | <!-- 기입 --> |
| `body.success` | `true` | <!-- 기입 --> |
| 새 `accessToken` | 이전 accessToken 과 다른 값 | <!-- 기입 --> |
| 새 `Set-Cookie: refreshToken` | 이전 refreshToken 과 다른 값 | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | 소요 시간 | 비고 |
|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- ms --> | <!-- 메모 --> |

---

#### TC-K-04: POST /auth/stepup (KAKAO) 이 stepUpToken 을 반환한다

**목적**: 로그인 후 Step-Up 인증을 위해 새 `authorization_code` 를 발급받아 `/auth/stepup` 을 호출한다. Kakao 는 동의 완료 후 재로그인 없이 바로 code 를 발급함을 검증한다.

**요청**:
```http
POST /auth/stepup
Content-Type: application/json
Authorization: Bearer <TC-K-02에서 획득한 accessToken>

{
  "authorizationCode": "<새 code>"
}
```

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `200` | <!-- 기입 --> |
| `body.success` | `true` | <!-- 기입 --> |
| `body.data.stepUpToken` | 비어 있지 않은 JWT 문자열 | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | stepUpToken 앞 20자 | 소요 시간 |
|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 토큰 앞부분 --> | <!-- ms --> |

---

#### TC-K-05: POST /auth/logout 가 204 와 maxAge=0 쿠키를 반환한다

**목적**: 로그아웃 시 서버가 `Max-Age=0` 으로 refreshToken 쿠키를 삭제하는지 검증한다.

**요청**:
```http
POST /auth/logout
Cookie: refreshToken=<refreshToken 값>
```

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `204` | <!-- 기입 --> |
| `Set-Cookie` 헤더 | 존재 | <!-- 기입 --> |
| `Max-Age=0` | 쿠키에 포함 | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | Set-Cookie 원문 | 소요 시간 |
|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 쿠키 헤더 --> | <!-- ms --> |

---

### Google 소셜 로그인 E2E

#### TC-G-01: 구글 동의 화면을 통해 authorization_code 를 획득한다

**목적**: Playwright 가 Google OAuth 동의 화면을 자동으로 통과하고 `authorization_code` 를 인터셉트한다. `access_type=offline` + `prompt=consent` 로 요청하여 `refresh_token` 까지 포함된 토큰 응답을 유도한다.

**시나리오**:
1. `https://accounts.google.com/o/oauth2/v2/auth?...` 로 네비게이션
2. 이메일 → 비밀번호 순서로 입력
3. 2단계 인증 생략 처리
4. 동의 화면 "계속" 버튼 클릭
5. redirect_uri 인터셉트로 `code` 추출

**실행 결과**:

| 실행 일시 | 결과 | 획득한 code 길이 | 소요 시간 | 비고 |
|---|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 길이 --> | <!-- ms --> | <!-- 2단계 인증 여부 등 --> |

---

#### TC-G-02: POST /auth/login (GOOGLE) 가 200 과 토큰을 반환한다

**목적**: Google `authorization_code` 로 `/auth/login` 을 호출하여, 서버가 Google `token_endpoint` + `userinfo_endpoint` 를 실제 호출하고 JWT를 발급하는지 검증한다.

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `200` | <!-- 기입 --> |
| `body.success` | `true` | <!-- 기입 --> |
| `body.data.accessToken` | 비어 있지 않은 JWT | <!-- 기입 --> |
| `Set-Cookie: refreshToken; HttpOnly` | 존재 | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | HTTP 상태코드 | Set-Cookie 원문 | 소요 시간 |
|---|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 상태코드 --> | <!-- 쿠키 헤더 --> | <!-- ms --> |

---

#### TC-G-03: POST /auth/stepup (GOOGLE) 이 stepUpToken 을 반환한다

**목적**: Google Step-Up 은 `offline_access` 재동의가 필요하다. `prompt=consent` + `access_type=offline` 으로 재인증하여, 서버가 `GoogleAuthenticationStrategy` 의 `GOOGLE_OFFLINE_CONSENT_REQUIRED` 예외를 발생시키지 않고 정상 처리하는지 검증한다.

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `200` | <!-- 기입 --> |
| `body.data.stepUpToken` | 비어 있지 않은 JWT | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | 소요 시간 | 비고 |
|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- ms --> | <!-- refresh_token 포함 여부 등 --> |

---

### 인증 실패 케이스

#### TC-E-01: 위조된 authorization_code 로 Kakao 로그인 시 외부 API 에러를 반환한다

**목적**: `INVALID_CODE_12345` 와 같은 위조된 코드를 전달했을 때, 서버가 Kakao로부터 에러를 수신하고 적절한 4xx/5xx 응답을 반환하는지 검증한다.

**요청**:
```http
POST /auth/login
Content-Type: application/json

{
  "provider": "KAKAO",
  "authorizationCode": "INVALID_CODE_12345",
  "redirectUri": "http://localhost:3000/callback"
}
```

**검증 항목**:

| 검증 항목 | 예상 값 | 실제 값 |
|---|---|---|
| HTTP 상태코드 | `400`, `502`, `503` 중 하나 | <!-- 기입 --> |
| `body.success` | `false` | <!-- 기입 --> |

**실행 결과**:

| 실행 일시 | 결과 | HTTP 상태코드 | 오류 코드 | 소요 시간 |
|---|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 상태코드 --> | <!-- 에러코드 --> | <!-- ms --> |

---

#### TC-E-02: refreshToken 쿠키 없이 /auth/reissue 호출 시 400 을 반환한다

**목적**: `@CookieValue(required = true)` 바인딩 실패 시 Spring 이 400 을 응답하는지 검증한다.

**실행 결과**:

| 실행 일시 | 결과 | HTTP 상태코드 | 소요 시간 |
|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 상태코드 --> | <!-- ms --> |

---

#### TC-E-03: 인증 토큰 없이 /auth/stepup 호출 시 401 을 반환한다

**목적**: `/auth/stepup` 은 `authenticated()` 로 보호되어 있으므로, Authorization 헤더 없이 호출하면 Spring Security 가 401 을 반환하는지 검증한다.

**실행 결과**:

| 실행 일시 | 결과 | HTTP 상태코드 | 소요 시간 |
|---|---|---|---|
| <!-- 날짜 --> | <!-- PASS/FAIL --> | <!-- 상태코드 --> | <!-- ms --> |

---

## 전체 결과 요약

| TC ID | 테스트명 | 결과 | 소요 시간 |
|---|---|---|---|
| TC-K-01 | 카카오 authorization_code 획득 | ✅ PASS | 27,900ms |
| TC-K-02 | POST /auth/login (KAKAO) | ✅ PASS | 365ms |
| TC-K-03 | POST /auth/reissue (토큰 로테이션) | ✅ PASS | 37ms |
| TC-K-04 | POST /auth/stepup (KAKAO) | ✅ PASS | 6,900ms |
| TC-K-05 | POST /auth/logout | ✅ PASS | 29ms |
| TC-G-01 | 구글 authorization_code 획득 | ✅ PASS | 10,100ms |
| TC-G-02 | POST /auth/login (GOOGLE) | ✅ PASS | 607ms |
| TC-G-03 | POST /auth/stepup (GOOGLE) | ✅ PASS | 14,100ms |
| TC-E-01 | 위조 코드 → 에러 처리 (502) | ✅ PASS | 39ms |
| TC-E-02 | 쿠키 없는 reissue → 400 | ✅ PASS | 6ms |
| TC-E-03 | 토큰 없는 stepup → 401 | ✅ PASS | 5ms |
| **합계** | | **11/11 PASS** | **63,951ms (약 1분)** |

---

## 발견된 이슈

> 테스트 실행 후 발견된 버그, 예상과 다른 동작, 개선 필요 사항을 기록합니다.

| 번호 | TC ID | 증상 | 원인 분석 | 처리 상태 |
|---|---|---|---|---|
| 1 | <!-- TC ID --> | <!-- 증상 --> | <!-- 원인 --> | <!-- 미처리/수정중/완료 --> |

---

## 실행 환경

| 항목 | 값 |
|---|---|
| OS | macOS 24.6.0 (darwin) |
| Node.js | v22.x |
| Playwright | v1.50.0 |
| 브라우저 | Chromium (Playwright 내장) |
| 백엔드 Spring Boot | 3.3.4 |
| DB | PostgreSQL (로컬) |
| Kakao 앱 | 개발자 앱 (테스트 계정 등록됨) |
| Google OAuth 앱 | GCP 테스트 모드 (테스트 유저 등록됨) |

---

## 실행 명령어

```bash
# play_write 디렉터리에서 실행
cd src/test/java/momzzangseven/mztkbe/integration/play_write

# 1. 의존성 설치 (최초 1회)
npm install
npx playwright install chromium

# 2. .env 파일 준비
cp .env.example .env
# .env 에 테스트 계정 정보 입력

# 3. 백엔드 서버 기동 확인
curl http://localhost:8080/actuator/health

# 4. 테스트 실행 (브라우저 표시 모드)
npm run test:headed

# 5. 결과 보고서 열기
npm run test:report
```

---

## 참고 사항

### Kakao 테스트 계정 등록 방법
1. [카카오 개발자 콘솔](https://developers.kakao.com) > 앱 선택
2. 팀원 관리 > 팀원 등록 (또는 테스트용 카카오계정 생성)
3. 앱 설정 > 플랫폼 > Web → `http://localhost:3000` 등록
4. 카카오 로그인 > Redirect URI → `http://localhost:3000/callback` 등록
5. 동의 항목: `profile_nickname`, `account_email` 활성화

### Google 테스트 계정 등록 방법
1. [GCP Console](https://console.cloud.google.com) > API 및 서비스 > OAuth 동의 화면
2. 사용자 유형: 외부 > 테스트 사용자 추가
3. 사용자 인증 정보 > OAuth 2.0 클라이언트 ID > 승인된 리디렉션 URI 에 `http://localhost:3000/callback` 추가
4. 범위: `openid`, `email`, `profile` 활성화

### authorization_code 만료 주의
- Kakao `authorization_code` 는 **1회성 + 짧은 TTL** (약 10분)
- Google `authorization_code` 는 **1회성 + 짧은 TTL** (약 10분)
- TC-K-01 ~ TC-K-02 는 반드시 연속으로 실행해야 합니다.
- `playwright test` 는 기본적으로 순서대로 실행되므로 문제없습니다.
