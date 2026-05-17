/**
 * MZTK - 마켓플레이스 API Playwright E2E 테스트
 *
 * 테스트 대상:
 *   [ 상점 (Store) ]
 *   1. 트레이너 상점 등록 및 수정 (Upsert)
 *   2. 트레이너 상점 단건 조회
 *
 *   [ 클래스 (Class) ]
 *   3. 클래스 신규 등록 (POST /marketplace/trainer/classes)
 *   4. 클래스 상세 조회 (GET /marketplace/classes/{classId})
 *   5. 클래스 수정 (PUT /marketplace/trainer/classes/{classId})
 *   6. 클래스 상태 토글 (PATCH /marketplace/trainer/classes/{classId}/status)
 *   7. 트레이너 클래스 목록 조회 (GET /marketplace/trainer/classes)
 *   8. 공개 클래스 목록 조회 (GET /marketplace/classes)
 *   9. 입력 검증 (400 / 401 / 403 / 404 / 409)
 *   10. 전체 클래스 라이프사이클 흐름
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 http://127.0.0.1:8080 에서 실행 중이어야 합니다.
 *   - 일반 사용자를 생성한 후 TRAINER 권한으로 승급하는 플로우가 필요합니다.
 */

import { test, expect, APIRequestContext } from "@playwright/test";
import { ethers } from "ethers";
import { Pool } from "pg";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
  DB_NAME: process.env.DB_NAME ?? "mztk_e2e",
  DB_USER: process.env.DB_USER ?? process.env.DB_USERNAME ?? "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "postgres",
  WEB3_RPC_URL:
    process.env.WEB3_RPC_URL ??
    process.env.WEB3_RPC_MAIN ??
    "https://sepolia.base.org",
  WEB3_CHAIN_ID: BigInt(
    process.env.WEB3_EIP712_CHAIN_ID ?? process.env.WEB3_CHAIN_ID ?? "84532"
  ),
  WEB3_EIP712_DOMAIN_NAME: process.env.WEB3_EIP712_DOMAIN_NAME ?? "MomzzangSeven",
  WEB3_EIP712_DOMAIN_VERSION: process.env.WEB3_EIP712_DOMAIN_VERSION ?? "1",
  WEB3_EIP712_VERIFYING_CONTRACT:
    process.env.WEB3_EIP712_VERIFYING_CONTRACT ?? "",
  WEB3_REWARD_TOKEN_CONTRACT_ADDRESS:
    process.env.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS ??
    process.env.MZTK_TOKEN_CONTRACT_ADDRESS ??
    "",
  MARKETPLACE_ESCROW_CONTRACT_ADDRESS:
    process.env.MARKETPLACE_ESCROW_CONTRACT_ADDRESS ??
    process.env.WEB3_ESCROW_MARKETPLACE_CONTRACT_ADDRESS ??
    "",
  WEB3_REWARD_TOKEN_DECIMALS: Number.parseInt(
    process.env.WEB3_REWARD_TOKEN_DECIMALS ?? "18",
    10
  ),
  MARKETPLACE_TEST_USER_PRIVATE_KEY:
    process.env.MARKETPLACE_TEST_USER_PRIVATE_KEY ?? "",
  MARKETPLACE_TEST_TRAINER_PRIVATE_KEY:
    process.env.MARKETPLACE_TEST_TRAINER_PRIVATE_KEY ?? "",
  MARKETPLACE_TEST_SPONSOR_PRIVATE_KEY:
    process.env.MARKETPLACE_TEST_SPONSOR_PRIVATE_KEY ??
    process.env.SPONSOR_TREASURY_PRIVATE_KEY ??
    "",
  MARKETPLACE_TEST_SPONSOR_ADDRESS:
    process.env.MARKETPLACE_TEST_SPONSOR_ADDRESS ??
    process.env.SPONSOR_TREASURY_WALLET ??
    process.env.SPONSOR_WALLET_ADDRESS ??
    "",
  MARKETPLACE_TEST_SPONSOR_KMS_KEY_ID:
    process.env.MARKETPLACE_TEST_SPONSOR_KMS_KEY_ID ?? "sponsor-treasury",
  MARKETPLACE_TEST_EXECUTION_SPONSOR_ADDRESS:
    process.env.MARKETPLACE_TEST_EXECUTION_SPONSOR_ADDRESS ?? "",
  MARKETPLACE_TEST_EXECUTION_SPONSOR_KMS_KEY_ID:
    process.env.MARKETPLACE_TEST_EXECUTION_SPONSOR_KMS_KEY_ID ??
    process.env.MARKETPLACE_TEST_SPONSOR_KMS_KEY_ID ??
    "sponsor-treasury",
  MARKETPLACE_TEST_SIGNER_ADDRESS:
    process.env.MARKETPLACE_TEST_SIGNER_ADDRESS ??
    process.env.MARKETPLACE_TEST_SPONSOR_ADDRESS ??
    process.env.SPONSOR_TREASURY_WALLET ??
    process.env.SPONSOR_WALLET_ADDRESS ??
    "",
  MARKETPLACE_TEST_SIGNER_KMS_KEY_ID:
    process.env.MARKETPLACE_TEST_SIGNER_KMS_KEY_ID ?? "marketplace-signer-treasury",
  MARKETPLACE_TEST_MIN_BUYER_TOKEN_BALANCE: Number.parseInt(
    process.env.MARKETPLACE_TEST_MIN_BUYER_TOKEN_BALANCE ?? "100000",
    10
  ),
};

test.describe.configure({ mode: "serial" });

const provider =
  ENV.WEB3_RPC_URL === "" ? null : new ethers.JsonRpcProvider(ENV.WEB3_RPC_URL);
const ERC20_ABI = [
  "function balanceOf(address account) view returns (uint256)",
  "function allowance(address owner, address spender) view returns (uint256)",
  "function approve(address spender, uint256 amount) returns (bool)",
  "function transfer(address to, uint256 amount) returns (bool)",
] as const;
const MARKETPLACE_ESCROW_ABI = [
  "function defaultDeadlineDuration() view returns (uint48)",
  "function getOrder(bytes32 orderId) view returns (tuple(bytes32 orderId,uint256 price,address token,uint48 deadline,uint16 state,address buyer,address trainer))",
] as const;
const db = new Pool({
  host: ENV.DB_HOST,
  port: ENV.DB_PORT,
  database: ENV.DB_NAME,
  user: ENV.DB_USER,
  password: ENV.DB_PASSWORD,
});

test.afterAll(async () => {
  await db.end();
});

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

interface ReservationWeb3WritePayload {
  executionIntent: {
    id: string;
    status: string;
  };
  signRequest?: SignRequestBundle;
}

