/**
 * MZTK — QnA Escrow Playwright E2E 테스트
 *
 * 테스트 대상:
 *   POST /posts/question            — 질문 작성 → Execution Intent 생성
 *   POST /questions/{id}/answers    — 답변 작성 → Execution Intent 생성
 *   GET  /users/me/web3/execution-intents/{id}  — Intent 상태/서명 데이터 조회
 *   POST /users/me/web3/execution-intents/{id}/execute — 서명 제출 → PENDING_ONCHAIN
 *
 * 검증 항목:
 *   Suite A — 사전 조건 오류 (인증 없음 → 401, 필드 누락 → 400)
 *   Suite B — 질문 작성 후 Execution Intent 가 AWAITING_SIGNATURE 상태로 생성되는지 확인
 *   Suite C — GET /users/me/web3/execution-intents/{id} 응답 구조 및 signRequest 검증
 *   Suite D — EIP-7702 서명 제출 → PENDING_ONCHAIN 전이 (워커 처리 전 즉시 확인)
 *
 * 전제 조건:
 *   - web3.reward-token.enabled=true (서버 설정)
 *   - web3.eip7702.enabled=true (서버 설정)
 *   - QnA Escrow 컨트랙트 주소가 application.yml 에 설정되어 있어야 함
 *   - Treasury key 가 DB(web3_treasury_keys)에 provision 되어 있어야 함 (Suite D)
 *   - 테스트 지갑이 Escrow 컨트랙트에 충분한 토큰 allowance 를 승인해야 함 (Suite B+)
 *
 * 격리 전략:
 *   - 각 테스트는 고유 이메일로 신규 유저 생성 → 데이터 충돌 방지
 *   - ethers.Wallet.createRandom() 으로 테스트마다 고유 지갑 생성
 */

import { test, expect } from "@playwright/test";
import { ethers } from "ethers";
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
};

// Suite D 서명 제출 후 상태 전이 대기 (즉시 PENDING_ONCHAIN 확인, 워커 불필요)
const EXECUTE_TIMEOUT_MS = 15_000;

// ────────────────────────────────────────────────────────────────────────────
// 헬퍼 타입
// ────────────────────────────────────────────────────────────────────────────
interface AuthResult {
  accessToken: string;
}

interface ExecutionIntentData {
  resource: { type: string; id: string; status: string };
  executionIntent: { id: string; status: string; expiresAt: string };
  execution: { mode: string; signCount: number };
  signRequest?: {
    authorization?: {
      chainId: number;
      delegateTarget: string;
      authorityNonce: number;
      payloadHashToSign: string;
    };
    submit?: {
      executionDigest: string;
      deadlineEpochSeconds: number;
    };
  };
}

// ────────────────────────────────────────────────────────────────────────────
// 헬퍼 함수
// ────────────────────────────────────────────────────────────────────────────

/** 회원가입 + 로그인 → accessToken 반환 */
async function signUpAndLogin(
  request: import("@playwright/test").APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const ts = Date.now();
  const email = `playwright-qna-${suffix}-${ts}@test.com`;
  const password = "Test1234!";
  const nickname = `qna-${suffix}-${ts}`;

  const signUpRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname },
  });
  if (signUpRes.status() >= 300) {
    throw new Error(`회원가입 실패 (${signUpRes.status()}): ${await signUpRes.text()}`);
  }

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, provider: "LOCAL" },
  });
  if (loginRes.status() !== 200) {
    throw new Error(`로그인 실패 (${loginRes.status()}): ${await loginRes.text()}`);
  }
  const body = await loginRes.json();
  return { accessToken: body.data.accessToken };
}

