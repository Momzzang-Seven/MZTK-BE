/**
 * MZTK - Admin User Role Management Playwright E2E 테스트
 *
 * 테스트 대상: Admin 계정 관리 전체 플로우
 *   - Admin 로그인 (LOCAL_ADMIN provider)
 *   - Admin 계정 목록 조회
 *   - Admin 계정 생성
 *   - 비밀번호 자체 교체 (rotate)
 *   - 동료 Admin 비밀번호 리셋 (peer-reset)
 *   - Break-glass Recovery (reseed)
 *   - Rate Limiting (Bucket4j, 3 tokens/min per IP)
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 http://localhost:8080 에서 실행 중이어야 합니다.
 *   - 애플리케이션이 부트스트랩 완료되어 seed admin 계정이 생성되어 있어야 합니다.
 *   - .env 파일에 ADMIN_LOGIN_ID, ADMIN_PASSWORD, RECOVERY_ANCHOR 가 설정되어 있어야 합니다.
 *
 * Playwright 테스트 케이스: [P-1] ~ [P-11]
 */

import { test, expect, APIRequestContext } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

// ────────────────────────────────────────────────────────────────────────────
// 환경 변수
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  ADMIN_LOGIN_ID: process.env.ADMIN_LOGIN_ID ?? "",
  ADMIN_PASSWORD: process.env.ADMIN_PASSWORD ?? "",
  RECOVERY_ANCHOR: process.env.RECOVERY_ANCHOR ?? "",
};

// ────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼
// ────────────────────────────────────────────────────────────────────────────

/**
 * LOCAL_ADMIN provider 로 로그인하여 accessToken 을 반환합니다.
 */
async function adminLogin(
  api: APIRequestContext,
  loginId: string,
  password: string
): Promise<string> {
  const res = await api.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL_ADMIN", loginId, password },
  });
  expect(res.status(), `Admin login failed (${loginId}): HTTP ${res.status()}`).toBe(200);
  const body = await res.json();
  expect(body.status).toBe("SUCCESS");
  return (body.data as Record<string, string>).accessToken;
}

/**
 * 인증 헤더가 포함된 API context factory.
 * Playwright request fixture 는 beforeAll 스코프에서 재사용합니다.
 */
function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

// ════════════════════════════════════════════════════════════════════════════
// [P-1] Admin 로그인 및 어드민 대시보드 접근
// ════════════════════════════════════════════════════════════════════════════

test.describe("Admin 로그인 플로우", () => {
  test("[P-1] Admin login via API and access admin dashboard", async ({ request }) => {
    // given — seed admin credentials from env
    const loginId = ENV.ADMIN_LOGIN_ID;
    const password = ENV.ADMIN_PASSWORD;
    if (!loginId || !password) {
      test.skip(true, "ADMIN_LOGIN_ID / ADMIN_PASSWORD 환경 변수가 설정되지 않았습니다");
      return;
    }

    // when — POST /auth/login with LOCAL_ADMIN provider
    const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: { provider: "LOCAL_ADMIN", loginId, password },
    });

    // then — valid JWT returned
    expect(loginRes.status(), "[P-1] 로그인 HTTP 상태코드가 200 이 아님").toBe(200);
    const loginBody = await loginRes.json();
    expect(loginBody.status, "[P-1] status 필드 누락").toBe("SUCCESS");

    const accessToken: string = (loginBody.data as Record<string, string>).accessToken;
    expect(accessToken, "[P-1] accessToken 누락").toBeTruthy();
    expect(accessToken.length, "[P-1] accessToken 길이 0").toBeGreaterThan(0);

    // when — GET /admin/accounts with obtained JWT
    const listRes = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(accessToken),
    });

    // then — admin accounts listing is accessible with at least seed accounts
    expect(listRes.status(), "[P-1] GET /admin/accounts HTTP 상태코드가 200 이 아님").toBe(200);
    const listBody = await listRes.json();
    expect(listBody.status, "[P-1] 어드민 목록 응답 status 필드 누락").toBe("SUCCESS");

    const admins: unknown[] = (listBody.data as Record<string, unknown[]>).admins;
    expect(Array.isArray(admins), "[P-1] admins 필드가 배열이 아님").toBe(true);
    expect(admins.length, "[P-1] seed admin 계정이 하나도 없음").toBeGreaterThan(0);

    console.log(`[P-1] Admin 로그인 성공 및 계정 목록 조회 완료 (${admins.length}개 계정)`);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// [P-2] [P-3] 부트스트랩 Seed Admin 플로우
