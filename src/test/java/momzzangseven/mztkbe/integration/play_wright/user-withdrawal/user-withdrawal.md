# 회원 탈퇴 Playwright E2E 테스트

## 개요

`user-withdrawal.spec.ts` 는 **회원 탈퇴 플로우**를 Playwright + HTTP API 레이어에서 검증하는 E2E 테스트입니다.

탈퇴는 민감한 작업으로, **Step-Up 인증**(비밀번호 재확인 또는 소셜 재인증)을 거친 후에만 실행됩니다.  
본 테스트는 **로컬(LOCAL) 계정**뿐 아니라 **Kakao / Google 소셜 계정** 탈퇴까지 검증합니다.  
소셜 계정 탈퇴는 Playwright 브라우저 자동화로 실제 OAuth 동의 화면을 통해 `authorization_code`를 획득하고, 서버가 외부 API(`unlinkUser` / `revokeToken`)를 호출하는 전 과정을 E2E로 검증합니다.

**테스트 범위:**
- Suite A, B — 탈퇴 HTTP API 흐름 검증 (상태 코드, 권한)
- Suite C — 탈퇴(soft delete) 후 연결된 **wallet** 상태 전환 확인 (`USER_DELETED`)
- Suite D — Hard delete 후 **wallet** 레코드 물리 삭제 확인
- Suite E — **Kakao 소셜 계정** 탈퇴 (OAuth 실제 연동 + `unlinkUser` 호출)
- Suite F — **Google 소셜 계정** 탈퇴 (OAuth 실제 연동 + `revokeToken` 호출)

> **Location 연쇄 삭제는 이 파일에서 검증하지 않습니다.**  
> `LocationUserSoftDeleteEventHandler` / `LocationUserHardDeleteEventHandler` 동작은  
> Spring 컨텍스트에서 `WithdrawalHardDeleteService`를 직접 호출할 수 있는  
> `UserE2ETest.java` (Java E2E) 에서 검증합니다.

---

## 외부 의존성

| Suite | 테스트 대상 | 외부 API 호출 | 비고 |
|---|---|:---:|---|
| A, B | 로컬 계정 탈퇴 (`provider=LOCAL`) | ✗ | DB soft-delete 만 수행 |
| C, D | EIP-712 지갑 서명 | ✗ | `ethers.js` 로컬 서명, 온체인 트랜잭션 없음 |
| E | Kakao 소셜 계정 탈퇴 | ✅ | Kakao OAuth + `unlinkUser` API 실제 호출 |
| F | Google 소셜 계정 탈퇴 | ✅ | Google OAuth + `revokeToken` API 실제 호출 |

> Suite E, F는 실제 Kakao / Google OAuth 동의 화면을 Playwright 브라우저로 자동화합니다.  
> `unlinkUser` / `revokeToken` 은 **best-effort** 백그라운드 처리이므로,  
> Playwright 에서는 탈퇴 API 200 OK 및 재탈퇴 차단으로 검증합니다.

---

## 테스트 플로우

### Suite A / B — 탈퇴 API 기본 흐름

```
회원가입 (POST /auth/signup)
    ↓
로그인 (POST /auth/login)  → accessToken 발급
    ↓
Step-Up 인증 (POST /auth/stepup, password 사용)  → stepUpToken 발급 (ROLE_STEP_UP 포함)
    ↓
탈퇴 요청 (POST /users/me/withdrawal, stepUpToken 사용)  → 200 OK
    ↓
탈퇴 후 검증 (재로그인 시도 → 409, 재탈퇴 시도 → 409 / USER_004)
```

### Suite C — Soft Delete 연쇄 검증

```
회원가입 + 로그인
    ↓
지갑 등록 (POST /web3/challenges → EIP-712 서명 → POST /web3/wallets)
    ↓
탈퇴 (POST /users/me/withdrawal)
    → UserSoftDeletedEvent 발행
    → WalletUserSoftDeleteEventHandler: ACTIVE wallet → USER_DELETED
    ↓
DB 조회: user_wallets.status = 'USER_DELETED' 확인
```

### Suite D — Hard Delete 연쇄 검증

```
회원가입 + 로그인 + 지갑 등록
    ↓
탈퇴 (soft delete) → wallet.status = USER_DELETED 확인
    ↓
DB 직접 조작: user_wallets WHERE status='USER_DELETED' 삭제 + users 삭제
    ↓
user_wallets 레코드 0건 확인
```

