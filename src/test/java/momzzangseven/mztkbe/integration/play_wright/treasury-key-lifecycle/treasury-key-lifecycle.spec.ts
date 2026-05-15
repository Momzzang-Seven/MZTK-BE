/**
 * MZTK — Treasury Provision API branch coverage (Playwright, prod profile + real AWS KMS)
 *
 * Group B (state / existing-row branches):
 *   [P-7]  Happy path                              — service B8
 *   [P-8]  Re-provision while ENABLED              — service B5  (depends on [P-7] state)
 *   [P-10] Other alias holds same address          — service B7
 *   [P-11] Legacy row mismatch                     — service B4
 *
 * Group C (AFTER_COMMIT KMS branches via TreasuryWalletProvisionedKmsHandler):
 *   [P-12] alias bound to foreign ENABLED key      — handler B10  (KMS_CREATE_ALIAS audit fail)
 *   [P-13] alias bound to ghost PENDING_DELETION   — handler B11  (KMS_UPDATE_ALIAS audit success)
 *   [P-9]  Service alias-repair (DB row + alias UNAVAILABLE) — service B6 + handler success
 *
 * 환경:
 *   - prod-profile 로 기동된 백엔드 + 실제 AWS KMS 접근권한이 있는 AWS_PROFILE
 *   - .env: BACKEND_URL, DATABASE_URL (또는 DB_HOST/...), TREASURY_E2E_PRIVATE_KEY,
 *           TREASURY_E2E_EXPECTED_ADDRESS, AWS_REGION (선택)
 *   - 머신에 `aws` CLI 가 PATH 에 있어야 함 (KMS fixture 조작용)
 *
 * 단일 실행으로 7개 분기를 모두 검증한다. 각 테스트는 자신의 setup 을 직접 수행하고
 * `fullCleanup()` 으로 외부 상태를 초기화한 뒤 실행한다. [P-8] 만 [P-7] 직후 상태에 의존한다.
 */

import { test, expect, APIRequestContext, request } from "@playwright/test";
import { Pool } from "pg";
import { execSync } from "child_process";
import * as bcrypt from "bcryptjs";
import * as crypto from "crypto";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

test.describe.configure({ mode: "serial" });

// ────────────────────────────────────────────────────────────────────────────
// ENV
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  TREASURY_E2E_PRIVATE_KEY: process.env.TREASURY_E2E_PRIVATE_KEY ?? "",
  TREASURY_E2E_EXPECTED_ADDRESS: (process.env.TREASURY_E2E_EXPECTED_ADDRESS ?? "").toLowerCase(),
  // MOM-444 rotation 검증 ([P-MOM444-2]) 전용 두 번째 키 페어. 다른 시나리오는 KEY_1 만 사용.
  TREASURY_E2E_PRIVATE_KEY_2: process.env.TREASURY_E2E_PRIVATE_KEY_2 ?? "",
  TREASURY_E2E_EXPECTED_ADDRESS_2: (process.env.TREASURY_E2E_EXPECTED_ADDRESS_2 ?? "").toLowerCase(),
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
  DB_NAME: process.env.DB_NAME ?? "mztk_dev",
  DB_USER: process.env.DB_USER ?? process.env.DB_USERNAME ?? "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "postgres",
  DATABASE_URL: process.env.DATABASE_URL,
};

const REWARD_ALIAS = "reward-treasury";
const OTHER_ALIAS = "pending-treasury"; // for [P-10]
const SPONSOR_ALIAS = "sponsor-treasury"; // for [P-MOM444-1] 공유 운영지갑
const PROVISION_PENDING_DELETION_DAYS = 7; // AWS 최소값

const db = ENV.DATABASE_URL
  ? new Pool({ connectionString: ENV.DATABASE_URL })
  : new Pool({
      host: ENV.DB_HOST,
      port: ENV.DB_PORT,
      database: ENV.DB_NAME,
      user: ENV.DB_USER,
      password: ENV.DB_PASSWORD,
    });

// 테스트 도중 만들어진 KMS 키 (fixture 또는 service-created) — afterAll 에서 모두 schedule-deletion
const trackedKmsKeys = new Set<string>();

// ────────────────────────────────────────────────────────────────────────────
// AWS KMS helpers (aws CLI 직접 호출)
// ────────────────────────────────────────────────────────────────────────────
function awsExec(cmd: string, opts: { swallow?: boolean } = {}): string | null {
  try {
    return execSync(`aws ${cmd} --output json`, {
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "pipe"],
    });
  } catch (err: any) {
    if (opts.swallow) return null;
    throw new Error(
      `aws ${cmd} failed:\n${err.stderr?.toString?.() ?? err.message}`
    );
  }
}

function kmsDescribeAlias(alias: string): { keyId: string; state: string } | null {
  const out = awsExec(`kms describe-key --key-id alias/${alias}`, { swallow: true });
  if (!out) return null;
  const meta = JSON.parse(out).KeyMetadata;
  return { keyId: meta.KeyId, state: meta.KeyState };
}

function kmsDescribeKeyState(keyId: string): string | null {
  const out = awsExec(`kms describe-key --key-id ${keyId}`, { swallow: true });
  if (!out) return null;
  return JSON.parse(out).KeyMetadata.KeyState;
}