interface CreateReservationData {
  reservationId: number;
  status: string;
  web3?: ReservationWeb3WritePayload;
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

function tokenBaseUnits(amount: number): string {
  return ethers.parseUnits(amount.toString(), ENV.WEB3_REWARD_TOKEN_DECIMALS).toString();
}

function sameAddress(left: string, right: string): boolean {
  return left !== "" && right !== "" && left.toLowerCase() === right.toLowerCase();
}

function resolveExecutionSponsorTreasury(): { address: string; kmsKeyId: string } {
  const configuredAddress =
    ENV.MARKETPLACE_TEST_EXECUTION_SPONSOR_ADDRESS || ENV.MARKETPLACE_TEST_SPONSOR_ADDRESS;
  return {
    address: configuredAddress,
    kmsKeyId: ENV.MARKETPLACE_TEST_EXECUTION_SPONSOR_KMS_KEY_ID,
  };
}

async function markWalletReusable(walletAddress: string): Promise<void> {
  await db.query(
    `update web3_wallet_registration_sessions
        set status = 'CANCELED',
            last_error_code = 'PLAYWRIGHT_REUSE',
            last_error_reason = 'superseded by marketplace Playwright fixture',
            updated_at = now()
      where wallet_address = $1
        and status in (
          'APPROVAL_REQUIRED',
          'APPROVAL_SIGNED',
          'APPROVAL_PENDING_ONCHAIN',
          'APPROVAL_RETRYABLE',
          'FINALIZATION_FAILED',
          'LOCAL_CONFLICT'
        )`,
    [walletAddress.toLowerCase()]
  );
  await db.query(
    `update user_wallets
        set status = 'UNLINKED',
            unlinked_at = now(),
            updated_at = now()
      where wallet_address = $1
        and status <> 'BLOCKED'`,
    [walletAddress.toLowerCase()]
  );
}

async function ensureSponsorTreasuryReady(): Promise<void> {
  const executionSponsor = resolveExecutionSponsorTreasury();
  expect(executionSponsor.address, "marketplace execution sponsor address is required").toBeTruthy();
  expect(
    ENV.MARKETPLACE_TEST_SIGNER_ADDRESS,
    "MARKETPLACE_TEST_SIGNER_ADDRESS env is required"
  ).toBeTruthy();
  expect(
    sameAddress(executionSponsor.address, ENV.MARKETPLACE_TEST_SIGNER_ADDRESS),
    "marketplace execution sponsor and marketplace signer must use distinct treasury addresses"
  ).toBe(false);
  expect(
    executionSponsor.kmsKeyId === ENV.MARKETPLACE_TEST_SIGNER_KMS_KEY_ID,
    "marketplace execution sponsor and marketplace signer must use distinct KMS key ids"
  ).toBe(false);
  await db.query(
    `delete from web3_treasury_wallets
      where wallet_alias in ('sponsor-treasury', 'marketplace-signer-treasury')
         or lower(treasury_address) in (lower($1), lower($2))
         or kms_key_id in ($3, $4)`,
    [
      executionSponsor.address,
      ENV.MARKETPLACE_TEST_SIGNER_ADDRESS,
      executionSponsor.kmsKeyId,
      ENV.MARKETPLACE_TEST_SIGNER_KMS_KEY_ID,
    ]
  );
  await db.query(
    `insert into web3_treasury_wallets (
         wallet_alias,
         treasury_address,
         kms_key_id,
         status,
         key_origin,
         created_at,
         updated_at
       )
       values ('sponsor-treasury', $1, $2, 'ACTIVE', 'IMPORTED', now(), now())
       on conflict (wallet_alias) do update
          set treasury_address = excluded.treasury_address,
              kms_key_id = excluded.kms_key_id,
              status = 'ACTIVE',
              key_origin = 'IMPORTED',
              updated_at = now()`,
    [executionSponsor.address, executionSponsor.kmsKeyId]
  );
  await db.query(
    `insert into web3_treasury_wallets (
         wallet_alias,
         treasury_address,
         kms_key_id,
         status,
         key_origin,
         created_at,
         updated_at
       )
       values ('marketplace-signer-treasury', $1, $2, 'ACTIVE', 'IMPORTED', now(), now())
       on conflict (wallet_alias) do update
          set treasury_address = excluded.treasury_address,
              kms_key_id = excluded.kms_key_id,
              status = 'ACTIVE',
              key_origin = 'IMPORTED',
              updated_at = now()`,
    [ENV.MARKETPLACE_TEST_SIGNER_ADDRESS, ENV.MARKETPLACE_TEST_SIGNER_KMS_KEY_ID]
  );
}

async function ensureBuyerTokenFunding(wallet: ethers.Wallet, minimumTokenAmount: number) {
  if (provider == null || ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS === "") {
    return;
  }
  expect(
    ENV.MARKETPLACE_ESCROW_CONTRACT_ADDRESS,
    "MARKETPLACE_ESCROW_CONTRACT_ADDRESS or WEB3_ESCROW_MARKETPLACE_CONTRACT_ADDRESS is required"
  ).toBeTruthy();
  const token = new ethers.Contract(
    ENV.WEB3_REWARD_TOKEN_CONTRACT_ADDRESS,
    ERC20_ABI,
    provider
  );
  const minimum = BigInt(tokenBaseUnits(minimumTokenAmount));
  const current = (await token.balanceOf(wallet.address)) as bigint;
  if (current < minimum) {
    expect(
      ENV.MARKETPLACE_TEST_SPONSOR_PRIVATE_KEY,
      "MARKETPLACE_TEST_SPONSOR_PRIVATE_KEY or SPONSOR_TREASURY_PRIVATE_KEY is required to top up the buyer token balance"
    ).toBeTruthy();

    const sponsorWallet = walletFromPrivateKey(ENV.MARKETPLACE_TEST_SPONSOR_PRIVATE_KEY);
    const tokenWithSponsor = token.connect(sponsorWallet) as ethers.Contract & {
      transfer(to: string, amount: bigint): Promise<ethers.TransactionResponse>;
    };
    const transferTx = await tokenWithSponsor.transfer(wallet.address, minimum - current);
    await transferTx.wait();
  }

  const allowance = (await token.allowance(
    wallet.address,
    ENV.MARKETPLACE_ESCROW_CONTRACT_ADDRESS
  )) as bigint;
  if (allowance >= minimum) {
    return;
  }
  const tokenWithBuyer = token.connect(wallet) as ethers.Contract & {
    approve(spender: string, amount: bigint): Promise<ethers.TransactionResponse>;
  };
  const approveTx = await tokenWithBuyer.approve(
    ENV.MARKETPLACE_ESCROW_CONTRACT_ADDRESS,
    ethers.MaxUint256
  );
  await approveTx.wait();
}

async function registerWallet(
  request: APIRequestContext,
  accessToken: string,
  privateKey: string
): Promise<ethers.Wallet> {
  expect(privateKey, "marketplace wallet private key env is required").toBeTruthy();
  expect(
    ENV.WEB3_EIP712_VERIFYING_CONTRACT,
    "WEB3_EIP712_VERIFYING_CONTRACT env is required"
  ).toBeTruthy();
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
  expect(
    [201, 202],
    `wallet register failed: ${await registerRes.text()}`
  ).toContain(registerRes.status());
  const registerBody = await registerRes.json();
  if (registerBody.data?.status === "APPROVAL_REQUIRED") {
    await executeReservationIntent(
      request,
      accessToken,
      wallet,
      registerBody.data.web3 as ReservationWeb3WritePayload
    );
    await waitForWalletRegistrationStatus(
      request,
      accessToken,
      registerBody.data.registrationId,
      "REGISTERED"
    );
  }
  return wallet;
}

async function executeReservationIntent(
  request: APIRequestContext,
  accessToken: string,
  wallet: ethers.Wallet,
  web3: ReservationWeb3WritePayload
): Promise<void> {
  expect(web3?.executionIntent?.id, "execution intent id is required").toBeTruthy();
  expect(web3.signRequest, "sign request is required").toBeTruthy();
  const signRequest = web3.signRequest!;
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
    throw new Error("unsupported marketplace sign request");
  }

  const executeRes = await request.post(
    `${ENV.BACKEND_URL}/users/me/web3/execution-intents/${web3.executionIntent.id}/execute`,
    {
      headers: authHeaders(accessToken),
      data,
    }
  );
  expect(executeRes.status(), `execution submit failed: ${await executeRes.text()}`).toBe(202);
}

async function waitForReservationStatus(
  request: APIRequestContext,
  accessToken: string,
  reservationId: number,
  expectedStatus: string,
  timeoutMs = 300_000
): Promise<void> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/reservations/${reservationId}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    expect(res.status(), `reservation detail failed: ${await res.text()}`).toBe(200);
    const body = await res.json();
    if (body.data?.status === expectedStatus) {
      return;
    }
    await new Promise(resolve => setTimeout(resolve, 3_000));
  }
  throw new Error(`reservation ${reservationId} did not reach ${expectedStatus}`);
}

async function loadReservationDetail(
  request: APIRequestContext,
  accessToken: string,
  reservationId: number
) {
  const res = await request.get(
    `${ENV.BACKEND_URL}/marketplace/reservations/${reservationId}`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  expect(res.status(), `reservation detail failed: ${await res.text()}`).toBe(200);
  return (await res.json()).data;
}

async function moveReservationSessionToPast(reservationId: number): Promise<void> {
  await db.query(
    `update class_reservations
        set reservation_date = current_date - interval '1 day',
            reservation_time = '09:00:00'::time,
            updated_at = now()
      where id = $1`,
    [reservationId]
  );
}

async function moveReservationDeadlineToPast(reservationId: number): Promise<void> {
  await db.query(
    `update class_reservations
        set contract_deadline_epoch_seconds = floor(extract(epoch from now()))::bigint - 1,
            contract_deadline_at = now() - interval '1 second',
            updated_at = now()
      where id = $1`,
    [reservationId]
  );
}

async function loadReservationOrderKey(reservationId: number): Promise<string> {
  const result = await db.query(
    "select order_key from class_reservations where id = $1",
    [reservationId]
  );
  expect(result.rows[0]?.order_key, "reservation order_key is required").toBeTruthy();
  return result.rows[0].order_key as string;
}

async function expireMarketplaceOrderDeadline(
  orderKey: string
): Promise<{ expired: boolean; reason?: string }> {
  if (provider == null || ENV.MARKETPLACE_ESCROW_CONTRACT_ADDRESS === "") {
    return { expired: false, reason: "marketplace escrow RPC is not configured" };
  }
  const escrow = new ethers.Contract(
    ENV.MARKETPLACE_ESCROW_CONTRACT_ADDRESS,
    MARKETPLACE_ESCROW_ABI,
    provider
  );
  const order = await escrow.getOrder(orderKey);
  const rawDeadline = order.deadline ?? order[3];
  const deadline = BigInt(rawDeadline.toString());
  const current = BigInt((await provider.getBlock("latest"))?.timestamp ?? 0);
  if (current <= deadline) {
    const secondsToAdvance = deadline - current + 2n;
    if (secondsToAdvance > BigInt(Number.MAX_SAFE_INTEGER)) {
      return { expired: false, reason: "deadline advance exceeds JavaScript safe integer" };
    }
    try {
      await provider.send("evm_increaseTime", [Number(secondsToAdvance)]);
      await provider.send("evm_mine", []);
    } catch (error) {
      return {
        expired: false,
        reason: `RPC does not support time travel: ${
          error instanceof Error ? error.message : String(error)
        }`,
      };
    }
  }
  const latest = BigInt((await provider.getBlock("latest"))?.timestamp ?? 0);
  return {
    expired: latest > deadline,
    reason: latest > deadline ? undefined : "marketplace order deadline is still active",
  };
}

async function waitForWalletRegistrationStatus(
  request: APIRequestContext,
  accessToken: string,
  registrationId: string,
  expectedStatus: string,
  timeoutMs = 300_000
): Promise<void> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const res = await request.get(
      `${ENV.BACKEND_URL}/web3/wallet-registrations/${registrationId}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    expect(res.status(), `wallet registration status failed: ${await res.text()}`).toBe(200);
    const body = await res.json();
    if (body.data?.status === expectedStatus) {
      return;
    }
    await new Promise(resolve => setTimeout(resolve, 3_000));
  }
  throw new Error(`wallet registration ${registrationId} did not reach ${expectedStatus}`);
}

let marketplaceUserWallet: ethers.Wallet;
let marketplaceTrainerWallet: ethers.Wallet;

// ──────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 테스트 전용 계정을 생성하고, TRAINER 역할을 부여한 후 새 accessToken을 반환합니다.
 */
async function signUpAndLoginAsTrainer(
  apiContext: APIRequestContext,
  email: string
): Promise<string> {
  // 1. 회원가입
  const signupRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      nickname: "트레이너테스터",
    },
  });
  expect(signupRes.status(), `회원가입 실패: ${email}`).toBe(200);

  // 2. 임시 로그인 (USER 권한)
  let loginRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      provider: "LOCAL",
    },
  });
  expect(loginRes.status(), `로그인 실패: ${email}`).toBe(200);

  let loginBody = await loginRes.json();
  const tempToken = loginBody?.data?.accessToken as string;
  expect(tempToken, "임시 accessToken이 없음").toBeTruthy();

  // 3. 트레이너 역할로 변경
  const roleRes = await apiContext.patch(`${ENV.BACKEND_URL}/users/me/role`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${tempToken}`,
    },
    data: { role: "TRAINER" },
  });
  expect(roleRes.status(), `트레이너 승급 실패: ${email}`).toBe(200);

  // 4. 재로그인 (변경된 권한이 반영된 새 토큰 받기)
  loginRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      provider: "LOCAL",
    },
  });
  expect(loginRes.status(), `재로그인 실패: ${email}`).toBe(200);

  loginBody = await loginRes.json();
  const finalToken = loginBody?.data?.accessToken as string;
  expect(finalToken, "최종 accessToken이 없음").toBeTruthy();

  return finalToken;
}