> **Hard delete 제약:** `WithdrawalHardDeleteService.runBatch()`는 `@Scheduled` 스케줄러에 의해서만  
> 실행되며 HTTP로 직접 트리거하는 Admin API가 없습니다.  
> 따라서 Suite D는 실제 이벤트 흐름 대신 DB 직접 조작으로 최종 데이터 정합성을 검증합니다.  
> 이벤트 핸들러(`WalletUserHardDeleteEventHandler`) 동작 자체는 Spring 통합 테스트에서 별도 검증하는 것이 정확합니다.

### Suite E — Kakao 소셜 계정 탈퇴

```
[1/4] Playwright 브라우저 → Kakao 동의 화면 자동화 → authorization_code 획득
    ↓
POST /auth/login { provider: "KAKAO", authorizationCode, redirectUri }
    → accessToken 발급
    ↓
[2/4] Playwright 브라우저 → 새 컨텍스트(세션 없음) 생성 → Kakao 로그인 폼 입력
    → 새 authorization_code 획득
    ↓
POST /auth/stepup { authorizationCode }  → stepUpToken 발급 (ROLE_STEP_UP 포함)
    ↓
[3/4] POST /users/me/withdrawal  → 200 OK
    서버 백그라운드: Kakao unlinkUser API 호출 (best-effort)
    ↓
[4/4] DB 조회: users.deleted_at NOT NULL 확인 (soft delete)
    ↓
재탈퇴 시도 (TC-UW-E-02): 동일 stepUpToken 으로 재시도 → 실패 확인
```

### Suite F — Google 소셜 계정 탈퇴

```
[1/4] Playwright 브라우저 → Google 동의 화면 자동화 → authorization_code 획득
    ↓
POST /auth/login { provider: "GOOGLE", authorizationCode, redirectUri }
    → accessToken 발급
    ↓
[2/4] Playwright 브라우저 → 새 컨텍스트(세션 없음) 생성 → Google 로그인 폼 + prompt=consent
    → 새 authorization_code 획득
    ↓
POST /auth/stepup { authorizationCode }  → stepUpToken 발급 (ROLE_STEP_UP 포함)
    ↓
[3/4] POST /users/me/withdrawal  → 200 OK
    서버 백그라운드: Google revokeToken API 호출 (best-effort)
    ↓
[4/4] DB 조회: users.deleted_at NOT NULL 확인 (soft delete)
    ↓
재탈퇴 시도 (TC-UW-F-02): 동일 stepUpToken 으로 재시도 → 실패 확인
```

---

## 사전 조건

### 1. 백엔드 서버 실행

```bash
# 로컬 서버 실행 (기본 8080 포트)
./gradlew bootRun
```

### 2. Playwright `.env` 설정

`play_wright/.env.example` 을 복사하여 `.env` 를 생성합니다.

```dotenv
# 백엔드 서버 URL
BACKEND_URL=http://127.0.0.1:8080

# DB 접속 정보 (Suite C, D, E, F — DB 상태 직접 확인에 필요)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mztk_dev
DB_USER=postgres
DB_PASSWORD=postgres

# Kakao OAuth 설정 (Suite E 필수)
KAKAO_CLIENT_ID=bac9e0d526d96cd190eb972238209c42
KAKAO_REDIRECT_URI=http://localhost:3000/callback
KAKAO_TEST_EMAIL=your-kakao-test@email.com
KAKAO_TEST_PASSWORD=your-kakao-password

# Google OAuth 설정 (Suite F 필수)
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_REDIRECT_URI=http://localhost:3000/callback
GOOGLE_TEST_EMAIL=your-google-test@gmail.com
GOOGLE_TEST_PASSWORD=your-google-password
```

| Suite | 필수 환경변수 |
|---|---|
| A, B | `BACKEND_URL` |
| C, D | `BACKEND_URL`, `DB_*` |
| E | `BACKEND_URL`, `DB_*`, `KAKAO_CLIENT_ID`, `KAKAO_TEST_EMAIL`, `KAKAO_TEST_PASSWORD` |
| F | `BACKEND_URL`, `DB_*`, `GOOGLE_CLIENT_ID`, `GOOGLE_TEST_EMAIL`, `GOOGLE_TEST_PASSWORD` |

