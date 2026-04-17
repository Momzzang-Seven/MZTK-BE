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
 *   - 실환경에서는 sponsor policy 에 따라 EIP-7702 또는 EIP-1559 fallback 이 선택될 수 있음
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

class NonceStaleError extends Error {}

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

interface PostDetailData {
  postId: number;
  content: string;
  question?: {
    isSolved: boolean;
  };
}

interface AnswerListItem {
  answerId: number;
  content: string;
  isAccepted: boolean;
}

function expectExecutionMode(
  execution: { mode: string; signCount: number },
  signRequest?: SignRequestBundle
) {
  expect(["EIP7702", "EIP1559"]).toContain(execution.mode);

  if (execution.mode === "EIP7702") {
    expect(execution.signCount).toBe(2);
    expect(signRequest?.authorization?.payloadHashToSign).toBeTruthy();
    expect(signRequest?.submit?.executionDigest).toBeTruthy();
    return;
  }

  expect(execution.signCount).toBe(1);
  expect(signRequest?.transaction?.toAddress).toBeTruthy();
  expect(signRequest?.transaction?.data).toBeTruthy();
  expect(signRequest?.transaction?.gasLimitHex).toBeTruthy();
  expect(signRequest?.transaction?.maxPriorityFeePerGasHex).toBeTruthy();
  expect(signRequest?.transaction?.maxFeePerGasHex).toBeTruthy();
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

async function updateQuestion(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  content: string
): Promise<{ postId: number; web3: QuestionWritePayload }> {
  const res = await request.patch(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: authHeaders(accessToken),
    data: { content },
  });
  expect(res.status(), `question update failed: ${await res.text()}`).toBe(200);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.web3).toBeTruthy();

  return {
    postId: body.data.postId as number,
    web3: body.data.web3 as QuestionWritePayload,
  };
}

async function deleteQuestion(
  request: APIRequestContext,
  accessToken: string,
  postId: number
): Promise<{ postId: number; web3: QuestionWritePayload }> {
  const res = await request.delete(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(res.status(), `question delete failed: ${await res.text()}`).toBe(200);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.web3).toBeTruthy();

  return {
    postId: body.data.postId as number,
    web3: body.data.web3 as QuestionWritePayload,
  };
}

async function recoverQuestionCreate(
  request: APIRequestContext,
  accessToken: string,
  postId: number
): Promise<{ postId: number; web3: QuestionWritePayload }> {
  const res = await request.post(
    `${ENV.BACKEND_URL}/posts/${postId}/web3/recover-create`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );
  expect(res.status(), `question recover-create failed: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.web3).toBeTruthy();
  return {
    postId: body.data.postId as number,
    web3: body.data.web3 as QuestionWritePayload,
  };
}

async function updateAnswer(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  answerId: number,
  content: string
): Promise<{ postId: number; answerId: number; web3: AnswerWritePayload }> {
  const res = await request.put(
    `${ENV.BACKEND_URL}/questions/${postId}/answers/${answerId}`,
    {
      headers: authHeaders(accessToken),
      data: { content },
    }
  );
  expect(res.status(), `answer update failed: ${await res.text()}`).toBe(200);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.answerId).toBe(answerId);
  expect(body.data.web3).toBeTruthy();

  return {
    postId: body.data.postId as number,
    answerId: body.data.answerId as number,
    web3: body.data.web3 as AnswerWritePayload,
  };
}

async function deleteAnswer(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  answerId: number
): Promise<{ postId: number; answerId: number; web3: AnswerWritePayload }> {
  const res = await request.delete(
    `${ENV.BACKEND_URL}/questions/${postId}/answers/${answerId}`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );
  expect(res.status(), `answer delete failed: ${await res.text()}`).toBe(200);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.answerId).toBe(answerId);
  expect(body.data.web3).toBeTruthy();

  return {
    postId: body.data.postId as number,
    answerId: body.data.answerId as number,
    web3: body.data.web3 as AnswerWritePayload,
  };
}

async function recoverAnswerCreate(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  answerId: number
): Promise<{ postId: number; answerId: number; web3: AnswerWritePayload }> {
  const res = await request.post(
    `${ENV.BACKEND_URL}/questions/${postId}/answers/${answerId}/web3/recover-create`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );
  expect(res.status(), `answer recover-create failed: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.answerId).toBe(answerId);
  expect(body.data.web3).toBeTruthy();
  return {
    postId: body.data.postId as number,
    answerId: body.data.answerId as number,
    web3: body.data.web3 as AnswerWritePayload,
  };
}

