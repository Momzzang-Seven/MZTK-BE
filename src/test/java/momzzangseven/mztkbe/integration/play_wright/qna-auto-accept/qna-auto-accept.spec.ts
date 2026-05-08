import { APIRequestContext, expect, test } from "@playwright/test";
import { randomUUID } from "crypto";
import * as dotenv from "dotenv";
import { ethers } from "ethers";
import { Pool } from "pg";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

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
  WEB3_CHAIN_ID: BigInt(process.env.WEB3_CHAIN_ID ?? "11155420"),
  EXECUTION_SIGNER_ALIAS: process.env.EXECUTION_SIGNER_ALIAS ?? "sponsor-treasury",
};

const provider = new ethers.JsonRpcProvider(ENV.WEB3_RPC_URL);
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

const adminSettleInterface = new ethers.Interface([
  "function adminSettle(bytes32 questionId, bytes32 answerId, bytes32 questionHash, bytes32 contentHash)",
]);

test.afterAll(async () => {
  await db.end();
});

interface AuthResult {
  userId: number;
  accessToken: string;
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

test.describe("QnA Auto Accept Playwright E2E", () => {
  test(
    "TC-QAA-PW-01: seeded admin-settle intent leaves AWAITING_SIGNATURE through internal issuer",
    { tag: ["@requires-escrow-infra", "@requires-rpc"] },
    async ({ request }) => {
      test.skip(
        ENV.WEB3_RPC_URL === "" || ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS === "",
        "WEB3 RPC / escrow contract env is required"
      );

      const asker = await signUpAndLogin(request, "qaa-asker");
      const responder = await signUpAndLogin(request, "qaa-responder");
      const signerAddress = await loadSignerAddress();
      const nonce = await provider.getTransactionCount(signerAddress, "pending");
      const feeData = await provider.getFeeData();
      const maxPriorityFeePerGas =
        feeData.maxPriorityFeePerGas ?? ethers.parseUnits("2", "gwei");
      const maxFeePerGas = feeData.maxFeePerGas ?? ethers.parseUnits("30", "gwei");

      const seeded = await seedOverdueScenario(asker.userId, responder.userId);
      const publicId = await insertAdminSettleIntent(
        asker.userId,
        responder.userId,
        seeded.postId,
        seeded.answerId,
        seeded.questionHash,
        seeded.answerHash,
        signerAddress,
        nonce,
        maxPriorityFeePerGas,
        maxFeePerGas
      );

      const finalState = await waitForIntentStatus(
        request,
        asker.accessToken,
        publicId,
        30_000
      );

      expect(finalState.executionIntent.id).toBe(publicId);
      expect(finalState.execution.mode).toBe("EIP1559");
      expect(["SIGNED", "PENDING_ONCHAIN", "FAILED_ONCHAIN", "CONFIRMED"]).toContain(
        finalState.executionIntent.status
      );
      expect(finalState.executionIntent.status).not.toBe("AWAITING_SIGNATURE");
      if (
        ["PENDING_ONCHAIN", "FAILED_ONCHAIN", "CONFIRMED"].includes(
          finalState.executionIntent.status
        )
      ) {
        expect(finalState.transaction?.id).toBeTruthy();
        expect(finalState.transaction?.txHash).toBeTruthy();
      }
    }
  );
});

async function signUpAndLogin(
  request: APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const ts = Date.now();
  const email = `playwright-qna-auto-${suffix}-${ts}@test.com`;
  const password = "Test1234!";
  const nickname = `qaa-${suffix}-${ts}`;

  const signUpRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname },
  });
  expect(signUpRes.ok()).toBeTruthy();
  const signUpBody = await signUpRes.json();

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, provider: "LOCAL" },
  });
  expect(loginRes.ok()).toBeTruthy();
  const loginBody = await loginRes.json();

  return {
    userId: signUpBody.data.userId as number,
    accessToken: loginBody.data.accessToken as string,
  };
}

async function loadSignerAddress(): Promise<string> {
  const result = await db.query<{ treasury_address: string }>(
    "select treasury_address from web3_treasury_wallets where wallet_alias = $1 limit 1",
    [ENV.EXECUTION_SIGNER_ALIAS]
  );
  expect(result.rowCount).toBe(1);
  return ethers.getAddress(result.rows[0].treasury_address).toLowerCase();
}

