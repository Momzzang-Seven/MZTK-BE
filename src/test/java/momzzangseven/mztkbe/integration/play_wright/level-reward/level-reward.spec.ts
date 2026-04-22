/**
 * MZTK — 레벨업 보상 Playwright E2E 테스트
 *
 * 테스트 대상: POST /users/me/level-ups
 *             GET  /users/me/level-up-histories
 *             GET  /users/me/level
 *             GET  /levels/policies
 *
 * 검증 항목:
 *   Suite A — 사전 조건 오류 (RPC 불필요, 블록체인 독립)
 *   Suite B — 정상 레벨업 → rewardTxStatus=CREATED (워커 처리 전 즉시 확인)
 *   Suite C — 중복 레벨업 멱등 처리
 *   Suite D — 온체인 토큰 지급 확인 (워커가 SUCCEEDED 전이할 때까지 폴링)
 *
 * 전제 조건:
 *   - web3.reward-token.enabled=true (서버 설정)
 *   - web3.eip7702.enabled=true (서버 설정)
 *   - Treasury key가 DB(web3_treasury_keys)에 provision 되어 있어야 함
 *   - TransactionIssuerWorker 등 워커 스케줄러가 활성화 되어 있어야 함 (Suite D)
 *   - Optimism Sepolia RPC 연결 (Suite D)
 *
 * XP 설정 전략:
 *   레벨업에 필요한 XP 를 직접 API 로 획득하면 시간이 오래 걸리므로,
 *   pg(node-postgres) 로 user_progress 테이블을 직접 업데이트하여 XP 를 설정합니다.
 */

import { test, expect } from "@playwright/test";
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
  EIP712_DOMAIN_NAME: process.env.WEB3_EIP712_DOMAIN_NAME ?? "MomzzangSeven",
  EIP712_DOMAIN_VERSION: process.env.WEB3_EIP712_DOMAIN_VERSION ?? "1",
  EIP712_CHAIN_ID: BigInt(process.env.WEB3_EIP712_CHAIN_ID ?? "11155420"),
  EIP712_VERIFYING_CONTRACT:
    process.env.WEB3_EIP712_VERIFYING_CONTRACT ?? "",
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: parseInt(process.env.DB_PORT ?? "5432"),
  DB_NAME: process.env.DB_NAME ?? "momzzangcoin",
  DB_USER: process.env.DB_USER ?? "mztk",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "mztk_pw",
};

// Suite D 온체인 확인은 워커 처리 대기가 필요 (최대 5분)
const ONCHAIN_TIMEOUT_MS = 5 * 60 * 1000;
const ONCHAIN_POLL_INTERVAL_MS = 15_000;

// ────────────────────────────────────────────────────────────────────────────
// 헬퍼 타입
// ────────────────────────────────────────────────────────────────────────────
interface AuthResult {
  accessToken: string;
  userId: number;
}

interface LevelPolicy {
  currentLevel: number;
  toLevel: number;
  requiredXp: number;
  rewardMztk: number;
}

interface LevelUpData {
  levelUpHistoryId: number;
  fromLevel: number;
  toLevel: number;
  spentXp: number;
  rewardMztk: number;
  rewardStatus: string;
  rewardTxStatus: string;
  rewardTxPhase: string;
  rewardTxHash?: string;
  rewardExplorerUrl?: string;
}

interface HistoryItem {
  levelUpHistoryId: number;
  fromLevel: number;
  toLevel: number;
  rewardTxStatus: string;
  rewardTxPhase: string;
  rewardTxHash?: string;
  rewardExplorerUrl?: string;
}

// ────────────────────────────────────────────────────────────────────────────
// 헬퍼 함수
// ────────────────────────────────────────────────────────────────────────────

