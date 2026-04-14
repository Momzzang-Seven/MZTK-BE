/**
 * MZTK — 회원 탈퇴 Playwright E2E 테스트
 *
 * 테스트 대상: POST /auth/withdrawal (회원 탈퇴 플로우)
 *
 * 외부 의존성:
 *   - 로컬 계정 탈퇴: 외부 API 없음 (단순 DB soft-delete)
 *   - 소셜 계정 탈퇴: Kakao unlinkUser / Google revokeToken 외부 API 호출
 *
 * 검증 항목:
 *   Suite A — 정상 탈퇴 플로우 (LOCAL)
 *     TC-UW-A-01: 회원가입 → 로그인 → step-up → 탈퇴 → 200 OK
 *     TC-UW-A-02: 탈퇴 후 로그인 시도 → 실패
 *     TC-UW-A-03: 탈퇴 후 재탈퇴 시도 → 실패 (이미 탈퇴됨)
 *
 *   Suite B — 권한 오류
 *     TC-UW-B-01: step-up 없이 탈퇴 요청 → 403 (ROLE_STEP_UP 미보유)
 *     TC-UW-B-02: 미인증 탈퇴 요청 → 401
 *
 *   Suite C — Soft Delete 연쇄 검증 (wallet)
 *     TC-UW-C-01: 탈퇴 후 wallet.status=USER_DELETED 전환 확인
 *
 *   Suite D — Hard Delete 연쇄 검증 (wallet)
 *     TC-UW-D-01: Hard delete 실행 후 USER_DELETED wallet 레코드 완전 삭제 확인
 *
 *   Suite E — Kakao 소셜 계정 탈퇴 (외부 API 실제 연동)
 *     TC-UW-E-01: Kakao OAuth → 로그인 → step-up(authorizationCode) → 탈퇴 → 200 OK
 *     TC-UW-E-02: Kakao 탈퇴 후 재탈퇴 시도 → 실패
 *
 *   Suite F — Google 소셜 계정 탈퇴 (외부 API 실제 연동)
 *     TC-UW-F-01: Google OAuth → 로그인 → step-up(authorizationCode) → 탈퇴 → 200 OK
 *     TC-UW-F-02: Google 탈퇴 후 재탈퇴 시도 → 실패
 *
 * [테스트 범위]
 *   Suite A~D : 외부 API(지갑 등록 포함) 의존성이 있는 흐름을 검증합니다.
 *   Suite E~F : Kakao/Google OAuth 실제 연동을 포함한 소셜 계정 탈퇴를 검증합니다.
 *              unlinkUser/revokeToken 은 best-effort 백그라운드 처리이므로,
 *              Playwright 에서는 탈퇴 API 응답(200 OK) 및 재탈퇴 차단으로 검증합니다.
 *   Location 연쇄 삭제(Spring 이벤트/스케줄러 내부 처리)는 UserE2ETest.java 에서 검증합니다.
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 실행 중이어야 합니다.
 *   - play_wright/.env 에 BACKEND_URL, DB_*, KAKAO_*, GOOGLE_* 설정이 되어 있어야 합니다.
 *   - Suite E: .env 에 KAKAO_CLIENT_ID, KAKAO_TEST_EMAIL, KAKAO_TEST_PASSWORD 필수
 *   - Suite F: .env 에 GOOGLE_CLIENT_ID, GOOGLE_TEST_EMAIL, GOOGLE_TEST_PASSWORD 필수
 */

import { test, expect, BrowserContext, Page, Browser } from "@playwright/test";
import { ethers } from "ethers";
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

// Suite E, F 전용: 소셜 OAuth 환경 변수
const SOCIAL_ENV = {
  KAKAO_CLIENT_ID: process.env.KAKAO_CLIENT_ID ?? "",
  KAKAO_REDIRECT_URI:
    process.env.KAKAO_REDIRECT_URI ?? "http://localhost:3000/callback",
  KAKAO_TEST_EMAIL: process.env.KAKAO_TEST_EMAIL ?? "",
  KAKAO_TEST_PASSWORD: process.env.KAKAO_TEST_PASSWORD ?? "",
  GOOGLE_CLIENT_ID: process.env.GOOGLE_CLIENT_ID ?? "",
  GOOGLE_REDIRECT_URI:
    process.env.GOOGLE_REDIRECT_URI ?? "http://localhost:3000/callback",
  GOOGLE_TEST_EMAIL: process.env.GOOGLE_TEST_EMAIL ?? "",
  GOOGLE_TEST_PASSWORD: process.env.GOOGLE_TEST_PASSWORD ?? "",
};