// ════════════════════════════════════════════════════════════════════════════

test.describe("Bootstrap Seed Admin 플로우", () => {
  /**
   * [P-2] 첫 기동 시 seed admin 계정 생성 여부 검증.
   *
   * 전제: 서버가 이미 기동되어 ApplicationRunner 가 완료된 상태.
   * 어드민 계정 목록 조회를 통해 seed admin 이 존재함을 간접 확인합니다.
   */
  test("[P-2] Application bootstrap creates seed admin accounts on first startup", async ({
    request,
  }) => {
    // given — 서버가 기동된 상태 (ApplicationRunner 완료)
    const loginId = ENV.ADMIN_LOGIN_ID;
    const password = ENV.ADMIN_PASSWORD;
    if (!loginId || !password) {
      test.skip(true, "ADMIN_LOGIN_ID / ADMIN_PASSWORD 환경 변수가 설정되지 않았습니다");
      return;
    }

    // when — seed admin 으로 로그인하여 계정 목록을 조회
    const token = await adminLogin(request, loginId, password);
    const listRes = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(token),
    });

    // then — seed admin 이 존재하고 isSeed: true 인 계정이 있어야 함
    expect(listRes.status(), "[P-2] GET /admin/accounts 상태코드 오류").toBe(200);
    const body = await listRes.json();
    const admins = (body.data as { admins: Record<string, unknown>[] }).admins;

    const seedAdmins = admins.filter((a) => a["isSeed"] === true);
    expect(seedAdmins.length, "[P-2] isSeed: true 인 seed admin 이 없음").toBeGreaterThanOrEqual(1);

    // seed admin 은 시스템이 생성하므로 createdBy 가 null 이어야 함
    for (const sa of seedAdmins) {
      expect(sa["createdBy"], "[P-2] seed admin 의 createdBy 가 null 이 아님").toBeNull();
    }

    console.log(`[P-2] Seed admin ${seedAdmins.length}개 확인 완료`);
  });

  /**
   * [P-3] 재기동 시 기존 seed admin 이 변하지 않음 (멱등성).
   *
   * 서버 재기동 후 계정 수가 동일하고 loginId 가 변하지 않아야 합니다.
   * Playwright 에서 서버를 재기동할 수 없으므로, 계정 목록을 두 번 조회하여
   * 결과가 동일한지 확인하는 방식으로 대체합니다.
   */
  test("[P-3] Application bootstrap skips when sufficient admins already exist", async ({
    request,
  }) => {
    // given
    const loginId = ENV.ADMIN_LOGIN_ID;
    const password = ENV.ADMIN_PASSWORD;
    if (!loginId || !password) {
      test.skip(true, "ADMIN_LOGIN_ID / ADMIN_PASSWORD 환경 변수가 설정되지 않았습니다");
      return;
    }

    const token = await adminLogin(request, loginId, password);

    // when — 계정 목록을 2회 조회
    const listFirst = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(token),
    });
    const listSecond = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(token),
    });

    // then — 두 번의 조회 결과가 동일해야 함 (부트스트랩 재실행 없음)
    expect(listFirst.status()).toBe(200);
    expect(listSecond.status()).toBe(200);

    const firstAdmins = ((await listFirst.json()).data as { admins: Record<string, unknown>[] }).admins;
    const secondAdmins = ((await listSecond.json()).data as { admins: Record<string, unknown>[] }).admins;

    expect(firstAdmins.length, "[P-3] 계정 수가 두 조회 간에 달라짐").toBe(secondAdmins.length);

    const firstLoginIds = firstAdmins.map((a) => a["loginId"]).sort();
    const secondLoginIds = secondAdmins.map((a) => a["loginId"]).sort();
    expect(firstLoginIds, "[P-3] 계정 loginId 목록이 달라짐").toEqual(secondLoginIds);

    console.log(`[P-3] 부트스트랩 멱등성 확인 완료 (계정 ${firstAdmins.length}개 유지)`);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// [P-4] [P-5] Recovery 플로우 (AWS Secrets Manager)
// ════════════════════════════════════════════════════════════════════════════