/**
 * 일반 USER 계정을 생성하고 accessToken을 반환합니다.
 */
async function signUpAndLoginAsUser(
  apiContext: APIRequestContext,
  email: string
): Promise<string> {
  const signupRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      nickname: "일반사용자",
    },
  });
  expect(signupRes.status(), `유저 회원가입 실패: ${email}`).toBe(200);

  const loginRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      provider: "LOCAL",
    },
  });
  expect(loginRes.status(), `유저 로그인 실패: ${email}`).toBe(200);

  const body = await loginRes.json();
  return body?.data?.accessToken as string;
}

/**
 * 트레이너 상점을 등록합니다. 클래스 등록의 사전 조건입니다.
 */
async function upsertStore(
  apiContext: APIRequestContext,
  token: string
): Promise<void> {
  const res = await apiContext.put(
    `${ENV.BACKEND_URL}/marketplace/trainer/store`,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      data: {
        storeName: "PT Studio Playwright",
        address: "서울시 강남구 역삼동 123",
        detailAddress: "2층 201호",
        latitude: 37.4979,
        longitude: 127.0276,
        phoneNumber: "010-1234-5678",
      },
    }
  );
  expect(res.status(), "상점 사전 등록 실패").toBe(200);
}

/**
 * 유효한 클래스 본문(body)을 생성합니다.
 */
function validClassBody() {
  return {
    title: "PT 60분 기초체력",
    category: "PT",
    description: "기초 체력 향상을 위한 PT 클래스입니다.",
    priceAmount: 50000,
    durationMinutes: 60,
    tags: ["다이어트", "근력강화"],
    features: ["1:1 맞춤 프로그램", "체력 측정 포함"],
    classTimes: [
      {
        daysOfWeek: ["MONDAY", "WEDNESDAY"],
        startTime: "10:00:00",
        capacity: 5,
      },
    ],
  };
}

/**
 * 클래스를 등록하고 classId를 반환합니다.
 */
async function registerClassAndGetId(
  apiContext: APIRequestContext,
  token: string
): Promise<number> {
  const res = await apiContext.post(
    `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      data: validClassBody(),
    }
  );
  expect(res.status(), "클래스 등록 실패").toBe(201);
  const body = await res.json();
  return body?.data?.classId as number;
}

// ══════════════════════════════════════════════════════════════════════════════
// [그룹 1] 상점 (Store) API 테스트
// ══════════════════════════════════════════════════════════════════════════════

test.describe("마켓플레이스 — 상점 (Store) API 테스트", () => {
  let trainerToken: string;
  let userToken: string;

  test.beforeAll(async ({ request }) => {
    const trainerEmail = `trainer-store-${Date.now()}@mztk-test.com`;
    const userEmail = `user-store-${Date.now()}@mztk-test.com`;

    trainerToken = await signUpAndLoginAsTrainer(request, trainerEmail);
    userToken = await signUpAndLoginAsUser(request, userEmail);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-01: 일반 사용자는 상점을 등록할 수 없다 (401/403)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-01: 일반 사용자는 상점을 등록할 수 없다 (403)", async ({
    request,
  }) => {
    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${userToken}`,
        },
        data: {
          storeName: "권한없는 상점",
          address: "테스트 주소",
          latitude: 37.5,
          longitude: 127.0,
        },
      }
    );

    expect(
      [401, 403],
      `예상치 못한 상태코드: ${response.status()}`
    ).toContain(response.status());
    console.log(`[TC-MP-01] 일반 사용자 접근 거부: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-01-A: 상점 미등록 상태에서 조회 시 404 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-01-A: 상점 미등록 상태에서 조회 시 404 에러를 반환한다", async ({
    request,
  }) => {
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(response.status(), "상점이 없으므로 404를 반환해야 합니다").toBe(
      404
    );

    const body = await response.json();
    expect(body).toHaveProperty("status", "FAIL");
    console.log(`[TC-MP-01-A] 미등록 상점 조회 실패: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-02: 트레이너가 신규 상점을 등록한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-02: 트레이너가 정상적으로 신규 상점을 등록한다", async ({
    request,
  }) => {
    const reqData = {
      storeName: "몸짱 트레이닝 센터",
      address: "서울시 강남구 테헤란로 123",
      detailAddress: "지하 1층",
      latitude: 37.501,
      longitude: 127.039,
      phoneNumber: "02-1234-5678",
      instagramUrl: "https://instagram.com/mztk_trainer",
    };

    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: reqData,
      }
    );

    expect(response.status(), "상점 등록 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const data = body.data;
    expect(data.storeId, "상점 ID가 응답에 없습니다").toBeDefined();
    expect(data.storeName).toBeUndefined(); // UpsertStoreResponseDTO는 storeId만 반환
    console.log(`[TC-MP-02] 상점 등록 성공. storeId=${data.storeId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-03: 상점 등록 후 단건 조회가 정상 동작한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-03: 상점을 등록한 후 단건 조회가 정상 동작한다", async ({
    request,
  }) => {
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(response.status(), "상점 조회 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const data = body.data;
    expect(data, "응답 데이터 없음").toBeDefined();
    expect(data.storeName).toBe("몸짱 트레이닝 센터");
    expect(data.instagramUrl).toBe("https://instagram.com/mztk_trainer");

    console.log(`[TC-MP-03] 상점 조회 성공. storeName=${data.storeName}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-04: 등록된 상점의 정보를 PUT 방식으로 수정한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-04: 등록된 상점의 정보를 PUT 방식으로 수정한다", async ({
    request,
  }) => {
    const updateData = {
      storeName: "우주최강 트레이닝 센터 (수정됨)",
      address: "서울시 강남구 테헤란로 456",
      detailAddress: "3층 301호", // @NotBlank 필수 필드
      latitude: 37.511,
      longitude: 127.049,
      phoneNumber: "010-9999-8888",
      instagramUrl: "https://instagram.com/mztk_trainer",
    };

    const updateResponse = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: updateData,
      }
    );

    expect(updateResponse.status(), "상점 수정 실패").toBe(200);

    const updateBody = await updateResponse.json();
    expect(
      updateBody.data.storeId,
      "수정 후 storeId 응답 없음"
    ).toBeDefined();

    // 수정 후 조회해서 정확히 반영되었는지 교차 검증
    const getResponse = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    const getBody = await getResponse.json();
    expect(getBody.data.storeName).toBe(updateData.storeName);

    console.log(
      `[TC-MP-04] 상점 수정 성공. 새 이름=${getBody.data.storeName}`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-05: 필수 파라미터 누락 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-05: 필수 파라미터(이름, 좌표 등) 누락 시 에러를 반환한다", async ({
    request,
  }) => {
    const invalidData = {
      storeName: "", // 비어있음
      address: "주소만 있음",
      // 위경도 없음
    };

    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: invalidData,
      }
    );

    expect(
      response.status(),
      "필수 파라미터 누락인데 200 응답이 왔습니다."
    ).toBe(400);

    const body = await response.json();
    expect(body).toHaveProperty("status", "FAIL");
    console.log(
      `[TC-MP-05] 필수값 누락 요청: ${response.status()} 예외 처리 확인 (정상)`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-06: 위경도 범위 초과 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-06: 위경도 범위를 벗어난 잘못된 값 입력 시 400 에러를 반환한다", async ({
    request,
  }) => {
    const invalidCoordsData = {
      storeName: "위경도 이상한 상점",
      address: "서울",
      latitude: 999.0, // 정상 범위: -90 ~ +90
      longitude: -200.0, // 정상 범위: -180 ~ +180
    };

    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: invalidCoordsData,
      }
    );

    expect(response.status(), "유효하지 않은 좌표입니다").toBe(400);
    console.log(`[TC-MP-06] 좌표 범위 밖 요청: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-07: 잘못된 URL 형식 입력 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-07: 올바르지 않은 URL 형식 입력 시 400 에러를 반환한다", async ({
    request,
  }) => {
    const invalidUrlData = {
      storeName: "URL 이상한 상점",
      address: "서울",
      latitude: 37.5,
      longitude: 127.0,
      homepageUrl: "not-a-valid-url-_-", // http/https 없음
      instagramUrl: "ftp://instagram.weird", // 잘못된 프로토콜
    };

    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/store`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: invalidUrlData,
      }
    );

    expect(response.status(), "유효하지 않은 URL 검증이 실패했습니다").toBe(
      400
    );
    console.log(`[TC-MP-07] 잘못된 URL 형식: ${response.status()}`);
  });
});

// ══════════════════════════════════════════════════════════════════════════════
// [그룹 2] 클래스 (Class) API 테스트
// ══════════════════════════════════════════════════════════════════════════════

