/**
 * MZTK — QnA Escrow Playwright E2E 테스트
 *
 * 목표:
 *   - 질문 생성 응답이 즉시 Web3 write payload 를 반환하는지 검증
 *   - execution intent 조회 / execute 엔드포인트가 실제 응답 계약대로 동작하는지 검증
 *   - 실제 RPC + funded test wallet 이 있으면 질문 intent execute 까지 진행
 *   - 질문 on-chain confirm 이후 답변 생성 intent 까지 이어지는지 검증
 *
 * 범위:
 *   - 브라우저 UI가 아니라 HTTP/API + ethers.js 기반 API E2E
 *   - 실제 서버 / 실제 DB / 실제 RPC 사용 가능
 *   - funded wallet / token allowance 가 없으면 관련 Suite 는 skip
 */

import { APIRequestContext, expect, test } from "@playwright/test";
import { ethers } from "ethers";
import { Pool } from "pg";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

test.describe.configure({ mode: "serial" });

const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  DATABASE_URL: process.env.DATABASE_URL,
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
  DB_NAME: process.env.DB_NAME ?? "mztk_dev",
  DB_USER: process.env.DB_USER ?? process.env.DB_USERNAME ?? "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "postgres",
  WEB3_RPC_URL: process.env.WEB3_RPC_URL ?? "",
  WEB3_CHAIN_ID: BigInt(
    process.env.WEB3_EIP712_CHAIN_ID ?? process.env.WEB3_CHAIN_ID ?? "11155420"
  ),
  WEB3_EIP712_DOMAIN_NAME: process.env.WEB3_EIP712_DOMAIN_NAME ?? "MomzzangSeven",
  WEB3_EIP712_DOMAIN_VERSION: process.env.WEB3_EIP712_DOMAIN_VERSION ?? "1",
  WEB3_EIP712_VERIFYING_CONTRACT:
    process.env.WEB3_EIP712_VERIFYING_CONTRACT ?? "",
  WEB3_ESCROW_QNA_CONTRACT_ADDRESS:
    process.env.WEB3_ESCROW_QNA_CONTRACT_ADDRESS ?? "",
  WEB3_REWARD_TOKEN_CONTRACT_ADDRESS:
    process.env.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS ??
    process.env.MZTK_TOKEN_CONTRACT_ADDRESS ??
    "",
  WEB3_REWARD_TOKEN_DECIMALS: Number.parseInt(
    process.env.WEB3_REWARD_TOKEN_DECIMALS ?? "18",
    10
  ),
  QNA_TEST_ASKER_PRIVATE_KEY: process.env.QNA_TEST_ASKER_PRIVATE_KEY ?? "",
  QNA_TEST_RESPONDER_PRIVATE_KEY: process.env.QNA_TEST_RESPONDER_PRIVATE_KEY ?? "",
};

const provider =
  ENV.WEB3_RPC_URL === "" ? null : new ethers.JsonRpcProvider(ENV.WEB3_RPC_URL);
const db =
  ENV.DATABASE_URL != null && ENV.DATABASE_URL !== ""
    ? new Pool({ connectionString: ENV.DATABASE_URL })
    : new Pool({
        host: ENV.DB_HOST,
        port: ENV.DB_PORT,
        database: ENV.DB_NAME,
        user: ENV.DB_USER,
        password: ENV.DB_PASSWORD,
      });

const rewardTokenInterface = new ethers.Interface([
  "function allowance(address owner, address spender) view returns (uint256)",
  "function approve(address spender, uint256 amount) returns (bool)",
  "function balanceOf(address owner) view returns (uint256)",
]);

const EXECUTE_TIMEOUT_MS = 30_000;
const CONFIRM_TIMEOUT_MS = 5 * 60 * 1000;
const POLL_INTERVAL_MS = 3_000;

test.afterAll(async () => {
  await db.end();
});

interface AuthResult {
  accessToken: string;
  userId: number;
}

interface QuestionWritePayload {
  resource: {
    type: string;
    id: string;
    status: string;
  };
  actionType: string;
  executionIntent: {
    id: string;
    status: string;
    expiresAt: string;
  };
  execution: {
    mode: string;
    signCount: number;
  };
  signRequest?: SignRequestBundle;
  existing: boolean;
}

