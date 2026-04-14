/**
 * MZTK - 회원가입 role 설정 Playwright E2E 테스트
 *
 * 테스트 대상:
 *   1. Kakao OAuth 신규 가입 시 TRAINER role 설정
 *   2. Google OAuth 신규 가입 시 role 미지정 → 기본값 USER
 *   3. 기존 소셜 유저 재로그인 시 role 파라미터 무시
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 http://127.0.0.1:8080 에서 실행 중이어야 합니다.
 *   - .env 파일에 Kakao/Google 테스트 계정 자격증명이 입력되어 있어야 합니다.
 *   - PostgreSQL DB에 직접 접속 가능해야 합니다 (DB 검증용).
 *
 * 실행:
 *   cd src/test/java/momzzangseven/mztkbe/integration/play_wright
 *   npx playwright test signup-role/signup-role.spec.ts --headed
 */

import { test, expect, BrowserContext, Page } from "@playwright/test";
import { Client } from "pg";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

// ────────────────────────────────────────────────────────────────────────────
// 환경 변수
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: parseInt(process.env.DB_PORT ?? "5432"),
  DB_NAME: process.env.DB_NAME ?? "mztk_dev",
  DB_USER: process.env.DB_USER ?? "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "postgres",
};

const SOCIAL_ENV = {
  KAKAO_CLIENT_ID: process.env.KAKAO_CLIENT_ID ?? "",
  KAKAO_REDIRECT_URI: process.env.KAKAO_REDIRECT_URI ?? "http://localhost:3000/callback",
  KAKAO_TEST_EMAIL: process.env.KAKAO_TEST_EMAIL ?? "",
  KAKAO_TEST_PASSWORD: process.env.KAKAO_TEST_PASSWORD ?? "",
  GOOGLE_CLIENT_ID: process.env.GOOGLE_CLIENT_ID ?? "",
  GOOGLE_REDIRECT_URI: process.env.GOOGLE_REDIRECT_URI ?? "http://localhost:3000/callback",
  GOOGLE_TEST_EMAIL: process.env.GOOGLE_TEST_EMAIL ?? "",
  GOOGLE_TEST_PASSWORD: process.env.GOOGLE_TEST_PASSWORD ?? "",
};

// ────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼
// ────────────────────────────────────────────────────────────────────────────

/** PostgreSQL 직접 쿼리 (테스트 데이터 검증 및 정리). */
async function queryDb<T extends Record<string, unknown>>(
  sql: string,
  params: unknown[] = []
): Promise<T[]> {
  const client = new Client({
    host: ENV.DB_HOST,
    port: ENV.DB_PORT,
    database: ENV.DB_NAME,
    user: ENV.DB_USER,
    password: ENV.DB_PASSWORD,
  });
  await client.connect();
  try {
    const result = await client.query(sql, params);
    return result.rows as T[];
  } finally {
    await client.end();
  }
}

/** redirect_uri(localhost:3000/callback)를 인터셉트하여 authorization_code 를 추출. */
async function interceptAuthorizationCode(context: BrowserContext): Promise<string> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error("authorization_code 수신 타임아웃 (30s)")),
      30_000
    );

    context.on("request", (request) => {
      const url = new URL(request.url());
      if (url.hostname === "localhost" && url.port === "3000" && url.pathname === "/callback") {
        const code = url.searchParams.get("code");
        if (code) {
          clearTimeout(timer);
          resolve(code);
        }
      }
    });
  });
}

/** localhost:3000/callback 요청을 더미 200으로 차단. */
async function mockCallbackRoute(page: Page): Promise<void> {
  await page.route("http://localhost:3000/**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<html><body>OK - Playwright callback intercepted</body></html>",
    });
  });
}

/**
 * LOCAL 계정 생성 후 accessToken 반환.
 * 테스트 격리를 위해 매 호출마다 고유 이메일 사용.
 */
async function signUpAndLogin(
  apiContext: import("@playwright/test").APIRequestContext,
  email: string
): Promise<{ userId: number; accessToken: string }> {
  const signupRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      nickname: "role-tester",
    },
  });
  expect(signupRes.status(), `회원가입 실패: ${email}`).toBe(200);
  const signupBody = await signupRes.json();
  const userId = signupBody.data.userId as number;

  const loginRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL", email, password: "TestPass1234!" },
  });
  expect(loginRes.status(), `로그인 실패: ${email}`).toBe(200);
  const loginBody = await loginRes.json();
  const accessToken = loginBody.data.accessToken as string;

  return { userId, accessToken };
}

