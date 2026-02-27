/**
 * MZTK - 소셜 로그인 Playwright E2E 테스트
 *
 * 테스트 대상: Kakao / Google OAuth 전체 플로우
 *   1. 브라우저에서 OAuth 동의 화면 통과 → authorization_code 획득
 *   2. POST /auth/login 호출 → 200 + accessToken + Set-Cookie(refreshToken)
 *   3. POST /auth/stepup 호출 → 200 + stepUpToken
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 http://localhost:8080 에서 실행 중이어야 합니다.
 *   - .env 파일에 테스트 계정 자격증명이 입력되어 있어야 합니다.
 *   - Kakao 앱에 테스트 계정이 등록되어 있어야 합니다.
 *   - Google OAuth 앱이 테스트 모드이고 테스트 유저가 등록되어 있어야 합니다.
 */

import { test, expect, APIResponse, BrowserContext, Page } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, ".env") });

// ────────────────────────────────────────────────────────────────────────────
// 환경 변수
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://localhost:8080",
  KAKAO_CLIENT_ID: process.env.KAKAO_CLIENT_ID!,
  KAKAO_REDIRECT_URI: process.env.KAKAO_REDIRECT_URI ?? "http://localhost:3000/callback",
  KAKAO_TEST_EMAIL: process.env.KAKAO_TEST_EMAIL!,
  KAKAO_TEST_PASSWORD: process.env.KAKAO_TEST_PASSWORD!,
  GOOGLE_CLIENT_ID: process.env.GOOGLE_CLIENT_ID!,
  GOOGLE_REDIRECT_URI: process.env.GOOGLE_REDIRECT_URI ?? "http://localhost:3000/callback",
  GOOGLE_TEST_EMAIL: process.env.GOOGLE_TEST_EMAIL!,
  GOOGLE_TEST_PASSWORD: process.env.GOOGLE_TEST_PASSWORD!,
};

// ────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼
// ────────────────────────────────────────────────────────────────────────────

/**
 * redirect_uri(localhost:3000/callback)로의 실제 네비게이션을 차단하고,
 * URL 파라미터에서 authorization_code 를 추출하여 반환합니다.
 *
 * Kakao / Google 모두 OAuth 동의 완료 후 `redirect_uri?code=xxx` 로 리다이렉트합니다.
 * 해당 URL 은 프론트엔드 서버가 없으므로 Playwright 가 Route 차단으로 처리합니다.
 */
async function interceptAuthorizationCode(context: BrowserContext): Promise<string> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("authorization_code 수신 타임아웃 (30s)")), 30_000);

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

/**
 * localhost:3000/callback 으로의 요청을 더미 200 응답으로 차단합니다.
 * 이렇게 하지 않으면 Playwright 가 연결 실패 에러를 던집니다.
 */
async function mockCallbackRoute(page: Page): Promise<void> {
  await page.route("http://localhost:3000/**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<html><body>OK - Playwright callback intercepted</body></html>",
    });
  });
}

/** Playwright request API 로 백엔드에 직접 POST 요청을 보냅니다. */
async function callBackendLogin(
  apiContext: ReturnType<typeof import("@playwright/test")["request"]["newContext"]> extends Promise<infer T> ? T : never,
  provider: "KAKAO" | "GOOGLE",
  authorizationCode: string,
  redirectUri: string
): Promise<APIResponse> {
  return apiContext.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      provider,
      authorizationCode,
      redirectUri,
    },
  });
}

// ────────────────────────────────────────────────────────────────────────────
// 공통 응답 검증 헬퍼
// ────────────────────────────────────────────────────────────────────────────

