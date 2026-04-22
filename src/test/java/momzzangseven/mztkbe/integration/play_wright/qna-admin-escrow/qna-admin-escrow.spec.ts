import { APIRequestContext, expect, test } from "@playwright/test";
import { randomUUID } from "crypto";
import * as dotenv from "dotenv";
import { ethers } from "ethers";
import { Pool } from "pg";
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

const REWARD_MZTK = 50;
const INTENT_TIMEOUT_MS = 90_000;
const RECEIPT_TIMEOUT_MS = 180_000;
const USER_EXECUTE_TIMEOUT_MS = 30_000;
const USER_CONFIRM_TIMEOUT_MS = 5 * 60 * 1000;
const POLL_INTERVAL_MS = 3_000;
const ADMIN_LOGIN_PASSWORD = "AdminPass123!";
const ADMIN_LOGIN_PASSWORD_HASH =
  "########################################################################"; // bcrypt hash of ADMIN_LOGIN_PASSWORD

test.afterAll(async () => {
  await db.end();
});

interface AuthResult {
  accessToken: string;
  userId: number;
  email: string;
  password: string;
}

interface UserExecutionWritePayload {
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

interface UserExecutionIntentReadData {
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

interface ExecutionIntentEnvelope {
  data?: {
    executionIntent: {
      id: string;
      status: string;
    };
    execution: {
      mode: string;
    };
    transaction?: {
      id: number;
      status: string;
      txHash?: string;
    };
  };
}

class NonceStaleError extends Error {}

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

test.describe("QnA Admin Escrow Playwright E2E", () => {
  test(
    "[P-1] admin settlement review/settle broadcasts and mines an on-chain admin settle tx",
    { tag: ["@requires-rpc", "@requires-internal-issuer"] },
    async ({ request }) => {
      test.skip(
        !hasAnswerInfra(),
        "RPC / QnA contract / reward token / funded asker+responder wallet env is required"
      );

      const admin = await createAdminAndLogin(request, "settle-admin");
      const asker = await signUpAndLogin(request, "settle-asker");
      const responder = await signUpAndLogin(request, "settle-responder");
      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, REWARD_MZTK);
      const { postId } = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        REWARD_MZTK,
        `QNA Admin Settle ${randomUUID()}`,
        "playwright admin settle question"
      );

      const responderWallet = await registerWallet(
        request,
        responder.accessToken,
        ENV.QNA_TEST_RESPONDER_PRIVATE_KEY
      );
      const { answerId } = await createAnswerAndConfirm(
        request,
        responder.accessToken,
        responderWallet,
        postId,
        "playwright admin settle answer"
      );

      const reviewRes = await request.get(
        `${ENV.BACKEND_URL}/admin/web3/qna/questions/${postId}/answers/${answerId}/settlement-review`,
        { headers: authHeaders(admin.accessToken) }
      );
      expect(
        reviewRes.status(),
        `settlement review failed: ${await reviewRes.text()}`
      ).toBe(200);
      const reviewBody = await reviewRes.json();
      expect(reviewBody.status).toBe("SUCCESS");
      expect(reviewBody.data.processable).toBe(true);

      const settleRes = await request.post(
        `${ENV.BACKEND_URL}/admin/web3/qna/questions/${postId}/answers/${answerId}/settle`,
        { headers: authHeaders(admin.accessToken) }
      );
      expect(
        settleRes.status(),
        `settle failed: ${await settleRes.text()}`
      ).toBe(200);
      const settleBody = await settleRes.json();
      expect(settleBody.status).toBe("SUCCESS");
      expect(settleBody.data.actionType).toBe("QNA_ADMIN_SETTLE");
      expect(settleBody.data.execution.mode).toBe("EIP1559");

      const publicId = settleBody.data.executionIntent.id as string;
      const finalState = await waitForIntentProgress(
        request,
        asker.accessToken,
        publicId,
        INTENT_TIMEOUT_MS
      );

      expect(["PENDING_ONCHAIN", "CONFIRMED"]).toContain(
        finalState.executionIntent.status
      );
      const txHash =
        finalState.transaction?.txHash ?? (await loadTransactionHash(publicId));
      expect(txHash).toBeTruthy();

      const receipt = await waitForReceipt(txHash, RECEIPT_TIMEOUT_MS);
      expect(receipt?.status === 1 || receipt?.status === 1n).toBe(true);
      expect(await adminAuditCount("QNA_ADMIN_SETTLE", `post:${postId}`)).toBeGreaterThanOrEqual(
        1
      );
    }
  );