async function acceptAnswer(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  answerId: number
): Promise<{
  postId: number;
  acceptedAnswerId: number;
  status: string;
  web3: QuestionWritePayload;
}> {
  const res = await request.post(
    `${ENV.BACKEND_URL}/posts/${postId}/answers/${answerId}/accept`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );
  expect(res.status(), `answer accept failed: ${await res.text()}`).toBe(200);
  const body = await res.json();

  expect(body.status).toBe("SUCCESS");
  expect(body.data.postId).toBe(postId);
  expect(body.data.acceptedAnswerId).toBe(answerId);
  expect(body.data.web3).toBeTruthy();

  return {
    postId: body.data.postId as number,
    acceptedAnswerId: body.data.acceptedAnswerId as number,
    status: body.data.status as string,
    web3: body.data.web3 as QuestionWritePayload,
  };
}

async function getPostDetail(
  request: APIRequestContext,
  accessToken: string | null,
  postId: number
): Promise<{ status: number; data: PostDetailData | null }> {
  const res = await request.get(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: accessToken == null ? undefined : { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok()) {
    return { status: res.status(), data: null };
  }
  const body = await res.json();
  return { status: res.status(), data: body.data as PostDetailData };
}

async function getAnswers(
  request: APIRequestContext,
  accessToken: string,
  postId: number
): Promise<{ status: number; data: AnswerListItem[] }> {
  const res = await request.get(`${ENV.BACKEND_URL}/questions/${postId}/answers`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(res.status(), `get answers failed: ${await res.text()}`).toBe(200);
  const body = await res.json();
  return { status: res.status(), data: body.data as AnswerListItem[] };
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
  let data:
    | {
        authorizationSignature: string;
        submitSignature: string;
      }
    | {
        signedRawTransaction: string;
      };

  if (signRequest.authorization != null && signRequest.submit != null) {
    data = {
      authorizationSignature: signRawDigest(
        wallet,
        signRequest.authorization.payloadHashToSign
      ),
      submitSignature: signRawDigest(wallet, signRequest.submit.executionDigest),
    };
  } else if (signRequest.transaction != null) {
    data = {
      signedRawTransaction: await wallet.signTransaction({
        type: 2,
        chainId: signRequest.transaction.chainId,
        nonce:
          signRequest.transaction.expectedNonce ?? signRequest.transaction.nonce ?? 0,
        to: signRequest.transaction.toAddress,
        value: BigInt(signRequest.transaction.valueHex),
        data: signRequest.transaction.data,
        gasLimit: BigInt(signRequest.transaction.gasLimitHex!),
        maxPriorityFeePerGas: BigInt(signRequest.transaction.maxPriorityFeePerGasHex!),
        maxFeePerGas: BigInt(signRequest.transaction.maxFeePerGasHex!),
      }),
    };
  } else {
    throw new Error("unsupported signRequest payload");
  }

  const res = await request.post(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}/execute`,
    {
      headers: authHeaders(accessToken),
      data,
    }
  );
  if (res.status() === 409) {
    const body = await res.json();
    if (body.code === "WEB3_014") {
      throw new NonceStaleError(body.message ?? "Nonce stale");
    }
    throw new Error(`execution submit conflict: ${JSON.stringify(body)}`);
  }
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

async function executeAndAwaitConfirmed(
  request: APIRequestContext,
  accessToken: string,
  wallet: ethers.Wallet,
  writePayload: QuestionWritePayload | AnswerWritePayload,
  recoverWritePayload?: () => Promise<QuestionWritePayload | AnswerWritePayload>
): Promise<ExecutionIntentReadData> {
  let payload = writePayload;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      await executeExecutionIntent(
        request,
        accessToken,
        payload.executionIntent.id,
        wallet,
        payload.signRequest!
      );
      break;
    } catch (error) {
      if (
        error instanceof NonceStaleError &&
        recoverWritePayload != null &&
        attempt === 0
      ) {
        payload = await recoverWritePayload();
        continue;
      }
      throw error;
    }
  }

  const finalState = await waitForIntentStatus(
    request,
    accessToken,
    payload.executionIntent.id,
    ["CONFIRMED", "FAILED_ONCHAIN", "CANCELED", "NONCE_STALE", "EXPIRED"],
    CONFIRM_TIMEOUT_MS
  );

  expect(
    finalState.executionIntent.status,
    `intent ${payload.executionIntent.id} did not confirm. txHash=${finalState.transaction?.txHash ?? "n/a"}`
  ).toBe("CONFIRMED");

  return finalState;
}

async function createQuestionAndConfirm(
  request: APIRequestContext,
  accessToken: string,
  wallet: ethers.Wallet,
  reward: number,
  title: string,
  content: string
): Promise<{ postId: number; intent: ExecutionIntentReadData }> {
  const created = await createQuestion(request, accessToken, title, content, reward);
  const intent = await executeAndAwaitConfirmed(
    request,
    accessToken,
    wallet,
    created.web3,
    async () => (await recoverQuestionCreate(request, accessToken, created.postId)).web3
  );
  return { postId: created.postId, intent };
}

async function createAnswerAndConfirm(
  request: APIRequestContext,
  accessToken: string,
  wallet: ethers.Wallet,
  postId: number,
  content: string
): Promise<{ answerId: number; intent: ExecutionIntentReadData }> {
  const created = await createAnswer(request, accessToken, postId, content);
  const intent = await executeAndAwaitConfirmed(
    request,
    accessToken,
    wallet,
    created.web3,
    async () =>
      (await recoverAnswerCreate(request, accessToken, postId, created.answerId)).web3
  );
  return { answerId: created.answerId, intent };
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
      expectExecutionMode(created.web3.execution, created.web3.signRequest);
      if (created.web3.signRequest?.transaction?.toAddress != null) {
        expect(created.web3.signRequest.transaction.toAddress.toLowerCase()).toBe(
          ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS.toLowerCase()
        );
      }
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
      expectExecutionMode(loaded.data!.execution, loaded.data!.signRequest);
      if (loaded.data!.execution.mode === "EIP7702") {
        expect(loaded.data!.signRequest?.authorization?.payloadHashToSign).toBe(
          created.web3.signRequest?.authorization?.payloadHashToSign
        );
        expect(loaded.data!.signRequest?.submit?.executionDigest).toBe(
          created.web3.signRequest?.submit?.executionDigest
        );
      } else {
        expect(loaded.data!.signRequest?.transaction?.toAddress).toBe(
          created.web3.signRequest?.transaction?.toAddress
        );
        expect(loaded.data!.signRequest?.transaction?.data).toBe(
          created.web3.signRequest?.transaction?.data
        );
      }
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

      let payload = created.web3;
      let executeResponse: { status: number; data: ExecuteIntentResponseData };
      try {
        executeResponse = await executeExecutionIntent(
          request,
          accessToken,
          payload.executionIntent.id,
          wallet,
          payload.signRequest!
        );
      } catch (error) {
        if (!(error instanceof NonceStaleError)) {
          throw error;
        }
        payload = (await recoverQuestionCreate(request, accessToken, created.postId)).web3;
        executeResponse = await executeExecutionIntent(
          request,
          accessToken,
          payload.executionIntent.id,
          wallet,
          payload.signRequest!
        );
      }

      expect(executeResponse.data.executionIntent.id).toBe(payload.executionIntent.id);
      expect(executeResponse.data.executionIntent.status).toBe("PENDING_ONCHAIN");
      expect(executeResponse.data.transaction?.id).toBeTruthy();

      const pending = await waitForIntentStatus(
        request,
        accessToken,
        payload.executionIntent.id,
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

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        reward,
        "답변 create 전 질문 확정 테스트",
        "Suite E 질문 본문"
      );

      expect(
        confirmedQuestion.intent.executionIntent.status,
        `question intent failed before answer create. txHash=${confirmedQuestion.intent.transaction?.txHash ?? "n/a"}`
      ).toBe("CONFIRMED");
      await registerWallet(request, responder.accessToken, ENV.QNA_TEST_RESPONDER_PRIVATE_KEY);

      const createdAnswer = await createAnswer(
        request,
        responder.accessToken,
        confirmedQuestion.postId,
        "Playwright responder answer"
      );

      expect(createdAnswer.web3.actionType).toBe("QNA_ANSWER_SUBMIT");
      expect(createdAnswer.web3.resource.type).toBe("ANSWER");
      expect(createdAnswer.web3.executionIntent.id).toBeTruthy();
      expect(createdAnswer.web3.executionIntent.status).toBe("AWAITING_SIGNATURE");
      expectExecutionMode(createdAnswer.web3.execution, createdAnswer.web3.signRequest);

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

test.describe("Suite F — 질문 update/delete on-chain lifecycle", () => {
  test(
    "TC-QNA-F-01: question update execute → CONFIRMED 후 상세 조회 content 가 유지된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasQuestionInfra(), "RPC / QnA contract / reward token / funded asker wallet env is required");

      const reward = 10;
      const { accessToken } = await signUpAndLogin(request, "f01");
      const askerWallet = await registerWallet(
        request,
        accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        accessToken,
        askerWallet,
        reward,
        "질문 update baseline",
        "question-original-content"
      );

      const updated = await updateQuestion(
        request,
        accessToken,
        confirmedQuestion.postId,
        "question-updated-content"
      );

      expect(updated.web3.actionType).toBe("QNA_QUESTION_UPDATE");
      await executeAndAwaitConfirmed(
        request,
        accessToken,
        askerWallet,
        updated.web3,
        async () =>
          (await updateQuestion(
            request,
            accessToken,
            confirmedQuestion.postId,
            "question-updated-content"
          )).web3
      );

      const postDetail = await getPostDetail(request, null, confirmedQuestion.postId);
      expect(postDetail.status).toBe(200);
      expect(postDetail.data?.content).toBe("question-updated-content");
      expect(postDetail.data?.question?.isSolved).toBe(false);
    }
  );

  test(
    "TC-QNA-F-02: question delete execute → CONFIRMED 후 상세 조회에서 제거된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasQuestionInfra(), "RPC / QnA contract / reward token / funded asker wallet env is required");

      const reward = 10;
      const { accessToken } = await signUpAndLogin(request, "f02");
      const askerWallet = await registerWallet(
        request,
        accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        accessToken,
        askerWallet,
        reward,
        "질문 delete baseline",
        "question-delete-content"
      );

      const deleted = await deleteQuestion(request, accessToken, confirmedQuestion.postId);
      expect(deleted.web3.actionType).toBe("QNA_QUESTION_DELETE");
      await executeAndAwaitConfirmed(
        request,
        accessToken,
        askerWallet,
        deleted.web3,
        async () => (await deleteQuestion(request, accessToken, confirmedQuestion.postId)).web3
      );

      const postDetail = await getPostDetail(request, null, confirmedQuestion.postId);
      expect(postDetail.status).toBeGreaterThanOrEqual(400);
      expect(postDetail.status).toBeLessThan(500);
    }
  );
});

test.describe("Suite G — answer submit/update/delete/accept on-chain lifecycle", () => {
  test(
    "TC-QNA-G-01: answer submit execute → CONFIRMED 후 답변 row 가 유지된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasAnswerInfra(), "RPC / contracts / funded asker+responder wallet env is required");

      const reward = 10;
      const asker = await signUpAndLogin(request, "g01-asker");
      const responder = await signUpAndLogin(request, "g01-responder");

      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      const responderWallet = await registerWallet(
        request,
        responder.accessToken,
        ENV.QNA_TEST_RESPONDER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        reward,
        "answer submit baseline",
        "question-for-answer-submit"
      );

      const confirmedAnswer = await createAnswerAndConfirm(
        request,
        responder.accessToken,
        responderWallet,
        confirmedQuestion.postId,
        "answer-submit-content"
      );

      const answers = await getAnswers(request, responder.accessToken, confirmedQuestion.postId);
      const row = answers.data.find(answer => answer.answerId === confirmedAnswer.answerId);
      expect(row?.content).toBe("answer-submit-content");
      expect(row?.isAccepted).toBe(false);
    }
  );

  test(
    "TC-QNA-G-02: answer update execute → CONFIRMED 후 답변 content 가 갱신된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasAnswerInfra(), "RPC / contracts / funded asker+responder wallet env is required");

      const reward = 10;
      const asker = await signUpAndLogin(request, "g02-asker");
      const responder = await signUpAndLogin(request, "g02-responder");

      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      const responderWallet = await registerWallet(
        request,
        responder.accessToken,
        ENV.QNA_TEST_RESPONDER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        reward,
        "answer update baseline",
        "question-for-answer-update"
      );
      const confirmedAnswer = await createAnswerAndConfirm(
        request,
        responder.accessToken,
        responderWallet,
        confirmedQuestion.postId,
        "answer-before-update"
      );

      const updated = await updateAnswer(
        request,
        responder.accessToken,
        confirmedQuestion.postId,
        confirmedAnswer.answerId,
        "answer-after-update"
      );

      expect(updated.web3.actionType).toBe("QNA_ANSWER_UPDATE");
      await executeAndAwaitConfirmed(
        request,
        responder.accessToken,
        responderWallet,
        updated.web3,
        async () =>
          (await updateAnswer(
            request,
            responder.accessToken,
            confirmedQuestion.postId,
            confirmedAnswer.answerId,
            "answer-after-update"
          )).web3
      );

      const answers = await getAnswers(request, responder.accessToken, confirmedQuestion.postId);
      const row = answers.data.find(answer => answer.answerId === confirmedAnswer.answerId);
      expect(row?.content).toBe("answer-after-update");
      expect(row?.isAccepted).toBe(false);
    }
  );

  test(
    "TC-QNA-G-03: answer delete execute → CONFIRMED 후 답변 목록에서 제거된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasAnswerInfra(), "RPC / contracts / funded asker+responder wallet env is required");

      const reward = 10;
      const asker = await signUpAndLogin(request, "g03-asker");
      const responder = await signUpAndLogin(request, "g03-responder");

      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      const responderWallet = await registerWallet(
        request,
        responder.accessToken,
        ENV.QNA_TEST_RESPONDER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        reward,
        "answer delete baseline",
        "question-for-answer-delete"
      );
      const confirmedAnswer = await createAnswerAndConfirm(
        request,
        responder.accessToken,
        responderWallet,
        confirmedQuestion.postId,
        "answer-before-delete"
      );

      const deleted = await deleteAnswer(
        request,
        responder.accessToken,
        confirmedQuestion.postId,
        confirmedAnswer.answerId
      );

      expect(deleted.web3.actionType).toBe("QNA_ANSWER_DELETE");
      await executeAndAwaitConfirmed(
        request,
        responder.accessToken,
        responderWallet,
        deleted.web3,
        async () =>
          (await deleteAnswer(
            request,
            responder.accessToken,
            confirmedQuestion.postId,
            confirmedAnswer.answerId
          )).web3
      );

      const answers = await getAnswers(request, responder.accessToken, confirmedQuestion.postId);
      const row = answers.data.find(answer => answer.answerId === confirmedAnswer.answerId);
      expect(row).toBeUndefined();
    }
  );

  test(
    "TC-QNA-G-04: answer accept execute → CONFIRMED 후 질문 solved / 답변 accepted 상태가 반영된다",
    { tag: ["@requires-escrow-infra", "@requires-rpc", "@requires-funded-wallet"] },
    async ({ request }) => {
      test.skip(!hasAnswerInfra(), "RPC / contracts / funded asker+responder wallet env is required");

      const reward = 10;
      const asker = await signUpAndLogin(request, "g04-asker");
      const responder = await signUpAndLogin(request, "g04-responder");

      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      const responderWallet = await registerWallet(
        request,
        responder.accessToken,
        ENV.QNA_TEST_RESPONDER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, reward);

      const confirmedQuestion = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        reward,
        "answer accept baseline",
        "question-for-answer-accept"
      );
      const confirmedAnswer = await createAnswerAndConfirm(
        request,
        responder.accessToken,
        responderWallet,
        confirmedQuestion.postId,
        "answer-for-accept"
      );

      const accepted = await acceptAnswer(
        request,
        asker.accessToken,
        confirmedQuestion.postId,
        confirmedAnswer.answerId
      );

      expect(accepted.status).toBe("PENDING_ACCEPT");
      expect(accepted.web3.actionType).toBe("QNA_ANSWER_ACCEPT");
      await executeAndAwaitConfirmed(
        request,
        asker.accessToken,
        askerWallet,
        accepted.web3,
        async () =>
          (await acceptAnswer(
            request,
            asker.accessToken,
            confirmedQuestion.postId,
            confirmedAnswer.answerId
          )).web3
      );

      const postDetail = await getPostDetail(request, null, confirmedQuestion.postId);
      expect(postDetail.status).toBe(200);
      expect(postDetail.data?.question?.isSolved).toBe(true);

      const answers = await getAnswers(request, responder.accessToken, confirmedQuestion.postId);
      const row = answers.data.find(answer => answer.answerId === confirmedAnswer.answerId);
      expect(row?.isAccepted).toBe(true);
      expect(row?.content).toBe("answer-for-accept");
    }
  );
});