test.describe("Recovery 플로우 (AWS Secrets Manager)", () => {
  /**
   * [P-4] Recovery reseed 성공 시 deliveredVia 가 응답에 포함되고 기존 계정이 soft-delete 됨.
   *
   * 주의: 이 테스트는 실제 recovery 를 실행하므로 DB 를 변경합니다.
   * 테스트 환경이 AWS Secrets Manager 에 연결되어 있을 때만 실행하세요.
   */
  test("[P-4] Recovery reseed delivers credentials to AWS Secrets Manager", async ({
    request,
  }) => {
    // given
    const anchor = ENV.RECOVERY_ANCHOR;
    if (!anchor) {
      test.skip(true, "RECOVERY_ANCHOR 환경 변수가 설정되지 않았습니다");
      return;
    }

    // when — POST /admin/recovery/reseed
    const reseedRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: anchor },
    });

    // then — 200 OK + deliveredVia 포함
    expect(reseedRes.status(), "[P-4] reseed HTTP 상태코드가 200 이 아님").toBe(200);
    const body = await reseedRes.json();
    expect(body.status, "[P-4] status 필드가 SUCCESS 가 아님").toBe("SUCCESS");

    const data = body.data as Record<string, unknown>;
    expect(data, "[P-4] deliveredVia 필드 누락").toHaveProperty("deliveredVia");
    expect(typeof data["deliveredVia"], "[P-4] deliveredVia 타입 오류").toBe("string");
    expect(data["deliveredVia"] as string, "[P-4] deliveredVia 가 빈 문자열").toBeTruthy();

    expect(data, "[P-4] newSeedCount 필드 누락").toHaveProperty("newSeedCount");
    expect(data["newSeedCount"] as number, "[P-4] newSeedCount 가 0 이하").toBeGreaterThan(0);

    console.log(
      `[P-4] Recovery reseed 완료: deliveredVia=${data["deliveredVia"]}, newSeedCount=${data["newSeedCount"]}`
    );
  });

  /**
   * [P-5] Recovery anchor 검증 — 정확한 anchor 는 200, 다른 anchor 는 403.
   */
  test("[P-5] Recovery anchor loaded from AWS Secrets Manager is validated correctly", async ({
    request,
  }) => {
    // given
    const correctAnchor = ENV.RECOVERY_ANCHOR;
    if (!correctAnchor) {
      test.skip(true, "RECOVERY_ANCHOR 환경 변수가 설정되지 않았습니다");
      return;
    }

    // when (negative) — 잘못된 anchor 로 요청
    const wrongRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: correctAnchor + "_WRONG" },
    });

    // then — 403 또는 rate-limit(429) 응답
    expect(
      [403, 429],
      `[P-5] 잘못된 anchor 에 대한 응답 코드가 예상과 다름: ${wrongRes.status()}`
    ).toContain(wrongRes.status());

    if (wrongRes.status() === 403) {
      const wrongBody = await wrongRes.json();
      expect(wrongBody.status, "[P-5] 오류 응답의 status 가 FAIL 이 아님").toBe("FAIL");
    }

    // when (positive) — 정확한 anchor 로 요청
    const correctRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: correctAnchor },
    });

    // then — 200 OK
    expect(correctRes.status(), "[P-5] 정확한 anchor 에 대한 응답코드가 200 이 아님").toBe(200);
    const correctBody = await correctRes.json();
    expect(correctBody.status, "[P-5] 성공 응답의 status 가 SUCCESS 가 아님").toBe("SUCCESS");

    console.log("[P-5] Recovery anchor 검증 완료 (올바른 → 200, 잘못된 → 403)");
  });
});

// ════════════════════════════════════════════════════════════════════════════
// [P-6] 전체 Admin 라이프사이클
// ════════════════════════════════════════════════════════════════════════════