  test(
    "[P-2] admin refund review/refund broadcasts and mines an on-chain admin refund tx",
    { tag: ["@requires-rpc", "@requires-internal-issuer"] },
    async ({ request }) => {
      test.skip(
        !hasQuestionInfra(),
        "RPC / QnA contract / reward token / funded asker wallet env is required"
      );

      const admin = await createAdminAndLogin(request, "refund-admin");
      const asker = await signUpAndLogin(request, "refund-asker");
      const askerWallet = await registerWallet(
        request,
        asker.accessToken,
        ENV.QNA_TEST_ASKER_PRIVATE_KEY
      );
      await ensureRewardAllowance(ENV.QNA_TEST_ASKER_PRIVATE_KEY, REWARD_MZTK);
      const { postId } = await createQuestionAndConfirm(
        request,
        asker.accessToken,
        askerWallet,
        REWARD_MZTK,
        `QNA Admin Refund ${randomUUID()}`,
        "playwright admin refund question"
      );

      const reviewRes = await request.get(
        `${ENV.BACKEND_URL}/admin/web3/qna/questions/${postId}/refund-review`,
        { headers: authHeaders(admin.accessToken) }
      );
      expect(
        reviewRes.status(),
        `refund review failed: ${await reviewRes.text()}`
      ).toBe(200);
      const reviewBody = await reviewRes.json();
      expect(reviewBody.status).toBe("SUCCESS");
      expect(reviewBody.data.processable).toBe(true);

      const refundRes = await request.post(
        `${ENV.BACKEND_URL}/admin/web3/qna/questions/${postId}/refund`,
        { headers: authHeaders(admin.accessToken) }
      );
      expect(
        refundRes.status(),
        `refund failed: ${await refundRes.text()}`
      ).toBe(200);
      const refundBody = await refundRes.json();
      expect(refundBody.status).toBe("SUCCESS");
      expect(refundBody.data.actionType).toBe("QNA_ADMIN_REFUND");
      expect(refundBody.data.execution.mode).toBe("EIP1559");

      const publicId = refundBody.data.executionIntent.id as string;
      const finalState = await waitForIntentProgress(
        request,
        asker.accessToken,
        publicId,
        INTENT_TIMEOUT_MS
      );

      expect(["PENDING_ONCHAIN", "CONFIRMED"]).toContain(
        finalState.executionIntent.status
      );
      const txHash =
        finalState.transaction?.txHash ?? (await loadTransactionHash(publicId));
      expect(txHash).toBeTruthy();

      const receipt = await waitForReceipt(txHash, RECEIPT_TIMEOUT_MS);
      expect(receipt?.status === 1 || receipt?.status === 1n).toBe(true);
      expect(await adminAuditCount("QNA_ADMIN_REFUND", `post:${postId}`)).toBeGreaterThanOrEqual(1);
    }
  );
});

async function signUpAndLogin(
  request: APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const ts = Date.now();
  const email = `playwright-qna-admin-${suffix}-${ts}@test.com`;
  const password = "Test1234!";
  const nickname = `qna-admin-${suffix}-${ts}`;

  const signUpRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname },
  });
  expect(signUpRes.ok(), `signup failed: ${await signUpRes.text()}`).toBeTruthy();
  const signUpBody = await signUpRes.json();

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL", email, password },
  });
  expect(loginRes.ok(), `login failed: ${await loginRes.text()}`).toBeTruthy();
  const loginBody = await loginRes.json();

  return {
    userId: signUpBody.data.userId as number,
    accessToken: loginBody.data.accessToken as string,
    email,
    password,
  };
}

