/**
 * Reward Treasury readiness preflight for Playwright suites that exercise
 * `TransactionIssuerWorker` end-to-end (currently only level-reward Suite D).
 *
 * The worker requires the `reward-treasury` row in `web3_treasury_wallets` to
 * have a non-blank `kms_key_id` (TransactionIssuerWorker.java:111-118) AND the
 * KMS key behind that id to be Enabled (verifyTreasuryWalletForSignPort →
 * DescribeKey). On a freshly migrated database neither is true, so the worker
 * loops on TREASURY_KEY_MISSING / KMS_KEY_NOT_ENABLED forever.
 *
 * `ensureRewardTreasuryReady()` is idempotent:
 *   1. Read `web3_treasury_wallets` row.
 *   2. Run `aws kms describe-key --key-id alias/reward-treasury`.
 *   3. If both already report a healthy key bound to the expected address,
 *      return without touching anything.
 *   4. Otherwise call POST /admin/web3/treasury-keys/provision exactly once,
 *      using the same admin-seed pattern as treasury-key-lifecycle.spec.ts.
 *
 * Helpers (`setupAdmin`, `adminLogin`, `cleanupAdmin`, `awsExec`) are copies of
 * the same helpers in treasury-key-lifecycle.spec.ts. They are duplicated here
 * on purpose so the spec stays a self-contained Playwright file (exporting from
 * a `.spec.ts` would make Playwright collect it twice).
 */

import { execSync } from "child_process";
import * as bcrypt from "bcryptjs";
import * as crypto from "crypto";
import * as dotenv from "dotenv";
import * as path from "path";
import { Pool } from "pg";
import { request } from "@playwright/test";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

const REWARD_ALIAS = "reward-treasury";

interface ResolvedEnv {
  backendUrl: string;
  privateKey: string;
  expectedAddress: string;
  databaseUrl?: string;
  dbHost: string;
  dbPort: number;
  dbName: string;
  dbUser: string;
  dbPassword: string;
}

function readEnv(): ResolvedEnv {
  return {
    backendUrl: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
    privateKey: process.env.TREASURY_E2E_PRIVATE_KEY ?? "",
    expectedAddress: (process.env.TREASURY_E2E_EXPECTED_ADDRESS ?? "").toLowerCase(),
    databaseUrl: process.env.DATABASE_URL,
    dbHost: process.env.DB_HOST ?? "localhost",
    dbPort: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
    dbName: process.env.DB_NAME ?? "mztk_dev",
    dbUser: process.env.DB_USER ?? process.env.DB_USERNAME ?? "postgres",
    dbPassword: process.env.DB_PASSWORD ?? "postgres",
  };
}

function buildPool(env: ResolvedEnv): Pool {
  if (env.databaseUrl) {
    return new Pool({ connectionString: env.databaseUrl });
  }
  return new Pool({
    host: env.dbHost,
    port: env.dbPort,
    database: env.dbName,
    user: env.dbUser,
    password: env.dbPassword,
  });
}

function awsExec(cmd: string, opts: { swallow?: boolean } = {}): string | null {
  try {
    return execSync(`aws ${cmd} --output json`, {
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "pipe"],
    });
  } catch (err: unknown) {
    if (opts.swallow) return null;
    const stderr =
      err && typeof err === "object" && "stderr" in err
        ? String((err as { stderr?: unknown }).stderr ?? "")
        : String(err);
    throw new Error(`aws ${cmd} failed:\n${stderr}`);
  }
}

interface KmsAliasInfo {
  keyId: string;
  state: string;
}

function kmsDescribeAlias(alias: string): KmsAliasInfo | null {
  const out = awsExec(`kms describe-key --key-id alias/${alias}`, { swallow: true });
  if (!out) return null;
  const meta = JSON.parse(out).KeyMetadata;
  return { keyId: meta.KeyId, state: meta.KeyState };
}

interface WalletRow {
  kms_key_id: string | null;
  status: string | null;
  treasury_address: string | null;
}

async function getRewardWallet(pool: Pool): Promise<WalletRow | null> {
  const { rows } = await pool.query<WalletRow>(
    `SELECT kms_key_id, status, treasury_address
       FROM web3_treasury_wallets WHERE wallet_alias = $1`,
    [REWARD_ALIAS]
  );
  return rows[0] ?? null;
}

function isWalletReady(row: WalletRow | null, expectedAddress: string): boolean {
  if (row == null) return false;
  if (row.kms_key_id == null || row.kms_key_id.trim() === "") return false;
  if (row.status !== "ACTIVE") return false;
  if (row.treasury_address == null) return false;
  return row.treasury_address.toLowerCase() === expectedAddress;
}

interface AdminCredentials {
  loginId: string;
  password: string;
}

function randomSuffix(): string {
  return crypto.randomBytes(4).toString("hex");
}