interface AnswerWritePayload extends QuestionWritePayload {}

interface SignRequestBundle {
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
  transaction?: {
    chainId: number;
    fromAddress: string;
    toAddress: string;
    valueHex: string;
    data: string;
    nonce?: number;
    gasLimitHex?: string;
    maxPriorityFeePerGasHex?: string;
    maxFeePerGasHex?: string;
    expectedNonce?: number;
  };
}

interface ExecutionIntentReadData {
  resource: {
    type: string;
    id: string;
    status: string;
  };
  executionIntent: {
    id: string;
    status: string;
    expiresAt: string;
  };
  execution: {
    mode: string;
    signCount: number;
  };
  signRequest?: SignRequestBundle;
  transaction?: {
    id: number;
    status: string;
    txHash?: string;
  };
}

interface ExecuteIntentResponseData {
  executionIntent: {
    id: string;
    status: string;
  };
  transaction?: {
    id: number;
    status: string;
    txHash?: string;
  };
}

function hasQuestionInfra(): boolean {
  return (
    provider != null &&
    ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS !== "" &&
    ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS !== "" &&
    ENV.QNA_TEST_ASKER_PRIVATE_KEY !== "" &&
    ENV.WEB3_EIP712_VERIFYING_CONTRACT !== ""
  );
}

function hasAnswerInfra(): boolean {
  return hasQuestionInfra() && ENV.QNA_TEST_RESPONDER_PRIVATE_KEY !== "";
}

function authHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  };
}

function walletFromPrivateKey(privateKey: string): ethers.Wallet {
  return new ethers.Wallet(privateKey, provider ?? undefined);
}

function signRawDigest(wallet: ethers.Wallet, digest: string): string {
  const signature = wallet.signingKey.sign(digest);
  return ethers.Signature.from(signature).serialized;
}

async function signUpAndLogin(
  request: APIRequestContext,
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
  expect(signUpRes.ok(), `signup failed: ${await signUpRes.text()}`).toBeTruthy();
  const signUpBody = await signUpRes.json();

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, provider: "LOCAL" },
  });
  expect(loginRes.ok(), `login failed: ${await loginRes.text()}`).toBeTruthy();
  const loginBody = await loginRes.json();

  return {
    userId: signUpBody.data.userId as number,
    accessToken: loginBody.data.accessToken as string,
  };
}

async function markWalletReusable(walletAddress: string): Promise<void> {
  await db.query(
    `update user_wallets
        set status = 'UNLINKED',
            unlinked_at = now(),
            updated_at = now()
      where wallet_address = $1
        and status = 'ACTIVE'`,
    [walletAddress.toLowerCase()]
  );
}

async function registerWallet(
  request: APIRequestContext,
  accessToken: string,
  privateKey: string
): Promise<ethers.Wallet> {
  const wallet = walletFromPrivateKey(privateKey);
  await markWalletReusable(wallet.address);

  const challengeRes = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
    headers: authHeaders(accessToken),
    data: {
      purpose: "WALLET_REGISTRATION",
      walletAddress: wallet.address,
    },
  });
  expect(challengeRes.ok(), `challenge failed: ${await challengeRes.text()}`).toBeTruthy();
  const challengeBody = await challengeRes.json();

  const signature = await wallet.signTypedData(
    {
      name: ENV.WEB3_EIP712_DOMAIN_NAME,
      version: ENV.WEB3_EIP712_DOMAIN_VERSION,
      chainId: ENV.WEB3_CHAIN_ID,
      verifyingContract: ENV.WEB3_EIP712_VERIFYING_CONTRACT,
    },
    {
      AuthRequest: [
        { name: "content", type: "string" },
        { name: "nonce", type: "string" },
      ],
    },
    {
      content: challengeBody.data.message,
      nonce: challengeBody.data.nonce,
    }
  );

  const registerRes = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
    headers: authHeaders(accessToken),
    data: {
      walletAddress: wallet.address,
      signature,
      nonce: challengeBody.data.nonce,
    },
  });
  expect(registerRes.status(), `wallet register failed: ${await registerRes.text()}`).toBe(201);

  return wallet;
}