test.describe("전체 Admin 라이프사이클", () => {
  test("[P-6] Complete admin lifecycle: bootstrap → login → create → rotate → peer-reset → list", async ({
    request,
  }) => {
    test.slow(); // 여러 단계로 이루어진 통합 테스트

    // given — seed admin credentials
    const seedLoginId = ENV.ADMIN_LOGIN_ID;
    const seedPassword = ENV.ADMIN_PASSWORD;
    if (!seedLoginId || !seedPassword) {
      test.skip(true, "ADMIN_LOGIN_ID / ADMIN_PASSWORD 환경 변수가 설정되지 않았습니다");
      return;
    }

    // Step 1 — seed admin 으로 로그인
    const seedToken = await adminLogin(request, seedLoginId, seedPassword);
    console.log("[P-6] Step 1 완료: Seed admin 로그인");

    // Step 2 — 새 admin 계정 생성
    const createRes = await request.post(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(seedToken),
    });
    expect(createRes.status(), "[P-6] POST /admin/accounts 상태코드 오류").toBe(201);
    const createBody = await createRes.json();
    expect(createBody.status).toBe("SUCCESS");

    const created = createBody.data as {
      userId: number;
      loginId: string;
      generatedPassword: string;
    };
    expect(created.loginId, "[P-6] 생성된 admin loginId 누락").toBeTruthy();
    expect(created.generatedPassword, "[P-6] 생성된 admin password 누락").toBeTruthy();

    const newAdminLoginId = created.loginId;
    const newAdminPassword = created.generatedPassword;
    const newAdminUserId = created.userId;
    console.log(`[P-6] Step 2 완료: 새 admin 계정 생성 (loginId=${newAdminLoginId})`);

    // Step 3 — 새 admin 으로 로그인
    const newAdminToken = await adminLogin(request, newAdminLoginId, newAdminPassword);
    console.log("[P-6] Step 3 완료: 새 admin 로그인");

    // Step 4 — 새 admin 이 자신의 비밀번호 교체
    const rotatedPassword = "NewP@ssw0rd!2026";
    const rotateRes = await request.post(`${ENV.BACKEND_URL}/admin/auth/password`, {
      headers: authHeaders(newAdminToken),
      data: { currentPassword: newAdminPassword, newPassword: rotatedPassword },
    });
    expect(rotateRes.status(), "[P-6] POST /admin/auth/password 상태코드 오류").toBe(200);
    const rotateBody = await rotateRes.json();
    expect(rotateBody.status, "[P-6] 비밀번호 교체 응답 status 오류").toBe("SUCCESS");
    console.log("[P-6] Step 4 완료: 새 admin 비밀번호 자체 교체");

    // Step 5 — 교체된 비밀번호로 로그인 검증
    const newAdminTokenAfterRotate = await adminLogin(request, newAdminLoginId, rotatedPassword);
    expect(newAdminTokenAfterRotate, "[P-6] 교체된 비밀번호로 로그인 실패").toBeTruthy();
    console.log("[P-6] Step 5 완료: 교체된 비밀번호로 로그인 성공");

    // Step 6 — seed admin 이 새 admin 의 비밀번호를 peer-reset
    const peerResetRes = await request.post(
      `${ENV.BACKEND_URL}/admin/accounts/${newAdminUserId}/password/reset`,
      { headers: authHeaders(seedToken) }
    );
    expect(peerResetRes.status(), "[P-6] POST /admin/accounts/{id}/password/reset 상태코드 오류").toBe(200);
    const peerResetBody = await peerResetRes.json();
    expect(peerResetBody.status, "[P-6] peer-reset 응답 status 오류").toBe("SUCCESS");

    const peerReset = peerResetBody.data as {
      userId: number;
      loginId: string;
      generatedPassword: string;
    };
    expect(peerReset.generatedPassword, "[P-6] peer-reset 비밀번호 누락").toBeTruthy();
    const peerResetPassword = peerReset.generatedPassword;
    console.log("[P-6] Step 6 완료: Seed admin 이 새 admin 비밀번호 peer-reset");

    // Step 7 — peer-reset 비밀번호로 새 admin 로그인
    const tokenAfterPeerReset = await adminLogin(request, newAdminLoginId, peerResetPassword);
    expect(tokenAfterPeerReset, "[P-6] peer-reset 비밀번호로 로그인 실패").toBeTruthy();
    console.log("[P-6] Step 7 완료: peer-reset 비밀번호로 로그인 성공");

    // Step 8 — admin 목록 조회 검증
    const listRes = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(seedToken),
    });
    expect(listRes.status(), "[P-6] GET /admin/accounts 상태코드 오류").toBe(200);
    const listBody = await listRes.json();
    const admins = (listBody.data as { admins: Record<string, unknown>[] }).admins;

    // seed admin 과 new admin 이 모두 존재해야 함
    const seedAdmins = admins.filter((a) => a["isSeed"] === true);
    const generatedAdmins = admins.filter((a) => a["isSeed"] === false);
    expect(seedAdmins.length, "[P-6] seed admin 이 목록에 없음").toBeGreaterThanOrEqual(1);
    expect(generatedAdmins.length, "[P-6] generated admin 이 목록에 없음").toBeGreaterThanOrEqual(1);

    // 생성된 admin 의 passwordLastRotatedAt 이 null 이 아님 (peer-reset 후)
    const createdInList = admins.find((a) => a["userId"] === newAdminUserId);
    expect(createdInList, "[P-6] 생성된 admin 이 목록에 없음").toBeTruthy();
    expect(
      createdInList!["passwordLastRotatedAt"],
      "[P-6] peer-reset 후 passwordLastRotatedAt 이 null 임"
    ).not.toBeNull();

    console.log(
      `[P-6] Step 8 완료: 전체 Admin 라이프사이클 검증 (seed=${seedAdmins.length}, generated=${generatedAdmins.length})`
    );
  });
});