/**
 * Fixture KMS 키 생성. 운영 `KmsKeyLifecycleAdapter#createKey` 와 동일한 usage
 * (ECC_SECG_P256K1 + SIGN_VERIFY) 로 만들어야 alias UpdateAlias 가 동일 usage 제약을
 *통과한다. 다른 usage(예: 기본 SYMMETRIC_DEFAULT/ENCRYPT_DECRYPT) 로 만들면 P-13 에서
 * AWS KMS 가 ValidationException 으로 alias 이동을 거부한다.
 */
function kmsCreateKey(description: string): string {
  const out = awsExec(
    `kms create-key --description "${description}" --key-spec ECC_SECG_P256K1 --key-usage SIGN_VERIFY`
  );
  const meta = JSON.parse(out!).KeyMetadata;
  trackedKmsKeys.add(meta.KeyId);
  return meta.KeyId;
}

function kmsCreateAlias(alias: string, keyId: string): void {
  awsExec(`kms create-alias --alias-name alias/${alias} --target-key-id ${keyId}`);
}

function kmsDeleteAliasIfExists(alias: string): void {
  awsExec(`kms delete-alias --alias-name alias/${alias}`, { swallow: true });
}

function kmsScheduleKeyDeletion(keyId: string, days = PROVISION_PENDING_DELETION_DAYS): void {
  awsExec(
    `kms schedule-key-deletion --key-id ${keyId} --pending-window-in-days ${days}`,
    { swallow: true }
  );
}

// ────────────────────────────────────────────────────────────────────────────
// Admin / DB helpers
// ────────────────────────────────────────────────────────────────────────────
interface AdminCredentials {
  userId: number;
  loginId: string;
  password: string;
}
function randomSuffix(): string {
  return crypto.randomBytes(4).toString("hex");
}
async function setupAdmin(): Promise<AdminCredentials> {
  const suffix = randomSuffix();
  const email = `treasury-e2e-${suffix}@internal.mztk.local`;
  const loginId = `treasury-e2e-${randomSuffix()}`;
  const password = `TrE2E@Pass${randomSuffix()}`;
  const hash = await bcrypt.hash(password, 10);

  await db.query(
    `INSERT INTO users (email, role, nickname, created_at, updated_at)
     VALUES ($1, 'ADMIN_SEED', 'TreasuryE2EAdmin', NOW(), NOW())`,
    [email]
  );
  const { rows } = await db.query<{ id: number }>(
    "SELECT id FROM users WHERE email = $1",
    [email]
  );
  const userId = rows[0].id;
  await db.query(
    `INSERT INTO admin_accounts
       (user_id, login_id, password_hash, created_by,
        last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)
     VALUES ($1, $2, $3, NULL, NULL, NULL, NULL, NOW(), NOW())`,
    [userId, loginId, hash]
  );
  return { userId, loginId, password };
}
async function cleanupAdmin(loginId: string): Promise<void> {
  const { rows } = await db.query<{ user_id: number }>(
    "SELECT user_id FROM admin_accounts WHERE login_id = $1",
    [loginId]
  );
  if (rows.length === 0) return;
  const userId = rows[0].user_id;
  await db.query("DELETE FROM admin_accounts WHERE login_id = $1", [loginId]);
  await db.query("DELETE FROM users WHERE id = $1", [userId]);
}

async function adminLogin(loginId: string, password: string): Promise<string> {
  const ctx = await request.newContext({ baseURL: ENV.BACKEND_URL });
  const res = await ctx.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL_ADMIN", loginId, password },
  });
  expect(res.status(), `Admin login failed: ${res.status()}`).toBe(200);
  const body = await res.json();
  return (body.data as Record<string, string>).accessToken;
}
function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

// ────────────────────────────────────────────────────────────────────────────
// Cleanup
// ────────────────────────────────────────────────────────────────────────────

/** DB 의 모든 treasury 관련 row + KMS alias / 추적된 임시 키를 초기 상태로 되돌린다. */
async function fullCleanup(): Promise<void> {
  // 1) DB 에 적재된 kms_key_id 들 (이전 테스트에서 service 가 만든 것 포함) 추적
  const r = await db.query<{ kms_key_id: string | null }>(
    `SELECT kms_key_id FROM web3_treasury_wallets WHERE kms_key_id IS NOT NULL
     UNION
     SELECT kms_key_id FROM web3_treasury_kms_audits WHERE kms_key_id IS NOT NULL`
  );
  for (const row of r.rows) if (row.kms_key_id) trackedKmsKeys.add(row.kms_key_id);

  // 2) DB row 일괄 삭제
  await db.query(
    "DELETE FROM web3_treasury_kms_audits WHERE wallet_alias IN ($1, $2, $3)",
    [REWARD_ALIAS, OTHER_ALIAS, SPONSOR_ALIAS]
  );
  await db.query(
    "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN ($1, $2, $3)",
    [REWARD_ALIAS, OTHER_ALIAS, SPONSOR_ALIAS]
  );
  const provisionAddressesToWipe = [ENV.TREASURY_E2E_EXPECTED_ADDRESS];
  if (ENV.TREASURY_E2E_EXPECTED_ADDRESS_2) {
    provisionAddressesToWipe.push(ENV.TREASURY_E2E_EXPECTED_ADDRESS_2);
  }
  await db.query(
    `DELETE FROM web3_treasury_provision_audits
       WHERE treasury_address IS NULL
          OR lower(treasury_address) = ANY($1::text[])`,
    [provisionAddressesToWipe]
  );

  // 3) KMS alias 정리 (셋 다)
  kmsDeleteAliasIfExists(REWARD_ALIAS);
  kmsDeleteAliasIfExists(OTHER_ALIAS);
  kmsDeleteAliasIfExists(SPONSOR_ALIAS);
}

