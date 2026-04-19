/**
 * MZTK — Web3 Transfer Send Playwright E2E 테스트
 *
 * 목표:
 *   - transfer create 응답이 즉시 Web3 sign payload 를 반환하는지 검증
 *   - transfer read / execution-intent read 가 같은 execution contract 를 반환하는지 검증
 *   - 실제 RPC를 사용해 TRANSFER_SEND execute 후 CONFIRMED + 토큰 잔액 이동을 검증
 */

import { APIRequestContext, expect, test } from "@playwright/test";
import { ethers } from "ethers";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

test.describe.configure({ mode: "serial" });

const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  WEB3_RPC_URL: process.env.WEB3_RPC_URL ?? "",
  WEB3_CHAIN_ID: BigInt(
    process.env.WEB3_EIP712_CHAIN_ID ?? process.env.WEB3_CHAIN_ID ?? "11155420"
  ),
  WEB3_EIP712_DOMAIN_NAME: process.env.WEB3_EIP712_DOMAIN_NAME ?? "MomzzangSeven",
  WEB3_EIP712_DOMAIN_VERSION: process.env.WEB3_EIP712_DOMAIN_VERSION ?? "1",
  WEB3_EIP712_VERIFYING_CONTRACT:
    process.env.WEB3_EIP712_VERIFYING_CONTRACT ?? "",
  WEB3_REWARD_TOKEN_CONTRACT_ADDRESS:
    process.env.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS ??
    process.env.MZTK_TOKEN_CONTRACT_ADDRESS ??
    "",
  WEB3_REWARD_TOKEN_DECIMALS: Number.parseInt(
    process.env.WEB3_REWARD_TOKEN_DECIMALS ?? "18",
    10
  ),
  TRANSFER_TEST_SENDER_PRIVATE_KEY:
    process.env.TRANSFER_TEST_SENDER_PRIVATE_KEY ??
    process.env.QNA_TEST_ASKER_PRIVATE_KEY ??
    "",
  TRANSFER_TEST_RECIPIENT_PRIVATE_KEY:
    process.env.TRANSFER_TEST_RECIPIENT_PRIVATE_KEY ??
    process.env.QNA_TEST_RESPONDER_PRIVATE_KEY ??
    "",
  QNA_TEST_RESPONDER_PRIVATE_KEY: process.env.QNA_TEST_RESPONDER_PRIVATE_KEY ?? "",
  TREASURY_PRIVATE_KEY: process.env.TREASURY_PRIVATE_KEY ?? "",
};

const provider =
  ENV.WEB3_RPC_URL === "" ? null : new ethers.JsonRpcProvider(ENV.WEB3_RPC_URL);

const rewardTokenInterface = new ethers.Interface([
  "function balanceOf(address owner) view returns (uint256)",
  "function transfer(address to, uint256 amount) returns (bool)",
]);

const EXECUTE_TIMEOUT_MS = 30_000;
const CONFIRM_TIMEOUT_MS = 5 * 60 * 1000;
const POLL_INTERVAL_MS = 3_000;
const TRANSFER_AMOUNT_WEI = ethers.parseUnits(
  "1",
  ENV.WEB3_REWARD_TOKEN_DECIMALS
);
const SENDER_TOP_UP_WEI = ethers.parseUnits(
  "2",
  ENV.WEB3_REWARD_TOKEN_DECIMALS
);
const SENDER_NATIVE_TOP_UP_WEI = ethers.parseEther("0.0052");

interface AuthResult {
  accessToken: string;
  userId: number;
}

interface TransferWriteData {
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

interface TransferReadData {
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

type LocalWallet = ethers.Wallet | ethers.HDNodeWallet;

interface TransferScenarioSetup {
  sender: AuthResult;
  recipient: AuthResult;
  senderWallet: LocalWallet;
  recipientWallet: LocalWallet;
}

let sharedSetupPromise: Promise<TransferScenarioSetup> | null = null;

function hasTransferInfra(): boolean {
  return (
    provider != null &&
    ENV.WEB3_EIP712_VERIFYING_CONTRACT !== "" &&
    ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS !== "" &&
    ENV.TRANSFER_TEST_SENDER_PRIVATE_KEY !== ""
  );
}

function authHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  };
}

function walletFromPrivateKey(privateKey: string): LocalWallet {
  return new ethers.Wallet(privateKey, provider ?? undefined);
}

function createEphemeralWallet(): LocalWallet {
  if (provider == null) {
    throw new Error("provider not configured");
  }
  return ethers.Wallet.createRandom().connect(provider);
}