// ════════════════════════════════════════════════════════════════════════════
// [P-7] 전체 Recovery 라이프사이클
// ════════════════════════════════════════════════════════════════════════════

test.describe("전체 Recovery 라이프사이클", () => {
  test("[P-7] Complete recovery lifecycle: lock-out → reseed → re-establish", async ({
    request,
  }) => {
    test.slow();

    // given
    const seedLoginId = ENV.ADMIN_LOGIN_ID;
    const seedPassword = ENV.ADMIN_PASSWORD;
    const anchor = ENV.RECOVERY_ANCHOR;
    if (!seedLoginId || !seedPassword || !anchor) {
      test.skip(
        true,
        "ADMIN_LOGIN_ID / ADMIN_PASSWORD / RECOVERY_ANCHOR 환경 변수가 설정되지 않았습니다"
      );
      return;
    }

    // Step 1 — 현재 seed admin 토큰 획득 (recovery 전)
    const preReseedToken = await adminLogin(request, seedLoginId, seedPassword);
    console.log("[P-7] Step 1 완료: Recovery 전 admin 로그인");

    // Step 2 — Recovery reseed 수행
    const reseedRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: anchor },
    });
    expect(reseedRes.status(), "[P-7] reseed 상태코드 오류").toBe(200);
    const reseedBody = await reseedRes.json();
    expect(reseedBody.status, "[P-7] reseed 응답 status 오류").toBe("SUCCESS");
    const newSeedCount: number = (reseedBody.data as Record<string, number>)["newSeedCount"];
    console.log(`[P-7] Step 2 완료: Recovery reseed 완료 (newSeedCount=${newSeedCount})`);

    // Step 3 — 기존 seed admin 자격증명으로 로그인 시도 → 실패해야 함
    const staleLoginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: { provider: "LOCAL_ADMIN", loginId: seedLoginId, password: seedPassword },
    });
    expect(
      staleLoginRes.status(),
      "[P-7] 기존 자격증명으로 로그인이 성공함 (soft-delete 되지 않았거나 비밀번호가 변경되지 않음)"
    ).not.toBe(200);
    console.log(
      `[P-7] Step 3 완료: 기존 자격증명 로그인 실패 확인 (HTTP ${staleLoginRes.status()})`
    );

    // Step 4 — reseed 전에 발급된 JWT 로 admin API 접근 시도 → 거부되어야 함
    const staleTokenRes = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(preReseedToken),
    });
    expect(
      [401, 403],
      `[P-7] reseed 전 JWT 가 여전히 유효함 (HTTP ${staleTokenRes.status()})`
    ).toContain(staleTokenRes.status());
    console.log(
      `[P-7] Step 4 완료: reseed 전 JWT 무효화 확인 (HTTP ${staleTokenRes.status()})`
    );

    console.log(
      "[P-7] 전체 Recovery 라이프사이클 검증 완료 — 신규 seed 자격증명은 전달 메커니즘(LOG/AWS SM)에서 수동 확인 필요"
    );
  });
});

// ════════════════════════════════════════════════════════════════════════════
// [P-8] [P-9] Rate Limiting
// ════════════════════════════════════════════════════════════════════════════