/** afterAll: 추적된 모든 KMS 키를 7일 schedule-deletion. 이미 PENDING_DELETION 이면 무시. */
function disposeAllTrackedKeys(): void {
  for (const keyId of trackedKmsKeys) {
    const state = kmsDescribeKeyState(keyId);
    if (state && state !== "PendingDeletion") {
      kmsScheduleKeyDeletion(keyId);
    }
  }
}

// ────────────────────────────────────────────────────────────────────────────
// Verification helpers
// ────────────────────────────────────────────────────────────────────────────
async function getWalletRow(alias: string) {
  const { rows } = await db.query(
    `SELECT wallet_alias, treasury_address, kms_key_id, status, key_origin
       FROM web3_treasury_wallets WHERE wallet_alias = $1`,
    [alias]
  );
  return rows[0] ?? null;
}
async function getLatestProvisionAudit(address: string) {
  const { rows } = await db.query(
    `SELECT success, failure_reason FROM web3_treasury_provision_audits
       WHERE lower(treasury_address) = lower($1)
       ORDER BY id DESC LIMIT 1`,
    [address]
  );
  return rows[0] ?? null;
}
async function countProvisionAudits(address: string): Promise<number> {
  const { rows } = await db.query<{ c: string }>(
    `SELECT COUNT(*)::text AS c FROM web3_treasury_provision_audits
       WHERE lower(treasury_address) = lower($1)`,
    [address]
  );
  return Number(rows[0].c);
}
async function getKmsAudits(alias: string, action: string) {
  const { rows } = await db.query(
    `SELECT success, failure_reason, kms_key_id, created_at
       FROM web3_treasury_kms_audits
       WHERE wallet_alias = $1 AND action_type = $2
       ORDER BY id ASC`,
    [alias, action]
  );
  return rows;
}

type ProvisionOverrides = {
  role?: "REWARD" | "SPONSOR" | "QNA_SIGNER";
  privateKey?: string;
  expectedAddress?: string;
};

async function provision(api: APIRequestContext, opts: ProvisionOverrides = {}) {
  return api.post(`${ENV.BACKEND_URL}/admin/web3/treasury-keys/provision`, {
    data: {
      rawPrivateKey: opts.privateKey ?? ENV.TREASURY_E2E_PRIVATE_KEY,
      role: opts.role ?? "REWARD",
      expectedAddress: opts.expectedAddress ?? ENV.TREASURY_E2E_EXPECTED_ADDRESS,
    },
  });
}

async function disableWallet(api: APIRequestContext, alias: string) {
  return api.post(`${ENV.BACKEND_URL}/admin/web3/treasury-keys/${alias}/disable`);
}

async function archiveWallet(api: APIRequestContext, alias: string) {
  return api.post(`${ENV.BACKEND_URL}/admin/web3/treasury-keys/${alias}/archive`);
}