async function setupAdmin(pool: Pool): Promise<AdminCredentials> {
  const suffix = randomSuffix();
  const email = `lr-treasury-preflight-${suffix}@internal.mztk.local`;
  const loginId = `lr-treasury-preflight-${randomSuffix()}`;
  const password = `LrTrPreflight@${randomSuffix()}`;
  const hash = await bcrypt.hash(password, 10);

  await pool.query(
    `INSERT INTO users (email, role, nickname, created_at, updated_at)
     VALUES ($1, 'ADMIN_SEED', 'LrTreasuryPreflight', NOW(), NOW())`,
    [email]
  );
  const { rows } = await pool.query<{ id: number }>(
    "SELECT id FROM users WHERE email = $1",
    [email]
  );
  const userId = rows[0].id;
  await pool.query(
    `INSERT INTO admin_accounts
       (user_id, login_id, password_hash, created_by,
        last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)
     VALUES ($1, $2, $3, NULL, NULL, NULL, NULL, NOW(), NOW())`,
    [userId, loginId, hash]
  );
  return { loginId, password };
}

async function cleanupAdmin(pool: Pool, loginId: string): Promise<void> {
  const { rows } = await pool.query<{ user_id: number }>(
    "SELECT user_id FROM admin_accounts WHERE login_id = $1",
    [loginId]
  );
  if (rows.length === 0) return;
  const userId = rows[0].user_id;
  await pool.query("DELETE FROM admin_accounts WHERE login_id = $1", [loginId]);
  await pool.query("DELETE FROM users WHERE id = $1", [userId]);
}

async function adminLogin(backendUrl: string, creds: AdminCredentials): Promise<string> {
  const ctx = await request.newContext({ baseURL: backendUrl });
  try {
    const res = await ctx.post(`${backendUrl}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: { provider: "LOCAL_ADMIN", loginId: creds.loginId, password: creds.password },
    });
    if (res.status() !== 200) {
      throw new Error(
        `[treasury-preflight] admin login failed: HTTP ${res.status()} ${await res.text()}`
      );
    }
    const body = await res.json();
    return (body.data as Record<string, string>).accessToken;
  } finally {
    await ctx.dispose();
  }
}

async function callProvision(env: ResolvedEnv, token: string): Promise<void> {
  const ctx = await request.newContext({
    baseURL: env.backendUrl,
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });
  try {
    const res = await ctx.post(`${env.backendUrl}/admin/web3/treasury-keys/provision`, {
      data: {
        rawPrivateKey: env.privateKey,
        role: "REWARD",
        expectedAddress: env.expectedAddress,
      },
    });
    const status = res.status();
    if (status === 200) return;
    if (status === 409) {
      // Already provisioned with the same address — preflight job is done.
      return;
    }
    throw new Error(
      `[treasury-preflight] provision returned HTTP ${status}: ${await res.text()}\n` +
        `Hint: ensure backend runs with web3.reward-token.treasury.provisioning.enabled=true ` +
        `(env WEB3_TREASURY_PROVISIONING_ENABLED=true) and web3.kms.enabled=true (prod profile).`
    );
  } finally {
    await ctx.dispose();
  }
}

export async function ensureRewardTreasuryReady(): Promise<void> {
  const env = readEnv();
  if (!env.privateKey || !env.expectedAddress) {
    throw new Error(
      "[treasury-preflight] TREASURY_E2E_PRIVATE_KEY and TREASURY_E2E_EXPECTED_ADDRESS must be set " +
        "in src/test/java/momzzangseven/mztkbe/integration/play_wright/.env to run worker-dependent suites " +
        "(level-reward Suite D)."
    );
  }

  const pool = buildPool(env);
  try {
    const initialRow = await getRewardWallet(pool);
    const aliasInfo = kmsDescribeAlias(REWARD_ALIAS);
    const dbReady = isWalletReady(initialRow, env.expectedAddress);
    const kmsReady = aliasInfo != null && aliasInfo.state === "Enabled";
    const aliasMatchesDb =
      dbReady &&
      kmsReady &&
      initialRow!.kms_key_id != null &&
      aliasInfo!.keyId === initialRow!.kms_key_id;

    if (dbReady && kmsReady && aliasMatchesDb) {
      // Already usable — nothing to do.
      return;
    }

    const creds = await setupAdmin(pool);
    try {
      const token = await adminLogin(env.backendUrl, creds);
      await callProvision(env, token);
    } finally {
      await cleanupAdmin(pool, creds.loginId);
    }

    const verifyRow = await getRewardWallet(pool);
    if (!isWalletReady(verifyRow, env.expectedAddress)) {
      throw new Error(
        "[treasury-preflight] provision call returned success but DB row is still not ready: " +
          JSON.stringify(verifyRow)
      );
    }
    const verifyAlias = kmsDescribeAlias(REWARD_ALIAS);
    if (verifyAlias == null || verifyAlias.state !== "Enabled") {
      throw new Error(
        "[treasury-preflight] provision call returned success but KMS alias is not Enabled: " +
          JSON.stringify(verifyAlias)
      );
    }
  } finally {
    await pool.end();
  }
}