> Suite E, F는 환경변수가 없으면 `test.skip` 으로 자동 건너뜁니다.  
> EIP-712 도메인 설정은 스크립트에 하드코딩되어 있습니다.

### 3. Playwright 패키지 설치

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright
npm install
npx playwright install chromium
```

---

## 실행 방법

```bash
# play_wright/ 디렉터리에서 실행 (전체 Suite A~F)
npx playwright test user-withdrawal/user-withdrawal.spec.ts

# Suite A~D 만 실행 (소셜 OAuth 불필요)
npx playwright test user-withdrawal/user-withdrawal.spec.ts --grep "Suite [A-D]"

# Suite E (Kakao) 만 실행
npx playwright test user-withdrawal/user-withdrawal.spec.ts --grep "Suite E"

# Suite F (Google) 만 실행
npx playwright test user-withdrawal/user-withdrawal.spec.ts --grep "Suite F"

# 소셜 Suite (E, F) 만 실행
npx playwright test user-withdrawal/user-withdrawal.spec.ts --grep "Suite [E-F]"

# UI 모드로 실행 (디버깅 시 권장 — OAuth UI 자동화 확인 가능)
npx playwright test user-withdrawal/user-withdrawal.spec.ts --ui

# 결과 리포트 확인
npx playwright show-report
```

> Suite E, F는 브라우저 UI 자동화를 포함하므로 `--headed` 옵션을 추가하면 실제 브라우저 동작을 확인할 수 있습니다.
>
> ```bash
> npx playwright test user-withdrawal/user-withdrawal.spec.ts --grep "Suite [E-F]" --headed
> ```

---

## 테스트 케이스 목록

### Suite A — 정상 탈퇴 플로우 (LOCAL)

| TC ID | 제목 | 예상 결과 |
|---|---|---|
| TC-UW-A-01 | 회원가입 → 로그인 → step-up → 탈퇴 성공 | `200 OK` |
| TC-UW-A-02 | 탈퇴 후 로그인 시도 → 인증 실패 | `409 Conflict` |
| TC-UW-A-03 | 탈퇴 후 재탈퇴 시도 → 실패 (이미 탈퇴된 계정) | `409 Conflict` / `code: USER_004` |

### Suite B — 권한 오류

| TC ID | 제목 | 예상 결과 |
|---|---|---|
| TC-UW-B-01 | step-up 없이 탈퇴 요청 → 403 Forbidden | `403 Forbidden` |
| TC-UW-B-02 | 미인증 탈퇴 요청 → 401 Unauthorized | `401 Unauthorized` |

### Suite C — Soft Delete 연쇄 검증

| TC ID | 제목 | 검증 방법 |
|---|---|---|
| TC-UW-C-01 | 탈퇴 후 연결된 wallet.status 가 USER_DELETED 로 전환 | DB 직접 조회 (`user_wallets`) |

### Suite D — Hard Delete 연쇄 검증

| TC ID | 제목 | 검증 방법 |
|---|---|---|
| TC-UW-D-01 | Hard delete 실행 후 USER_DELETED wallet 레코드 완전 삭제 | DB 직접 삭제 후 COUNT = 0 확인 |

### Suite E — Kakao 소셜 계정 탈퇴

| TC ID | 제목 | 검증 방법 | 외부 의존 |
|---|---|---|:---:|
| TC-UW-E-01 | Kakao OAuth → 로그인 → step-up(code) → 탈퇴 → DB soft-delete 확인 | API 200 OK + `users.deleted_at NOT NULL` | ✅ Kakao OAuth |
| TC-UW-E-02 | Kakao 탈퇴 후 재탈퇴 시도 → 실패 | HTTP status ≠ 200 | — |

### Suite F — Google 소셜 계정 탈퇴

| TC ID | 제목 | 검증 방법 | 외부 의존 |
|---|---|---|:---:|
| TC-UW-F-01 | Google OAuth → 로그인 → step-up(code) → 탈퇴 → DB soft-delete 확인 | API 200 OK + `users.deleted_at NOT NULL` | ✅ Google OAuth |
| TC-UW-F-02 | Google 탈퇴 후 재탈퇴 시도 → 실패 | HTTP status ≠ 200 | — |

---

## 주요 API 엔드포인트

### `POST /auth/signup` — 회원가입

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "Test1234!",
  "nickname": "testuser",
  "provider": "LOCAL"
}
```