// ────────────────────────────────────────────────────────────────────────────
// Suite
// ────────────────────────────────────────────────────────────────────────────
test.describe("Treasury Provision API — Group B & C", () => {
  let creds: AdminCredentials;
  let token = "";
  let api: APIRequestContext;

  test.beforeAll(async () => {
    expect(
      ENV.TREASURY_E2E_PRIVATE_KEY,
      "TREASURY_E2E_PRIVATE_KEY 누락 — prod KMS 검증 불가"
    ).toBeTruthy();
    expect(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS,
      "TREASURY_E2E_EXPECTED_ADDRESS 누락"
    ).toBeTruthy();

    creds = await setupAdmin();
    token = await adminLogin(creds.loginId, creds.password);
    api = await request.newContext({
      baseURL: ENV.BACKEND_URL,
      extraHTTPHeaders: authHeaders(token),
    });

    await fullCleanup();
  });

  test.afterAll(async () => {
    await fullCleanup();
    disposeAllTrackedKeys();
    await cleanupAdmin(creds.loginId);
    await db.end();
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group B
  // ──────────────────────────────────────────────────────────────────────────

  test("[P-7] Happy path — 신규 키 provision 성공 (B8)", async () => {
    await fullCleanup();

    const res = await provision(api);
    expect(res.status(), "[P-7] HTTP").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    expect(body.data.walletAlias).toBe(REWARD_ALIAS);
    expect(body.data.status).toBe("ACTIVE");
    expect(body.data.kmsKeyId).toBeTruthy();
    trackedKmsKeys.add(body.data.kmsKeyId);

    const wallet = await getWalletRow(REWARD_ALIAS);
    expect(wallet).not.toBeNull();
    expect(wallet.status).toBe("ACTIVE");
    expect(wallet.key_origin).toBe("IMPORTED");
    expect(String(wallet.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );

    const provisionAudit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(provisionAudit?.success).toBe(true);

    const kmsAudits = await getKmsAudits(REWARD_ALIAS, "KMS_CREATE_ALIAS");
    expect(kmsAudits.length, "[P-7] KMS_CREATE_ALIAS audit 1건").toBe(1);
    expect(kmsAudits[0].success).toBe(true);

    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo, "[P-7] alias bound").not.toBeNull();
    expect(aliasInfo!.keyId).toBe(body.data.kmsKeyId);
    expect(aliasInfo!.state).toBe("Enabled");
  });

  test("[P-8] 동일 입력 재호출 (alias→ENABLED) → 409 (B5)", async () => {
    // [P-7] 직후 상태 그대로. cleanup 하지 않는다.
    const wallet0 = await getWalletRow(REWARD_ALIAS);
    expect(wallet0, "[P-8] 선행 [P-7] 상태 필요").not.toBeNull();
    const beforeProvisionAuditCount = await countProvisionAudits(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );
    const beforeKmsAuditCount = (await getKmsAudits(REWARD_ALIAS, "KMS_CREATE_ALIAS")).length;
    const beforeKmsKey = wallet0.kms_key_id;

    const res = await provision(api);
    expect(res.status(), "[P-8] HTTP").toBe(409);
    const body = await res.json();
    expect(body.status).toBe("FAIL");

    // wallet row 변동 없음
    const wallet1 = await getWalletRow(REWARD_ALIAS);
    expect(wallet1.kms_key_id, "[P-8] kms_key_id 변동 없음").toBe(beforeKmsKey);

    // provision_audits: ALREADY_PROVISIONED row 추가
    const afterProvisionAuditCount = await countProvisionAudits(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );
    expect(afterProvisionAuditCount).toBe(beforeProvisionAuditCount + 1);
    const last = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(last.success).toBe(false);
    expect(last.failure_reason).toBe("ALREADY_PROVISIONED");

    // KMS_CREATE_ALIAS audit 추가 없음 (이벤트 미발행)
    const afterKmsAudits = await getKmsAudits(REWARD_ALIAS, "KMS_CREATE_ALIAS");
    expect(afterKmsAudits.length, "[P-8] KMS_CREATE_ALIAS 추가 발생 없음").toBe(
      beforeKmsAuditCount
    );
  });

  test("[P-10] 다른 alias 가 같은 address 보유 → 200 (MOM-444 공유 운영지갑 허용)", async () => {
    // MOM-444 이전엔 cross-row UNIQUE 가 동일 treasury_address 를 다른 alias 가 보유하지 못하게
    // 막아 409 였다. MOM-444 가 UNIQUE 를 제거해 하나의 운영지갑이 모든 TreasuryRole 을 수행할
    // 수 있도록 허용한다. 따라서 REWARD provision 은 C0 fresh provision 으로 200 을 반환하고
    // 두 row 가 동일 treasury_address 를 공유한 채 공존한다.
    await fullCleanup();

    // 다른 alias (OTHER_ALIAS) 가 동일 address 를 이미 보유 (kms_key_id 는 placeholder — 본 테스트
    // 에서는 OTHER_ALIAS row 의 kms 검증은 범위 밖)
    await db.query(
      `INSERT INTO web3_treasury_wallets
         (wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at)
       VALUES ($1, $2, $3, 'ACTIVE', 'IMPORTED', NOW(), NOW())`,
      [OTHER_ALIAS, ENV.TREASURY_E2E_EXPECTED_ADDRESS, "placeholder-other-alias"]
    );

    const res = await provision(api);
    expect(res.status(), "[P-10] HTTP").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    expect(body.data.walletAlias).toBe(REWARD_ALIAS);
    expect(body.data.kmsKeyId).toBeTruthy();
    trackedKmsKeys.add(body.data.kmsKeyId);

    // REWARD row 가 생성되고 OTHER_ALIAS row 와 동일 address 를 공유함을 검증
    const rewardRow = await getWalletRow(REWARD_ALIAS);
    expect(rewardRow, "[P-10] REWARD row 생성됨").not.toBeNull();
    expect(String(rewardRow.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );
    expect(rewardRow.status).toBe("ACTIVE");
    const otherRow = await db.query(
      `SELECT wallet_alias, treasury_address, kms_key_id FROM web3_treasury_wallets WHERE wallet_alias = $1`,
      [OTHER_ALIAS]
    );
    expect(otherRow.rows.length).toBe(1);
    expect(String(otherRow.rows[0].treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );
    // 같은 address 를 공유하지만 kms_key_id 는 서로 다르다 (wallet_alias : kms_key_id = 1:1)
    expect(rewardRow.kms_key_id).not.toBe(otherRow.rows[0].kms_key_id);

    // provision_audits: SUCCESS 1건 (이전엔 ALREADY_PROVISIONED FAILURE 였음)
    const audit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(audit?.success).toBe(true);

    // KMS alias 가 REWARD_ALIAS 로 새로 바인딩됨
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo, "[P-10] REWARD alias 생성됨").not.toBeNull();
    expect(aliasInfo!.keyId).toBe(body.data.kmsKeyId);
    expect(aliasInfo!.state).toBe("Enabled");
  });

  test("[P-11] legacy row(kms_key_id=null) + 다른 wallet_address → 200 (MOM-444 C10 backfill — derived address 가 stored 를 덮어쓴다)", async () => {
    // MOM-444 이전엔 legacy row 의 stored address 와 derived address 가 다르면 ADDRESS_MISMATCH 400
    // 이었다. MOM-444 에서는 non-cohort-v2 decision table 의 C10 (diff addr, null kms, ACTIVE) 가
    // backfill 로 라우팅되어 derived address 가 stored 를 덮어쓰고 새 kms_key_id 와 함께 ACTIVE 로
    // 200 을 반환한다 — operator runbook 으로 빠지지 않는 자동 정정 경로.
    await fullCleanup();

    const fakeAddress = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    await db.query(
      `INSERT INTO web3_treasury_wallets
         (wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at)
       VALUES ($1, $2, NULL, 'ACTIVE', 'IMPORTED', NOW(), NOW())`,
      [REWARD_ALIAS, fakeAddress]
    );

    const res = await provision(api);
    expect(res.status(), "[P-11] HTTP").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    expect(body.data.kmsKeyId).toBeTruthy();
    trackedKmsKeys.add(body.data.kmsKeyId);

    // wallet row: kms_key_id 와 treasury_address 가 새 값으로 덮어쓰임 (C10 backfill)
    const wallet = await getWalletRow(REWARD_ALIAS);
    expect(String(wallet.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );
    expect(wallet.kms_key_id).toBe(body.data.kmsKeyId);
    expect(wallet.status).toBe("ACTIVE");
    expect(wallet.key_origin).toBe("IMPORTED");

    // provision_audits: SUCCESS (ADDRESS_MISMATCH 더 이상 기록 안 됨 — MOM-444)
    const audit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(audit?.success).toBe(true);

    // KMS alias 가 REWARD_ALIAS 로 새 키에 바인딩됨
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo, "[P-11] alias 생성됨").not.toBeNull();
    expect(aliasInfo!.keyId).toBe(body.data.kmsKeyId);
    expect(aliasInfo!.state).toBe("Enabled");
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group C
  // ──────────────────────────────────────────────────────────────────────────

  test("[P-12] AFTER_COMMIT — alias 가 다른 ENABLED 키 점유 → 200 + KMS_CREATE_ALIAS audit fail (B10)", async () => {
    await fullCleanup();

    // K_FOREIGN: 외부 점유 키 (default symmetric, ENABLED)
    const kForeign = kmsCreateKey(`mztk-e2e-foreign-${randomSuffix()}`);
    kmsCreateAlias(REWARD_ALIAS, kForeign);

    const res = await provision(api);
    expect(res.status(), "[P-12] HTTP — handler 가 swallow 하므로 200").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    const newKey: string = body.data.kmsKeyId;
    expect(newKey).toBeTruthy();
    expect(newKey).not.toBe(kForeign);
    trackedKmsKeys.add(newKey);

    // DB: wallet row 는 새 키로 저장됨
    const wallet = await getWalletRow(REWARD_ALIAS);
    expect(wallet.kms_key_id).toBe(newKey);
    expect(wallet.status).toBe("ACTIVE");

    // provision_audits: 비즈니스 흐름은 success=true
    const provisionAudit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(provisionAudit?.success).toBe(true);

    // KMS audit: KMS_CREATE_ALIAS success=false (KmsAliasAlreadyExistsException)
    const createAudits = await getKmsAudits(REWARD_ALIAS, "KMS_CREATE_ALIAS");
    expect(createAudits.length).toBe(1);
    expect(createAudits[0].success).toBe(false);
    expect(createAudits[0].failure_reason).toContain("AlreadyExists");

    // ENABLED 외부 점유라 UPDATE_ALIAS 는 시도되지 않음
    const updateAudits = await getKmsAudits(REWARD_ALIAS, "KMS_UPDATE_ALIAS");
    expect(updateAudits.length, "[P-12] UPDATE 시도 없음").toBe(0);

    // KMS: alias 는 여전히 K_FOREIGN
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo?.keyId).toBe(kForeign);
  });

  test("[P-13] AFTER_COMMIT — alias 가 ghost(PENDING_DELETION) 가리킴 → KMS_UPDATE_ALIAS 복구 (B11)", async () => {
    await fullCleanup();

    // K_GHOST: createKey → createAlias → scheduleDeletion (PENDING_DELETION 상태로 alias 가 붙은 채 남음)
    const kGhost = kmsCreateKey(`mztk-e2e-ghost-${randomSuffix()}`);
    kmsCreateAlias(REWARD_ALIAS, kGhost);
    kmsScheduleKeyDeletion(kGhost);
    expect(
      kmsDescribeKeyState(kGhost),
      "[P-13] K_GHOST PENDING_DELETION 상태 확인"
    ).toBe("PendingDeletion");

    const res = await provision(api);
    expect(res.status(), "[P-13] HTTP").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");
    const newKey: string = body.data.kmsKeyId;
    expect(newKey).not.toBe(kGhost);
    trackedKmsKeys.add(newKey);

    const wallet = await getWalletRow(REWARD_ALIAS);
    expect(wallet.kms_key_id).toBe(newKey);

    // BindKmsAliasService 의 ghost-recovery 분기는 KMS_UPDATE_ALIAS 만 기록한다.
    // (CreateAlias 실패는 adapter 가 throw 하지만 audit 행은 PENDING_DELETION 분기에서 안 남김)
    const createAudits = await getKmsAudits(REWARD_ALIAS, "KMS_CREATE_ALIAS");
    expect(createAudits.length, "[P-13] ghost 복구 분기는 CREATE_ALIAS audit 미기록").toBe(0);

    const updateAudits = await getKmsAudits(REWARD_ALIAS, "KMS_UPDATE_ALIAS");
    expect(updateAudits.length).toBe(1);
    expect(updateAudits[0].success).toBe(true);

    // alias 가 새 키로 갱신됨
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo?.keyId).toBe(newKey);
    expect(aliasInfo?.state).toBe("Enabled");
  });

  test("[P-9] Service alias-repair — DB row 존재 + alias UNAVAILABLE → KMS_CREATE_ALIAS 성공 (B6)", async () => {
    await fullCleanup();

    // K_OLD: ENABLED, alias 미바인딩 — "이전 provision 시 AFTER_COMMIT 까지 도달 못한" 시나리오 재현
    const kOld = kmsCreateKey(`mztk-e2e-repair-${randomSuffix()}`);

    // DB row 만 미리 박아놓음 (kms_key_id=K_OLD, address=expected)
    await db.query(
      `INSERT INTO web3_treasury_wallets
         (wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at)
       VALUES ($1, $2, $3, 'ACTIVE', 'IMPORTED', NOW(), NOW())`,
      [REWARD_ALIAS, ENV.TREASURY_E2E_EXPECTED_ADDRESS, kOld]
    );

    const res = await provision(api);
    expect(res.status(), "[P-9] HTTP").toBe(200);
    const body = await res.json();
    expect(body.status).toBe("SUCCESS");

    // 서비스가 신규 키를 만들지 않고 K_OLD 를 그대로 재사용
    expect(body.data.kmsKeyId, "[P-9] 기존 K_OLD 재사용").toBe(kOld);

    const wallet = await getWalletRow(REWARD_ALIAS);
    expect(wallet.kms_key_id).toBe(kOld);

    // provision_audits: success=true 추가
    const provisionAudit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(provisionAudit?.success).toBe(true);

    // KMS_CREATE_ALIAS success=true (alias 가 missing 이었으므로 신규 생성에 성공)
    const createAudits = await getKmsAudits(REWARD_ALIAS, "KMS_CREATE_ALIAS");
    expect(createAudits.length).toBe(1);
    expect(createAudits[0].success).toBe(true);
    expect(createAudits[0].kms_key_id).toBe(kOld);

    // alias 가 K_OLD 로 새로 바인딩됨
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo?.keyId).toBe(kOld);
    expect(aliasInfo?.state).toBe("Enabled");
  });

  // ──────────────────────────────────────────────────────────────────────────
  // [MOM-444] action scenarios — 운영 KMS 회귀
  //
  // MOM-444 가 도입한 새 dispatch (C0+C0 공유지갑 / C5 reactivate / C6 archived
  // re-provision / C7 rotation) 를 운영 KMS 경로에서 검증한다. Java E2E (Mock KMS)
  // 가 비즈니스 로직 단위 회귀를 잡고, 본 그룹은 real `kms:UpdateAlias` /
  // `kms:EnableKey` / `kms:DisableKey` / `kms:ScheduleKeyDeletion` 의 단조 동작을
  // AWS 에 직접 호출해 검증한다.
  //
  // 기존 [P-10] / [P-11] 은 MOM-444 이후 의미가 바뀐 케이스로 동일 .spec 안에서
  // 신규 동작 (P-10: 200 + 공유주소 허용, P-11: 200 + derived address overwrite
  // C10 backfill) 으로 업데이트되어 있다. 본 그룹은 그 외 4개 dispatch 분기를 본다.
  //
  // 옛 박제 5개 → 실제 4개로 통합: 옛 [P-MOM444-5] (diff-addr ACTIVE → C7 자동
  // 라우팅) 는 [P-MOM444-2] 와 동일 dispatch 경로(C7 replaceKey + disposeOldKey=true)
  // 이므로 [P-MOM444-2] 에 흡수.
  // ──────────────────────────────────────────────────────────────────────────

  test("[P-MOM444-1] 공유 운영지갑 — REWARD provision 후 동일 raw key 로 SPONSOR provision (C0+C0)", async () => {
    await fullCleanup();

    // Action 1: REWARD provision
    const res1 = await provision(api);
    expect(res1.status(), "[P-MOM444-1] REWARD HTTP").toBe(200);
    const body1 = await res1.json();
    expect(body1.data.kmsKeyId).toBeTruthy();
    trackedKmsKeys.add(body1.data.kmsKeyId);

    // Action 2: 동일 raw key 로 SPONSOR provision (공유 운영지갑)
    const res2 = await provision(api, { role: "SPONSOR" });
    expect(res2.status(), "[P-MOM444-1] SPONSOR HTTP").toBe(200);
    const body2 = await res2.json();
    expect(body2.data.kmsKeyId).toBeTruthy();
    trackedKmsKeys.add(body2.data.kmsKeyId);

    // 두 row 가 동일 treasury_address 를 공유, 서로 다른 kms_key_id (1:1 invariant)
    const reward = await getWalletRow(REWARD_ALIAS);
    const sponsor = await getWalletRow(SPONSOR_ALIAS);
    expect(reward, "[P-MOM444-1] REWARD row 생성").not.toBeNull();
    expect(sponsor, "[P-MOM444-1] SPONSOR row 생성").not.toBeNull();
    expect(String(reward.treasury_address).toLowerCase()).toBe(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(String(sponsor.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );
    expect(reward.kms_key_id, "[P-MOM444-1] kms_key_id 는 alias 당 다름").not.toBe(
      sponsor.kms_key_id
    );
    expect(reward.status).toBe("ACTIVE");
    expect(sponsor.status).toBe("ACTIVE");

    // 두 alias 모두 각자 자기 키에 바인딩되어 ENABLED
    const rewardAlias = kmsDescribeAlias(REWARD_ALIAS);
    const sponsorAlias = kmsDescribeAlias(SPONSOR_ALIAS);
    expect(rewardAlias?.keyId).toBe(reward.kms_key_id);
    expect(sponsorAlias?.keyId).toBe(sponsor.kms_key_id);
    expect(rewardAlias?.state).toBe("Enabled");
    expect(sponsorAlias?.state).toBe("Enabled");
  });

  test("[P-MOM444-2] Key rotation (C7) — 다른 raw key 로 동일 alias 재 provision, old key disable+schedule_deletion", async () => {
    test.skip(
      !ENV.TREASURY_E2E_PRIVATE_KEY_2 || !ENV.TREASURY_E2E_EXPECTED_ADDRESS_2,
      "TREASURY_E2E_PRIVATE_KEY_2 / EXPECTED_ADDRESS_2 누락 — rotation 검증 불가"
    );
    await fullCleanup();

    // Setup: key1 으로 REWARD provision (alias→K_OLD ENABLED)
    const setup = await provision(api);
    expect(setup.status(), "[P-MOM444-2] setup HTTP").toBe(200);
    const before = await getWalletRow(REWARD_ALIAS);
    const oldKeyId: string = before.kms_key_id;
    expect(oldKeyId).toBeTruthy();
    trackedKmsKeys.add(oldKeyId);

    // Action: key2 로 동일 REWARD alias 재 provision → C7 (replaceKey + disposeOldKey=true)
    const res = await provision(api, {
      privateKey: ENV.TREASURY_E2E_PRIVATE_KEY_2,
      expectedAddress: ENV.TREASURY_E2E_EXPECTED_ADDRESS_2,
    });
    expect(res.status(), "[P-MOM444-2] rotation HTTP — 이전엔 ADDRESS_MISMATCH 400").toBe(200);
    const body = await res.json();
    const newKeyId: string = body.data.kmsKeyId;
    expect(newKeyId).toBeTruthy();
    expect(newKeyId).not.toBe(oldKeyId);
    trackedKmsKeys.add(newKeyId);

    // DB: row 의 kms_key_id 와 treasury_address 가 새 값으로 교체, status ACTIVE 유지
    const after = await getWalletRow(REWARD_ALIAS);
    expect(after.kms_key_id).toBe(newKeyId);
    expect(String(after.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS_2
    );
    expect(after.status).toBe("ACTIVE");

    // KMS: alias 가 새 키로 재바인딩
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo?.keyId).toBe(newKeyId);
    expect(aliasInfo?.state).toBe("Enabled");

    // KMS audits: 새 키에 대한 UPDATE_ALIAS + 옛 키에 대한 DISABLE + SCHEDULE_DELETION 성공 row
    const updateAudits = await getKmsAudits(REWARD_ALIAS, "KMS_UPDATE_ALIAS");
    expect(
      updateAudits.find((a: any) => a.kms_key_id === newKeyId && a.success === true),
      "[P-MOM444-2] UPDATE_ALIAS success for new key"
    ).toBeTruthy();

    const disableAudits = await getKmsAudits(REWARD_ALIAS, "KMS_DISABLE");
    expect(
      disableAudits.find((a: any) => a.kms_key_id === oldKeyId && a.success === true),
      "[P-MOM444-2] DISABLE success for old key"
    ).toBeTruthy();

    const scheduleAudits = await getKmsAudits(REWARD_ALIAS, "KMS_SCHEDULE_DELETION");
    expect(
      scheduleAudits.find((a: any) => a.kms_key_id === oldKeyId && a.success === true),
      "[P-MOM444-2] SCHEDULE_DELETION success for old key"
    ).toBeTruthy();

    // 옛 키 KMS 실제 상태 — Disabled 또는 PendingDeletion (handler 가 둘 다 시도)
    const oldState = kmsDescribeKeyState(oldKeyId);
    expect(
      oldState === "Disabled" || oldState === "PendingDeletion",
      `[P-MOM444-2] old key state=${oldState}`
    ).toBeTruthy();
  });

  test("[P-MOM444-3] ARCHIVED → re-provision (C6) — 새 key, 기존 key 는 손대지 않음", async () => {
    await fullCleanup();

    // Setup: provision → disable → archive (기존 키는 archive 시 schedule_deletion 까지 적용됨)
    expect((await provision(api)).status(), "[P-MOM444-3] setup provision").toBe(200);
    const initial = await getWalletRow(REWARD_ALIAS);
    const oldKeyId: string = initial.kms_key_id;
    expect(oldKeyId).toBeTruthy();
    trackedKmsKeys.add(oldKeyId);

    expect((await disableWallet(api, REWARD_ALIAS)).status(), "[P-MOM444-3] disable").toBe(200);
    expect((await archiveWallet(api, REWARD_ALIAS)).status(), "[P-MOM444-3] archive").toBe(200);

    const archived = await getWalletRow(REWARD_ALIAS);
    expect(archived.status, "[P-MOM444-3] row ARCHIVED").toBe("ARCHIVED");
    expect(kmsDescribeKeyState(oldKeyId), "[P-MOM444-3] old key PendingDeletion").toBe(
      "PendingDeletion"
    );

    // 옛 키에 대한 disable / schedule audit 개수 스냅샷 (re-provision 후 증가하면 안 됨)
    const disableBefore = (await getKmsAudits(REWARD_ALIAS, "KMS_DISABLE")).filter(
      (a: any) => a.kms_key_id === oldKeyId
    ).length;
    const scheduleBefore = (await getKmsAudits(REWARD_ALIAS, "KMS_SCHEDULE_DELETION")).filter(
      (a: any) => a.kms_key_id === oldKeyId
    ).length;

    // Action: 동일 raw key 로 재 provision → C6 (replaceKey + disposeOldKey=false)
    const res = await provision(api);
    expect(res.status(), "[P-MOM444-3] re-provision HTTP").toBe(200);
    const body = await res.json();
    const newKeyId: string = body.data.kmsKeyId;
    expect(newKeyId).toBeTruthy();
    expect(newKeyId).not.toBe(oldKeyId);
    trackedKmsKeys.add(newKeyId);

    // DB: row 가 새 키로 부활, status ACTIVE, 주소는 동일
    const reactivated = await getWalletRow(REWARD_ALIAS);
    expect(reactivated.status).toBe("ACTIVE");
    expect(reactivated.kms_key_id).toBe(newKeyId);
    expect(String(reactivated.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );

    // KMS: alias 가 새 키로 재바인딩, 옛 키는 PendingDeletion 그대로
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo?.keyId).toBe(newKeyId);
    expect(aliasInfo?.state).toBe("Enabled");
    expect(kmsDescribeKeyState(oldKeyId), "[P-MOM444-3] old key 그대로 PendingDeletion").toBe(
      "PendingDeletion"
    );

    // 옛 키에 대한 추가 disable / schedule audit 없음 (disposeOldKey=false 분기)
    const disableAfter = (await getKmsAudits(REWARD_ALIAS, "KMS_DISABLE")).filter(
      (a: any) => a.kms_key_id === oldKeyId
    ).length;
    const scheduleAfter = (await getKmsAudits(REWARD_ALIAS, "KMS_SCHEDULE_DELETION")).filter(
      (a: any) => a.kms_key_id === oldKeyId
    ).length;
    expect(disableAfter, "[P-MOM444-3] old key DISABLE 추가 없음").toBe(disableBefore);
    expect(scheduleAfter, "[P-MOM444-3] old key SCHEDULE_DELETION 추가 없음").toBe(scheduleBefore);
  });

  test("[P-MOM444-4] DISABLED → reactivate (C5) — KMS_ENABLE + status ACTIVE", async () => {
    await fullCleanup();

    // Setup: provision → disable (같은 키가 alias 에 바인딩된 채 Disabled)
    expect((await provision(api)).status(), "[P-MOM444-4] setup provision").toBe(200);
    const initial = await getWalletRow(REWARD_ALIAS);
    const sameKeyId: string = initial.kms_key_id;
    expect(sameKeyId).toBeTruthy();
    trackedKmsKeys.add(sameKeyId);

    expect((await disableWallet(api, REWARD_ALIAS)).status(), "[P-MOM444-4] disable").toBe(200);
    const disabled = await getWalletRow(REWARD_ALIAS);
    expect(disabled.status).toBe("DISABLED");
    expect(kmsDescribeKeyState(sameKeyId)).toBe("Disabled");

    // Action: 동일 raw key 로 재 provision → C5 (reEnableSameKey)
    const res = await provision(api);
    expect(res.status(), "[P-MOM444-4] re-provision HTTP").toBe(200);

    // DB: status ACTIVE 복구, kms_key_id 는 동일 (새 키로 교체 안 됨)
    const reactivated = await getWalletRow(REWARD_ALIAS);
    expect(reactivated.status).toBe("ACTIVE");
    expect(reactivated.kms_key_id, "[P-MOM444-4] C5 는 같은 키 재사용").toBe(sameKeyId);
    expect(String(reactivated.treasury_address).toLowerCase()).toBe(
      ENV.TREASURY_E2E_EXPECTED_ADDRESS
    );

    // KMS: 동일 키가 다시 Enabled, alias 도 그대로
    expect(kmsDescribeKeyState(sameKeyId), "[P-MOM444-4] key Enabled 로 복귀").toBe("Enabled");
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    expect(aliasInfo?.keyId).toBe(sameKeyId);
    expect(aliasInfo?.state).toBe("Enabled");

    // MOM-444 가 새로 도입한 KMS_ENABLE 액션 — success audit row 가 같은 키에 대해 존재
    const enableAudits = await getKmsAudits(REWARD_ALIAS, "KMS_ENABLE");
    expect(
      enableAudits.find((a: any) => a.kms_key_id === sameKeyId && a.success === true),
      "[P-MOM444-4] KMS_ENABLE success row for same key"
    ).toBeTruthy();
  });
});