async function createAdminAndLogin(
  request: APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const localUser = await signUpAndLogin(request, suffix);

  await db.query("update users set role = 'ADMIN_GENERATED' where id = $1", [
    localUser.userId,
  ]);

  const loginId = String(10_000_000 + Math.floor(Math.random() * 90_000_000));
  await db.query(
    `insert into admin_accounts (
        user_id,
        login_id,
        password_hash,
        created_by,
        last_login_at,
        password_last_rotated_at,
        deleted_at,
        created_at,
        updated_at
      )
      values ($1, $2, $3, null, null, null, null, now(), now())`,
    [localUser.userId, loginId, ADMIN_LOGIN_PASSWORD_HASH]
  );

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      provider: "LOCAL_ADMIN",
      loginId,
      password: ADMIN_LOGIN_PASSWORD,
    },
  });
  expect(
    loginRes.ok(),
    `admin re-login failed: ${await loginRes.text()}`
  ).toBeTruthy();
  const loginBody = await loginRes.json();

  return {
    ...localUser,
    accessToken: loginBody.data.accessToken as string,
  };
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
): Promise<{ postId: number; web3: UserExecutionWritePayload }> {
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

  return {
    postId: body.data.postId as number,
    web3: body.data.web3 as UserExecutionWritePayload,
  };
}

async function createAnswer(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  content: string
): Promise<{ answerId: number; web3: UserExecutionWritePayload }> {
  const res = await request.post(`${ENV.BACKEND_URL}/questions/${postId}/answers`, {
    headers: authHeaders(accessToken),
    data: {
      content,
      imageIds: [],
    },
  });
  expect(res.status(), `answer create failed: ${await res.text()}`).toBe(201);
  const body = await res.json();

  return {
    answerId: body.data.answerId as number,
    web3: body.data.web3 as UserExecutionWritePayload,
  };
}

async function recoverQuestionCreate(
  request: APIRequestContext,
  accessToken: string,
  postId: number
): Promise<UserExecutionWritePayload> {
  const res = await request.post(
    `${ENV.BACKEND_URL}/posts/${postId}/web3/recover-create`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );
  expect(res.status(), `question recover-create failed: ${await res.text()}`).toBe(200);
  const body = await res.json();
  return body.data.web3 as UserExecutionWritePayload;
}

async function recoverAnswerCreate(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  answerId: number
): Promise<UserExecutionWritePayload> {
  const res = await request.post(
    `${ENV.BACKEND_URL}/questions/${postId}/answers/${answerId}/web3/recover-create`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );
  expect(res.status(), `answer recover-create failed: ${await res.text()}`).toBe(200);
  const body = await res.json();
  return body.data.web3 as UserExecutionWritePayload;
}