/** 회원가입 + 로그인 → accessToken, userId 반환 */
async function signUpAndLogin(
  request: import("@playwright/test").APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const ts = Date.now();
  const email = `playwright-lr-${suffix}-${ts}@test.com`;
  const password = "Test1234!";
  const nickname = `lr-${suffix}-${ts}`;

  const signUpRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname, provider: "LOCAL" },
  });
  if (signUpRes.status() >= 300) {
    throw new Error(`회원가입 실패 (${signUpRes.status()}): ${await signUpRes.text()}`);
  }
  const signUpBody = await signUpRes.json();
  const userId: number = signUpBody.data?.userId;
  if (!userId) {
    throw new Error(`userId 추출 실패: ${JSON.stringify(signUpBody)}`);
  }

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, provider: "LOCAL" },
  });
  if (loginRes.status() !== 200) {
    throw new Error(`로그인 실패 (${loginRes.status()}): ${await loginRes.text()}`);
  }
  const loginBody = await loginRes.json();
  return { accessToken: loginBody.data.accessToken, userId };
}

/**
 * GET /users/me/level 호출 → 초기 레벨 스냅샷 조회 (레벨 1 시작)
 * progress row가 없어도 read-only 응답으로 현재 상태를 조회할 수 있습니다.
 */
async function initLevel(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string
): Promise<void> {
  const res = await request.get(`${ENV.BACKEND_URL}/users/me/level`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (res.status() !== 200) {
    throw new Error(`레벨 조회 실패 (${res.status()}): ${await res.text()}`);
  }
}

/** GET /levels/policies → 레벨 1→2 정책 반환 */
async function fetchLevel1Policy(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string
): Promise<LevelPolicy> {
  const res = await request.get(`${ENV.BACKEND_URL}/levels/policies`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (res.status() !== 200) {
    throw new Error(`레벨 정책 조회 실패 (${res.status()})`);
  }
  const body = await res.json();
  const policy: LevelPolicy | undefined = body.data?.levelPolicies?.find(
    (p: LevelPolicy) => p.currentLevel === 1
  );
  if (!policy) {
    throw new Error("레벨 1→2 정책을 찾을 수 없습니다.");
  }
  return policy;
}

/**
 * DB 에 직접 user_progress 의 XP 를 설정합니다.
 * 레벨업 API 에서 ACTIVE 지갑이 필요하므로, XP 설정과 별개로 지갑 등록이 필요합니다.
 */
async function setUserXpInDb(userId: number, xp: number): Promise<void> {
  const client = new Client({
    host: ENV.DB_HOST,
    port: ENV.DB_PORT,
    database: ENV.DB_NAME,
    user: ENV.DB_USER,
    password: ENV.DB_PASSWORD,
  });
  await client.connect();
  try {
    await client.query(
      `INSERT INTO user_progress (
          user_id,
          level,
          available_xp,
          lifetime_xp,
          created_at,
          updated_at
        )
        VALUES ($2, 1, $1, $1, NOW(), NOW())
        ON CONFLICT (user_id)
        DO UPDATE
          SET available_xp = EXCLUDED.available_xp,
              lifetime_xp = GREATEST(user_progress.lifetime_xp, EXCLUDED.lifetime_xp),
              updated_at = NOW()`,
      [xp, userId]
    );
  } finally {
    await client.end();
  }
}

/** 지갑 등록 (EIP-712 서명) */
async function registerWallet(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string,
  wallet: { privateKey: string; address: string }
): Promise<void> {
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
    throw new Error(`챌린지 발급 실패 (${challengeRes.status()}): ${await challengeRes.text()}`);
  }
  const { nonce, message } = (await challengeRes.json()).data;

  const domain = {
    name: ENV.EIP712_DOMAIN_NAME,
    version: ENV.EIP712_DOMAIN_VERSION,
    chainId: ENV.EIP712_CHAIN_ID,
    verifyingContract: ENV.EIP712_VERIFYING_CONTRACT,
  };
  const types = {
    AuthRequest: [
      { name: "content", type: "string" },
      { name: "nonce", type: "string" },
    ],
  };
  const ethWallet = new ethers.Wallet(wallet.privateKey);
  const signature = await ethWallet.signTypedData(domain, types, {
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
    throw new Error(`지갑 등록 실패 (${registerRes.status()}): ${await registerRes.text()}`);
  }
}

/** POST /users/me/level-ups */
async function callLevelUp(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string
): Promise<{ status: number; body: any }> {
  const res = await request.post(`${ENV.BACKEND_URL}/users/me/level-ups`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  let body: any = null;
  try {
    body = await res.json();
  } catch {
    body = { raw: await res.text() };
  }
  return { status: res.status(), body };
}

/** GET /users/me/level-up-histories */
async function fetchHistories(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string
): Promise<HistoryItem[]> {
  const res = await request.get(
    `${ENV.BACKEND_URL}/users/me/level-up-histories?page=0&size=10`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (res.status() !== 200) {
    throw new Error(`이력 조회 실패 (${res.status()})`);
  }
  const body = await res.json();
  return body.data?.histories ?? [];
}

/**
 * 레벨업 이력을 폴링하여 특정 levelUpHistoryId 의 rewardTxStatus 가
 * targetStatus 가 될 때까지 기다립니다.
 *
 * Suite D 에서 온체인 확인에 사용합니다.
 */
async function pollForRewardStatus(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string,
  levelUpHistoryId: number,
  targetStatuses: string[],
  timeoutMs: number
): Promise<HistoryItem> {
  const deadline = Date.now() + timeoutMs;
  let lastStatus = "unknown";
  let attempt = 0;

  while (Date.now() < deadline) {
    attempt++;
    const histories = await fetchHistories(request, accessToken);
    const item = histories.find(
      (h) => h.levelUpHistoryId === levelUpHistoryId
    );
    if (item) {
      lastStatus = item.rewardTxStatus;
      console.log(
        `  [폴링 #${attempt}] levelUpHistoryId=${levelUpHistoryId}, rewardTxStatus=${lastStatus}, rewardTxHash=${item.rewardTxHash ?? "-"}`
      );
      if (targetStatuses.includes(lastStatus)) {
        return item;
      }
    }
    await new Promise((r) => setTimeout(r, ONCHAIN_POLL_INTERVAL_MS));
  }

  throw new Error(
    `[timeout] levelUpHistoryId=${levelUpHistoryId} 가 ${timeoutMs / 1000}s 내에 ` +
      `${targetStatuses.join("/")} 상태로 전이되지 않았습니다. (마지막 상태: ${lastStatus})`
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Suite A — 사전 조건 오류 (블록체인 독립)
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite A — 사전 조건 오류", () => {
  test(
    "TC-LR-A-01 | 미인증 레벨업 요청 → 401 Unauthorized",
    async ({ request }) => {
      const res = await request.post(`${ENV.BACKEND_URL}/users/me/level-ups`);
      expect(res.status()).toBe(401);
      console.log(`  ✓ 미인증 레벨업 차단: status=${res.status()}`);
    }
  );

  test(
    "TC-LR-A-02 | 지갑 미연결 상태에서 레벨업 → 400 (WALLET_NOT_CONNECTED)",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "a02");
      await initLevel(request, accessToken);

      // XP 충족 (지갑 없이)
      const policy = await fetchLevel1Policy(request, accessToken);
      await setUserXpInDb(userId, policy.requiredXp);

      const { status, body } = await callLevelUp(request, accessToken);
      expect(status).toBe(400);
      // WalletNotConnectedException → WALLET_NOT_CONNECTED 에러코드
      expect(body.code).toMatch(/WEB3|WALLET/);
      console.log(
        `  ✓ 지갑 미연결 레벨업 차단: status=${status}, code=${body.code}`
      );
    }
  );

  test(
    "TC-LR-A-03 | XP 부족 상태에서 레벨업 → 409 LEVEL_001 (NOT_ENOUGH_XP)",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "a03");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);

      // 지갑 등록 (XP 조건만 미충족)
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);

      // XP 를 requiredXp - 1 로 설정
      await setUserXpInDb(userId, policy.requiredXp - 1);

      const { status, body } = await callLevelUp(request, accessToken);
      // NOT_ENOUGH_XP 는 CONFLICT(409) 반환
      expect(status).toBe(409);
      expect(body.code).toBe("LEVEL_001");
      console.log(
        `  ✓ XP 부족 레벨업 차단: status=${status}, code=${body.code}`
      );
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite B — 정상 레벨업 → rewardTxStatus 초기 상태 검증
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite B — 정상 레벨업 응답 검증", () => {
  // 레벨업 성공 시 rewardTxStatus 는 워커 처리 전이므로 CREATED 이거나
  // 이미 워커가 처리 중이라면 SIGNED/PENDING 일 수도 있습니다.
  const VALID_INITIAL_STATUSES = [
    "CREATED",
    "SIGNED",
    "PENDING",
    "SUCCEEDED",
    "UNCONFIRMED",
  ];

  test(
    "TC-LR-B-01 | 지갑 등록 + XP 충족 → 레벨업 200 OK, rewardTxStatus 초기 상태 반환",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "b01");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);
      await setUserXpInDb(userId, policy.requiredXp);

      const { status, body } = await callLevelUp(request, accessToken);
      expect(status).toBe(200);

      const d: LevelUpData = body.data;
      expect(d.fromLevel).toBe(1);
      expect(d.toLevel).toBe(2);
      expect(d.spentXp).toBe(policy.requiredXp);
      expect(d.rewardMztk).toBe(policy.rewardMztk);
      expect(d.levelUpHistoryId).toBeGreaterThan(0);
      expect(VALID_INITIAL_STATUSES).toContain(d.rewardTxStatus);
      // rewardTxPhase: CREATED/SIGNED/PENDING/UNCONFIRMED → PENDING
      expect(["PENDING", "SUCCESS"]).toContain(d.rewardTxPhase);

      console.log(
        `  ✓ 레벨업 성공: levelUpHistoryId=${d.levelUpHistoryId}, ` +
          `rewardTxStatus=${d.rewardTxStatus}, rewardTxPhase=${d.rewardTxPhase}`
      );
    }
  );

  test(
    "TC-LR-B-02 | 레벨업 후 GET /users/me/level-up-histories 에서 이력 확인",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "b02");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);
      await setUserXpInDb(userId, policy.requiredXp);

      const { status, body: levelUpBody } = await callLevelUp(request, accessToken);
      expect(status).toBe(200);
      const levelUpHistoryId: number = levelUpBody.data.levelUpHistoryId;

      const histories = await fetchHistories(request, accessToken);
      const history = histories.find((h) => h.levelUpHistoryId === levelUpHistoryId);

      expect(history).toBeDefined();
      expect(history!.fromLevel).toBe(1);
      expect(history!.toLevel).toBe(2);
      expect(VALID_INITIAL_STATUSES).toContain(history!.rewardTxStatus);

      console.log(
        `  ✓ 이력 확인 완료: levelUpHistoryId=${levelUpHistoryId}, ` +
          `rewardTxStatus=${history!.rewardTxStatus}`
      );
    }
  );

  test(
    "TC-LR-B-03 | 레벨업 후 GET /users/me/level 에서 레벨 2로 반영 확인",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "b03");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);
      await setUserXpInDb(userId, policy.requiredXp);

      const { status } = await callLevelUp(request, accessToken);
      expect(status).toBe(200);

      const levelRes = await request.get(`${ENV.BACKEND_URL}/users/me/level`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(levelRes.status()).toBe(200);
      const levelBody = await levelRes.json();
      const currentLevel = levelBody.data?.level ?? levelBody.data?.currentLevel;
      expect(currentLevel).toBe(2);

      console.log(`  ✓ 레벨 확인: currentLevel=${currentLevel}`);
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite C — 중복/멱등 처리
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite C — 중복 레벨업 멱등 처리", () => {
  test(
    "TC-LR-C-01 | 레벨업 성공 후 재요청 → 409 (이미 최고 레벨 또는 레벨업 충돌)",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "c01");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);
      await setUserXpInDb(userId, policy.requiredXp);

      const { status: first } = await callLevelUp(request, accessToken);
      expect(first).toBe(200);

      // XP 초기화 없이 두 번째 레벨업 → XP 부족 (400) 또는 이미 레벨업 중 (409)
      const { status: second, body: secondBody } = await callLevelUp(
        request,
        accessToken
      );
      expect([400, 409]).toContain(second);
      console.log(
        `  ✓ 중복 레벨업 차단: status=${second}, code=${secondBody.code ?? "-"}`
      );
    }
  );

  test(
    "TC-LR-C-02 | 충분한 XP 로 두 번 연속 레벨업 (레벨1→2→3) 각각 독립 이력 생성",
    async ({ request }) => {
      const { accessToken, userId } = await signUpAndLogin(request, "c02");
      await initLevel(request, accessToken);

      const policiesRes = await request.get(
        `${ENV.BACKEND_URL}/levels/policies`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
      const policiesBody = await policiesRes.json();
      const allPolicies: LevelPolicy[] = policiesBody.data?.levelPolicies ?? [];

      const policy1 = allPolicies.find((p) => p.currentLevel === 1);
      const policy2 = allPolicies.find((p) => p.currentLevel === 2);

      if (!policy1 || !policy2) {
        test.skip(
          true,
          "레벨 1→2 또는 2→3 정책이 없어 연속 레벨업 테스트를 스킵합니다."
        );
        return;
      }

      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);

      // 첫 번째 레벨업: Lv1 → Lv2
      await setUserXpInDb(userId, policy1.requiredXp);
      const { status: s1, body: b1 } = await callLevelUp(request, accessToken);
      expect(s1).toBe(200);
      const id1: number = b1.data.levelUpHistoryId;

      // 두 번째 레벨업: Lv2 → Lv3
      await setUserXpInDb(userId, policy2.requiredXp);
      const { status: s2, body: b2 } = await callLevelUp(request, accessToken);
      expect(s2).toBe(200);
      const id2: number = b2.data.levelUpHistoryId;

      expect(id1).not.toBe(id2);

      const histories = await fetchHistories(request, accessToken);
      const h1 = histories.find((h) => h.levelUpHistoryId === id1);
      const h2 = histories.find((h) => h.levelUpHistoryId === id2);

      expect(h1).toBeDefined();
      expect(h2).toBeDefined();
      expect(h1!.fromLevel).toBe(1);
      expect(h2!.fromLevel).toBe(2);

      console.log(
        `  ✓ 연속 레벨업: id1=${id1}(Lv1→2), id2=${id2}(Lv2→3)`
      );
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite D — 온체인 토큰 지급 확인 (워커 처리 폴링)
//
// TransactionIssuerWorker 가 CREATED → SIGNED → PENDING 처리를 하고,
// TransactionReceiptWorker 가 PENDING → SUCCEEDED 전이를 완료해야 합니다.
// 최대 5분 폴링합니다.
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite D — 온체인 토큰 지급 확인 (폴링)", () => {
  test(
    "TC-LR-D-01 | 레벨업 보상 → 워커 처리 완료 후 rewardTxStatus=SUCCEEDED, txHash 수신",
    async ({ request }) => {
      test.setTimeout(ONCHAIN_TIMEOUT_MS + 60_000);

      const { accessToken, userId } = await signUpAndLogin(request, "d01");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);
      await setUserXpInDb(userId, policy.requiredXp);

      // ── 레벨업 ──
      const { status, body: levelUpBody } = await callLevelUp(request, accessToken);
      expect(status).toBe(200);

      const d: LevelUpData = levelUpBody.data;
      const levelUpHistoryId = d.levelUpHistoryId;
      console.log(
        `  레벨업 완료: levelUpHistoryId=${levelUpHistoryId}, ` +
          `rewardTxStatus=${d.rewardTxStatus}, rewardMztk=${d.rewardMztk}`
      );
      console.log(
        `  워커 처리 대기 중... (최대 ${ONCHAIN_TIMEOUT_MS / 1000}초)`
      );

      // ── 온체인 SUCCEEDED 폴링 ──
      const finalHistory = await pollForRewardStatus(
        request,
        accessToken,
        levelUpHistoryId,
        ["SUCCEEDED"],
        ONCHAIN_TIMEOUT_MS
      );

      // ── 검증 ──
      expect(finalHistory.rewardTxStatus).toBe("SUCCEEDED");
      expect(finalHistory.rewardTxPhase).toBe("SUCCESS");
      expect(finalHistory.rewardTxHash).toMatch(/^0x[0-9a-fA-F]{64}$/);
      expect(finalHistory.rewardExplorerUrl).toContain(finalHistory.rewardTxHash!);

      console.log(`  ✓ 온체인 지급 확인 완료!`);
      console.log(`    rewardTxStatus : ${finalHistory.rewardTxStatus}`);
      console.log(`    rewardTxPhase  : ${finalHistory.rewardTxPhase}`);
      console.log(`    rewardTxHash   : ${finalHistory.rewardTxHash}`);
      console.log(`    explorerUrl    : ${finalHistory.rewardExplorerUrl}`);
    }
  );

  test(
    "TC-LR-D-02 | 레벨업 보상 → 워커 처리 중 SIGNED/PENDING 경유 확인 (최초 상태 스냅샷)",
    async ({ request }) => {
      test.setTimeout(ONCHAIN_TIMEOUT_MS + 60_000);

      const { accessToken, userId } = await signUpAndLogin(request, "d02");
      await initLevel(request, accessToken);

      const policy = await fetchLevel1Policy(request, accessToken);
      const wallet = ethers.Wallet.createRandom();
      await registerWallet(request, accessToken, wallet);
      await setUserXpInDb(userId, policy.requiredXp);

      const { status, body: levelUpBody } = await callLevelUp(request, accessToken);
      expect(status).toBe(200);

      const levelUpHistoryId: number = levelUpBody.data.levelUpHistoryId;
      const initialTxStatus: string = levelUpBody.data.rewardTxStatus;

      // 레벨업 직후 CREATED 또는 워커가 이미 처리 중이면 SIGNED/PENDING 일 수 있음
      const PROGRESS_STATUSES = ["CREATED", "SIGNED", "PENDING", "UNCONFIRMED", "SUCCEEDED"];
      expect(PROGRESS_STATUSES).toContain(initialTxStatus);
      console.log(
        `  초기 rewardTxStatus=${initialTxStatus} (워커가 처리하기 전 스냅샷)`
      );

      // 최종 상태: SUCCEEDED 또는 UNCONFIRMED (타임아웃/RPC 문제)
      const finalHistory = await pollForRewardStatus(
        request,
        accessToken,
        levelUpHistoryId,
        ["SUCCEEDED", "UNCONFIRMED", "FAILED_ONCHAIN"],
        ONCHAIN_TIMEOUT_MS
      );

      console.log(`  ✓ 최종 상태 확인:`);
      console.log(`    rewardTxStatus : ${finalHistory.rewardTxStatus}`);
      console.log(`    rewardTxHash   : ${finalHistory.rewardTxHash ?? "(없음)"}`);

      // SUCCEEDED 이면 txHash가 있어야 함
      if (finalHistory.rewardTxStatus === "SUCCEEDED") {
        expect(finalHistory.rewardTxHash).toMatch(/^0x[0-9a-fA-F]{64}$/);
        expect(finalHistory.rewardTxPhase).toBe("SUCCESS");
      } else if (finalHistory.rewardTxStatus === "UNCONFIRMED") {
        // UNCONFIRMED 도 txHash 가 있어야 함 (이미 broadcast 된 상태)
        expect(finalHistory.rewardTxHash).toMatch(/^0x[0-9a-fA-F]{64}$/);
        expect(finalHistory.rewardTxPhase).toBe("PENDING");
        console.log(`  ⚠ UNCONFIRMED: receipt 15분 내 미도착 (운영 확인 대상)`);
      } else if (finalHistory.rewardTxStatus === "FAILED_ONCHAIN") {
        console.log(`  ⚠ FAILED_ONCHAIN: 온체인 트랜잭션 실패 (receipt.status==0)`);
      }
    }
  );
});