**Response:** `200 OK`
```json
{
  "data": {
    "userId": 123
  }
}
```

---

### `POST /auth/login` — 로그인

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "Test1234!",
  "provider": "LOCAL"
}
```

**Response:** `200 OK`
```json
{
  "data": {
    "accessToken": "eyJhbGc...",
    "grantType": "Bearer",
    "expiresIn": 3600
  }
}
```

---

### `POST /auth/stepup` — Step-Up 인증

**Request Header:** `Authorization: Bearer {accessToken}`

**Request Body:**
```json
{
  "password": "Test1234!"
}
```

**Response:** `200 OK`
```json
{
  "data": {
    "accessToken": "eyJhbGc...",
    "grantType": "Bearer",
    "expiresIn": 600
  }
}
```

> Step-Up 토큰은 `ROLE_STEP_UP` 권한을 포함하며, 탈퇴(`/users/me/withdrawal`)와 같은 민감한 작업에 필요합니다.

---

### `POST /users/me/withdrawal` — 회원 탈퇴

**Request Header:** `Authorization: Bearer {stepUpToken}`

**Response:** `200 OK`
```json
{
  "status": "SUCCESS",
  "message": "User withdrawn successfully"
}
```

**보안 제약:**
- `ROLE_STEP_UP` 권한이 없는 토큰으로 요청 시 `403 Forbidden`
- 미인증 요청 시 `401 Unauthorized`
- 이미 탈퇴된 계정 재로그인 시 `409 Conflict`
- 이미 탈퇴된 계정 재탈퇴 시 `409 Conflict` / `code: USER_004`

---

### `POST /web3/challenges` — EIP-712 챌린지 발급 (Suite C, D)

**Request Header:** `Authorization: Bearer {accessToken}`

**Request Body:**
```json
{
  "purpose": "WALLET_REGISTRATION",
  "walletAddress": "0x..."
}
```

**Response:** `200 OK`
```json
{
  "data": {
    "nonce": "uuid-string",
    "message": "MZTK wants you to register your wallet...",
    "expiresIn": 300
  }
}
```

---

### `POST /web3/wallets` — 지갑 등록 (Suite C, D)

**Request Header:** `Authorization: Bearer {accessToken}`

**Request Body:**
```json
{
  "walletAddress": "0x...",
  "signature": "0x...",
  "nonce": "uuid-string"
}
```

**Response:** `201 Created`

> EIP-712 서명은 `ethers.js v6` 의 `signTypedData()` 를 사용하여 생성합니다.  
> 서명 타입 정의: `AuthRequest { content: string, nonce: string }`  
> DB에는 `wallet_address` 가 **소문자**로 저장되므로, DB 조회 시 `wallet.address.toLowerCase()` 를 사용해야 합니다.

---

## EIP-712 도메인 설정

Suite C, D 에서 사용하는 EIP-712 도메인 정보는 스크립트에 하드코딩되어 있습니다.  
백엔드 `application.yml` 의 `web3.eip712.*` 설정과 반드시 일치해야 합니다.

```typescript
const EIP712_DOMAIN = {
  name: "MomzzangSeven",
  version: "1",
  chainId: 11155420, // Optimism Sepolia Testnet
  verifyingContract: "0x815B53fD2D56044BaC39c1f7a9C7d3E67322f0F5",
};
```

---

## 소셜 Step-Up 방식 비교

| 계정 종류 | Step-Up 요청 바디 | 설명 |
|---|---|---|
| LOCAL | `{ "password": "..." }` | 비밀번호 재확인 |
| KAKAO | `{ "authorizationCode": "..." }` | Kakao OAuth 재인증 코드 |
| GOOGLE | `{ "authorizationCode": "..." }` | Google OAuth 재인증 코드 |

소셜 계정의 Step-Up은 비밀번호가 없으므로 새 OAuth code로 신원을 재확인합니다.  
Step-Up 시에는 `browser.newContext()`로 **완전히 새로운 브라우저 컨텍스트**를 생성합니다.  
첫 번째 OAuth에서 생성된 세션 쿠키가 공유되지 않으므로, 카카오/구글 로그인 폼을 처음부터 입력하여 code를 획득합니다.  
이 방식은 `social-login.spec.ts` TC-K-04 / TC-G-03 과 동일한 인증 패턴이며, 소셜 플랫폼의 동일 세션 재요청 차단을 우회합니다.

## 소셜 탈퇴 후 외부 API 연결 해제

탈퇴 완료 시 서버는 백그라운드에서 다음 API를 호출합니다.

| 제공자 | 호출 API | 목적 |
|---|---|---|
| Kakao | `POST https://kapi.kakao.com/v1/user/unlink` | 카카오 앱 연결 해제 |
| Google | `POST https://oauth2.googleapis.com/revoke` | Google OAuth 토큰 무효화 |