async function getExecutionIntent(
  request: APIRequestContext,
  accessToken: string,
  intentId: string
): Promise<UserExecutionIntentReadData> {
  const res = await request.get(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}`,
    { headers: authHeaders(accessToken) }
  );
  expect(res.status(), `execution intent load failed: ${await res.text()}`).toBe(200);
  const body = await res.json();
  return body.data as UserExecutionIntentReadData;
}

async function executeExecutionIntent(
  request: APIRequestContext,
  accessToken: string,
  intentId: string,
  wallet: ethers.Wallet,
  signRequest: SignRequestBundle
): Promise<ExecuteIntentResponseData> {
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
  return body.data as ExecuteIntentResponseData;
}

async function waitForIntentStatus(
  request: APIRequestContext,
  accessToken: string,
  intentId: string,
  expectedStatuses: string[],
  timeoutMs: number
): Promise<UserExecutionIntentReadData> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const data = await getExecutionIntent(request, accessToken, intentId);
    if (expectedStatuses.includes(data.executionIntent.status)) {
      return data;
    }
    await sleep(POLL_INTERVAL_MS);
  }

  throw new Error(
    `execution intent ${intentId} did not reach [${expectedStatuses.join(", ")}] within ${timeoutMs}ms`
  );
}

async function executeAndAwaitConfirmed(
  request: APIRequestContext,
  accessToken: string,
  wallet: ethers.Wallet,
  writePayload: UserExecutionWritePayload,
  recoverWritePayload?: () => Promise<UserExecutionWritePayload>
): Promise<UserExecutionIntentReadData> {
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
    USER_CONFIRM_TIMEOUT_MS
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
): Promise<{ postId: number; intent: UserExecutionIntentReadData }> {
  const created = await createQuestion(request, accessToken, title, content, reward);
  const intent = await executeAndAwaitConfirmed(
    request,
    accessToken,
    wallet,
    created.web3,
    async () => await recoverQuestionCreate(request, accessToken, created.postId)
  );
  return { postId: created.postId, intent };
}

async function createAnswerAndConfirm(
  request: APIRequestContext,
  accessToken: string,
  wallet: ethers.Wallet,
  postId: number,
  content: string
): Promise<{ answerId: number; intent: UserExecutionIntentReadData }> {
  const created = await createAnswer(request, accessToken, postId, content);
  const intent = await executeAndAwaitConfirmed(
    request,
    accessToken,
    wallet,
    created.web3,
    async () => await recoverAnswerCreate(request, accessToken, postId, created.answerId)
  );
  return { answerId: created.answerId, intent };
}

async function waitForIntentProgress(
  request: APIRequestContext,
  accessToken: string,
  publicId: string,
  timeoutMs: number
): Promise<NonNullable<ExecutionIntentEnvelope["data"]>> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const response = await request.get(
      `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${publicId}`,
      {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
      }
    );
    expect(response.ok(), `intent read failed: ${await response.text()}`).toBeTruthy();
    const body = (await response.json()) as ExecutionIntentEnvelope;
    const data = body.data;
    expect(data).toBeDefined();

    const status = data!.executionIntent.status;
    if (status === "PENDING_ONCHAIN" || status === "CONFIRMED") {
      return data!;
    }
    if (
      status === "FAILED_ONCHAIN" ||
      status === "NONCE_STALE" ||
      status === "EXPIRED" ||
      status === "CANCELED"
    ) {
      throw new Error(`execution intent ${publicId} ended with ${status}`);
    }

    await sleep(2_000);
  }

  throw new Error(
    `execution intent ${publicId} did not leave AWAITING_SIGNATURE/SIGNED within ${timeoutMs}ms`
  );
}

async function loadTransactionHash(publicId: string): Promise<string | null> {
  const result = await db.query<{ tx_hash: string | null }>(
    `select tx.tx_hash
       from web3_execution_intents intent
       left join web3_transactions tx on tx.id = intent.submitted_tx_id
      where intent.public_id = $1`,
    [publicId]
  );
  expect(result.rowCount).toBe(1);
  return result.rows[0].tx_hash;
}

async function waitForReceipt(txHash: string, timeoutMs: number) {
  expect(provider).not.toBeNull();
  const receipt = await provider!.waitForTransaction(txHash, 1, timeoutMs);
  if (receipt == null) {
    throw new Error(`transaction ${txHash} was not mined within ${timeoutMs}ms`);
  }
  return receipt;
}

async function adminAuditCount(actionType: string, targetId: string) {
  const result = await db.query<{ count: string }>(
    "select count(*)::text as count from admin_action_audits where action_type = $1 and target_id = $2",
    [actionType, targetId]
  );
  return Number.parseInt(result.rows[0].count, 10);
}

function sleep(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