async function ensureRewardAllowance(privateKey: string, rewardMztk: number): Promise<void> {
  if (provider == null) {
    throw new Error("WEB3_RPC_URL is required");
  }

  const wallet = walletFromPrivateKey(privateKey);
  const token = new ethers.Contract(
    ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS,
    rewardTokenInterface,
    wallet
  );
  const amountWei = ethers.parseUnits(
    rewardMztk.toString(),
    ENV.WEB3_REWARD_TOKEN_DECIMALS
  );

  const [nativeBalance, tokenBalance, allowance] = await Promise.all([
    provider.getBalance(wallet.address),
    token.balanceOf(wallet.address) as Promise<bigint>,
    token.allowance(
      wallet.address,
      ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS
    ) as Promise<bigint>,
  ]);

  if (nativeBalance <= 0n) {
    throw new Error(`wallet ${wallet.address} has no native gas for approve`);
  }
  if (tokenBalance < amountWei) {
    throw new Error(`wallet ${wallet.address} has insufficient reward token balance`);
  }
  if (allowance >= amountWei) {
    return;
  }

  const approveTx = await token.approve(
    ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS,
    amountWei * 10n
  );
  const receipt = await approveTx.wait();
  expect(receipt?.status, "approve tx failed").toBe(1);
}

async function createQuestion(
  request: APIRequestContext,
  accessToken: string,
  title: string,
  content: string,
  reward: number
): Promise<{ postId: number; web3: QuestionWritePayload }> {
  const res = await request.post(`${ENV.BACKEND_URL}/posts/question`, {
    headers: authHeaders(accessToken),
    data: {
      title,
      content,
      reward,
      tags: [],
    },
  });
  expect(res.status(), `question create failed: ${await res.text()}`).toBe(201);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBeTruthy();
  expect(body.data.web3).toBeTruthy();

  return {
    postId: body.data.postId as number,
    web3: body.data.web3 as QuestionWritePayload,
  };
}

async function createAnswer(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  content: string
): Promise<{ answerId: number; web3: AnswerWritePayload }> {
  const res = await request.post(`${ENV.BACKEND_URL}/questions/${postId}/answers`, {
    headers: authHeaders(accessToken),
    data: {
      content,
      imageIds: [],
    },
  });
  expect(res.status(), `answer create failed: ${await res.text()}`).toBe(201);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.answerId).toBeTruthy();
  expect(body.data.web3).toBeTruthy();

  return {
    answerId: body.data.answerId as number,
    web3: body.data.web3 as AnswerWritePayload,
  };
}

async function getExecutionIntent(
  request: APIRequestContext,
  accessToken: string,
  intentId: string
): Promise<{ status: number; data: ExecutionIntentReadData | null }> {
  const res = await request.get(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}`,
    { headers: authHeaders(accessToken) }
  );
  if (!res.ok()) {
    return { status: res.status(), data: null };
  }
  const body = await res.json();
  return { status: res.status(), data: body.data as ExecutionIntentReadData };
}

async function executeExecutionIntent(
  request: APIRequestContext,
  accessToken: string,
  intentId: string,
  wallet: ethers.Wallet,
  signRequest: SignRequestBundle
): Promise<{ status: number; data: ExecuteIntentResponseData }> {
  const authorizationSignature = signRawDigest(
    wallet,
    signRequest.authorization!.payloadHashToSign
  );
  const submitSignature = signRawDigest(wallet, signRequest.submit!.executionDigest);

  const res = await request.post(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}/execute`,
    {
      headers: authHeaders(accessToken),
      data: {
        authorizationSignature,
        submitSignature,
      },
    }
  );
  expect(res.status(), `execution submit failed: ${await res.text()}`).toBe(202);
  const body = await res.json();
  return { status: res.status(), data: body.data as ExecuteIntentResponseData };
}