/** JWT 페이로드에서 userId 추출 (Base64 디코딩). */
function extractUserIdFromJwt(token: string): number {
  const payload = JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString());
  return payload.sub ? Number(payload.sub) : payload.userId;
}

/**
 * Kakao OAuth authorization_code 획득.
 * 브라우저에서 로그인 폼을 자동화하여 동의 화면을 통과하고 code를 반환.
 */
async function getKakaoAuthorizationCode(
  page: Page,
  context: BrowserContext
): Promise<string> {
  const authUrl =
    `https://kauth.kakao.com/oauth/authorize` +
    `?client_id=${SOCIAL_ENV.KAKAO_CLIENT_ID}` +
    `&redirect_uri=${encodeURIComponent(SOCIAL_ENV.KAKAO_REDIRECT_URI)}` +
    `&response_type=code` +
    `&scope=profile_nickname%2Caccount_email`;

  await mockCallbackRoute(page);
  const codePromise = interceptAuthorizationCode(context);

  await page.goto(authUrl);
  await page.waitForLoadState("networkidle");

  // 로그인 폼 입력
  try {
    const emailInput = page
      .locator("input#loginId--1")
      .or(page.locator("input[name='loginKey']"))
      .first();
    await emailInput.waitFor({ state: "visible", timeout: 5_000 });
    await emailInput.fill(SOCIAL_ENV.KAKAO_TEST_EMAIL);

    const passwordInput = page
      .locator("input#password--1")
      .or(page.locator("input[type='password']"))
      .first();
    await passwordInput.fill(SOCIAL_ENV.KAKAO_TEST_PASSWORD);

    const loginButton = page
      .locator("button.submit")
      .or(page.locator("button[type='submit']"))
      .first();
    await loginButton.click();

    // 동의 화면 처리
    try {
      const agreeButton = page.locator("button.agree-btn").or(page.locator(".btn_agree")).first();
      await agreeButton.waitFor({ timeout: 3_000 });
      await agreeButton.click();
    } catch {
      // 이미 동의한 계정
    }
  } catch {
    // 세션 유지로 자동 코드 발급
  }

  return codePromise;
}

/**
 * Google OAuth authorization_code 획득.
 * 브라우저에서 로그인 폼을 자동화하여 동의 화면을 통과하고 code를 반환.
 */
async function getGoogleAuthorizationCode(
  page: Page,
  context: BrowserContext
): Promise<string> {
  const authUrl =
    `https://accounts.google.com/o/oauth2/v2/auth` +
    `?client_id=${SOCIAL_ENV.GOOGLE_CLIENT_ID}` +
    `&redirect_uri=${encodeURIComponent(SOCIAL_ENV.GOOGLE_REDIRECT_URI)}` +
    `&response_type=code` +
    `&scope=openid%20email%20profile` +
    `&access_type=offline` +
    `&prompt=consent`;

  await mockCallbackRoute(page);
  const codePromise = interceptAuthorizationCode(context);

  await page.goto(authUrl);
  await page.waitForLoadState("networkidle");

  // 이메일 입력
  const emailInput = page.locator("input[type='email']");
  await emailInput.waitFor({ state: "visible", timeout: 15_000 });
  await emailInput.fill(SOCIAL_ENV.GOOGLE_TEST_EMAIL);
  await page.keyboard.press("Enter");

  await page.waitForLoadState("networkidle");

  // 비밀번호 입력
  const passwordInput = page.locator("input[type='password']:not([aria-hidden='true'])");
  await passwordInput.waitFor({ state: "visible", timeout: 15_000 });
  await passwordInput.fill(SOCIAL_ENV.GOOGLE_TEST_PASSWORD);
  await page.keyboard.press("Enter");

  // 2단계 인증 스킵
  try {
    const skipMfa = page
      .locator("button:has-text('지금은 하지 않기')")
      .or(page.locator("button:has-text('Not now')"));
    await skipMfa.waitFor({ timeout: 3_000 });
    await skipMfa.click();
  } catch {
    // 2단계 인증 없음
  }

  // 동의 화면 허용
  try {
    const continueButton = page
      .locator("button:has-text('계속')")
      .or(page.locator("button:has-text('Continue')"))
      .or(page.locator("#submit_approve_access"));
    await continueButton.waitFor({ timeout: 5_000 });
    await continueButton.click();
  } catch {
    // 동의 화면 없음
  }

  return codePromise;
}