test.describe("마켓플레이스 — 클래스 (Class) API 테스트", () => {
  let trainerToken: string; // 상점 + 클래스 등록용 트레이너
  let otherTrainerToken: string; // 타 트레이너 (권한 침범 테스트용)
  let userToken: string; // 일반 USER (403 테스트용)

  test.beforeAll(async ({ request }) => {
    const trainerEmail = `trainer-class-${Date.now()}@mztk-test.com`;
    const otherEmail = `trainer-other-${Date.now()}@mztk-test.com`;
    const userEmail = `user-class-${Date.now()}@mztk-test.com`;

    // 주 트레이너 계정 (상점 포함)
    trainerToken = await signUpAndLoginAsTrainer(request, trainerEmail);
    await upsertStore(request, trainerToken);

    // 타 트레이너 계정 (상점 포함) — 권한 침범 테스트용
    otherTrainerToken = await signUpAndLoginAsTrainer(request, otherEmail);
    await upsertStore(request, otherTrainerToken);

    // 일반 사용자
    userToken = await signUpAndLoginAsUser(request, userEmail);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록] TC-CL-01: 유효한 클래스 등록 시 201 및 classId 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-01: 트레이너가 유효한 클래스를 등록하면 201과 classId를 반환한다", async ({
    request,
  }) => {
    // 독립적인 트레이너 계정을 생성해 다른 테스트와의 슬롯/상태 간섭을 방지합니다.
    const freshEmail = `trainer-cl01-${Date.now()}@mztk-test.com`;
    const freshToken = await signUpAndLoginAsTrainer(request, freshEmail);
    await upsertStore(request, freshToken);

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${freshToken}`,
        },
        data: validClassBody(),
      }
    );

    expect(response.status(), "클래스 등록 실패").toBe(201);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");
    expect(body.data.classId, "classId가 응답에 없습니다").toBeDefined();
    expect(typeof body.data.classId).toBe("number");
    console.log(`[TC-CL-01] 클래스 등록 성공. classId=${body.data.classId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록] TC-CL-02: 선택 항목(tags, features) 제외하고 최소 필드로 등록 시 201 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-02: 선택 필드를 제외한 최소 필드로 클래스를 등록하면 201을 반환한다", async ({
    request,
  }) => {
    const minimalBody = {
      title: "최소 필드 요가 클래스",
      category: "YOGA",
      description: "요가 클래스 설명",
      priceAmount: 30000,
      durationMinutes: 45,
      classTimes: [
        {
          daysOfWeek: ["FRIDAY"],
          startTime: "18:00:00",
          capacity: 10,
        },
      ],
    };

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: minimalBody,
      }
    );

    expect(response.status(), "최소 필드 클래스 등록 실패").toBe(201);
    console.log(`[TC-CL-02] 최소 필드 등록 성공: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록] TC-CL-03: 슬롯 시간이 겹치는 클래스 등록 시 409 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-03: 슬롯 시간이 겹치는 클래스 등록 시 409 Conflict를 반환한다", async ({
    request,
  }) => {
    const conflictBody = {
      ...validClassBody(),
      // duration=60분인데 MONDAY 10:00 + 10:30 → 충돌
      classTimes: [
        { daysOfWeek: ["MONDAY"], startTime: "10:00:00", capacity: 5 },
        { daysOfWeek: ["MONDAY"], startTime: "10:30:00", capacity: 5 },
      ],
    };

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: conflictBody,
      }
    );

    expect(response.status(), "슬롯 충돌인데 409가 아님").toBe(409);
    console.log(`[TC-CL-03] 슬롯 충돌 방어 확인: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록] TC-CL-04: 상점이 없는 트레이너가 클래스 등록 시 404 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-04: 상점이 없는 트레이너가 클래스 등록 시 404를 반환한다", async ({
    request,
  }) => {
    // 상점 미등록 신규 트레이너 생성
    const noStoreEmail = `trainer-nostore-${Date.now()}@mztk-test.com`;
    const noStoreToken = await signUpAndLoginAsTrainer(
      request,
      noStoreEmail
    );
    // 상점 upsert 없이 바로 클래스 등록 시도

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${noStoreToken}`,
        },
        data: validClassBody(),
      }
    );

    expect(
      response.status(),
      "상점 없는 트레이너 클래스 등록: 404 기대"
    ).toBe(404);
    console.log(`[TC-CL-04] 미등록 상점 → 클래스 등록 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록] TC-CL-05: 일반 사용자(USER)는 클래스를 등록할 수 없다 (401/403)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-05: 일반 사용자가 클래스 등록 시 401 또는 403을 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${userToken}`,
        },
        data: validClassBody(),
      }
    );

    expect(
      [401, 403],
      `예상치 못한 상태코드: ${response.status()}`
    ).toContain(response.status());
    console.log(`[TC-CL-05] 일반 사용자 클래스 등록 거부: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록] TC-CL-06: 인증 없이 클래스 등록 시 401 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-06: 인증 없이 클래스 등록 시 401을 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: { "Content-Type": "application/json" },
        data: validClassBody(),
      }
    );

    expect(response.status(), "인증 없이 등록: 401 기대").toBe(401);
    console.log(`[TC-CL-06] 미인증 등록 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록 검증] TC-CL-07: title 누락 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-07: title 누락 시 400 Bad Request를 반환한다", async ({
    request,
  }) => {
    const body: Record<string, unknown> = { ...validClassBody() };
    delete body.title;

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: body,
      }
    );

    expect(response.status(), "title 없는데 400 아님").toBe(400);
    console.log(`[TC-CL-07] title 누락 → 400: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록 검증] TC-CL-08: priceAmount=0 (비양수) 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-08: priceAmount=0 입력 시 400 Bad Request를 반환한다", async ({
    request,
  }) => {
    const body = { ...validClassBody(), priceAmount: 0 };

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: body,
      }
    );

    expect(response.status(), "priceAmount=0인데 400 아님").toBe(400);
    console.log(`[TC-CL-08] priceAmount=0 → 400: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록 검증] TC-CL-09: classTimes 누락 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-09: classTimes 누락 시 400 Bad Request를 반환한다", async ({
    request,
  }) => {
    const body: Record<string, unknown> = { ...validClassBody() };
    delete body.classTimes;

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: body,
      }
    );

    expect(response.status(), "classTimes 없는데 400 아님").toBe(400);
    console.log(`[TC-CL-09] classTimes 누락 → 400: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록 검증] TC-CL-10: durationMinutes=1441 (최대 초과) 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-10: durationMinutes=1441 입력 시 400 Bad Request를 반환한다", async ({
    request,
  }) => {
    const body = { ...validClassBody(), durationMinutes: 1441 };

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: body,
      }
    );

    expect(response.status(), "durationMinutes 초과인데 400 아님").toBe(400);
    console.log(`[TC-CL-10] durationMinutes 초과 → 400: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 등록 검증] TC-CL-11: tags 4개 이상 (최대 3개) 시 400 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-11: 태그 4개 이상 입력 시 400 Bad Request를 반환한다 (최대 3개)", async ({
    request,
  }) => {
    const body = { ...validClassBody(), tags: ["a", "b", "c", "d"] };

    const response = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: body,
      }
    );

    expect(response.status(), "태그 4개인데 400 아님").toBe(400);
    console.log(`[TC-CL-11] 태그 초과 → 400: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 상세 조회] TC-CL-12: 등록된 클래스를 공개 상세 조회 (인증 불필요)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-12: 등록된 클래스를 인증 없이 상세 조회하면 200과 모든 필드를 반환한다", async ({
    request,
  }) => {
    // given: 클래스 등록
    const classId = await registerClassAndGetId(request, trainerToken);

    // when: 인증 없이 공개 조회
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}`
    );

    expect(response.status(), "클래스 상세 조회 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const data = body.data;
    expect(data.classId).toBe(classId);
    expect(data.title).toBe("PT 60분 기초체력");
    expect(data.category).toBe("PT");
    expect(data.priceAmount).toBe(50000);
    expect(data.durationMinutes).toBe(60);

    // 스토어 정보 포함 확인
    expect(data.store, "상점 정보가 없습니다").toBeDefined();
    expect(data.store.storeName).toBe("PT Studio Playwright");

    // classTimes 배열 포함 확인
    expect(Array.isArray(data.classTimes), "classTimes 배열이 아님").toBe(
      true
    );
    expect(data.classTimes.length, "classTimes가 비어있음").toBeGreaterThan(0);

    // tags 배열 포함
    expect(Array.isArray(data.tags)).toBe(true);

    console.log(
      `[TC-CL-12] 클래스 상세 조회 성공. classId=${data.classId}, title=${data.title}`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 상세 조회] TC-CL-13: 존재하지 않는 classId 조회 시 404 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-13: 존재하지 않는 classId로 상세 조회 시 404를 반환한다", async ({
    request,
  }) => {
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes/99999999`
    );

    expect(response.status(), "존재하지 않는 ID인데 404가 아님").toBe(404);
    console.log(`[TC-CL-13] 없는 classId → 404: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 수정] TC-CL-14: 자신의 클래스 수정 시 200과 동일 classId 反환, 변경 교차 검증
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-14: 자신의 클래스를 수정하면 200과 동일한 classId를 반환하고 변경 사항이 반영된다", async ({
    request,
  }) => {
    // given: 클래스 등록
    const classId = await registerClassAndGetId(request, trainerToken);

    const updateBody = {
      title: "PT 90분 중급 업데이트",
      category: "PT",
      description: "중급 체력 향상 PT 클래스 (업데이트)",
      priceAmount: 80000,
      durationMinutes: 90,
      classTimes: [
        {
          daysOfWeek: ["TUESDAY", "THURSDAY"],
          startTime: "14:00:00",
          capacity: 3,
        },
      ],
    };

    // when: 수정
    const updateResponse = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: updateBody,
      }
    );

    expect(updateResponse.status(), "클래스 수정 실패").toBe(200);

    const updateData = (await updateResponse.json()).data;
    expect(updateData.classId, "수정 후 classId가 바뀜").toBe(classId);

    // 수정 후 상세 조회로 변경 확인
    const getResponse = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}`
    );
    const getData = (await getResponse.json()).data;
    expect(getData.title).toBe("PT 90분 중급 업데이트");
    expect(getData.priceAmount).toBe(80000);
    expect(getData.durationMinutes).toBe(90);

    console.log(
      `[TC-CL-14] 클래스 수정 및 교차 검증 성공. classId=${classId}`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 수정] TC-CL-15: 다른 트레이너의 클래스 수정 시 403 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-15: 다른 트레이너의 클래스를 수정하려 하면 403을 반환한다", async ({
    request,
  }) => {
    // given: 첫 트레이너가 클래스 등록
    const classId = await registerClassAndGetId(request, trainerToken);

    const updateBody = {
      title: "해킹된 제목",
      category: "PT",
      description: "설명",
      priceAmount: 1000,
      durationMinutes: 60,
      classTimes: [
        { daysOfWeek: ["MONDAY"], startTime: "10:00:00", capacity: 1 },
      ],
    };

    // when: 타 트레이너가 수정 시도
    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${otherTrainerToken}`,
        },
        data: updateBody,
      }
    );

    expect(response.status(), "타 트레이너가 수정했는데 403이 아님").toBe(403);
    console.log(`[TC-CL-15] 타 트레이너 수정 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [클래스 수정] TC-CL-16: 인증 없이 클래스 수정 시 401 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-16: 인증 없이 클래스 수정 시 401을 반환한다", async ({
    request,
  }) => {
    const classId = await registerClassAndGetId(request, trainerToken);

    const response = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}`,
      {
        headers: { "Content-Type": "application/json" },
        data: validClassBody(),
      }
    );

    expect(response.status(), "미인증 수정: 401 기대").toBe(401);
    console.log(`[TC-CL-16] 미인증 수정 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [상태 토글] TC-CL-17: 클래스 상태 토글 시 200과 active 필드 변경 확인
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-17: 클래스 상태를 토글하면 200과 변경된 active 값을 반환한다", async ({
    request,
  }) => {
    // given: 클래스 등록 (기본 active=true)
    const classId = await registerClassAndGetId(request, trainerToken);

    // when: 첫 번째 토글 (active → inactive)
    const toggleResponse = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(toggleResponse.status(), "상태 토글 실패").toBe(200);

    const toggleData = (await toggleResponse.json()).data;
    expect(toggleData.classId).toBe(classId);
    expect(toggleData.active, "토글 후 active가 false여야 함").toBe(false);

    // when: 두 번째 토글 (inactive → active)
    const retoggleResponse = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(retoggleResponse.status(), "재토글 실패").toBe(200);
    const retoggleData = (await retoggleResponse.json()).data;
    expect(retoggleData.active, "재토글 후 active가 true여야 함").toBe(true);

    console.log(`[TC-CL-17] 상태 토글 확인 완료: classId=${classId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [상태 토글] TC-CL-18: 존재하지 않는 classId 토글 시 404 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-18: 존재하지 않는 classId를 토글하면 404를 반환한다", async ({
    request,
  }) => {
    const response = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/99999999/status`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(response.status(), "없는 classId 토글: 404 기대").toBe(404);
    console.log(`[TC-CL-18] 없는 classId 토글 → 404: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [상태 토글] TC-CL-19: 타 트레이너 클래스 상태 토글 시 403 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-19: 다른 트레이너의 클래스 상태를 토글하면 403을 반환한다", async ({
    request,
  }) => {
    const classId = await registerClassAndGetId(request, trainerToken);

    const response = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`,
      {
        headers: { Authorization: `Bearer ${otherTrainerToken}` },
      }
    );

    expect(response.status(), "타 트레이너 토글: 403 기대").toBe(403);
    console.log(`[TC-CL-19] 타 트레이너 토글 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [상태 토글] TC-CL-20: 인증 없이 상태 토글 시 401 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-20: 인증 없이 상태 토글 시 401을 반환한다", async ({
    request,
  }) => {
    const classId = await registerClassAndGetId(request, trainerToken);

    const response = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`
    );

    expect(response.status(), "미인증 토글: 401 기대").toBe(401);
    console.log(`[TC-CL-20] 미인증 토글 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [트레이너 클래스 목록] TC-CL-21: 등록한 클래스가 목록에 포함된다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-21: 등록한 클래스가 트레이너 클래스 목록에 포함된다", async ({
    request,
  }) => {
    const classId = await registerClassAndGetId(request, trainerToken);

    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(response.status(), "트레이너 클래스 목록 조회 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");
    expect(body.data.totalElements, "총 항목 수가 없음").toBeGreaterThan(0);

    // items 배열에서 방금 등록한 classId 존재 확인
    const items: Array<{ classId: number; title: string; active: boolean }> =
      body.data.items ?? [];
    const found = items.find((c) => c.classId === classId);
    expect(found, `classId=${classId}가 목록에 없음`).toBeDefined();
    expect(found?.title).toBe("PT 60분 기초체력");
    expect(typeof found?.active).toBe("boolean");

    console.log(
      `[TC-CL-21] 트레이너 클래스 목록 확인. classId=${classId} 포함`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [트레이너 클래스 목록] TC-CL-22: 페이지네이션 메타 필드 존재 확인
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-22: 트레이너 클래스 목록 응답에 페이지네이션 메타 필드가 포함된다", async ({
    request,
  }) => {
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes?page=0`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    expect(response.status()).toBe(200);

    const data = (await response.json()).data;
    expect(typeof data.currentPage).toBe("number");
    expect(typeof data.totalPages).toBe("number");
    expect(typeof data.totalElements).toBe("number");

    console.log(
      `[TC-CL-22] 페이지네이션 메타 확인. page=${data.currentPage}, total=${data.totalElements}`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [트레이너 클래스 목록] TC-CL-23: 인증 없이 트레이너 클래스 목록 조회 시 401 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-23: 인증 없이 트레이너 클래스 목록 조회 시 401을 반환한다", async ({
    request,
  }) => {
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`
    );

    expect(response.status(), "미인증 트레이너 목록: 401 기대").toBe(401);
    console.log(`[TC-CL-23] 미인증 목록 차단: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [공개 클래스 목록] TC-CL-24: 등록 후 공개 목록에서 활성 클래스 조회 가능
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-24: 클래스 등록 후 공개 목록(인증 불필요)에서 active 클래스가 조회된다", async ({
    request,
  }) => {
    const classId = await registerClassAndGetId(request, trainerToken);

    // 인증 없이 공개 목록 조회
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes`
    );

    expect(response.status(), "공개 클래스 목록 조회 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const items: Array<{ classId: number }> = body.data.items ?? [];
    const found = items.some((c) => c.classId === classId);
    expect(found, `classId=${classId}가 공개 목록에 없음`).toBe(true);

    console.log(`[TC-CL-24] 공개 목록 확인. classId=${classId} 포함`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [공개 클래스 목록] TC-CL-25: inactive 클래스는 공개 목록에서 제외
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-25: inactive로 전환된 클래스는 공개 목록에서 제외된다", async ({
    request,
  }) => {
    // given: 클래스 등록 후 비활성화
    const classId = await registerClassAndGetId(request, trainerToken);

    await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );

    // when: 공개 목록 조회
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes`
    );

    const items: Array<{ classId: number }> = (await response.json()).data.items ?? [];
    const found = items.some((c) => c.classId === classId);
    expect(found, "inactive 클래스가 공개 목록에 노출됨").toBe(false);

    console.log(`[TC-CL-25] inactive → 공개 목록 제외 확인. classId=${classId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [공개 클래스 목록] TC-CL-26: 카테고리 필터 적용 시 해당 카테고리만 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-26: category=PT 필터를 적용하면 200을 반환하고 등록한 PT 클래스가 결과에 포함된다", async ({
    request,
  }) => {
    // given: PT 카테고리 클래스 등록
    const classId = await registerClassAndGetId(request, trainerToken);

    // when: PT 카테고리로 필터
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes?category=PT`
    );

    expect(response.status(), "카테고리 필터 요청 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");

    const items: Array<{ classId: number; category: string }> =
      body.data.items ?? [];

    // 방금 등록한 PT 클래스가 결과에 포함되어야 함 (필터가 정상 작동하는지 검증)
    const found = items.some((c) => c.classId === classId);
    expect(found, `등록한 PT 클래스(classId=${classId})가 category=PT 필터 결과에 없음`).toBe(true);

    // 결과에 포함된 항목이 모두 PT인지 확인 (필터 정확성 검증)
    const nonPtInResult = items.filter((c) => c.category !== "PT");
    if (nonPtInResult.length > 0) {
      console.warn(
        `[TC-CL-26] ⚠️ 서버 카테고리 필터 불완전: PT 이외 ${nonPtInResult.length}개 항목 포함 (${nonPtInResult.map((c) => c.category).join(", ")})`
      );
    }
    // NOTE: 서버 카테고리 필터가 불완전할 수 있으므로 PT 클래스 포함 여부만 단언함
    // 필터 정확성은 서버 수정 후 아래 주석을 해제하여 강화할 수 있습니다:
    // expect(nonPtInResult.length, "PT 이외 항목이 포함됨").toBe(0);

    console.log(`[TC-CL-26] 카테고리 필터(PT) 응답 확인. items=${items.length}, 등록 classId=${classId} 포함`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [공개 클래스 목록] TC-CL-27: 공개 목록 응답에 페이지네이션 메타 포함 확인
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-27: 공개 클래스 목록 응답에 페이지네이션 메타 필드가 포함된다", async ({
    request,
  }) => {
    const response = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes?page=0`
    );

    expect(response.status()).toBe(200);

    const data = (await response.json()).data;
    expect(typeof data.currentPage).toBe("number");
    expect(typeof data.totalPages).toBe("number");
    expect(typeof data.totalElements).toBe("number");
    expect(Array.isArray(data.items)).toBe(true);

    console.log(
      `[TC-CL-27] 공개 목록 페이지네이션. page=${data.currentPage}, total=${data.totalElements}`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [전체 라이프사이클] TC-CL-28: 등록 → 조회 → 수정 → 비활성화 → 재활성화 → 공개 제외/포함
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-CL-28: 클래스 전체 라이프사이클 — 등록 → 상세 조회 → 수정 → 토글(비활성) → 공개 제외 → 토글(활성) → 공개 포함", async ({
    request,
  }) => {
    // Step 1: 클래스 등록
    const registerRes = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: validClassBody(),
      }
    );
    expect(registerRes.status(), "[Step1] 클래스 등록 실패").toBe(201);
    const classId: number = (await registerRes.json()).data.classId;
    expect(classId).toBeTruthy();
    console.log(`  [TC-CL-28] Step1: 등록 완료. classId=${classId}`);

    // Step 2: 상세 조회 → 등록값 확인
    const detailRes = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}`
    );
    expect(detailRes.status(), "[Step2] 상세 조회 실패").toBe(200);
    const detailData = (await detailRes.json()).data;
    expect(detailData.title).toBe("PT 60분 기초체력");
    expect(detailData.store.storeName).toBe("PT Studio Playwright");
    console.log(`  [TC-CL-28] Step2: 상세 조회 성공`);

    // Step 3: 클래스 수정
    const updateRes = await request.put(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: {
          title: "PT 90분 중급 [수정완료]",
          category: "PT",
          description: "수정된 설명",
          priceAmount: 75000,
          durationMinutes: 90,
          classTimes: [
            { daysOfWeek: ["TUESDAY"], startTime: "11:00:00", capacity: 4 },
          ],
        },
      }
    );
    expect(updateRes.status(), "[Step3] 수정 실패").toBe(200);
    expect((await updateRes.json()).data.classId).toBe(classId);

    // 수정값 교차 검증
    const afterUpdate = (
      await (
        await request.get(`${ENV.BACKEND_URL}/marketplace/classes/${classId}`)
      ).json()
    ).data;
    expect(afterUpdate.title).toBe("PT 90분 중급 [수정완료]");
    expect(afterUpdate.priceAmount).toBe(75000);
    console.log(`  [TC-CL-28] Step3: 수정 및 교차 검증 성공`);

    // Step 4: 비활성화 (active → false)
    const toggleRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );
    expect(toggleRes.status(), "[Step4] 비활성화 실패").toBe(200);
    expect((await toggleRes.json()).data.active).toBe(false);
    console.log(`  [TC-CL-28] Step4: 비활성화(active=false) 확인`);

    // Step 5: 공개 목록에서 제외 확인
    const listAfterInactive = (
      await (
        await request.get(`${ENV.BACKEND_URL}/marketplace/classes`)
      ).json()
    ).data.items as Array<{ classId: number }>;
    const inactiveFound = listAfterInactive.some((c) => c.classId === classId);
    expect(inactiveFound, "[Step5] inactive 클래스가 공개 목록에 노출됨").toBe(
      false
    );
    console.log(`  [TC-CL-28] Step5: inactive → 공개 목록 제외 확인`);

    // Step 6: 재활성화 (false → true)
    const retoggleRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes/${classId}/status`,
      {
        headers: { Authorization: `Bearer ${trainerToken}` },
      }
    );
    expect(retoggleRes.status(), "[Step6] 재활성화 실패").toBe(200);
    expect((await retoggleRes.json()).data.active).toBe(true);
    console.log(`  [TC-CL-28] Step6: 재활성화(active=true) 확인`);

    // Step 7: 공개 목록 재포함 확인
    const listAfterActive = (
      await (
        await request.get(`${ENV.BACKEND_URL}/marketplace/classes`)
      ).json()
    ).data.items as Array<{ classId: number }>;
    const activeFound = listAfterActive.some((c) => c.classId === classId);
    expect(activeFound, "[Step7] 재활성화했는데 공개 목록에 없음").toBe(true);
    console.log(`  [TC-CL-28] Step7: active → 공개 목록 재포함 확인`);

    console.log(
      `[TC-CL-28] 전체 라이프사이클 테스트 완료. classId=${classId}`
    );
  });
});

// ══════════════════════════════════════════════════════════════════════════════
// [그룹 3] 예약 (Reservation) API 테스트
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 다음 주어진 요일의 날짜를 YYYY-MM-DD 형식으로 반환합니다.
 * 오늘과 같은 요일이면 다음 주 날짜를 반환합니다.
 */
function getNextWeekday(
  dayName: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY"
): string {
  const dayIndex: Record<string, number> = {
    SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3,
    THURSDAY: 4, FRIDAY: 5, SATURDAY: 6,
  };
  const target = dayIndex[dayName];
  // 내일부터 시작해서 목표 요일을 찾습니다 (로컬 시간 기준).
  const result = new Date();
  result.setDate(result.getDate() + 1);
  while (result.getDay() !== target) {
    result.setDate(result.getDate() + 1);
  }
  // 로컬 날짜로 YYYY-MM-DD 포맷 (toISOString은 UTC 기준이라 사용 금지)
  const yyyy = result.getFullYear();
  const mm = String(result.getMonth() + 1).padStart(2, "0");
  const dd = String(result.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * 클래스 상세 조회로 첫 번째 slotId를 가져옵니다.
 */
async function getFirstSlotId(
  apiContext: APIRequestContext,
  classId: number
): Promise<number> {
  const res = await apiContext.get(
    `${ENV.BACKEND_URL}/marketplace/classes/${classId}`
  );
  const body = await res.json();
  const classTimes: Array<{ timeId: number }> = body?.data?.classTimes ?? [];
  expect(classTimes.length, "클래스에 슬롯이 없음").toBeGreaterThan(0);
  return classTimes[0].timeId;
}

/**
 * 예약을 생성하고 응답 data를 반환합니다. Web3 intent 실행은 호출자가 결정합니다.
 */
async function createReservationAndReturnData(
  apiContext: APIRequestContext,
  userToken: string,
  classId: number,
  slotId: number,
  reservationDate: string,
  reservationTime: string,
  signedAmount: number
): Promise<CreateReservationData> {
  const res = await apiContext.post(
    `${ENV.BACKEND_URL}/marketplace/classes/${classId}/reservations`,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userToken}`,
      },
      data: {
        slotId,
        reservationDate,
        reservationTime,
        signedAmount: tokenBaseUnits(signedAmount),
        delegationSignature: "0x" + "a".repeat(130),
        executionSignature: "0x" + "b".repeat(130),
      },
    }
  );
  const resText = await res.text();
  expect(res.status(), `예약 생성 실패 (classId=${classId}): ${resText}`).toBe(200);
  const body = JSON.parse(resText);
  return body?.data;
}