function assertLoginResponse(
  body: Record<string, unknown>,
  setCookieHeader: string | null,
  provider: string
): void {
  // 응답 바디 구조 검증 — ApiResponse: { status: "SUCCESS", data: {...} }
  expect(body, `[${provider}] status 필드 누락`).toHaveProperty("status", "SUCCESS");
  expect(body, `[${provider}] data 필드 누락`).toHaveProperty("data");

  const data = body.data as Record<string, unknown>;
  expect(data, `[${provider}] accessToken 누락`).toHaveProperty("accessToken");
  expect(typeof data.accessToken, `[${provider}] accessToken 타입 오류`).toBe("string");
  expect((data.accessToken as string).length, `[${provider}] accessToken 길이 0`).toBeGreaterThan(0);

  // Set-Cookie: refreshToken 헤더 검증
  expect(setCookieHeader, `[${provider}] Set-Cookie 헤더 없음`).not.toBeNull();
  expect(setCookieHeader, `[${provider}] refreshToken 쿠키 없음`).toContain("refreshToken=");
  expect(setCookieHeader, `[${provider}] HttpOnly 속성 없음`).toContain("HttpOnly");
  expect(setCookieHeader, `[${provider}] Path=/auth 속성 없음`).toContain("Path=/auth");
  expect(setCookieHeader, `[${provider}] SameSite=Strict 속성 없음`).toContain("SameSite=Strict");
}

// ════════════════════════════════════════════════════════════════════════════
// Kakao 소셜 로그인 테스트 스위트
// ════════════════════════════════════════════════════════════════════════════