// ════════════════════════════════════════════════════════════════════════════
// Suite A — Kakao OAuth 신규 가입 + TRAINER role 설정
// ════════════════════════════════════════════════════════════════════════════

test.describe.serial("Suite A: Kakao 소셜 가입 시 TRAINER role 설정", () => {
  let kakaoAccessToken: string;
  let kakaoUserId: number;

  test.beforeAll(async () => {
    // 테스트 전 Kakao 테스트 계정으로 생성된 기존 유저 정리
    // (이전 테스트 실행에서 남은 데이터 제거)
    const rows = await queryDb<{ id: number }>(
      "SELECT id FROM users WHERE email = $1",
      [SOCIAL_ENV.KAKAO_TEST_EMAIL]
    );
    for (const row of rows) {
      await queryDb("DELETE FROM refresh_tokens WHERE user_id = $1", [row.id]);
      await queryDb("DELETE FROM user_progress WHERE user_id = $1", [row.id]);
      await queryDb("DELETE FROM users_account WHERE user_id = $1", [row.id]);
      await queryDb("DELETE FROM users WHERE id = $1", [row.id]);
    }
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [P-1] Kakao OAuth 신규 가입 시 TRAINER role 설정
  // ──────────────────────────────────────────────────────────────────────────
  test("[P-1] Kakao 소셜 로그인으로 신규 가입 시 role=TRAINER 로 생성된다", async ({
    page,
    context,
    request,
  }) => {
    test.slow(); // OAuth UI 자동화는 시간이 오래 걸림

    // 1. Kakao authorization_code 획득
    const code = await getKakaoAuthorizationCode(page, context);
    expect(code, "authorization_code 가 빈 문자열").toBeTruthy();
    console.log(`[P-1] Kakao authorization_code 획득 (length=${code.length})`);

    // 2. POST /auth/login with role=TRAINER
    const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: {
        provider: "KAKAO",
        authorizationCode: code,
        redirectUri: SOCIAL_ENV.KAKAO_REDIRECT_URI,
        role: "TRAINER",
      },
    });
    expect(loginRes.status(), "로그인/가입 실패").toBe(200);

    const body = await loginRes.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const data = body.data as Record<string, unknown>;
    expect(data, "accessToken 누락").toHaveProperty("accessToken");
    expect(data.isNewUser, "신규 유저여야 함").toBe(true);

    kakaoAccessToken = data.accessToken as string;
    kakaoUserId = extractUserIdFromJwt(kakaoAccessToken);

    // 3. DB 검증: users 테이블에서 role 확인
    const dbRows = await queryDb<{ role: string }>(
      "SELECT role FROM users WHERE email = $1",
      [SOCIAL_ENV.KAKAO_TEST_EMAIL]
    );
    expect(dbRows.length, "DB에 유저가 생성되지 않음").toBe(1);
    expect(dbRows[0].role, "DB에 저장된 role이 TRAINER이어야 함").toBe("TRAINER");

    console.log(`[P-1] Kakao 신규 가입 성공: userId=${kakaoUserId}, role=TRAINER`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [P-3] 기존 Kakao 유저 재로그인 시 role 파라미터 무시
  // ──────────────────────────────────────────────────────────────────────────
  test("[P-3] 기존 Kakao 유저가 role=USER 로 재로그인해도 role 은 TRAINER 유지", async ({
    page,
    context,
    request,
  }) => {
    test.slow();

    if (!kakaoUserId) test.skip(true, "[P-1] 에서 Kakao 유저가 먼저 생성되어야 합니다");

    // 1. 새 authorization_code 획득
    const code = await getKakaoAuthorizationCode(page, context);
    expect(code, "authorization_code 가 빈 문자열").toBeTruthy();

    // 2. POST /auth/login with role=USER (기존 유저이므로 무시되어야 함)
    const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: {
        provider: "KAKAO",
        authorizationCode: code,
        redirectUri: SOCIAL_ENV.KAKAO_REDIRECT_URI,
        role: "USER",
      },
    });
    expect(loginRes.status(), "재로그인 실패").toBe(200);

    const body = await loginRes.json();
    const data = body.data as Record<string, unknown>;
    expect(data.isNewUser, "기존 유저이므로 isNewUser=false").toBe(false);

    // 3. DB 검증: role이 여전히 TRAINER인지 확인
    const dbRows = await queryDb<{ role: string }>(
      "SELECT role FROM users WHERE id = $1",
      [kakaoUserId]
    );
    expect(dbRows.length).toBe(1);
    expect(dbRows[0].role, "기존 유저의 role 이 변경되지 않아야 함").toBe("TRAINER");

    console.log(`[P-3] Kakao 재로그인 후 role 유지 확인: role=${dbRows[0].role}`);
  });

  test.afterAll(async () => {
    // 테스트 데이터 정리
    if (kakaoUserId) {
      await queryDb("DELETE FROM refresh_tokens WHERE user_id = $1", [kakaoUserId]);
      await queryDb("DELETE FROM user_progress WHERE user_id = $1", [kakaoUserId]);
      await queryDb("DELETE FROM users_account WHERE user_id = $1", [kakaoUserId]);
      await queryDb("DELETE FROM users WHERE id = $1", [kakaoUserId]);
    }
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Suite B — Google OAuth 신규 가입 + 기본값 USER role
// ════════════════════════════════════════════════════════════════════════════

test.describe.serial("Suite B: Google 소셜 가입 시 기본 USER role 설정", () => {
  let googleUserId: number;

  test.beforeAll(async () => {
    // 테스트 전 Google 테스트 계정으로 생성된 기존 유저 정리
    const rows = await queryDb<{ id: number }>(
      "SELECT id FROM users WHERE email = $1",
      [SOCIAL_ENV.GOOGLE_TEST_EMAIL]
    );
    for (const row of rows) {
      await queryDb("DELETE FROM refresh_tokens WHERE user_id = $1", [row.id]);
      await queryDb("DELETE FROM user_progress WHERE user_id = $1", [row.id]);
      await queryDb("DELETE FROM users_account WHERE user_id = $1", [row.id]);
      await queryDb("DELETE FROM users WHERE id = $1", [row.id]);
    }
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [P-2] Google OAuth 신규 가입 시 role 미지정 → 기본값 USER
  // ──────────────────────────────────────────────────────────────────────────
  test("[P-2] Google 소셜 로그인으로 신규 가입 시 role 미지정이면 USER 로 생성된다", async ({
    page,
    context,
    request,
  }) => {
    test.slow();

    // 1. Google authorization_code 획득
    const code = await getGoogleAuthorizationCode(page, context);
    expect(code, "Google authorization_code 가 빈 문자열").toBeTruthy();
    console.log(`[P-2] Google authorization_code 획득 (length=${code.length})`);

    // 2. POST /auth/login WITHOUT role field
    const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: {
        provider: "GOOGLE",
        authorizationCode: code,
        redirectUri: SOCIAL_ENV.GOOGLE_REDIRECT_URI,
        // role 필드 생략 → 기본값 USER 적용
      },
    });
    expect(loginRes.status(), "로그인/가입 실패").toBe(200);

    const body = await loginRes.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const data = body.data as Record<string, unknown>;
    expect(data, "accessToken 누락").toHaveProperty("accessToken");
    expect(data.isNewUser, "신규 유저여야 함").toBe(true);

    const accessToken = data.accessToken as string;
    googleUserId = extractUserIdFromJwt(accessToken);

    // 3. DB 검증: users 테이블에서 role 확인
    const dbRows = await queryDb<{ role: string }>(
      "SELECT role FROM users WHERE email = $1",
      [SOCIAL_ENV.GOOGLE_TEST_EMAIL]
    );
    expect(dbRows.length, "DB에 유저가 생성되지 않음").toBe(1);
    expect(dbRows[0].role, "role 미지정 시 기본값 USER 이어야 함").toBe("USER");

    console.log(`[P-2] Google 신규 가입 성공: userId=${googleUserId}, role=USER`);
  });

  test.afterAll(async () => {
    // 테스트 데이터 정리
    if (googleUserId) {
      await queryDb("DELETE FROM refresh_tokens WHERE user_id = $1", [googleUserId]);
      await queryDb("DELETE FROM user_progress WHERE user_id = $1", [googleUserId]);
      await queryDb("DELETE FROM users_account WHERE user_id = $1", [googleUserId]);
      await queryDb("DELETE FROM users WHERE id = $1", [googleUserId]);
    }
  });
});
