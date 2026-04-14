# Playwright E2E — 회원가입 role 설정 테스트

> Branch: `refactor/MOM-305-set-role-when-sign-up`
> Generated: 2026-04-08

---

## 개요

소셜 로그인(Kakao, Google)을 통한 신규 가입 시 `role` 파라미터가 정상적으로 적용되는지,
기존 유저 재로그인 시 `role` 파라미터가 무시되는지 검증합니다.

---

## 외부 의존성

| Suite | Kakao OAuth | Google OAuth | PostgreSQL |
|-------|:-----------:|:------------:|:----------:|
| A     | O           |              | O          |
| B     |             | O            | O          |

---

## 테스트 케이스

| ID   | Suite | 시나리오 | 기대 결과 |
|------|-------|---------|----------|
| P-1  | A     | Kakao 신규 가입 + role=TRAINER | DB role=TRAINER, isNewUser=true |
| P-2  | B     | Google 신규 가입 + role 미지정 | DB role=USER (기본값), isNewUser=true |
| P-3  | A     | Kakao 기존 유저 재로그인 + role=USER | DB role=TRAINER 유지 (변경 안 됨), isNewUser=false |

---

## 사전 조건

1. MZTK-BE 서버 실행 중 (`http://127.0.0.1:8080`)
2. PostgreSQL DB 접속 가능
3. `.env` 파일에 다음 값이 설정되어 있어야 함:
   - `KAKAO_CLIENT_ID`, `KAKAO_TEST_EMAIL`, `KAKAO_TEST_PASSWORD`
   - `GOOGLE_CLIENT_ID`, `GOOGLE_TEST_EMAIL`, `GOOGLE_TEST_PASSWORD`
   - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`

---

## 실행 방법

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright

# 전체 실행
npx playwright test signup-role/signup-role.spec.ts --headed

# Suite A (Kakao) 만 실행
npx playwright test signup-role/signup-role.spec.ts --headed --grep "Suite A"

# Suite B (Google) 만 실행
npx playwright test signup-role/signup-role.spec.ts --headed --grep "Suite B"
```

---

## API 엔드포인트

### POST /auth/login (소셜 로그인)

```json
{
  "provider": "KAKAO",
  "authorizationCode": "<oauth-code>",
  "redirectUri": "http://localhost:3000/callback",
  "role": "TRAINER"
}
```

**응답 (신규 유저):**
```json
{
  "status": "SUCCESS",
  "data": {
    "accessToken": "eyJ...",
    "grantType": "Bearer",
    "expiresIn": 900,
    "isNewUser": true,
    "userInfo": { "userId": 42, "email": "test@kakao.com" }
  }
}
```

**비즈니스 규칙:**
- `role`은 **신규 가입 시에만** 적용됨
- 기존 유저 재로그인 시 `role` 파라미터는 완전히 무시됨
- `role` 미지정 시 기본값 `"USER"` 적용

---

## 테스트 격리 전략

- Suite A/B 각각 `beforeAll`에서 테스트 계정의 기존 데이터를 삭제
- `afterAll`에서 생성된 데이터를 정리 (users, users_account, refresh_tokens, user_progress)
- `test.describe.serial`로 Suite 내 테스트 순서 보장

---

## 실행 결과

> 실행 일시: 2026-04-08 17:57 (KST)
> Playwright 버전: 1.58.2
> 브라우저: Chromium (workers: 1)
> 총 소요 시간: 44.27s

### 요약

| 항목 | 결과 |
|------|------|
| 전체 테스트 | 3 |
| 성공 (passed) | 3 |
| 실패 (failed) | 0 |
| 건너뜀 (skipped) | 0 |
| Flaky | 0 |

### 상세 결과

| ID | Suite | 시나리오 | 결과 | 소요 시간 | 비고 |
|----|-------|---------|------|----------|------|
| P-1 | A | Kakao 신규 가입 + role=TRAINER | ✅ PASS | 23.09s | userId=1897, role=TRAINER |
| P-3 | A | Kakao 기존 유저 재로그인 + role=USER → TRAINER 유지 | ✅ PASS | 5.27s | role=TRAINER 유지 확인 |
| P-2 | B | Google 신규 가입 + role 미지정 → USER | ✅ PASS | 11.43s | userId=1898, role=USER |

### 콘솔 출력

```
[P-1] Kakao authorization_code 획득 (length=86)
[P-1] Kakao 신규 가입 성공: userId=1897, role=TRAINER
[P-3] Kakao 재로그인 후 role 유지 확인: role=TRAINER
[P-2] Google authorization_code 획득 (length=73)
[P-2] Google 신규 가입 성공: userId=1898, role=USER
```

### 결론

모든 테스트 케이스가 통과했습니다.
- Kakao 소셜 가입 시 `role=TRAINER` 파라미터가 정상 적용됨
- Google 소셜 가입 시 role 미지정 → 기본값 `USER` 적용됨
- 기존 유저 재로그인 시 role 파라미터가 무시되고 기존 role이 유지됨
