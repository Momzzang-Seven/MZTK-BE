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
  WEB3_ESCROW_QNA_CONTRACT_ADDRESS:
    process.env.WEB3_ESCROW_QNA_CONTRACT_ADDRESS ?? "",
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

const TOKEN_ADDRESS = "0x1111111111111111111111111111111111111111";
const REWARD_AMOUNT_WEI = ethers.parseUnits("50", 18).toString();
const INTENT_TIMEOUT_MS = 90_000;
const RECEIPT_TIMEOUT_MS = 180_000;

test.afterAll(async () => {
  await db.end();
});

interface AuthResult {
  accessToken: string;
  userId: number;
  email: string;
  password: string;
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

function hasOnchainInfra(): boolean {
  return provider != null && ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS !== "";
}

test.describe("QnA Admin Escrow Playwright E2E", () => {
  test(
    "[P-1] admin settlement review/settle broadcasts and mines an on-chain admin settle tx",
    { tag: ["@requires-rpc", "@requires-internal-issuer"] },
    async ({ request }) => {
      test.skip(!hasOnchainInfra(), "WEB3 RPC / escrow contract env is required");

      const admin = await createAdminAndLogin(request, "settle-admin");
      const asker = await signUpAndLogin(request, "settle-asker");
      const responder = await signUpAndLogin(request, "settle-responder");
      const seeded = await seedSettlementScenario(asker.userId, responder.userId);

      const reviewRes = await request.get(
        `${ENV.BACKEND_URL}/admin/web3/qna/questions/${seeded.postId}/answers/${seeded.answerId}/settlement-review`,
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
        `${ENV.BACKEND_URL}/admin/web3/qna/questions/${seeded.postId}/answers/${seeded.answerId}/settle`,
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
      expect(receipt?.status).toBe(1n);
      expect(await adminAuditCount("QNA_ADMIN_SETTLE", `post:${seeded.postId}`)).toBeGreaterThanOrEqual(1);
    }
  );

  test(
    "[P-2] admin refund review/refund broadcasts and mines an on-chain admin refund tx",
    { tag: ["@requires-rpc", "@requires-internal-issuer"] },
    async ({ request }) => {
      test.skip(!hasOnchainInfra(), "WEB3 RPC / escrow contract env is required");

      const admin = await createAdminAndLogin(request, "refund-admin");
      const asker = await signUpAndLogin(request, "refund-asker");
      const postId = await seedRefundScenario(asker.userId);

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
      expect(receipt?.status).toBe(1n);
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

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      provider: "LOCAL",
      email: localUser.email,
      password: localUser.password,
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

async function seedSettlementScenario(askerUserId: number, responderUserId: number) {
  const questionContent = "playwright admin settle question";
  const answerContent = "playwright admin settle answer";
  const createdAt = new Date(Date.now() - 2 * 60 * 60 * 1000);
  const questionHash = ethers.keccak256(ethers.toUtf8Bytes(questionContent));
  const answerHash = ethers.keccak256(ethers.toUtf8Bytes(answerContent));

  const postInsert = await db.query<{ id: number }>(
    `insert into posts (user_id, type, title, content, reward, status, created_at, updated_at)
     values ($1, 'QUESTION', $2, $3, 50, 'OPEN', $4, $4)
     returning id`,
    [askerUserId, `QNA Admin Settle ${randomUUID()}`, questionContent, createdAt]
  );
  const postId = postInsert.rows[0].id;

  const answerInsert = await db.query<{ id: number }>(
    `insert into answers (post_id, user_id, content, is_accepted, created_at, updated_at)
     values ($1, $2, $3, false, $4, $4)
     returning id`,
    [postId, responderUserId, answerContent, createdAt]
  );
  const answerId = answerInsert.rows[0].id;

  await db.query(
    `insert into web3_qna_questions
      (post_id, question_id, asker_user_id, token_address, reward_amount_wei, question_hash,
       accepted_answer_id, answer_count, state, created_at, updated_at)
     values ($1, $2, $3, $4, $5, $6, $7, 1, 1100, $8, $8)`,
    [
      postId,
      bytes32Id(postId),
      askerUserId,
      TOKEN_ADDRESS,
      REWARD_AMOUNT_WEI,
      questionHash,
      bytes32Zero(),
      createdAt,
    ]
  );

  await db.query(
    `insert into web3_qna_answers
      (answer_id, post_id, question_id, answer_key, responder_user_id, content_hash, accepted, created_at, updated_at)
     values ($1, $2, $3, $4, $5, $6, false, $7, $7)`,
    [
      answerId,
      postId,
      bytes32Id(postId),
      bytes32Id(answerId),
      responderUserId,
      answerHash,
      createdAt,
    ]
  );

  return { postId, answerId };
}

async function seedRefundScenario(askerUserId: number) {
  const questionContent = "playwright admin refund question";
  const createdAt = new Date(Date.now() - 60 * 60 * 1000);
  const questionHash = ethers.keccak256(ethers.toUtf8Bytes(questionContent));

  const postInsert = await db.query<{ id: number }>(
    `insert into posts (user_id, type, title, content, reward, status, created_at, updated_at)
     values ($1, 'QUESTION', $2, $3, 50, 'OPEN', $4, $4)
     returning id`,
    [askerUserId, `QNA Admin Refund ${randomUUID()}`, questionContent, createdAt]
  );
  const postId = postInsert.rows[0].id;

  await db.query(
    `insert into web3_qna_questions
      (post_id, question_id, asker_user_id, token_address, reward_amount_wei, question_hash,
       accepted_answer_id, answer_count, state, created_at, updated_at)
     values ($1, $2, $3, $4, $5, $6, $7, 0, 1000, $8, $8)`,
    [
      postId,
      bytes32Id(postId),
      askerUserId,
      TOKEN_ADDRESS,
      REWARD_AMOUNT_WEI,
      questionHash,
      bytes32Zero(),
      createdAt,
    ]
  );

  return postId;
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

function bytes32Id(value: number) {
  return ethers.toBeHex(BigInt(value), 32);
}

function bytes32Zero() {
  return `0x${"0".repeat(64)}`;
}

function sleep(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