async function waitForIntentStatus(
  request: APIRequestContext,
  accessToken: string,
  intentId: string,
  expectedStatuses: string[],
  timeoutMs: number
): Promise<ExecutionIntentReadData> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const response = await getExecutionIntent(request, accessToken, intentId);
    expect(response.status).toBe(200);
    expect(response.data).not.toBeNull();
    const data = response.data!;
    if (expectedStatuses.includes(data.executionIntent.status)) {
      return data;
    }
    await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  throw new Error(
    `execution intent ${intentId} did not reach [${expectedStatuses.join(", ")}] within ${timeoutMs}ms`
  );
}

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

test.describe("Suite B — 질문 작성 응답의 Web3 payload 검증", () => {
  test(
    "TC-QNA-B-01: 질문 작성 → response 에 execution intent / signRequest 가 포함된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasQuestionInfra(), "RPC / QnA contract / reward token / funded asker wallet env is required");

      const reward = 10;
      const { accessToken } = await signUpAndLogin(request, "b01");
      await registerWallet(request, accessToken, ENV.QNA_TEST_ASKER_PRIVATE_KEY);
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const created = await createQuestion(
        request,
        accessToken,
        "Playwright 에스크로 테스트 질문",
        "QnA Escrow 질문 생성 실플로우 검증",
        reward
      );

      expect(created.web3.actionType).toBe("QNA_QUESTION_CREATE");
      expect(created.web3.resource.type).toBe("QUESTION");
      expect(created.web3.executionIntent.id).toBeTruthy();
      expect(created.web3.executionIntent.status).toBe("AWAITING_SIGNATURE");
      expect(created.web3.execution.mode).toBe("EIP7702");
      expect(created.web3.execution.signCount).toBe(2);
      expect(created.web3.signRequest?.authorization?.payloadHashToSign).toBeTruthy();
      expect(created.web3.signRequest?.submit?.executionDigest).toBeTruthy();
      expect(created.web3.signRequest?.transaction?.toAddress?.toLowerCase()).toBe(
        ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS.toLowerCase()
      );
    }
  );
});

test.describe("Suite C — execution intent 조회 검증", () => {
  test(
    "TC-QNA-C-01: 질문 작성 직후 GET /execution-intents/{id} 로 동일 payload 를 조회할 수 있다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasQuestionInfra(), "RPC / QnA contract / reward token / funded asker wallet env is required");

      const reward = 10;
      const { accessToken } = await signUpAndLogin(request, "c01");
      await registerWallet(request, accessToken, ENV.QNA_TEST_ASKER_PRIVATE_KEY);
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const created = await createQuestion(
        request,
        accessToken,
        "GET Intent 테스트 질문",
        "Intent 조회 테스트 본문",
        reward
      );

      const loaded = await getExecutionIntent(
        request,
        accessToken,
        created.web3.executionIntent.id
      );

      expect(loaded.status).toBe(200);
      expect(loaded.data).not.toBeNull();
      expect(loaded.data!.resource.type).toBe("QUESTION");
      expect(loaded.data!.resource.id).toBe(String(created.postId));
      expect(loaded.data!.executionIntent.id).toBe(created.web3.executionIntent.id);
      expect(loaded.data!.executionIntent.status).toBe("AWAITING_SIGNATURE");
      expect(loaded.data!.execution.mode).toBe("EIP7702");
      expect(loaded.data!.signRequest?.authorization?.payloadHashToSign).toBe(
        created.web3.signRequest?.authorization?.payloadHashToSign
      );
      expect(loaded.data!.signRequest?.submit?.executionDigest).toBe(
        created.web3.signRequest?.submit?.executionDigest
      );
    }
  );

  test("TC-QNA-C-02: 존재하지 않는 intent ID 조회 → 4xx", async ({ request }) => {
    const { accessToken } = await signUpAndLogin(request, "c02");

    const res = await request.get(
      `${ENV.BACKEND_URL}/users/me/web3/execution-intents/non-existent-intent-id`,
      { headers: authHeaders(accessToken) }
    );

    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });
});