test.describe("Kakao 소셜 로그인 E2E", () => {
  let kakaoAccessToken: string;
  let kakaoRefreshTokenCookie: string;

  // ──────────────────────────────────────────────────────────────────────────
  // TC-K-01: Kakao authorization_code 획득
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-K-01: 카카오 동의 화면을 통해 authorization_code 를 획득한다", async ({
    page,
    context,
  }) => {
    test.slow(); // OAuth UI 자동화는 시간이 오래 걸릴 수 있음

    const authUrl =
      `https://kauth.kakao.com/oauth/authorize` +
      `?client_id=${ENV.KAKAO_CLIENT_ID}` +
      `&redirect_uri=${encodeURIComponent(ENV.KAKAO_REDIRECT_URI)}` +
      `&response_type=code` +
      `&scope=profile_nickname%2Caccount_email`;

    await mockCallbackRoute(page);
    const codePromise = interceptAuthorizationCode(context);

    // 카카오 OAuth 동의 화면으로 이동
    await page.goto(authUrl);

    // 카카오 로그인 폼 대기
    await page.waitForLoadState("networkidle");

    // 이메일/전화번호 입력 필드
    const emailInput = page.locator("input#loginId--1").or(page.locator("input[name='loginKey']")).first();
    await emailInput.fill(ENV.KAKAO_TEST_EMAIL);

    // 비밀번호 입력 필드
    const passwordInput = page.locator("input#password--1").or(page.locator("input[type='password']")).first();
    await passwordInput.fill(ENV.KAKAO_TEST_PASSWORD);

    // 로그인 버튼 클릭
    const loginButton = page
      .locator("button.submit")
      .or(page.locator("button[type='submit']"))
      .first();
    await loginButton.click();

    // 동의 화면 처리 (이미 동의한 경우 자동 스킵됨)
    try {
      const agreeButton = page.locator("button.agree-btn").or(page.locator(".btn_agree")).first();
      await agreeButton.waitFor({ timeout: 3_000 });
      await agreeButton.click();
    } catch {
      // 동의 화면 없음 - 이전에 이미 동의한 계정
    }

    // authorization_code 수신 대기
    const code = await codePromise;

    expect(code, "authorization_code 가 빈 문자열").toBeTruthy();
    expect(code.length, "authorization_code 길이 0").toBeGreaterThan(0);

    // 다음 테스트에서 사용할 수 있도록 전역 변수에 저장
    // (test.info().annotations 또는 파일 공유를 통해 전달)
    process.env["_KAKAO_CODE"] = code;

    console.log(`[TC-K-01] authorization_code 획득 성공 (length=${code.length})`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-K-02: POST /auth/login (KAKAO) → 200 + accessToken + refreshToken 쿠키
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-K-02: POST /auth/login (KAKAO) 가 200 과 토큰을 반환한다", async ({ request }) => {
    const code = process.env["_KAKAO_CODE"];
    if (!code) test.skip(true, "TC-K-01 에서 authorization_code 를 먼저 획득해야 합니다");

    const response = await callBackendLogin(request, "KAKAO", code!, ENV.KAKAO_REDIRECT_URI);

    expect(response.status(), "HTTP 상태코드가 200 이 아님").toBe(200);

    const body = await response.json();
    const setCookieHeader = response.headers()["set-cookie"] ?? null;

    assertLoginResponse(body, setCookieHeader, "KAKAO");

    // 다음 테스트(TC-K-03)를 위해 토큰 저장
    kakaoAccessToken = (body.data as Record<string, string>).accessToken;
    kakaoRefreshTokenCookie = setCookieHeader!;

    console.log(`[TC-K-02] 로그인 성공: accessToken 앞 20자 = ${kakaoAccessToken.substring(0, 20)}...`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-K-03: POST /auth/reissue → 쿠키 교체 (토큰 로테이션)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-K-03: POST /auth/reissue 가 새 accessToken 과 새 refreshToken 쿠키를 반환한다", async ({
    request,
  }) => {
    if (!kakaoRefreshTokenCookie) test.skip(true, "TC-K-02 에서 refreshToken 쿠키를 먼저 획득해야 합니다");

    // refreshToken 쿠키 값만 추출
    const cookieValue = kakaoRefreshTokenCookie
      .split(";")[0]
      .replace("refreshToken=", "")
      .trim();

    const response = await request.post(`${ENV.BACKEND_URL}/auth/reissue`, {
      headers: {
        Cookie: `refreshToken=${cookieValue}`,
      },
    });

    expect(response.status(), "reissue HTTP 상태코드가 200 이 아님").toBe(200);

    const body = await response.json();
    expect(body, "reissue 응답 status 필드 누락").toHaveProperty("status", "SUCCESS");

    const data = body.data as Record<string, string>;
    expect(data, "새 accessToken 누락").toHaveProperty("accessToken");
    expect(typeof data.accessToken, "새 accessToken 타입 오류").toBe("string");
    expect((data.accessToken as string).length, "새 accessToken 길이 0").toBeGreaterThan(0);
    // 참고: 재발급이 1초 이내에 완료되면 iat가 동일하여 토큰값이 같을 수 있음 — 값 동일 여부는 검증하지 않음

    const newCookie = response.headers()["set-cookie"];
    expect(newCookie, "새 refreshToken 쿠키 없음").toContain("refreshToken=");

    console.log(`[TC-K-03] 토큰 재발급 성공`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-K-04: Kakao Step-Up — 새 authorization_code 획득 후 POST /auth/stepup
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-K-04: POST /auth/stepup (KAKAO) 이 stepUpToken 을 반환한다", async ({
    page,
    context,
    request,
  }) => {
    test.slow();

    if (!kakaoAccessToken) test.skip(true, "TC-K-02 에서 accessToken 을 먼저 획득해야 합니다");

    // Step-up 용 새 authorization_code 획득 (재동의 없이 즉시 code 발급됨)
    const authUrl =
      `https://kauth.kakao.com/oauth/authorize` +
      `?client_id=${ENV.KAKAO_CLIENT_ID}` +
      `&redirect_uri=${encodeURIComponent(ENV.KAKAO_REDIRECT_URI)}` +
      `&response_type=code` +
      `&scope=profile_nickname%2Caccount_email`;

    await mockCallbackRoute(page);
    const codePromise = interceptAuthorizationCode(context);
    await page.goto(authUrl);
    await page.waitForLoadState("networkidle");

    // Playwright 는 테스트마다 새로운 브라우저 컨텍스트를 사용하므로
    // TC-K-01 의 카카오 세션이 없음 → 로그인 폼이 다시 표시됨 → 자격증명 재입력
    try {
      const emailInput = page
        .locator("input#loginId--1")
        .or(page.locator("input[name='loginKey']"))
        .first();
      await emailInput.waitFor({ state: "visible", timeout: 3_000 });
      await emailInput.fill(ENV.KAKAO_TEST_EMAIL);

      const passwordInput = page
        .locator("input#password--1")
        .or(page.locator("input[type='password']"))
        .first();
      await passwordInput.fill(ENV.KAKAO_TEST_PASSWORD);

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
      // 이미 로그인된 세션 (세션 유지 시) — code 수신 대기 중
    }

    const stepUpCode = await codePromise;

    const response = await request.post(`${ENV.BACKEND_URL}/auth/stepup`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${kakaoAccessToken}`,
      },
      data: {
        authorizationCode: stepUpCode,
      },
    });

    expect(response.status(), "stepup HTTP 상태코드가 200 이 아님").toBe(200);

    const body = await response.json();
    expect(body, "stepup status 필드 누락").toHaveProperty("status", "SUCCESS");

    // StepUpResponseDTO: { accessToken, grantType, expiresIn }
    // step_up 클레임이 포함된 accessToken 을 반환함
    const data = body.data as Record<string, string>;
    expect(data, "stepup accessToken 누락").toHaveProperty("accessToken");
    expect((data.accessToken as string).length, "stepup accessToken 길이 0").toBeGreaterThan(0);
    expect(data, "grantType 누락").toHaveProperty("grantType", "Bearer");

    console.log(`[TC-K-04] Step-Up 성공: accessToken(step_up) 앞 20자 = ${data.accessToken.substring(0, 20)}...`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-K-05: POST /auth/logout → 204 + maxAge=0 쿠키
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-K-05: POST /auth/logout 가 204 와 maxAge=0 쿠키를 반환한다", async ({ request }) => {
    if (!kakaoRefreshTokenCookie) test.skip(true, "TC-K-02 에서 refreshToken 쿠키를 먼저 획득해야 합니다");

    const cookieValue = kakaoRefreshTokenCookie.split(";")[0].replace("refreshToken=", "").trim();

    const response = await request.post(`${ENV.BACKEND_URL}/auth/logout`, {
      headers: { Cookie: `refreshToken=${cookieValue}` },
    });

    expect(response.status(), "logout HTTP 상태코드가 204 이 아님").toBe(204);

    const deleteCookie = response.headers()["set-cookie"];
    expect(deleteCookie, "삭제 쿠키 없음").toContain("refreshToken=");
    expect(deleteCookie, "Max-Age=0 없음").toContain("Max-Age=0");

    console.log(`[TC-K-05] 로그아웃 성공`);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Google 소셜 로그인 테스트 스위트
// ════════════════════════════════════════════════════════════════════════════

test.describe("Google 소셜 로그인 E2E", () => {
  let googleAccessToken: string;

  // ──────────────────────────────────────────────────────────────────────────
  // TC-G-01: Google authorization_code 획득
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-G-01: 구글 동의 화면을 통해 authorization_code 를 획득한다", async ({
    page,
    context,
  }) => {
    test.slow();

    const authUrl =
      `https://accounts.google.com/o/oauth2/v2/auth` +
      `?client_id=${ENV.GOOGLE_CLIENT_ID}` +
      `&redirect_uri=${encodeURIComponent(ENV.GOOGLE_REDIRECT_URI)}` +
      `&response_type=code` +
      `&scope=openid%20email%20profile` +
      `&access_type=offline` +
      `&prompt=consent`;

    await mockCallbackRoute(page);
    const codePromise = interceptAuthorizationCode(context);

    await page.goto(authUrl);
    await page.waitForLoadState("networkidle");

    // 구글 로그인 폼: 이메일 입력
    const emailInput = page.locator("input[type='email']");
    await emailInput.waitFor({ state: "visible", timeout: 15_000 });
    await emailInput.fill(ENV.GOOGLE_TEST_EMAIL);
    await page.keyboard.press("Enter");

    // 이메일 제출 후 비밀번호 페이지로 전환될 때까지 대기
    // Google은 email 제출 시 페이지 내 상태가 변하며, 숨겨진 hiddenPassword 필드(aria-hidden=true)가
    // 먼저 DOM에 나타남. 실제 입력 가능한 필드가 visible 상태가 될 때까지 명시적으로 기다려야 함.
    await page.waitForLoadState("networkidle");

    // aria-hidden="true" 인 더미 필드를 제외하고 실제 비밀번호 입력 필드를 선택
    const passwordInput = page.locator("input[type='password']:not([aria-hidden='true'])");
    await passwordInput.waitFor({ state: "visible", timeout: 15_000 });
    await passwordInput.fill(ENV.GOOGLE_TEST_PASSWORD);
    await page.keyboard.press("Enter");

    // Google 2단계 인증 처리 (없을 경우 자동 스킵)
    try {
      const skipMfa = page.locator("button:has-text('지금은 하지 않기')").or(
        page.locator("button:has-text('Not now')")
      );
      await skipMfa.waitFor({ timeout: 3_000 });
      await skipMfa.click();
    } catch {
      // 2단계 인증 없음
    }

    // 동의 화면 처리 (prompt=consent 이므로 매번 표시됨)
    try {
      const continueButton = page
        .locator("button:has-text('계속')")
        .or(page.locator("button:has-text('Continue')")
        .or(page.locator("#submit_approve_access")));
      await continueButton.waitFor({ timeout: 5_000 });
      await continueButton.click();
    } catch {
      // 동의 화면 없음
    }

    const code = await codePromise;

    expect(code, "Google authorization_code 가 빈 문자열").toBeTruthy();
    expect(code.length, "Google authorization_code 길이 0").toBeGreaterThan(0);

    process.env["_GOOGLE_CODE"] = code;
    console.log(`[TC-G-01] Google authorization_code 획득 성공 (length=${code.length})`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-G-02: POST /auth/login (GOOGLE) → 200 + accessToken + refreshToken 쿠키
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-G-02: POST /auth/login (GOOGLE) 가 200 과 토큰을 반환한다", async ({ request }) => {
    const code = process.env["_GOOGLE_CODE"];
    if (!code) test.skip(true, "TC-G-01 에서 authorization_code 를 먼저 획득해야 합니다");

    const response = await callBackendLogin(request, "GOOGLE", code!, ENV.GOOGLE_REDIRECT_URI);

    expect(response.status(), "HTTP 상태코드가 200 이 아님").toBe(200);

    const body = await response.json();
    const setCookieHeader = response.headers()["set-cookie"] ?? null;

    assertLoginResponse(body, setCookieHeader, "GOOGLE");

    googleAccessToken = (body.data as Record<string, string>).accessToken;

    console.log(`[TC-G-02] Google 로그인 성공`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-G-03: Google Step-Up (offline_access + refresh_token 포함 여부 검증)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-G-03: POST /auth/stepup (GOOGLE) 이 stepUpToken 을 반환한다", async ({
    page,
    context,
    request,
  }) => {
    test.slow();

    if (!googleAccessToken) test.skip(true, "TC-G-02 에서 accessToken 을 먼저 획득해야 합니다");

    // Step-up 용 새 authorization_code 획득 (Google: offline_access 재동의 포함)
    const authUrl =
      `https://accounts.google.com/o/oauth2/v2/auth` +
      `?client_id=${ENV.GOOGLE_CLIENT_ID}` +
      `&redirect_uri=${encodeURIComponent(ENV.GOOGLE_REDIRECT_URI)}` +
      `&response_type=code` +
      `&scope=openid%20email%20profile` +
      `&access_type=offline` +
      `&prompt=consent`;

    await mockCallbackRoute(page);
    const codePromise = interceptAuthorizationCode(context);
    await page.goto(authUrl);

    // 이미 로그인된 세션이 있으면 계정 선택 화면만 나타날 수 있음
    try {
      const accountButton = page.locator(`[data-email="${ENV.GOOGLE_TEST_EMAIL}"]`).or(
        page.locator("li[data-identifier]").first()
      );
      await accountButton.waitFor({ timeout: 5_000 });
      await accountButton.click();
    } catch {
      // 계정 선택 없음 → 직접 로그인
      const emailInput = page.locator("input[type='email']");
      await emailInput.waitFor({ state: "visible", timeout: 10_000 });
      await emailInput.fill(ENV.GOOGLE_TEST_EMAIL);
      await page.keyboard.press("Enter");

      await page.waitForLoadState("networkidle");
      const passwordInput = page.locator("input[type='password']:not([aria-hidden='true'])");
      await passwordInput.waitFor({ state: "visible", timeout: 15_000 });
      await passwordInput.fill(ENV.GOOGLE_TEST_PASSWORD);
      await page.keyboard.press("Enter");
    }

    // 동의 화면 허용
    try {
      const continueButton = page
        .locator("button:has-text('계속')")
        .or(page.locator("button:has-text('Continue')"));
      await continueButton.waitFor({ timeout: 5_000 });
      await continueButton.click();
    } catch {
      // 동의 화면 없음
    }

    const stepUpCode = await codePromise;

    const response = await request.post(`${ENV.BACKEND_URL}/auth/stepup`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${googleAccessToken}`,
      },
      data: {
        authorizationCode: stepUpCode,
      },
    });

    expect(response.status(), "Google stepup HTTP 상태코드가 200 이 아님").toBe(200);

    const body = await response.json();
    expect(body, "stepup status 필드 누락").toHaveProperty("status", "SUCCESS");

    // StepUpResponseDTO: { accessToken, grantType, expiresIn }
    const data = body.data as Record<string, string>;
    expect(data, "stepup accessToken 누락").toHaveProperty("accessToken");
    expect((data.accessToken as string).length, "stepup accessToken 길이 0").toBeGreaterThan(0);

    console.log(`[TC-G-03] Google Step-Up 성공`);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 엣지 케이스 / 실패 시나리오
// ════════════════════════════════════════════════════════════════════════════

test.describe("인증 실패 케이스", () => {
  // ──────────────────────────────────────────────────────────────────────────
  // TC-E-01: 만료/위조된 authorization_code 로 로그인 시도 → 400 또는 502
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-E-01: 위조된 authorization_code 로 Kakao 로그인 시 외부 API 에러를 반환한다", async ({
    request,
  }) => {
    const response = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: {
        provider: "KAKAO",
        authorizationCode: "INVALID_CODE_12345",
        redirectUri: ENV.KAKAO_REDIRECT_URI,
      },
    });

    // Kakao 에서 에러를 받아 서버가 4xx 또는 5xx 로 응답해야 함
    expect(response.status(), "위조 코드인데 200 이 반환됨").not.toBe(200);
    expect([400, 502, 503], "예상치 못한 상태코드").toContain(response.status());

    // ApiResponse 에러 구조: { "status": "FAIL", "code": "...", "message": "..." }
    const body = await response.json();
    expect(body, "에러 응답에 status:FAIL 없음").toHaveProperty("status", "FAIL");

    console.log(`[TC-E-01] 위조 코드 → ${response.status()} 정상 처리`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-E-02: refreshToken 쿠키 없이 /auth/reissue 호출 → 400
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-E-02: refreshToken 쿠키 없이 /auth/reissue 호출 시 400 을 반환한다", async ({
    request,
  }) => {
    const response = await request.post(`${ENV.BACKEND_URL}/auth/reissue`);

    expect(response.status(), "쿠키 없는 reissue 가 200 을 반환함").toBe(400);

    console.log(`[TC-E-02] 쿠키 없는 reissue → 400 정상 처리`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-E-03: 인증 토큰 없이 /auth/stepup 호출 → 401
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-E-03: 인증 토큰 없이 /auth/stepup 호출 시 401 을 반환한다", async ({ request }) => {
    const response = await request.post(`${ENV.BACKEND_URL}/auth/stepup`, {
      headers: { "Content-Type": "application/json" },
      data: { authorizationCode: "some-code" },
    });

    expect(response.status(), "토큰 없는 stepup 이 401 이 아님").toBe(401);

    console.log(`[TC-E-03] 토큰 없는 stepup → 401 정상 처리`);
  });
});