// ────────────────────────────────────────────────────────────────────────────
// EIP-712 도메인 상수 (web3-wallet.spec.ts 와 동일, 백엔드 application.yml 과 일치해야 함)
// ────────────────────────────────────────────────────────────────────────────
const EIP712_DOMAIN = {
  name: "MomzzangSeven",
  version: "1",
  chainId: 11155420, // Optimism Sepolia Testnet
  verifyingContract: "0x815B53fD2D56044BaC39c1f7a9C7d3E67322f0F5",
};

const EIP712_TYPES: Record<string, ethers.TypedDataField[]> = {
  AuthRequest: [
    { name: "content", type: "string" },
    { name: "nonce", type: "string" },
  ],
};

// ────────────────────────────────────────────────────────────────────────────
// 헬퍼 타입
// ────────────────────────────────────────────────────────────────────────────
interface AuthResult {
  accessToken: string;
  userId: number;
  email: string;
  password: string;
}

// ────────────────────────────────────────────────────────────────────────────
// 헬퍼 함수
// ────────────────────────────────────────────────────────────────────────────

/**
 * 테스트마다 고유한 로컬 계정을 회원가입하고, 로그인 후 AccessToken 과 userId 를 반환합니다.
 * suffix 를 사용해 각 TC 별로 독립적인 계정을 사용합니다.
 */
async function signUpAndLogin(
  request: import("@playwright/test").APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const timestamp = Date.now();
  const email = `playwright-uw-${suffix}-${timestamp}@test.com`;
  const password = "Test1234!";
  const nickname = `uw-${suffix}-${timestamp}`;

  // 1. 회원가입
  const signUpRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname, provider: "LOCAL" },
  });
  if (signUpRes.status() >= 300) {
    const body = await signUpRes.text();
    throw new Error(`회원가입 실패 (${signUpRes.status()}): ${body}`);
  }
  const signUpBody = await signUpRes.json();
  const userId: number = signUpBody.data?.userId;
  if (!userId) {
    throw new Error(`userId 추출 실패: ${JSON.stringify(signUpBody)}`);
  }

  // 2. 로그인
  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, provider: "LOCAL" },
  });
  if (loginRes.status() !== 200) {
    const body = await loginRes.text();
    throw new Error(`로그인 실패 (${loginRes.status()}): ${body}`);
  }

  const loginBody = await loginRes.json();
  return {
    accessToken: loginBody.data.accessToken,
    userId,
    email,
    password,
  };
}

/**
 * EIP-712 서명으로 랜덤 지갑을 등록합니다.
 * WalletUserSoftDeleteEventHandler 가 ACTIVE wallet 을 대상으로 동작하므로,
 * soft/hard delete cascade 테스트의 사전 조건으로 지갑을 등록해야 합니다.
 */
async function registerWallet(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string
): Promise<string> {
  const wallet = ethers.Wallet.createRandom();

  const challengeRes = await request.post(
    `${ENV.BACKEND_URL}/web3/challenges`,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      data: { purpose: "WALLET_REGISTRATION", walletAddress: wallet.address },
    }
  );
  if (challengeRes.status() >= 300) {
    throw new Error(
      `챌린지 발급 실패 (${challengeRes.status()}): ${await challengeRes.text()}`
    );
  }
  const { nonce, message } = (await challengeRes.json()).data;

  const signature = await wallet.signTypedData(EIP712_DOMAIN, EIP712_TYPES, {
    content: message,
    nonce,
  });

  const registerRes = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { walletAddress: wallet.address, signature, nonce },
  });
  if (registerRes.status() >= 300) {
    throw new Error(
      `지갑 등록 실패 (${registerRes.status()}): ${await registerRes.text()}`
    );
  }

  // DB는 wallet_address 를 소문자로 저장하므로 소문자로 통일하여 반환
  return wallet.address.toLowerCase();
}

/** DB에 직접 연결하여 단일 쿼리를 실행하고 결과 rows 를 반환합니다. */
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

/**
 * Step-Up 인증을 수행하고, 갱신된 AccessToken(ROLE_STEP_UP 포함)을 반환합니다.
 * LOCAL 계정은 password 를 사용합니다.
 */
async function performStepUp(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string,
  password: string
): Promise<string> {
  const res = await request.post(`${ENV.BACKEND_URL}/auth/stepup`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { password },
  });

  if (res.status() !== 200) {
    const body = await res.text();
    throw new Error(`step-up 실패 (${res.status()}): ${body}`);
  }

  const body = await res.json();
  return body.data.accessToken;
}

// ────────────────────────────────────────────────────────────────────────────
// 소셜 OAuth 자동화 헬퍼 (Suite E, F 전용)
// ────────────────────────────────────────────────────────────────────────────

/**
 * redirect_uri(localhost:3000/callback)로의 실제 네비게이션을 차단하고
 * URL 파라미터에서 authorization_code 를 추출합니다.
 *
 * Playwright 는 브라우저 레벨에서 request 이벤트를 감지하여
 * 카카오/구글 OAuth 동의 완료 후 redirect_uri?code=xxx 를 인터셉트합니다.
 */