test.describe("Suite D — 질문 intent execute 검증", () => {
  test(
    "TC-QNA-D-01: execution intent execute → PENDING_ONCHAIN + transaction summary 반환",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasQuestionInfra(), "RPC / QnA contract / reward token / funded asker wallet env is required");

      const reward = 10;
      const { accessToken } = await signUpAndLogin(request, "d01");
      const wallet = await registerWallet(
        request,
        accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const created = await createQuestion(
        request,
        accessToken,
        "서명 제출 테스트 질문",
        "Suite D 질문 execute 테스트",
        reward
      );

      const executeResponse = await executeExecutionIntent(
        request,
        accessToken,
        created.web3.executionIntent.id,
        wallet,
        created.web3.signRequest!
      );

      expect(executeResponse.data.executionIntent.id).toBe(created.web3.executionIntent.id);
      expect(executeResponse.data.executionIntent.status).toBe("PENDING_ONCHAIN");
      expect(executeResponse.data.transaction?.id).toBeTruthy();

      const pending = await waitForIntentStatus(
        request,
        accessToken,
        created.web3.executionIntent.id,
        ["PENDING_ONCHAIN", "CONFIRMED", "FAILED_ONCHAIN"],
        EXECUTE_TIMEOUT_MS
      );

      expect(["PENDING_ONCHAIN", "CONFIRMED", "FAILED_ONCHAIN"]).toContain(
        pending.executionIntent.status
      );
      expect(pending.transaction?.id).toBeTruthy();
      expect(pending.transaction?.txHash).toBeTruthy();
    }
  );
});

test.describe("Suite E — 질문 confirm 이후 답변 intent 생성 검증", () => {
  test(
    "TC-QNA-E-01: 질문 CONFIRMED 이후 답변 작성 → answer execution intent 가 생성된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(
        !hasAnswerInfra(),
        "RPC / contracts / funded asker+responder wallet env is required"
      );

      const reward = 10;
      const asker = await signUpAndLogin(request, "e01-asker");
      const responder = await signUpAndLogin(request, "e01-responder");

      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const createdQuestion = await createQuestion(
        request,
        asker.accessToken,
        "답변 create 전 질문 확정 테스트",
        "Suite E 질문 본문",
        reward
      );

      await executeExecutionIntent(
        request,
        asker.accessToken,
        createdQuestion.web3.executionIntent.id,
        askerWallet,
        createdQuestion.web3.signRequest!
      );

      const confirmedQuestion = await waitForIntentStatus(
        request,
        asker.accessToken,
        createdQuestion.web3.executionIntent.id,
        ["CONFIRMED", "FAILED_ONCHAIN"],
        CONFIRM_TIMEOUT_MS
      );

      expect(
        confirmedQuestion.executionIntent.status,
        `question intent failed before answer create. txHash=${confirmedQuestion.transaction?.txHash ?? "n/a"}`
      ).toBe("CONFIRMED");

      await registerWallet(request, responder.accessToken, ENV.QNA_TEST_RESPONDER_PRIVATE_KEY);

      const createdAnswer = await createAnswer(
        request,
        responder.accessToken,
        createdQuestion.postId,
        "Playwright responder answer"
      );

      expect(createdAnswer.web3.actionType).toBe("QNA_ANSWER_SUBMIT");
      expect(createdAnswer.web3.resource.type).toBe("ANSWER");
      expect(createdAnswer.web3.executionIntent.id).toBeTruthy();
      expect(createdAnswer.web3.executionIntent.status).toBe("AWAITING_SIGNATURE");
      expect(createdAnswer.web3.execution.mode).toBe("EIP7702");
      expect(createdAnswer.web3.signRequest?.authorization?.payloadHashToSign).toBeTruthy();
      expect(createdAnswer.web3.signRequest?.submit?.executionDigest).toBeTruthy();

      const loadedAnswerIntent = await getExecutionIntent(
        request,
        responder.accessToken,
        createdAnswer.web3.executionIntent.id
      );

      expect(loadedAnswerIntent.status).toBe(200);
      expect(loadedAnswerIntent.data?.resource.type).toBe("ANSWER");
      expect(loadedAnswerIntent.data?.resource.id).toBe(String(createdAnswer.answerId));
      expect(loadedAnswerIntent.data?.executionIntent.status).toBe("AWAITING_SIGNATURE");
    }
  );
});