async function seedOverdueScenario(askerUserId: number, responderUserId: number) {
  const questionContent = "playwright overdue question";
  const answerContent = "playwright first responder answer";
  const questionHash = ethers.keccak256(ethers.toUtf8Bytes(questionContent));
  const answerHash = ethers.keccak256(ethers.toUtf8Bytes(answerContent));
  const now = new Date();
  const answerCreatedAt = new Date(now.getTime() - 8 * 24 * 60 * 60 * 1000);

  const postInsert = await db.query<{ id: number }>(
    `insert into posts (user_id, type, title, content, reward, status, created_at, updated_at)
     values ($1, 'QUESTION', $2, $3, 50, 'OPEN', $4, $4)
     returning id`,
    [askerUserId, `QAA ${randomUUID()}`, questionContent, answerCreatedAt]
  );
  const postId = postInsert.rows[0].id;

  const answerInsert = await db.query<{ id: number }>(
    `insert into answers (post_id, user_id, content, is_accepted, created_at, updated_at)
     values ($1, $2, $3, false, $4, $4)
     returning id`,
    [postId, responderUserId, answerContent, answerCreatedAt]
  );
  const answerId = answerInsert.rows[0].id;

  await db.query(
    `insert into web3_qna_questions
      (post_id, question_id, asker_user_id, token_address, reward_amount_wei, question_hash, accepted_answer_id, answer_count, state, created_at, updated_at)
     values ($1, $2, $3, $4, $5, $6, $7, 1, 1100, $8, $8)`,
    [
      postId,
      bytes32Id(postId),
      askerUserId,
      "0x1111111111111111111111111111111111111111",
      ethers.parseUnits("50", 18).toString(),
      questionHash,
      bytes32Zero(),
      answerCreatedAt,
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
      answerCreatedAt,
    ]
  );

  return { postId, answerId, questionHash, answerHash };
}

async function insertAdminSettleIntent(
  requesterUserId: number,
  responderUserId: number,
  postId: number,
  answerId: number,
  questionHash: string,
  answerHash: string,
  signerAddress: string,
  nonce: number,
  maxPriorityFeePerGas: bigint,
  maxFeePerGas: bigint
): Promise<string> {
  const publicId = randomUUID();
  const now = new Date();
  const callData = adminSettleInterface.encodeFunctionData("adminSettle", [
    bytes32Id(postId),
    bytes32Id(answerId),
    questionHash,
    answerHash,
  ]);
  const payloadSnapshot = JSON.stringify({
    actionType: "QNA_ADMIN_SETTLE",
    postId,
    answerId,
    authorityAddress: null,
    tokenAddress: "0x1111111111111111111111111111111111111111",
    amountWei: ethers.parseUnits("50", 18).toString(),
    questionHash,
    contentHash: answerHash,
    callTarget: ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS,
    callData,
  });
  const unsignedTxSnapshot = JSON.stringify({
    chainId: Number(ENV.WEB3_CHAIN_ID),
    fromAddress: signerAddress,
    toAddress: ENV.WEB3_ESCROW_QNA_CONTRACT_ADDRESS,
    valueWei: "0",
    data: callData,
    expectedNonce: nonce,
    gasLimit: "250000",
    maxPriorityFeePerGas: maxPriorityFeePerGas.toString(),
    maxFeePerGas: maxFeePerGas.toString(),
  });

  await db.query(
    `insert into web3_execution_intents
      (public_id, root_idempotency_key, attempt_no, resource_type, resource_id, action_type, requester_user_id, counterparty_user_id,
       mode, status, payload_hash, payload_snapshot_json, expires_at, unsigned_tx_snapshot, unsigned_tx_fingerprint,
       reserved_sponsor_cost_wei, sponsor_usage_date_kst, created_at, updated_at)
     values
      ($1, $2, 1, 'QUESTION', $3, 'QNA_ADMIN_SETTLE', $4, $5,
       'EIP1559', 'AWAITING_SIGNATURE', $6, $7, $8, $9, $10, 0, current_date, $11, $11)`,
    [
      publicId,
      `pw-auto-accept-${postId}-${answerId}`,
      String(postId),
      requesterUserId,
      responderUserId,
      ethers.keccak256(ethers.toUtf8Bytes(payloadSnapshot)),
      payloadSnapshot,
      new Date(now.getTime() + 5 * 60 * 1000),
      unsignedTxSnapshot,
      ethers.keccak256(ethers.toUtf8Bytes(unsignedTxSnapshot)),
      now,
    ]
  );

  return publicId;
}

async function waitForIntentStatus(
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
    expect(response.ok()).toBeTruthy();
    const body = (await response.json()) as ExecutionIntentEnvelope;
    const data = body.data;
    expect(data).toBeDefined();
    if (data!.executionIntent.status !== "AWAITING_SIGNATURE") {
      return data!;
    }
    await new Promise(resolve => setTimeout(resolve, 2_000));
  }

  throw new Error(`execution intent ${publicId} stayed in AWAITING_SIGNATURE`);
}

function bytes32Id(id: number): string {
  return ethers.zeroPadValue(ethers.toBeHex(BigInt(id)), 32);
}

function bytes32Zero(): string {
  return "0x" + "0".repeat(64);
}