> 외부 API 호출은 **best-effort** 방식입니다.  
> 외부 API 실패 시에도 탈퇴 자체는 성공하며,  
> 실패 작업은 재시도 큐(`ExternalDisconnectTask`)에 등록되어 스케줄러가 재처리합니다.

---

## 테스트 격리 전략

각 테스트는 `signUpAndLogin(suffix)` 헬퍼를 통해 고유한 계정(`playwright-uw-{suffix}-{timestamp}@test.com`)을 생성합니다.  
이를 통해 테스트 간 상태 간섭 없이 독립적으로 실행됩니다.

Suite C, D 는 추가로 `ethers.Wallet.createRandom()` 으로 테스트마다 고유한 지갑을 생성하여 409 Conflict 를 방지합니다.

---

## 마지막 실행 결과

| 항목 | 값 |
|---|---|
| 실행일 | 2026-03-07 |
| 플랫폼 | chromium |
| Playwright 버전 | 1.58.2 |
| 전체 테스트 수 | 11 |
| 통과 | **11** |
| 실패 | 0 |
| 스킵 | 0 |
| 총 실행 시간 | 46,512 ms |

### 케이스별 결과

| TC ID | 결과 | 실행 시간 | 출력 |
|---|:---:|---:|---|
| TC-UW-A-01 | ✅ passed | 267 ms | `탈퇴 성공: User withdrawn successfully` |
| TC-UW-A-02 | ✅ passed | 420 ms | `탈퇴 후 재로그인 차단 확인: status=409` |
| TC-UW-A-03 | ✅ passed | 275 ms | `재탈퇴 차단 확인: status=409, code=USER_004` |
| TC-UW-B-01 | ✅ passed | 165 ms | `step-up 없이 탈퇴 차단: status=403` |
| TC-UW-B-02 | ✅ passed | 4 ms | `미인증 탈퇴 차단: status=401` |
| TC-UW-C-01 | ✅ passed | 315 ms | `wallet.status=USER_DELETED 확인: userId=303` |
| TC-UW-D-01 | ✅ passed | 296 ms | `hard delete 후 user_wallets 레코드 완전 삭제 확인: userId=304` |
| TC-UW-E-01 | ✅ passed | 11,487 ms | `Kakao 로그인 완료: userId=12` → `탈퇴 성공 (200 OK)` → `DB soft-delete 확인 완료: userId=12, deleted_at=2026-03-07 23:41:37` |
| TC-UW-E-02 | ✅ passed | 14 ms | `Kakao 재탈퇴 차단 확인: status=409, code=USER_004` |
| TC-UW-F-01 | ✅ passed | 31,568 ms | `Google 로그인 완료: userId=13` → `탈퇴 성공 (200 OK)` → `DB soft-delete 확인 완료: userId=13, deleted_at=2026-03-07 23:42:08` |
| TC-UW-F-02 | ✅ passed | 8 ms | `Google 재탈퇴 차단 확인: status=409, code=USER_004` |

### 소요 시간 분포

| Suite | 합계 | 비고 |
|---|---:|---|
| A (LOCAL 탈퇴 기본 흐름) | ~962 ms | API 호출 위주 |
| B (권한 오류) | ~169 ms | 빠른 실패 검증 |
| C (Soft Delete 연쇄) | ~315 ms | 지갑 등록 + DB 조회 포함 |
| D (Hard Delete 연쇄) | ~296 ms | DB 직접 삭제 후 확인 |
| E (Kakao 소셜 탈퇴) | ~11,501 ms | OAuth UI 자동화 2회 + 새 컨텍스트 생성 |
| F (Google 소셜 탈퇴) | ~31,576 ms | OAuth UI 자동화 2회 + Google 동의 화면 처리 시간 |