function signRawDigest(wallet: LocalWallet, digest: string): string {
  const signature = wallet.signingKey.sign(digest);
  return ethers.Signature.from(signature).serialized;
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

async function signUpAndLogin(
  request: APIRequestContext,
  suffix: string
): Promise<AuthResult> {
  const ts = Date.now();
  const email = `playwright-transfer-${suffix}-${ts}@test.com`;
  const password = "Test1234!";
  const nickname = `transfer-${suffix}-${ts}`;

  const signUpRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname, provider: "LOCAL" },
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
    accessToken: loginBody.data.accessToken,
    userId: signUpBody.data.userId,
  };
}

async function registerWallet(
  request: APIRequestContext,
  accessToken: string,
  wallet: LocalWallet
): Promise<void> {
  const challengeRes = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
    headers: authHeaders(accessToken),
    data: { purpose: "WALLET_REGISTRATION", walletAddress: wallet.address },
  });
  expect(
    challengeRes.ok(),
    `challenge failed: ${await challengeRes.text()}`
  ).toBeTruthy();
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
  expect(
    registerRes.status(),
    `wallet register failed: ${await registerRes.text()}`
  ).toBe(201);
}

async function createTransfer(
  request: APIRequestContext,
  accessToken: string,
  toUserId: number,
  clientRequestId: string,
  amountWei: bigint
): Promise<{ status: number; data: TransferWriteData }> {
  const res = await request.post(`${ENV.BACKEND_URL}/users/me/transfers`, {
    headers: authHeaders(accessToken),
    data: {
      toUserId,
      clientRequestId,
      amountWei: amountWei.toString(),
    },
  });
  expect(res.status(), `transfer create failed: ${await res.text()}`).toBe(201);
  const body = await res.json();
  return { status: res.status(), data: body.data as TransferWriteData };
}

async function getTransfer(
  request: APIRequestContext,
  accessToken: string,
  resourceId: string
): Promise<{ status: number; data: TransferReadData | null }> {
  const res = await request.get(
    `${ENV.BACKEND_URL}/users/me/transfers/${encodeURIComponent(resourceId)}`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!res.ok()) {
    return { status: res.status(), data: null };
  }
  const body = await res.json();
  return { status: res.status(), data: body.data as TransferReadData };
}