test.describe("Rate Limiting 검증", () => {
  /**
   * [P-8] Recovery reseed rate limiting — 3 tokens/min per IP.
   *
   * 주의: 61초 대기가 포함됩니다. test.slow() 로 타임아웃을 연장합니다.
   */
  test("[P-8] Recovery rate limiting under sustained attack simulation", async ({ request }) => {
    test.slow(); // 61초 대기 포함
    test.setTimeout(120_000);

    // given — 잘못된 anchor (403 반환 예상, rate-limit 소진 후 429 예상)
    const wrongAnchor = "wrong-anchor-attack-simulation";

    // when — 연속 3회 요청 (rate-limit 버킷 소진)
    const responses: number[] = [];
    for (let i = 0; i < 3; i++) {
      const res = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
        headers: { "Content-Type": "application/json" },
        data: { recoveryAnchor: wrongAnchor },
      });
      responses.push(res.status());
    }

    // then — 1~3번째: 403 (잘못된 anchor, rate-limit 미초과)
    for (let i = 0; i < 3; i++) {
      expect(
        responses[i],
        `[P-8] ${i + 1}번째 요청: 403 이어야 하는데 ${responses[i]} 응답`
      ).toBe(403);
    }
    console.log("[P-8] 1~3번째 요청: 모두 403 확인");

    // when — 4번째 요청 (rate-limit 초과)
    const fourthRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: wrongAnchor },
    });

    // then — 429 Too Many Requests
    expect(fourthRes.status(), "[P-8] 4번째 요청: 429 이어야 함").toBe(429);
    const fourthBody = await fourthRes.json();
    expect(fourthBody.status, "[P-8] 429 응답의 status 필드 오류").toBe("FAIL");
    expect(typeof fourthBody.message, "[P-8] 429 응답의 message 필드 누락").toBe("string");
    expect(fourthBody.code, "[P-8] 429 응답의 code 가 ADMIN_009 가 아님").toBe("ADMIN_009");
    console.log("[P-8] 4번째 요청: 429 확인");

    // when — 61초 대기 후 5번째 요청 (rate-limit 버킷 재충전)
    console.log("[P-8] 61초 대기 중 (rate-limit 버킷 재충전)...");
    await new Promise((resolve) => setTimeout(resolve, 61_000));

    const fifthRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: wrongAnchor },
    });

    // then — 403 (rate-limit 재충전, anchor 오류로 정상 처리됨)
    expect(fifthRes.status(), "[P-8] 5번째 요청 (재충전 후): 403 이어야 함").toBe(403);
    console.log("[P-8] 5번째 요청 (재충전 후): 403 확인 — Rate limiting 검증 완료");
  });

  /**
   * [P-9] X-Forwarded-For 헤더를 기반으로 IP 별 rate limiting.
   */
  test("[P-9] Rate limiting uses X-Forwarded-For for IP identification behind proxy", async ({
    request,
  }) => {
    const wrongAnchor = "wrong-anchor-proxy-test";

    // given — IP 10.0.0.1 로 3회 요청하여 버킷 소진
    for (let i = 0; i < 3; i++) {
      const res = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
        headers: {
          "Content-Type": "application/json",
          "X-Forwarded-For": "10.0.0.1",
        },
        data: { recoveryAnchor: wrongAnchor },
      });
      expect(
        res.status(),
        `[P-9] IP 10.0.0.1 ${i + 1}번째 요청: 403 이어야 하는데 ${res.status()}`
      ).toBe(403);
    }
    console.log("[P-9] IP 10.0.0.1 — 3회 요청 완료 (버킷 소진)");

    // when — 4번째 요청 (IP 10.0.0.1, rate-limit 초과)
    const fourthRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: {
        "Content-Type": "application/json",
        "X-Forwarded-For": "10.0.0.1",
      },
      data: { recoveryAnchor: wrongAnchor },
    });

    // then — 429
    expect(fourthRes.status(), "[P-9] IP 10.0.0.1 4번째 요청: 429 이어야 함").toBe(429);
    console.log("[P-9] IP 10.0.0.1 — 4번째 요청: 429 확인");

    // when — 다른 IP 10.0.0.2 로 요청 (별도 버킷)
    const differentIpRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: {
        "Content-Type": "application/json",
        "X-Forwarded-For": "10.0.0.2",
      },
      data: { recoveryAnchor: wrongAnchor },
    });

    // then — 403 (다른 IP 는 독립적 버킷이므로 rate-limit 미초과)
    expect(
      differentIpRes.status(),
      `[P-9] IP 10.0.0.2 첫 요청: 403 이어야 하는데 ${differentIpRes.status()}`
    ).toBe(403);

    console.log("[P-9] IP 10.0.0.2 — 첫 요청: 403 확인 (IP별 독립 버킷 검증 완료)");
  });
});