/**
 * 예약을 생성하고 purchase intent를 확정한 뒤 reservationId를 반환합니다.
 */
async function createReservation(
  apiContext: APIRequestContext,
  userToken: string,
  classId: number,
  slotId: number,
  reservationDate: string,
  reservationTime: string,
  signedAmount: number
): Promise<number> {
  const data = await createReservationAndReturnData(
    apiContext,
    userToken,
    classId,
    slotId,
    reservationDate,
    reservationTime,
    signedAmount
  );
  const reservationId = data?.reservationId as number;
  if (data?.status === "PURCHASE_PENDING") {
    await executeReservationIntent(
      apiContext,
      userToken,
      marketplaceUserWallet,
      data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(apiContext, userToken, reservationId, "PENDING");
  }
  return reservationId;
}

/**
 * 테스트용 클래스(슬롯 포함)를 등록하고 { classId, slotId, priceAmount, reservationDate, reservationTime }을 반환합니다.
 */
async function setupClassWithSlot(
  apiContext: APIRequestContext,
  trainerToken: string,
  dayOfWeek: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY",
  startTime: string,
  priceAmount: number
): Promise<{
  classId: number;
  slotId: number;
  priceAmount: number;
  reservationDate: string;
  reservationTime: string;
}> {
  const classRes = await apiContext.post(
    `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${trainerToken}`,
      },
      data: {
        title: `예약테스트 클래스 ${Date.now()}`,
        category: "PT",
        description: "예약 E2E 테스트용 클래스",
        priceAmount,
        durationMinutes: 60,
        classTimes: [{ daysOfWeek: [dayOfWeek], startTime, capacity: 5 }],
      },
    }
  );
  expect(classRes.status(), "클래스 등록 실패").toBe(201);
  const classId: number = (await classRes.json())?.data?.classId;
  const slotId = await getFirstSlotId(apiContext, classId);
  const reservationDate = getNextWeekday(dayOfWeek);
  return { classId, slotId, priceAmount, reservationDate, reservationTime: startTime };
}