async function getExecutionIntent(
  request: APIRequestContext,
  accessToken: string,
  intentId: string
): Promise<{ status: number; data: ExecutionIntentReadData | null }> {
  const res = await request.get(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${intentId}`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
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
  wallet: LocalWallet,
  signRequest: SignRequestBundle
): Promise<void> {
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
  expect(res.status(), `execution submit failed: ${await res.text()}`).toBe(202);
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

async function rewardBalanceOf(address: string): Promise<bigint> {
  if (provider == null) {
    throw new Error("provider not configured");
  }
  const result = await provider.call({
    to: ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS,
    data: rewardTokenInterface.encodeFunctionData("balanceOf", [address]),
  });
  const [balance] = rewardTokenInterface.decodeFunctionResult("balanceOf", result);
  return balance as bigint;
}

async function selectFundingWallet(): Promise<LocalWallet> {
  if (provider == null) {
    throw new Error("provider not configured");
  }

  const candidateKeys = [
    ENV.TRANSFER_TEST_SENDER_PRIVATE_KEY,
    ENV.QNA_TEST_RESPONDER_PRIVATE_KEY,
    ENV.TREASURY_PRIVATE_KEY,
  ].filter((key, index, all) => key !== "" && all.indexOf(key) === index);

  let lastObservedState = "no candidate funding wallet configured";
  for (const privateKey of candidateKeys) {
    const wallet = walletFromPrivateKey(privateKey);
    const nativeBalance = await provider.getBalance(wallet.address);
    const tokenBalance = await rewardBalanceOf(wallet.address);
    if (
      nativeBalance >= SENDER_NATIVE_TOP_UP_WEI &&
      tokenBalance >= SENDER_TOP_UP_WEI
    ) {
      return wallet;
    }
    lastObservedState = `${wallet.address}: native=${nativeBalance.toString()}, token=${tokenBalance.toString()}`;
  }

  throw new Error(
    `no funding wallet has enough native/token balance (${lastObservedState})`
  );
}

async function fundEphemeralSender(wallet: LocalWallet): Promise<void> {
  if (provider == null) {
    throw new Error("provider not configured");
  }

  const funder = await selectFundingWallet();

  const nativeTx = await funder.sendTransaction({
    to: wallet.address,
    value: SENDER_NATIVE_TOP_UP_WEI,
  });
  await nativeTx.wait();

  const tokenTx = await funder.sendTransaction({
    to: ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS,
    data: rewardTokenInterface.encodeFunctionData("transfer", [
      wallet.address,
      SENDER_TOP_UP_WEI,
    ]),
  });
  await tokenTx.wait();
}

async function ensureSharedSetup(
  request: APIRequestContext
): Promise<TransferScenarioSetup> {
  if (sharedSetupPromise != null) {
    return sharedSetupPromise;
  }

  sharedSetupPromise = (async () => {
    const senderWallet = createEphemeralWallet();
    const recipientWallet = createEphemeralWallet();
    if (senderWallet.address === recipientWallet.address) {
      throw new Error("sender and recipient wallet must be distinct");
    }

    await fundEphemeralSender(senderWallet);

    const sender = await signUpAndLogin(request, "shared-sender");
    const recipient = await signUpAndLogin(request, "shared-recipient");

    await registerWallet(request, sender.accessToken, senderWallet);
    await registerWallet(request, recipient.accessToken, recipientWallet);

    return {
      sender,
      recipient,
      senderWallet,
      recipientWallet,
    };
  })();

  return sharedSetupPromise;
}

test.describe("Web3 transfer send success cases", () => {
  test.skip(!hasTransferInfra(), "transfer on-chain env not configured");

  test("TC-TRANSFER-PW-01 | POST /users/me/transfers returns immediate sign payload", async ({
    request,
  }) => {
    const setup = await ensureSharedSetup(request);

    const created = await createTransfer(
      request,
      setup.sender.accessToken,
      setup.recipient.userId,
      `pw-transfer-create-${Date.now()}`,
      TRANSFER_AMOUNT_WEI
    );

    expect(created.data.resource.type).toBe("TRANSFER");
    expect(created.data.resource.status).toBe("PENDING_EXECUTION");
    expect(created.data.executionIntent.status).toBe("AWAITING_SIGNATURE");
    expect(created.data.executionIntent.id).toBeTruthy();
    expectExecutionMode(created.data.execution, created.data.signRequest);
  });

  test("TC-TRANSFER-PW-02 | GET /users/me/transfers/{resourceId} returns same execution contract", async ({
    request,
  }) => {
    const setup = await ensureSharedSetup(request);

    const created = await createTransfer(
      request,
      setup.sender.accessToken,
      setup.recipient.userId,
      `pw-transfer-get-${Date.now()}`,
      TRANSFER_AMOUNT_WEI
    );

    const loaded = await getTransfer(
      request,
      setup.sender.accessToken,
      created.data.resource.id
    );
    expect(loaded.status).toBe(200);
    expect(loaded.data).not.toBeNull();
    expect(loaded.data!.resource.id).toBe(created.data.resource.id);
    expect(loaded.data!.executionIntent.id).toBe(created.data.executionIntent.id);
    expectExecutionMode(loaded.data!.execution, loaded.data!.signRequest);
  });

  test("TC-TRANSFER-PW-03 | execute transfer confirms on-chain and moves token balance", async ({
    request,
  }) => {
    const setup = await ensureSharedSetup(request);
    const senderBalanceBefore = await rewardBalanceOf(setup.senderWallet.address);
    const recipientBalanceBefore = await rewardBalanceOf(
      setup.recipientWallet.address
    );
    expect(senderBalanceBefore >= TRANSFER_AMOUNT_WEI).toBeTruthy();

    const created = await createTransfer(
      request,
      setup.sender.accessToken,
      setup.recipient.userId,
      `pw-transfer-execute-${Date.now()}`,
      TRANSFER_AMOUNT_WEI
    );

    await executeExecutionIntent(
      request,
      setup.sender.accessToken,
      created.data.executionIntent.id,
      setup.senderWallet,
      created.data.signRequest!
    );

    const pendingIntent = await waitForIntentStatus(
      request,
      setup.sender.accessToken,
      created.data.executionIntent.id,
      ["PENDING_ONCHAIN", "CONFIRMED"],
      EXECUTE_TIMEOUT_MS
    );
    expect(["PENDING_ONCHAIN", "CONFIRMED"]).toContain(
      pendingIntent.executionIntent.status
    );

    const confirmedIntent = await waitForIntentStatus(
      request,
      setup.sender.accessToken,
      created.data.executionIntent.id,
      ["CONFIRMED"],
      CONFIRM_TIMEOUT_MS
    );
    expect(confirmedIntent.transaction?.txHash).toBeTruthy();
    expect(confirmedIntent.transaction?.status).toBe("SUCCEEDED");

    const loadedTransfer = await getTransfer(
      request,
      setup.sender.accessToken,
      created.data.resource.id
    );
    expect(loadedTransfer.status).toBe(200);
    expect(loadedTransfer.data?.executionIntent.status).toBe("CONFIRMED");
    expect(loadedTransfer.data?.transaction?.txHash).toBeTruthy();

    const senderBalanceAfter = await rewardBalanceOf(setup.senderWallet.address);
    const recipientBalanceAfter = await rewardBalanceOf(
      setup.recipientWallet.address
    );

    expect(senderBalanceAfter).toBe(senderBalanceBefore - TRANSFER_AMOUNT_WEI);
    expect(recipientBalanceAfter).toBe(
      recipientBalanceBefore + TRANSFER_AMOUNT_WEI
    );
  });
});