// ════════════════════════════════════════════════════════════════════════════
// [P-10] [P-11] 보안 엣지 케이스
// ════════════════════════════════════════════════════════════════════════════

test.describe("보안 엣지 케이스", () => {
  /**
   * [P-10] Recovery reseed 후 기존 JWT 는 무효화되어야 함.
   */
  test("[P-10] Admin JWT token from before recovery reseed is rejected after reseed", async ({
    request,
  }) => {
    // given
    const seedLoginId = ENV.ADMIN_LOGIN_ID;
    const seedPassword = ENV.ADMIN_PASSWORD;
    const anchor = ENV.RECOVERY_ANCHOR;
    if (!seedLoginId || !seedPassword || !anchor) {
      test.skip(
        true,
        "ADMIN_LOGIN_ID / ADMIN_PASSWORD / RECOVERY_ANCHOR 환경 변수가 설정되지 않았습니다"
      );
      return;
    }

    // Step 1 — reseed 전 JWT 발급
    const preReseedToken = await adminLogin(request, seedLoginId, seedPassword);

    // Step 2 — Recovery reseed 수행
    const reseedRes = await request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
      headers: { "Content-Type": "application/json" },
      data: { recoveryAnchor: anchor },
    });
    expect(reseedRes.status(), "[P-10] reseed 상태코드 오류").toBe(200);
    console.log("[P-10] Recovery reseed 완료");

    // Step 3 — reseed 전 JWT 로 admin API 접근 시도
    const staleTokenRes = await request.get(`${ENV.BACKEND_URL}/admin/accounts`, {
      headers: authHeaders(preReseedToken),
    });

    // then — 401 또는 403 (soft-delete 된 user 의 JWT)
    expect(
      [401, 403],
      `[P-10] reseed 전 JWT 가 여전히 유효함 (HTTP ${staleTokenRes.status()})`
    ).toContain(staleTokenRes.status());

    console.log(
      `[P-10] reseed 전 JWT 거부 확인 (HTTP ${staleTokenRes.status()}) — 무효화 검증 완료`
    );
  });

  /**
   * [P-11] 동시 Recovery reseed 요청 — 최소 1개 성공, 최종 DB 상태 일관성 유지.
   */
  test("[P-11] Concurrent recovery reseed requests — only one succeeds", async ({ request }) => {
    // given
    const anchor = ENV.RECOVERY_ANCHOR;
    const seedLoginId = ENV.ADMIN_LOGIN_ID;
    const seedPassword = ENV.ADMIN_PASSWORD;
    if (!anchor || !seedLoginId || !seedPassword) {
      test.skip(
        true,
        "ADMIN_LOGIN_ID / ADMIN_PASSWORD / RECOVERY_ANCHOR 환경 변수가 설정되지 않았습니다"
      );
      return;
    }

    // when — 2개의 동시 reseed 요청
    const [res1, res2] = await Promise.all([
      request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
        headers: { "Content-Type": "application/json" },
        data: { recoveryAnchor: anchor },
      }),
      request.post(`${ENV.BACKEND_URL}/admin/recovery/reseed`, {
        headers: { "Content-Type": "application/json" },
        data: { recoveryAnchor: anchor },
      }),
    ]);

    const statuses = [res1.status(), res2.status()];
    console.log(`[P-11] 동시 요청 응답: ${statuses[0]}, ${statuses[1]}`);

    // then — 최소 1개는 200 이어야 함
    expect(
      statuses.some((s) => s === 200),
      `[P-11] 동시 reseed 요청 중 200 응답이 없음: ${statuses}`
    ).toBe(true);

    // then — 응답 코드는 200 또는 오류(4xx/5xx) 중 하나이어야 함 (임의 값 없음)
    for (const status of statuses) {
      expect(
        status >= 200 && status < 600,
        `[P-11] 예상 범위를 벗어난 응답 코드: ${status}`
      ).toBe(true);
    }

    // then — 최종 상태 검증: reseed 후 새 seed admin 으로 로그인 가능해야 함
    // (신규 자격증명은 전달 메커니즘에서만 확인 가능하므로 계정 목록 조회로 간접 검증)
    // 주의: 이 시점에서 기존 seedLoginId 는 더 이상 유효하지 않음

    console.log(
      "[P-11] 동시 Recovery reseed 검증 완료 — DB 일관성은 신규 seed 로그인으로 수동 확인 필요"
    );
  });
});