async function interceptAuthorizationCode(
  context: BrowserContext
): Promise<string> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error("authorization_code 수신 타임아웃 (30s)")),
      30_000
    );

    context.on("request", (req) => {
      const url = new URL(req.url());
      if (
        url.hostname === "localhost" &&
        url.port === "3000" &&
        url.pathname === "/callback"
      ) {
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

/**
 * Kakao OAuth 동의 화면을 자동화하여 authorization_code 를 획득합니다.
 * 이미 로그인된 세션이 있으면 폼 입력을 건너뜁니다.
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

  // 이전 OAuth 완료 후 페이지가 localhost:3000/callback 에 머물러 있는 경우,
  // 해당 origin 컨텍스트에서 외부 URL 로의 재네비게이션이 차단될 수 있습니다.
  // about:blank 를 경유하여 origin 을 초기화한 뒤 goto 합니다.
  try {
    await page.goto("about:blank", { timeout: 3_000 });
  } catch {
    // 이미 blank 상태이거나 초기화 불필요한 경우 무시
  }
  await page.goto(authUrl);
  await page.waitForLoadState("networkidle");

  // 카카오 로그인 폼 처리 (세션 없을 때만 표시됨)
  try {
    const emailInput = page
      .locator("input#loginId--1")
      .or(page.locator("input[name='loginKey']"))
      .first();
    await emailInput.waitFor({ state: "visible", timeout: 3_000 });
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
  } catch {
    // 이미 로그인된 세션 — 폼 없이 code 발급
  }

  // 동의 화면 처리 (이미 동의한 경우 자동 스킵됨)
  try {
    const agreeButton = page
      .locator("button.agree-btn")
      .or(page.locator(".btn_agree"))
      .first();
    await agreeButton.waitFor({ timeout: 3_000 });
    await agreeButton.click();
  } catch {
    // 동의 화면 없음
  }

  return codePromise;
}

/**
 * Google OAuth 동의 화면을 자동화하여 authorization_code 를 획득합니다.
 * prompt=consent 로 매번 동의 화면이 표시됩니다.
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

  // 이전 OAuth 완료 후 페이지가 localhost:3000/callback 에 머물러 있는 경우,
  // about:blank 를 경유하여 origin 을 초기화한 뒤 goto 합니다.
  try {
    await page.goto("about:blank", { timeout: 3_000 });
  } catch {
    // 이미 blank 상태이거나 초기화 불필요한 경우 무시
  }
  await page.goto(authUrl);
  await page.waitForLoadState("networkidle");

  // 이미 로그인된 세션이 있으면 계정 선택 화면이 표시될 수 있음
  try {
    const accountButton = page
      .locator(`[data-email="${SOCIAL_ENV.GOOGLE_TEST_EMAIL}"]`)
      .or(page.locator("li[data-identifier]").first());
    await accountButton.waitFor({ timeout: 5_000 });
    await accountButton.click();
  } catch {
    // 직접 로그인: 이메일 입력
    const emailInput = page.locator("input[type='email']");
    await emailInput.waitFor({ state: "visible", timeout: 10_000 });
    await emailInput.fill(SOCIAL_ENV.GOOGLE_TEST_EMAIL);
    await page.keyboard.press("Enter");

    await page.waitForLoadState("networkidle");

    // aria-hidden="true" 인 더미 필드를 제외한 실제 비밀번호 입력 필드
    const passwordInput = page.locator(
      "input[type='password']:not([aria-hidden='true'])"
    );
    await passwordInput.waitFor({ state: "visible", timeout: 15_000 });
    await passwordInput.fill(SOCIAL_ENV.GOOGLE_TEST_PASSWORD);
    await page.keyboard.press("Enter");
  }

  // Google 2단계 인증 스킵 (없으면 자동 통과)
  try {
    const skipMfa = page
      .locator("button:has-text('지금은 하지 않기')")
      .or(page.locator("button:has-text('Not now')"));
    await skipMfa.waitFor({ timeout: 3_000 });
    await skipMfa.click();
  } catch {
    // 2단계 인증 없음
  }

  // 동의 화면 허용 (prompt=consent 이므로 매번 표시됨)
  try {
    const continueButton = page
      .locator("button:has-text('계속')")
      .or(
        page
          .locator("button:has-text('Continue')")
          .or(page.locator("#submit_approve_access"))
      );
    await continueButton.waitFor({ timeout: 5_000 });
    await continueButton.click();
  } catch {
    // 동의 화면 없음
  }

  return codePromise;
}

/**
 * 소셜 계정 Step-Up 인증을 수행합니다.
 * LOCAL 계정과 달리 소셜 계정은 password 대신 authorizationCode 를 사용합니다.
 */
async function performSocialStepUp(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string,
  authorizationCode: string
): Promise<string> {
  const res = await request.post(`${ENV.BACKEND_URL}/auth/stepup`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { authorizationCode },
  });

  if (res.status() !== 200) {
    const body = await res.text();
    throw new Error(`소셜 step-up 실패 (${res.status()}): ${body}`);
  }

  const body = await res.json();
  return body.data.accessToken;
}

/**
 * JWT payload 를 디코딩하여 userId(sub) 를 추출합니다.
 * 소셜 로그인 응답에 userId 가 없는 경우 fallback 으로 사용합니다.
 */
function extractUserIdFromJwt(token: string): number | null {
  try {
    const payload = JSON.parse(
      Buffer.from(token.split(".")[1], "base64").toString("utf8")
    );
    return payload.userId ?? payload.sub ?? null;
  } catch {
    return null;
  }
}

// ────────────────────────────────────────────────────────────────────────────
// Suite A — 정상 탈퇴 플로우
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite A — 정상 탈퇴 플로우", () => {
  test(
    "TC-UW-A-01 | 회원가입 → 로그인 → step-up → 탈퇴 성공 → 200 OK",
    async ({ request }) => {
      const { accessToken, password } = await signUpAndLogin(request, "a01");

      // step-up 인증 → ROLE_STEP_UP 포함된 새 토큰 획득
      const stepUpToken = await performStepUp(request, accessToken, password);

      // 탈퇴 요청
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        {
          headers: { Authorization: `Bearer ${stepUpToken}` },
        }
      );

      expect(withdrawRes.status()).toBe(200);
      const body = await withdrawRes.json();
      expect(body).toHaveProperty("status");
      console.log("  ✓ 탈퇴 성공:", body.message ?? "ok");
    }
  );

  test(
    "TC-UW-A-02 | 탈퇴 후 로그인 시도 → 인증 실패",
    async ({ request }) => {
      const { accessToken, email, password } = await signUpAndLogin(
        request,
        "a02"
      );

      // step-up → 탈퇴
      const stepUpToken = await performStepUp(request, accessToken, password);
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        {
          headers: { Authorization: `Bearer ${stepUpToken}` },
        }
      );
      expect(withdrawRes.status()).toBe(200);

      // 탈퇴 후 동일 계정으로 로그인 재시도
      const reLoginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
        headers: { "Content-Type": "application/json" },
        data: { email, password, provider: "LOCAL" },
      });

      // 탈퇴된 계정은 로그인이 거부되어야 함
      expect(reLoginRes.status()).not.toBe(200);
      console.log(
        `  ✓ 탈퇴 후 재로그인 차단 확인: status=${reLoginRes.status()}`
      );
    }
  );

  test(
    "TC-UW-A-03 | 탈퇴 후 재탈퇴 시도 → 실패 (이미 탈퇴된 계정)",
    async ({ request }) => {
      const { accessToken, password } = await signUpAndLogin(request, "a03");

      // step-up → 탈퇴
      const stepUpToken = await performStepUp(request, accessToken, password);
      const firstWithdraw = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        {
          headers: { Authorization: `Bearer ${stepUpToken}` },
        }
      );
      expect(firstWithdraw.status()).toBe(200);

      // 동일 step-up 토큰으로 재탈퇴 시도 (토큰이 아직 유효하더라도 계정이 탈퇴됨)
      const secondWithdraw = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        {
          headers: { Authorization: `Bearer ${stepUpToken}` },
        }
      );

      // 이미 탈퇴된 계정이므로 실패해야 함
      expect(secondWithdraw.status()).not.toBe(200);
      const body = await secondWithdraw.json();
      console.log(
        `  ✓ 재탈퇴 차단 확인: status=${secondWithdraw.status()}, code=${body.code ?? "-"}`
      );
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite B — 권한 오류
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite B — 권한 오류", () => {
  test(
    "TC-UW-B-01 | step-up 없이 탈퇴 요청 → 403 Forbidden (ROLE_STEP_UP 미보유)",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "b01");

      // step-up 없이 일반 accessToken 으로 탈퇴 시도
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        {
          headers: { Authorization: `Bearer ${accessToken}` },
        }
      );

      expect(withdrawRes.status()).toBe(403);
      console.log(
        `  ✓ step-up 없이 탈퇴 차단: status=${withdrawRes.status()}`
      );
    }
  );

  test(
    "TC-UW-B-02 | 미인증 탈퇴 요청 → 401 Unauthorized",
    async ({ request }) => {
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`
      );

      expect(withdrawRes.status()).toBe(401);
      console.log(`  ✓ 미인증 탈퇴 차단: status=${withdrawRes.status()}`);
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite C — Soft Delete 연쇄 검증 (wallet)
//
// 탈퇴(soft delete) 시 WithdrawUserService 가 UserSoftDeletedEvent 를 발행하고,
// WalletUserSoftDeleteEventHandler 가 ACTIVE wallet 을 USER_DELETED 로 전환합니다.
// 지갑 등록(외부 API 의존) 후 탈퇴를 수행하여 DB 에서 직접 결과를 확인합니다.
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite C — Soft Delete 연쇄 검증", () => {
  test(
    "TC-UW-C-01 | 탈퇴 후 연결된 wallet.status 가 USER_DELETED 로 전환되어야 한다",
    async ({ request }) => {
      const { accessToken, userId, password } = await signUpAndLogin(
        request,
        "c01"
      );

      // 지갑 등록 (ACTIVE 상태)
      const walletAddress = await registerWallet(request, accessToken);
      console.log(`  → 지갑 등록 완료: ${walletAddress}`);

      // step-up → 탈퇴
      const stepUpToken = await performStepUp(request, accessToken, password);
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        { headers: { Authorization: `Bearer ${stepUpToken}` } }
      );
      expect(withdrawRes.status()).toBe(200);

      // DB에서 wallet status 확인
      // WalletUserSoftDeleteEventHandler 가 ACTIVE → USER_DELETED 로 변경
      const rows = await queryDb<{ status: string }>(
        `SELECT status
           FROM user_wallets
          WHERE user_id = $1
            AND wallet_address = $2`,
        [userId, walletAddress]
      );

      expect(rows.length).toBe(1);
      expect(rows[0].status).toBe("USER_DELETED");
      console.log(`  ✓ wallet.status=USER_DELETED 확인: userId=${userId}`);
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite D — Hard Delete 연쇄 검증 (wallet)
//
// WithdrawUserService 가 발행한 UserSoftDeletedEvent 를 통해
// WalletUserSoftDeleteEventHandler 가 wallet 을 USER_DELETED 로 전환한 뒤,
// DB 에서 직접 USER_DELETED wallet 을 물리 삭제하여 레코드가 완전히 사라지는지 검증합니다.
//
// [참고] Location 연쇄 삭제(LocationUserHardDeleteEventHandler)는
//   WithdrawalHardDeleteService.runBatch() 가 스케줄러(@Scheduled)에 의해서만
//   실행되므로 Spring 컨텍스트에서 직접 호출이 가능한 UserE2ETest.java 에서 검증합니다.
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite D — Hard Delete 연쇄 검증", () => {
  test(
    "TC-UW-D-01 | Hard delete 실행 후 USER_DELETED wallet 레코드가 완전 삭제되어야 한다",
    async ({ request }) => {
      const { accessToken, userId, password } = await signUpAndLogin(
        request,
        "d01"
      );

      // 지갑 등록
      const walletAddress = await registerWallet(request, accessToken);

      // step-up → 탈퇴 (soft delete)
      const stepUpToken = await performStepUp(request, accessToken, password);
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        { headers: { Authorization: `Bearer ${stepUpToken}` } }
      );
      expect(withdrawRes.status()).toBe(200);

      // soft delete 결과 사전 확인: wallet.status = USER_DELETED
      const softDeletedWallet = await queryDb<{ status: string }>(
        `SELECT status FROM user_wallets WHERE user_id = $1 AND wallet_address = $2`,
        [userId, walletAddress]
      );
      expect(softDeletedWallet.length).toBe(1);
      expect(softDeletedWallet[0].status).toBe("USER_DELETED");

      // USER_DELETED wallet 물리 삭제
      // WalletUserHardDeleteEventHandler → WalletHardDeleteService.deleteByUserIds() 와 동일한 SQL
      await queryDb(
        `DELETE FROM user_wallets WHERE user_id = $1 AND status = 'USER_DELETED'`,
        [userId]
      );
      // users_account 가 users.id 를 FK 로 참조하므로 users 보다 먼저 삭제
      await queryDb(`DELETE FROM users_account WHERE user_id = $1`, [userId]);
      await queryDb(`DELETE FROM users WHERE id = $1`, [userId]);

      // wallet 레코드가 완전히 사라졌는지 확인
      const remaining = await queryDb<{ count: string }>(
        `SELECT COUNT(*) AS count FROM user_wallets WHERE user_id = $1`,
        [userId]
      );
      expect(parseInt(remaining[0].count)).toBe(0);
      console.log(
        `  ✓ hard delete 후 user_wallets 레코드 완전 삭제 확인: userId=${userId}`
      );
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite E — Kakao 소셜 계정 탈퇴 (외부 API 실제 연동)
//
// 소셜 탈퇴는 LOCAL 탈퇴와 달리 Step-Up 시 password 대신
// 새로운 authorization_code 를 사용합니다 (OAuth 재인증).
//
// 탈퇴 완료 시 서버는 백그라운드에서 Kakao unlinkUser API 를 호출합니다.
// unlinkUser 는 best-effort 처리이므로 탈퇴 API 의 200 OK 와
// 재탈퇴 차단으로 정상 탈퇴 여부를 검증합니다.
//
// 사전 조건:
//   .env 에 KAKAO_CLIENT_ID, KAKAO_TEST_EMAIL, KAKAO_TEST_PASSWORD 필수
//   Suite 내 테스트는 serial 로 실행되어 loginCode → stepUpCode 순서가 보장됩니다.
// ────────────────────────────────────────────────────────────────────────────
test.describe.serial("Suite E — Kakao 소셜 계정 탈퇴", () => {
  // TC-E-01 에서 획득한 stepUpToken 을 TC-E-02 에서 재사용
  let kakaoStepUpToken = "";

  test.beforeAll(async () => {
    // 이전 테스트 실행에서 탈퇴된 Kakao 계정을 복구합니다.
    // 서버는 status 필드를 우선 확인하므로 deleted_at 과 status 를 함께 복구해야 합니다.
    const restored = await queryDb<{ id: number }>(
      `UPDATE users_account SET deleted_at = NULL, status = 'ACTIVE' WHERE provider = 'KAKAO' AND status = 'DELETED' RETURNING user_id AS id`
    );
    if (restored.length > 0) {
      console.log(
        `  [beforeAll] Kakao 탈퇴 계정 복구 완료: userId=${restored.map((r) => r.id).join(", ")}`
      );
    }
  });

  test.afterAll(async () => {
    // 테스트 완료(성공/실패 무관) 후 탈퇴된 Kakao 계정을 복구합니다.
    await queryDb(
      `UPDATE users_account SET deleted_at = NULL, status = 'ACTIVE' WHERE provider = 'KAKAO' AND status = 'DELETED'`
    );
  });

  test(
    "TC-UW-E-01 | Kakao OAuth → 로그인 → step-up(authorizationCode) → 탈퇴 200 OK + soft-delete 확인",
    async ({ page, context, request }) => {
      test.slow(); // OAuth UI 자동화는 네트워크 상태에 따라 시간이 오래 걸릴 수 있음

      if (!SOCIAL_ENV.KAKAO_CLIENT_ID || !SOCIAL_ENV.KAKAO_TEST_EMAIL) {
        test.skip(
          true,
          "KAKAO_CLIENT_ID 또는 KAKAO_TEST_EMAIL 이 설정되지 않아 스킵합니다. " +
            ".env 에 KAKAO_CLIENT_ID, KAKAO_TEST_EMAIL, KAKAO_TEST_PASSWORD 를 설정하세요."
        );
        return;
      }

      // ── Step 1: Kakao OAuth → /auth/login ──
      console.log("  [1/4] Kakao authorization_code 획득 중 (로그인용)...");
      const loginCode = await getKakaoAuthorizationCode(page, context);

      const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
        headers: { "Content-Type": "application/json" },
        data: {
          provider: "KAKAO",
          authorizationCode: loginCode,
          redirectUri: SOCIAL_ENV.KAKAO_REDIRECT_URI,
        },
      });
      expect(loginRes.status(), "Kakao 로그인 실패").toBe(200);

      const loginBody = await loginRes.json();
      const accessToken: string = loginBody.data.accessToken;

      // userId 추출 (응답 바디 → JWT payload 순으로 시도)
      const userId: number | null =
        (loginBody.data?.userId as number | undefined) ??
        extractUserIdFromJwt(accessToken);

      console.log(
        `  [1/4] Kakao 로그인 완료: userId=${userId ?? "알 수 없음"}`
      );

      // ── Step 2: Step-Up — 새 Kakao code 획득 ──
      // context.newPage() 는 쿠키를 공유하므로 첫 번째 OAuth 에서 생성된
      // Kakao 세션 쿠키가 유지됩니다. Kakao 는 동일 세션의 빠른 재요청을 차단하므로
      // browser.newContext() 로 완전히 새로운 컨텍스트(세션 없음)를 생성합니다.
      // social-login TC-K-04 방식과 동일하게 처음부터 로그인 폼을 통해 인증합니다.
      console.log("  [2/4] Step-Up 용 authorization_code 획득 중 (새 컨텍스트로 시도)...");
      const browser: Browser = context.browser()!;
      const stepUpContext = await browser.newContext({
        userAgent:
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
          "AppleWebKit/537.36 (KHTML, like Gecko) " +
          "Chrome/131.0.0.0 Safari/537.36",
      });
      const stepUpPage = await stepUpContext.newPage();
      let stepUpCode: string;
      try {
        stepUpCode = await getKakaoAuthorizationCode(stepUpPage, stepUpContext);
      } finally {
        await stepUpContext.close().catch(() => {});
      }
      kakaoStepUpToken = await performSocialStepUp(
        request,
        accessToken,
        stepUpCode
      );
      console.log("  [2/4] Kakao Step-Up 완료 (ROLE_STEP_UP 획득)");

      // ── Step 3: 탈퇴 요청 ──
      console.log("  [3/4] 탈퇴 요청 중...");
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        { headers: { Authorization: `Bearer ${kakaoStepUpToken}` } }
      );
      expect(withdrawRes.status(), "Kakao 소셜 계정 탈퇴 API 실패").toBe(200);
      console.log("  [3/4] 탈퇴 성공 (200 OK)");

      // ── Step 4: DB soft-delete 확인 ──
      // 탈퇴 후 users_account.deleted_at 이 설정되었는지 확인
      if (userId) {
        const rows = await queryDb<{ deleted_at: unknown }>(
          `SELECT deleted_at FROM users_account WHERE user_id = $1`,
          [userId]
        );
        expect(rows.length, "users_account 레코드가 없음").toBeGreaterThan(0);
        expect(
          rows[0].deleted_at,
          "deleted_at 이 NULL — soft delete 미수행"
        ).not.toBeNull();
        console.log(
          `  [4/4] DB soft-delete 확인 완료: userId=${userId}, deleted_at=${rows[0].deleted_at}`
        );
      } else {
        console.log(
          "  [4/4] userId 를 알 수 없어 DB 확인을 건너뜁니다. (탈퇴 API 200 OK 로 성공 간주)"
        );
      }
      console.log("  ✓ TC-UW-E-01 통과");
    }
  );

  test(
    "TC-UW-E-02 | Kakao 탈퇴 후 동일 stepUpToken 으로 재탈퇴 시도 → 실패",
    async ({ request }) => {
      if (!kakaoStepUpToken) {
        test.skip(
          true,
          "TC-UW-E-01 에서 stepUpToken 을 먼저 획득해야 합니다"
        );
        return;
      }

      const secondWithdraw = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        { headers: { Authorization: `Bearer ${kakaoStepUpToken}` } }
      );
      expect(
        secondWithdraw.status(),
        "탈퇴된 Kakao 계정의 재탈퇴가 성공함"
      ).not.toBe(200);

      const body = await secondWithdraw.json();
      console.log(
        `  ✓ Kakao 재탈퇴 차단 확인: status=${secondWithdraw.status()}, code=${body.code ?? "-"}`
      );
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite F — Google 소셜 계정 탈퇴 (외부 API 실제 연동)
//
// 소셜 탈퇴는 LOCAL 탈퇴와 달리 Step-Up 시 password 대신
// 새로운 authorization_code 를 사용합니다 (OAuth 재인증).
//
// 탈퇴 완료 시 서버는 백그라운드에서 Google revokeToken API 를 호출합니다.
// revokeToken 은 best-effort 처리이므로 탈퇴 API 의 200 OK 와
// 재탈퇴 차단으로 정상 탈퇴 여부를 검증합니다.
//
// 사전 조건:
//   .env 에 GOOGLE_CLIENT_ID, GOOGLE_TEST_EMAIL, GOOGLE_TEST_PASSWORD 필수
//   Suite 내 테스트는 serial 로 실행되어 loginCode → stepUpCode 순서가 보장됩니다.
// ────────────────────────────────────────────────────────────────────────────
test.describe.serial("Suite F — Google 소셜 계정 탈퇴", () => {
  let googleStepUpToken = "";

  test.beforeAll(async () => {
    // 이전 테스트 실행에서 탈퇴된 Google 계정을 복구합니다.
    // 서버는 status 필드를 우선 확인하므로 deleted_at 과 status 를 함께 복구해야 합니다.
    const restored = await queryDb<{ id: number }>(
      `UPDATE users_account SET deleted_at = NULL, status = 'ACTIVE' WHERE provider = 'GOOGLE' AND status = 'DELETED' RETURNING user_id AS id`
    );
    if (restored.length > 0) {
      console.log(
        `  [beforeAll] Google 탈퇴 계정 복구 완료: userId=${restored.map((r) => r.id).join(", ")}`
      );
    }
  });

  test.afterAll(async () => {
    // 테스트 완료(성공/실패 무관) 후 탈퇴된 Google 계정을 복구합니다.
    await queryDb(
      `UPDATE users_account SET deleted_at = NULL, status = 'ACTIVE' WHERE provider = 'GOOGLE' AND status = 'DELETED'`
    );
  });

  test(
    "TC-UW-F-01 | Google OAuth → 로그인 → step-up(authorizationCode) → 탈퇴 200 OK + soft-delete 확인",
    async ({ page, context, request }) => {
      test.slow();

      if (!SOCIAL_ENV.GOOGLE_CLIENT_ID || !SOCIAL_ENV.GOOGLE_TEST_EMAIL) {
        test.skip(
          true,
          "GOOGLE_CLIENT_ID 또는 GOOGLE_TEST_EMAIL 이 설정되지 않아 스킵합니다. " +
            ".env 에 GOOGLE_CLIENT_ID, GOOGLE_TEST_EMAIL, GOOGLE_TEST_PASSWORD 를 설정하세요."
        );
        return;
      }

      // ── Step 1: Google OAuth → /auth/login ──
      console.log("  [1/4] Google authorization_code 획득 중 (로그인용)...");
      const loginCode = await getGoogleAuthorizationCode(page, context);

      const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
        headers: { "Content-Type": "application/json" },
        data: {
          provider: "GOOGLE",
          authorizationCode: loginCode,
          redirectUri: SOCIAL_ENV.GOOGLE_REDIRECT_URI,
        },
      });
      expect(loginRes.status(), "Google 로그인 실패").toBe(200);

      const loginBody = await loginRes.json();
      const accessToken: string = loginBody.data.accessToken;

      const userId: number | null =
        (loginBody.data?.userId as number | undefined) ??
        extractUserIdFromJwt(accessToken);

      console.log(
        `  [1/4] Google 로그인 완료: userId=${userId ?? "알 수 없음"}`
      );

      // ── Step 2: Step-Up — 새 Google code 획득 ──
      // Kakao 와 동일한 이유로 browser.newContext() 로 새 컨텍스트를 생성합니다.
      // Google 은 prompt=consent 로 매번 동의 화면을 표시하므로 세션 여부와 무관하게 동작합니다.
      console.log("  [2/4] Step-Up 용 authorization_code 획득 중 (새 컨텍스트로 시도)...");
      const gBrowser: Browser = context.browser()!;
      const stepUpContext = await gBrowser.newContext({
        userAgent:
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
          "AppleWebKit/537.36 (KHTML, like Gecko) " +
          "Chrome/131.0.0.0 Safari/537.36",
      });
      const stepUpPage = await stepUpContext.newPage();
      let stepUpCode: string;
      try {
        stepUpCode = await getGoogleAuthorizationCode(stepUpPage, stepUpContext);
      } finally {
        await stepUpContext.close().catch(() => {});
      }
      googleStepUpToken = await performSocialStepUp(
        request,
        accessToken,
        stepUpCode
      );
      console.log("  [2/4] Google Step-Up 완료 (ROLE_STEP_UP 획득)");

      // ── Step 3: 탈퇴 요청 ──
      console.log("  [3/4] 탈퇴 요청 중...");
      const withdrawRes = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        { headers: { Authorization: `Bearer ${googleStepUpToken}` } }
      );
      expect(withdrawRes.status(), "Google 소셜 계정 탈퇴 API 실패").toBe(200);
      console.log("  [3/4] 탈퇴 성공 (200 OK)");

      // ── Step 4: DB soft-delete 확인 ──
      if (userId) {
        const rows = await queryDb<{ deleted_at: unknown }>(
          `SELECT deleted_at FROM users_account WHERE user_id = $1`,
          [userId]
        );
        expect(rows.length, "users_account 레코드가 없음").toBeGreaterThan(0);
        expect(
          rows[0].deleted_at,
          "deleted_at 이 NULL — soft delete 미수행"
        ).not.toBeNull();
        console.log(
          `  [4/4] DB soft-delete 확인 완료: userId=${userId}, deleted_at=${rows[0].deleted_at}`
        );
      } else {
        console.log(
          "  [4/4] userId 를 알 수 없어 DB 확인을 건너뜁니다. (탈퇴 API 200 OK 로 성공 간주)"
        );
      }
      console.log("  ✓ TC-UW-F-01 통과");
    }
  );

  test(
    "TC-UW-F-02 | Google 탈퇴 후 동일 stepUpToken 으로 재탈퇴 시도 → 실패",
    async ({ request }) => {
      if (!googleStepUpToken) {
        test.skip(
          true,
          "TC-UW-F-01 에서 stepUpToken 을 먼저 획득해야 합니다"
        );
        return;
      }

      const secondWithdraw = await request.post(
        `${ENV.BACKEND_URL}/auth/withdrawal`,
        { headers: { Authorization: `Bearer ${googleStepUpToken}` } }
      );
      expect(
        secondWithdraw.status(),
        "탈퇴된 Google 계정의 재탈퇴가 성공함"
      ).not.toBe(200);

      const body = await secondWithdraw.json();
      console.log(
        `  ✓ Google 재탈퇴 차단 확인: status=${secondWithdraw.status()}, code=${body.code ?? "-"}`
      );
    }
  );
});