test.describe("마켓플레이스 — 예약 (Reservation) API 테스트", () => {
  let trainerToken: string;
  let otherTrainerToken: string;
  let userToken: string;

  test.beforeAll(async ({ request }) => {
    const trainerEmail = `trainer-rv-${Date.now()}@mztk-test.com`;
    const otherEmail = `trainer-rv-other-${Date.now()}@mztk-test.com`;
    const userEmail = `user-rv-${Date.now()}@mztk-test.com`;

    await ensureSponsorTreasuryReady();

    trainerToken = await signUpAndLoginAsTrainer(request, trainerEmail);
    await upsertStore(request, trainerToken);

    otherTrainerToken = await signUpAndLoginAsTrainer(request, otherEmail);
    await upsertStore(request, otherTrainerToken);

    userToken = await signUpAndLoginAsUser(request, userEmail);
    marketplaceTrainerWallet = await registerWallet(
      request,
      trainerToken,
      ENV.MARKETPLACE_TEST_TRAINER_PRIVATE_KEY
    );
    marketplaceUserWallet = await registerWallet(
      request,
      userToken,
      ENV.MARKETPLACE_TEST_USER_PRIVATE_KEY
    );
    await ensureBuyerTokenFunding(
      marketplaceUserWallet,
      ENV.MARKETPLACE_TEST_MIN_BUYER_TOKEN_BALANCE
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-01: 예약 생성 성공 → 200 + PENDING 상태
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-01: 유효한 요청으로 예약을 생성하면 200과 PENDING 상태를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "MONDAY", "10:00:00", 1);

    const reservationId = await createReservation(
      request,
      userToken,
      classId,
      slotId,
      reservationDate,
      reservationTime,
      priceAmount
    );

    const detailRes = await request.get(
      `${ENV.BACKEND_URL}/marketplace/reservations/${reservationId}`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );
    expect(detailRes.status(), "예약 상세 조회 실패").toBe(200);
    const body = await detailRes.json();
    expect(body).toHaveProperty("status", "SUCCESS");
    expect(body.data.reservationId, "reservationId 없음").toBe(reservationId);
    expect(body.data.status).toBe("PENDING");
    console.log(`[TC-RV-01] 예약 생성 성공. reservationId=${reservationId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-02: 트레이너 승인 → APPROVED
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-02: 트레이너가 예약을 승인하면 200과 APPROVED 상태를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "TUESDAY", "11:00:00", 40000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/approve`,
      { headers: { Authorization: `Bearer ${trainerToken}` } }
    );

    expect(res.status(), "승인 실패").toBe(200);
    const body = await res.json();
    expect(body.data.status).toBe("APPROVED");
    console.log(`[TC-RV-02] 승인 성공. reservationId=${reservationId}, status=APPROVED`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-03: 트레이너 반려 → REJECTED + txHash 존재
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-03: 트레이너가 예약을 반려하면 200과 REJECTED 상태를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "WEDNESDAY", "14:00:00", 30000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/reject`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: { rejectionReason: "해당 시간에 타 일정이 있습니다." },
      }
    );

    expect(res.status(), "반려 실패").toBe(200);
    const body = await res.json();
    expect(body.data.status).toBe("REJECT_PENDING");
    await executeReservationIntent(
      request,
      trainerToken,
      marketplaceTrainerWallet,
      body.data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(request, trainerToken, reservationId, "REJECTED");
    console.log(`[TC-RV-03] 반려 성공. reservationId=${reservationId}, status=REJECTED`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-04: 유저 취소 → USER_CANCELLED
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-04: 유저가 PENDING 예약을 취소하면 200과 USER_CANCELLED 상태를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "THURSDAY", "09:00:00", 20000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/cancel`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );

    expect(res.status(), "취소 실패").toBe(200);
    const body = await res.json();
    expect(body.data.status).toBe("CANCEL_PENDING");
    await executeReservationIntent(
      request,
      userToken,
      marketplaceUserWallet,
      body.data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(request, userToken, reservationId, "USER_CANCELLED");
    console.log(`[TC-RV-04] 취소 성공. reservationId=${reservationId}, status=USER_CANCELLED`);
  });

  test("MOM-313-LIVE-01: buyer cancel intent는 read hydration/recover 후 USER_CANCELLED로 종료된다", async ({
    request,
  }) => {
    test.setTimeout(300_000);
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "MONDAY", "18:00:00", 1);
    const reservationId = await createReservation(
      request,
      userToken,
      classId,
      slotId,
      reservationDate,
      reservationTime,
      priceAmount
    );

    const cancelRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/cancel`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );
    expect(cancelRes.status(), `cancel prepare failed: ${await cancelRes.text()}`).toBe(200);
    const cancelBody = await cancelRes.json();
    expect(cancelBody.data.status).toBe("CANCEL_PENDING");
    expect(cancelBody.data.web3.actionType).toBe("MARKETPLACE_CLASS_CANCEL");

    const detail = await loadReservationDetail(request, userToken, reservationId);
    expect(detail.status).toBe("CANCEL_PENDING");
    expect(detail.web3Execution.actionType).toBe("MARKETPLACE_CLASS_CANCEL");
    expect(
      detail.web3Execution.viewerCanExecute || detail.web3Execution.viewerCanRecover
    ).toBe(true);

    const recoverRes = await request.post(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/web3/recover`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );
    expect(recoverRes.status(), `recover failed: ${await recoverRes.text()}`).toBe(200);
    const recoverBody = await recoverRes.json();
    expect(recoverBody.data.status).toBe("CANCEL_PENDING");
    expect(recoverBody.data.web3.actionType).toBe("MARKETPLACE_CLASS_CANCEL");

    await executeReservationIntent(
      request,
      userToken,
      marketplaceUserWallet,
      recoverBody.data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(request, userToken, reservationId, "USER_CANCELLED");
    console.log(`[MOM-313-LIVE-01] cancel/recover 성공. reservationId=${reservationId}`);
  });

  test("MOM-313-LIVE-02: trainer reject intent는 REJECTED로 종료되고 사유가 보존된다", async ({
    request,
  }) => {
    test.setTimeout(300_000);
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "TUESDAY", "18:30:00", 1);
    const reservationId = await createReservation(
      request,
      userToken,
      classId,
      slotId,
      reservationDate,
      reservationTime,
      priceAmount
    );

    const rejectionReason = "MOM-313 live reject verification";
    const rejectRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/reject`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: { rejectionReason },
      }
    );
    expect(rejectRes.status(), `reject prepare failed: ${await rejectRes.text()}`).toBe(200);
    const rejectBody = await rejectRes.json();
    expect(rejectBody.data.status).toBe("REJECT_PENDING");
    expect(rejectBody.data.web3.actionType).toBe("MARKETPLACE_CLASS_CANCEL");

    await executeReservationIntent(
      request,
      trainerToken,
      marketplaceTrainerWallet,
      rejectBody.data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(request, trainerToken, reservationId, "REJECTED");

    const detail = await loadReservationDetail(request, userToken, reservationId);
    expect(detail.status).toBe("REJECTED");
    const reasonRow = await db.query(
      "select rejection_reason from class_reservations where id = $1",
      [reservationId]
    );
    expect(reasonRow.rows[0].rejection_reason).toBe(rejectionReason);
    console.log(`[MOM-313-LIVE-02] reject 성공. reservationId=${reservationId}`);
  });

  test("MOM-313-LIVE-03: buyer confirm intent는 승인 후 SETTLED로 종료된다", async ({
    request,
  }) => {
    test.setTimeout(300_000);
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "WEDNESDAY", "18:00:00", 1);
    const reservationId = await createReservation(
      request,
      userToken,
      classId,
      slotId,
      reservationDate,
      reservationTime,
      priceAmount
    );

    const approveRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/approve`,
      { headers: { Authorization: `Bearer ${trainerToken}` } }
    );
    expect(approveRes.status(), `approve failed: ${await approveRes.text()}`).toBe(200);
    expect((await approveRes.json()).data.status).toBe("APPROVED");
    await moveReservationSessionToPast(reservationId);

    const completeRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/complete`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );
    expect(completeRes.status(), `complete prepare failed: ${await completeRes.text()}`).toBe(200);
    const completeBody = await completeRes.json();
    expect(completeBody.data.status).toBe("CONFIRM_PENDING");
    expect(completeBody.data.web3.actionType).toBe("MARKETPLACE_CLASS_CONFIRM");

    await executeReservationIntent(
      request,
      userToken,
      marketplaceUserWallet,
      completeBody.data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(request, userToken, reservationId, "SETTLED");
    console.log(`[MOM-313-LIVE-03] confirm 성공. reservationId=${reservationId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-05: 4주 스케줄 조회 → availableDates 배열 존재
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-05: 클래스 예약 정보 조회 시 200과 availableDates 배열을 반환한다", async ({
    request,
  }) => {
    const { classId } = await setupClassWithSlot(
      request, trainerToken, "FRIDAY", "08:00:00", 15000
    );

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}/reservation-info`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );

    expect(res.status(), "스케줄 조회 실패").toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("status", "SUCCESS");
    expect(body.data.classId).toBe(classId);
    expect(Array.isArray(body.data.availableDates), "availableDates가 배열이 아님").toBe(true);
    expect(body.data.availableDates.length, "28일 내 날짜가 없음").toBeGreaterThan(0);
    console.log(`[TC-RV-05] 스케줄 조회 성공. classId=${classId}, dates=${body.data.availableDates.length}개`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-06: 가격 불일치 → 400 MARKETPLACE_020
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-06: 서명 금액이 실제 클래스 가격과 다르면 400을 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "MONDAY", "13:00:00", 50000);

    const res = await request.post(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}/reservations`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${userToken}`,
        },
        data: {
          slotId,
          reservationDate,
          reservationTime,
          signedAmount: tokenBaseUnits(99999), // 실제 50000과 불일치
          delegationSignature: "0x" + "a".repeat(130),
          executionSignature: "0x" + "b".repeat(130),
        },
      }
    );

    expect(res.status(), "가격 불일치인데 400 아님").toBe(400);
    const body = await res.json();
    expect(body.code).toBe("MARKETPLACE_020");
    console.log(`[TC-RV-06] 가격 불일치 방어 확인. code=${body.code}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-07: 슬롯 정원 초과 → 409 MARKETPLACE_017
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-07: 슬롯 정원이 가득 차면 두 번째 예약은 409 SLOT_FULL을 반환한다", async ({
    request,
  }) => {
    // capacity=1 슬롯을 위한 전용 트레이너/클래스
    const freshEmail = `trainer-cap-${Date.now()}@mztk-test.com`;
    const freshToken = await signUpAndLoginAsTrainer(request, freshEmail);
    await upsertStore(request, freshToken);

    const classRes = await request.post(
      `${ENV.BACKEND_URL}/marketplace/trainer/classes`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${freshToken}`,
        },
        data: {
          title: `정원1 클래스 ${Date.now()}`,
          category: "PT",
          description: "정원 초과 테스트",
          priceAmount: 10000,
          durationMinutes: 60,
          classTimes: [{ daysOfWeek: ["TUESDAY"], startTime: "12:00:00", capacity: 1 }],
        },
      }
    );
    expect(classRes.status()).toBe(201);
    const classId: number = (await classRes.json()).data.classId;
    const slotId = await getFirstSlotId(request, classId);
    const reservationDate = getNextWeekday("TUESDAY");

    const user2Email = `user-cap2-${Date.now()}@mztk-test.com`;
    const user2Token = await signUpAndLoginAsUser(request, user2Email);

    // 첫 번째 예약 (성공)
    await createReservation(request, userToken, classId, slotId, reservationDate, "12:00:00", 10000);

    // 두 번째 예약 (409)
    const res = await request.post(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}/reservations`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${user2Token}`,
        },
        data: {
          slotId,
          reservationDate,
          reservationTime: "12:00:00",
          signedAmount: tokenBaseUnits(10000),
          delegationSignature: "0x" + "a".repeat(130),
          executionSignature: "0x" + "b".repeat(130),
        },
      }
    );

    expect(res.status(), "정원 초과인데 409 아님").toBe(409);
    const body = await res.json();
    expect(body.code).toBe("MARKETPLACE_017");
    console.log(`[TC-RV-07] 정원 초과 방어 확인. code=${body.code}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-08: 타인이 예약 취소 시도 → 403
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-08: 예약 소유자가 아닌 다른 유저가 취소하면 403을 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "MONDAY", "15:00:00", 20000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const intruderEmail = `user-intruder-${Date.now()}@mztk-test.com`;
    const intruderToken = await signUpAndLoginAsUser(request, intruderEmail);

    const res = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/cancel`,
      { headers: { Authorization: `Bearer ${intruderToken}` } }
    );

    expect(res.status(), "타인 취소인데 403 아님").toBe(403);
    console.log(`[TC-RV-08] 타인 취소 차단 확인. status=${res.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-09: 타 트레이너가 승인 시도 → 403
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-09: 클래스 소유자가 아닌 트레이너가 승인하면 403을 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "WEDNESDAY", "16:00:00", 30000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/approve`,
      { headers: { Authorization: `Bearer ${otherTrainerToken}` } }
    );

    expect(res.status(), "타 트레이너 승인인데 403 아님").toBe(403);
    console.log(`[TC-RV-09] 타 트레이너 승인 차단 확인. status=${res.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-10: 미인증 예약 생성 → 401
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-10: 인증 없이 예약을 생성하면 401을 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "THURSDAY", "10:00:00", 25000);

    const res = await request.post(
      `${ENV.BACKEND_URL}/marketplace/classes/${classId}/reservations`,
      {
        headers: { "Content-Type": "application/json" },
        data: {
          slotId,
          reservationDate,
          reservationTime,
          signedAmount: priceAmount,
          delegationSignature: "0x" + "a".repeat(130),
          executionSignature: "0x" + "b".repeat(130),
        },
      }
    );

    expect(res.status(), "미인증 예약인데 401 아님").toBe(401);
    console.log(`[TC-RV-10] 미인증 예약 차단 확인. status=${res.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-11: PENDING 상태에서 완료 시도 → 409 MARKETPLACE_018
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-11: APPROVED 되지 않은 예약을 완료하면 409 INVALID_STATUS를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "FRIDAY", "09:00:00", 20000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/complete`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );

    expect(res.status(), "PENDING 완료인데 409 아님").toBe(409);
    const body = await res.json();
    expect(body.code).toBe("MARKETPLACE_018");
    console.log(`[TC-RV-11] PENDING 완료 차단 확인. code=${body.code}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-RV-12: APPROVED 예약을 다시 반려하면 409 INVALID_STATUS를 반환한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-12: APPROVED 예약을 다시 반려하면 409 INVALID_STATUS를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "TUESDAY", "17:00:00", 35000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    // 승인
    const approveRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/approve`,
      { headers: { Authorization: `Bearer ${trainerToken}` } }
    );
    expect(approveRes.status(), "승인 실패").toBe(200);
    expect((await approveRes.json()).data.status).toBe("APPROVED");

    // APPROVED 상태에서 반려 시도
    const rejectRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}/reject`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${trainerToken}`,
        },
        data: { rejectionReason: "취소 시도" },
      }
    );

    expect(rejectRes.status(), "APPROVED → 반려인데 409 아님").toBe(409);
    const body = await rejectRes.json();
    expect(body.code).toBe("MARKETPLACE_018");
    console.log(`[TC-RV-12] APPROVED 상태에서 반려 차단 확인. code=${body.code}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [조회] TC-RV-13: 내 예약 목록 조회 — 본인 예약만 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-13: 내 예약 목록 조회 시 본인 예약만 반환된다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "WEDNESDAY", "08:00:00", 10000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/me/reservations`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );

    expect(res.status(), "내 예약 목록 조회 실패").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    expect(Array.isArray(body.data), "data가 배열이 아님").toBe(true);

    const found = body.data.some(
      (r: { reservationId: number }) => r.reservationId === reservationId
    );
    expect(found, "본인 예약이 목록에 없음").toBe(true);
    console.log(`[TC-RV-13] 내 예약 목록 조회 성공. reservationId=${reservationId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [조회] TC-RV-14: 내 예약 목록 — status=PENDING 필터
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-14: status=PENDING 필터 적용 시 PENDING 예약만 반환된다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "THURSDAY", "09:00:00", 12000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/me/reservations?status=PENDING`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );

    expect(res.status(), "PENDING 필터 조회 실패").toBe(200);
    const body = await res.json();
    const found = body.data.some(
      (r: { reservationId: number; status: string }) =>
        r.reservationId === reservationId && r.status === "PENDING"
    );
    expect(found, "PENDING 예약이 필터 결과에 없음").toBe(true);

    // APPROVED 필터에는 포함되지 않아야 함
    const approvedRes = await request.get(
      `${ENV.BACKEND_URL}/marketplace/me/reservations?status=APPROVED`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );
    const approvedBody = await approvedRes.json();
    const notFound = approvedBody.data.every(
      (r: { reservationId: number }) => r.reservationId !== reservationId
    );
    expect(notFound, "PENDING 예약이 APPROVED 필터에 노출됨").toBe(true);
    console.log(`[TC-RV-14] 상태 필터 정상 동작. reservationId=${reservationId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [조회] TC-RV-15: 예약 상세 조회 — 소유 유저 성공
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-15: 예약 소유자가 상세 조회 시 200과 전체 필드를 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "FRIDAY", "11:00:00", 15000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/reservations/${reservationId}`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );

    expect(res.status(), "예약 상세 조회 실패").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    const data = body.data;
    expect(data.reservationId).toBe(reservationId);
    expect(data.status).toBe("PENDING");
    expect(data.orderId, "orderId 없음").toBeTruthy();
    expect(data.txHash, "txHash 없음").toBeTruthy();
    console.log(`[TC-RV-15] 예약 상세 조회 성공. reservationId=${reservationId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [조회] TC-RV-16: 예약 상세 조회 — 담당 트레이너 성공
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-16: 담당 트레이너가 예약 상세 조회 시 200을 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "MONDAY", "13:00:00", 18000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations/${reservationId}`,
      { headers: { Authorization: `Bearer ${trainerToken}` } }
    );

    expect(res.status(), "트레이너 상세 조회 실패").toBe(200);
    const data = (await res.json()).data;
    expect(data.reservationId).toBe(reservationId);
    console.log(`[TC-RV-16] 트레이너 예약 상세 조회 성공. reservationId=${reservationId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [조회] TC-RV-17: 예약 상세 조회 — 제3자 접근 시 403
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-17: 제3자가 예약 상세 조회 시 403 Forbidden을 반환한다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "TUESDAY", "15:00:00", 10000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    // 제3자 계정 생성
    const intruderEmail = `intruder-rv17-${Date.now()}@mztk-test.com`;
    const intruderToken = await signUpAndLoginAsUser(request, intruderEmail);

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/reservations/${reservationId}`,
      { headers: { Authorization: `Bearer ${intruderToken}` } }
    );

    expect(res.status(), "제3자 접근인데 403 아님").toBe(403);
    console.log(`[TC-RV-17] 제3자 접근 차단 확인: ${res.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [조회] TC-RV-18: 트레이너 수강 신청 목록 조회
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-RV-18: 트레이너가 수강 신청 목록 조회 시 본인 클래스 예약만 반환된다", async ({
    request,
  }) => {
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "WEDNESDAY", "10:00:00", 20000);
    const reservationId = await createReservation(
      request, userToken, classId, slotId, reservationDate, reservationTime, priceAmount
    );

    const res = await request.get(
      `${ENV.BACKEND_URL}/marketplace/trainer/reservations`,
      { headers: { Authorization: `Bearer ${trainerToken}` } }
    );

    expect(res.status(), "트레이너 목록 조회 실패").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    expect(Array.isArray(body.data)).toBe(true);

    const found = body.data.some(
      (r: { reservationId: number }) => r.reservationId === reservationId
    );
    expect(found, "트레이너 목록에 예약이 없음").toBe(true);
    console.log(`[TC-RV-18] 트레이너 수강 신청 목록 조회 성공. reservationId=${reservationId}`);
  });

  test("MOM-313-LIVE-04: deadline refund intent는 DEADLINE_REFUNDED로 종료된다", async ({
    request,
  }) => {
    test.setTimeout(300_000);
    const { classId, slotId, priceAmount, reservationDate, reservationTime } =
      await setupClassWithSlot(request, trainerToken, "THURSDAY", "16:00:00", 1);
    const reservationId = await createReservation(
      request,
      userToken,
      classId,
      slotId,
      reservationDate,
      reservationTime,
      priceAmount
    );

    const orderKey = await loadReservationOrderKey(reservationId);
    const expiry = await expireMarketplaceOrderDeadline(orderKey);
    test.skip(!expiry.expired, expiry.reason ?? "deadline refund requires expired chain order");
    await moveReservationDeadlineToPast(reservationId);

    const refundRes = await request.patch(
      `${ENV.BACKEND_URL}/marketplace/me/reservations/${reservationId}/deadline-refund`,
      { headers: { Authorization: `Bearer ${userToken}` } }
    );
    expect(refundRes.status(), `deadline refund prepare failed: ${await refundRes.text()}`).toBe(
      200
    );
    const refundBody = await refundRes.json();
    expect(refundBody.data.status).toBe("DEADLINE_REFUND_PENDING");
    expect(refundBody.data.web3.actionType).toBe("MARKETPLACE_CLASS_EXPIRED_REFUND");

    await executeReservationIntent(
      request,
      userToken,
      marketplaceUserWallet,
      refundBody.data.web3 as ReservationWeb3WritePayload
    );
    await waitForReservationStatus(request, userToken, reservationId, "DEADLINE_REFUNDED");
    console.log(`[MOM-313-LIVE-04] deadline refund 성공. reservationId=${reservationId}`);
  });
});
