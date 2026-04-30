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
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
  DB_NAME: process.env.DB_NAME ?? "mztk_dev",
  DB_USER: process.env.DB_USER ?? process.env.DB_USERNAME ?? "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "postgres",
  DATABASE_URL: process.env.DATABASE_URL,
};

const REWARD_ALIAS = "reward-treasury";
const OTHER_ALIAS = "pending-treasury"; // for [P-10]
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
  await db.query("DELETE FROM web3_treasury_kms_audits WHERE wallet_alias IN ($1, $2)", [
    REWARD_ALIAS,
    OTHER_ALIAS,
  ]);
  await db.query("DELETE FROM web3_treasury_wallets WHERE wallet_alias IN ($1, $2)", [
    REWARD_ALIAS,
    OTHER_ALIAS,
  ]);
  await db.query(
    `DELETE FROM web3_treasury_provision_audits
       WHERE treasury_address IS NULL
          OR lower(treasury_address) = lower($1)`,
    [ENV.TREASURY_E2E_EXPECTED_ADDRESS]
  );

  // 3) KMS alias 정리 (둘 다)
  kmsDeleteAliasIfExists(REWARD_ALIAS);
  kmsDeleteAliasIfExists(OTHER_ALIAS);
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

async function provision(api: APIRequestContext) {
  return api.post(`${ENV.BACKEND_URL}/admin/web3/treasury-keys/provision`, {
    data: {
      rawPrivateKey: ENV.TREASURY_E2E_PRIVATE_KEY,
      role: "REWARD",
      expectedAddress: ENV.TREASURY_E2E_EXPECTED_ADDRESS,
    },
  });
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

  test("[P-10] 다른 alias 가 같은 address 보유 → 409 (B7)", async () => {
    await fullCleanup();

    // 다른 alias 로 동일 address row seed (kms_key_id 는 placeholder)
    await db.query(
      `INSERT INTO web3_treasury_wallets
         (wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at)
       VALUES ($1, $2, $3, 'ACTIVE', 'IMPORTED', NOW(), NOW())`,
      [OTHER_ALIAS, ENV.TREASURY_E2E_EXPECTED_ADDRESS, "placeholder-other-alias"]
    );

    const res = await provision(api);
    expect(res.status(), "[P-10] HTTP").toBe(409);
    const body = await res.json();
    expect(body.status).toBe("FAIL");

    // REWARD wallet row 생성되지 않음
    expect(await getWalletRow(REWARD_ALIAS), "[P-10] REWARD row 미생성").toBeNull();

    // provision_audits: ALREADY_PROVISIONED 1건
    const audit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(audit?.success).toBe(false);
    expect(audit?.failure_reason).toBe("ALREADY_PROVISIONED");

    // KMS alias / key 신규 생성 없음
    expect(kmsDescribeAlias(REWARD_ALIAS), "[P-10] KMS alias 생성 안 됨").toBeNull();
  });

  test("[P-11] 기존 legacy row + 다른 wallet_address → 400 (B4)", async () => {
    await fullCleanup();

    const fakeAddress = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    await db.query(
      `INSERT INTO web3_treasury_wallets
         (wallet_alias, treasury_address, kms_key_id, status, key_origin, created_at, updated_at)
       VALUES ($1, $2, NULL, 'ACTIVE', 'IMPORTED', NOW(), NOW())`,
      [REWARD_ALIAS, fakeAddress]
    );

    const res = await provision(api);
    expect(res.status(), "[P-11] HTTP").toBe(400);
    const body = await res.json();
    expect(body.status).toBe("FAIL");

    // wallet row 변동 없음 (여전히 legacy address)
    const wallet = await getWalletRow(REWARD_ALIAS);
    expect(String(wallet.treasury_address).toLowerCase()).toBe(fakeAddress.toLowerCase());
    expect(wallet.kms_key_id).toBeNull();

    // provision_audits: ADDRESS_MISMATCH (derivedAddress 와 함께 기록됨)
    const audit = await getLatestProvisionAudit(ENV.TREASURY_E2E_EXPECTED_ADDRESS);
    expect(audit?.success).toBe(false);
    expect(audit?.failure_reason).toBe("ADDRESS_MISMATCH");

    // KMS 신규 생성 없음
    expect(kmsDescribeAlias(REWARD_ALIAS)).toBeNull();
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
});