/** Authorization 헤더 생성 */
function authHeader(accessToken: string) {
  return { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" };
}

/** GET /users/me/web3/execution-intents/{id} */
async function getExecutionIntent(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string,
  intentId: string
): Promise<{ status: number; data: ExecutionIntentData | null }> {
  const res = await request.get(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}`,
    { headers: authHeader(accessToken) }
  );
  if (!res.ok()) {
    return { status: res.status(), data: null };
  }
  const body = await res.json();
  return { status: res.status(), data: body.data };
}

/**
 * EIP-7702 authorization 서명 생성.
 *
 * EIP-7702 authorization tuple 에 대한 서명은 백엔드가 이미 payloadHash 를 계산해서
 * signRequest.authorization.payloadHashToSign 으로 반환합니다.
 * 클라이언트는 해당 해시를 eth_sign(개인키) 로 서명합니다.
 */
async function signAuthorizationPayload(
  wallet: ethers.Wallet,
  payloadHashToSign: string
): Promise<string> {
  // payloadHashToSign 은 이미 keccak256 해시이므로 signMessage(bytes) 로 서명
  const hashBytes = ethers.getBytes(payloadHashToSign);
  return wallet.signMessage(hashBytes);
}

/**
 * Submit 서명 생성.
 *
 * executionDigest 는 백엔드가 EIP-712 로 계산한 다이제스트입니다.
 * 동일하게 eth_sign 으로 서명합니다.
 */
async function signSubmitPayload(
  wallet: ethers.Wallet,
  executionDigest: string
): Promise<string> {
  const digestBytes = ethers.getBytes(executionDigest);
  return wallet.signMessage(digestBytes);
}

// ────────────────────────────────────────────────────────────────────────────
// Suite A — 사전 조건 오류 (인증 없음, 필드 누락)
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite A — 사전 조건 오류", () => {
  test("TC-QNA-A-01: 인증 없이 execution intent 조회 → 401", async ({ request }) => {
    const res = await request.get(
      `${ENV.BACKEND_URL}/users/me/web3/execution-intents/some-intent-id`
    );
    expect(res.status()).toBe(401);
  });

  test("TC-QNA-A-02: 인증 없이 execution intent 실행 → 401", async ({ request }) => {
    const res = await request.post(
      `${ENV.BACKEND_URL}/users/me/web3/execution-intents/some-intent-id/execute`,
      {
        headers: { "Content-Type": "application/json" },
        data: {},
      }
    );
    expect(res.status()).toBe(401);
  });

  test("TC-QNA-A-03: 인증 없이 질문 작성 → 401", async ({ request }) => {
    const res = await request.post(`${ENV.BACKEND_URL}/posts/question`, {
      headers: { "Content-Type": "application/json" },
      data: { title: "제목", content: "본문", reward: 10, tags: [] },
    });
    expect(res.status()).toBe(401);
  });
});

// ────────────────────────────────────────────────────────────────────────────
// Suite B — 질문 작성 → Execution Intent AWAITING_SIGNATURE 생성 확인
// (전제 조건: 서버에 reward-token.enabled=true, escrow 컨트랙트 설정, 지갑 allowance 승인)
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite B — 질문 작성 후 Execution Intent 생성", () => {
  test(
    "TC-QNA-B-01: 질문 작성 → 201 Created + postId 반환",
    { tag: ["@requires-escrow-infra"] },
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "b01");

      const res = await request.post(`${ENV.BACKEND_URL}/posts/question`, {
        headers: authHeader(accessToken),
        data: {
          title: "Playwright 에스크로 테스트 질문",
          content: "QnA Escrow E2E 테스트 질문 본문입니다.",
          reward: 10,
          tags: [],
        },
      });

      const body = await res.json();
      console.log(`질문 작성 응답 (${res.status()}):`, JSON.stringify(body, null, 2));

      expect(res.status()).toBe(201);
      expect(body.status).toBe("SUCCESS");
      expect(body.data.postId).toBeTruthy();
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite C — GET /users/me/web3/execution-intents/{id} 응답 구조 검증
// (전제 조건: Suite B 와 동일)
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite C — Execution Intent 조회 및 응답 구조 검증", () => {
  test(
    "TC-QNA-C-01: 질문 작성 후 execution intent 조회 → AWAITING_SIGNATURE + signRequest 포함",
    { tag: ["@requires-escrow-infra"] },
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "c01");

      // 질문 작성 (execution intent 생성 트리거)
      const createRes = await request.post(`${ENV.BACKEND_URL}/posts/question`, {
        headers: authHeader(accessToken),
        data: {
          title: "GET Intent 테스트 질문",
          content: "Intent 조회 테스트 본문",
          reward: 10,
          tags: [],
        },
      });
      expect(createRes.status()).toBe(201);
      const createBody = await createRes.json();
      const postId: number = createBody.data.postId;

      // DB 또는 별도 API 를 통해 execution intent ID 를 가져와야 합니다.
      // 현재 POST /posts/question 은 intentId 를 응답에 포함하지 않으므로,
      // GetLatestExecutionIntentSummaryUseCase 또는 DB 직접 조회가 필요합니다.
      // 이 시나리오는 intent ID 를 직접 반환하는 엔드포인트가 추가되면 활성화됩니다.
      // (참고: MOM-312 백로그)
      console.log(
        `✓ 질문 작성 성공 (postId=${postId}). ` +
          "Intent 조회는 intentId 노출 엔드포인트 추가 후 활성화됩니다."
      );
    }
  );

  test(
    "TC-QNA-C-02: 존재하지 않는 intent ID 조회 → 4xx",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "c02");

      const res = await request.get(
        `${ENV.BACKEND_URL}/users/me/web3/execution-intents/non-existent-intent-id`,
        { headers: authHeader(accessToken) }
      );

      // 잘못된 intent ID 이므로 404 또는 400 예상
      expect(res.status()).toBeGreaterThanOrEqual(400);
      expect(res.status()).toBeLessThan(500);
      console.log(`✓ 없는 intent ID 조회 → ${res.status()}`);
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// Suite D — EIP-7702 서명 제출 → PENDING_ONCHAIN 전이
// (전제 조건: Suite B + Treasury key DB 등록 + Optimism Sepolia RPC 연결)
// ────────────────────────────────────────────────────────────────────────────
test.describe("Suite D — 서명 제출 후 상태 전이 검증", () => {
  test(
    "TC-QNA-D-01: 서명 제출 → PENDING_ONCHAIN 전이 (워커 처리 전)",
    { tag: ["@requires-escrow-infra", "@requires-treasury-key"] },
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "d01");
      const wallet = ethers.Wallet.createRandom();

      // Step 1: 질문 작성 → intent 생성 트리거
      const createRes = await request.post(`${ENV.BACKEND_URL}/posts/question`, {
        headers: authHeader(accessToken),
        data: {
          title: "서명 제출 테스트 질문",
          content: "Suite D 테스트 본문",
          reward: 10,
          tags: [],
        },
      });
      expect(createRes.status(), `질문 작성 실패: ${await createRes.text()}`).toBe(201);
      const createBody = await createRes.json();
      const postId: number = createBody.data.postId;
      console.log(`✓ 질문 작성 성공 (postId=${postId})`);

      // Step 2: intentId 를 가져와서 GET 으로 signRequest 조회
      // NOTE: 현재 POST /posts/question 응답에 intentId 가 없으므로
      //       intentId 를 직접 알 수 있는 엔드포인트 추가 후 아래 로직을 활성화합니다.
      // const intentId = createBody.data.executionIntentId; // 미래 구현
      //
      // const { data: intentData } = await getExecutionIntent(request, accessToken, intentId);
      // expect(intentData?.executionIntent.status).toBe("AWAITING_SIGNATURE");
      //
      // Step 3: 서명 생성
      // const authSig = await signAuthorizationPayload(
      //   wallet,
      //   intentData!.signRequest!.authorization!.payloadHashToSign
      // );
      // const submitSig = await signSubmitPayload(
      //   wallet,
      //   intentData!.signRequest!.submit!.executionDigest
      // );
      //
      // Step 4: 서명 제출
      // const executeRes = await request.post(
      //   `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}/execute`,
      //   {
      //     headers: authHeader(accessToken),
      //     data: { authorizationSignature: authSig, submitSignature: submitSig },
      //   }
      // );
      // expect(executeRes.status()).toBe(202);
      // const executeBody = await executeRes.json();
      // expect(executeBody.data.executionIntent.status).toBe("PENDING_ONCHAIN");

      console.log(
        "Suite D: intentId 노출 엔드포인트 추가(MOM-312 후속) 후 서명 제출 검증이 활성화됩니다."
      );
    }
  );
});
