import { APIRequestContext, expect, test } from "@playwright/test";
import * as bcrypt from "bcryptjs";
import { randomBytes } from "crypto";
import * as dotenv from "dotenv";
import * as path from "path";
import { Pool } from "pg";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  ADMIN_LOGIN_ID: process.env.ADMIN_LOGIN_ID ?? "",
  ADMIN_PASSWORD: process.env.ADMIN_PASSWORD ?? "",
  USER_ACCESS_TOKEN: process.env.USER_ACCESS_TOKEN ?? "",
  DATABASE_URL: process.env.DATABASE_URL,
  DB_URL: process.env.DB_URL,
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
  DB_NAME: process.env.DB_NAME ?? "mztk_dev",
  DB_USER: process.env.DB_USER ?? process.env.DB_USERNAME ?? "",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "",
  SPONSOR_NONCE_CHAIN_ID: Number.parseInt(
    envOrDefault(process.env.SPONSOR_NONCE_CHAIN_ID, "84532"),
    10
  ),
  SPONSOR_NONCE_ADDRESS: normalizeAddress(
    requiredEnv("SPONSOR_NONCE_ADDRESS", process.env.SPONSOR_NONCE_ADDRESS)
  ),
};

interface ApiEnvelope {
  status?: string;
  data?: unknown;
}

interface NonceSlotResponse {
  chainId?: unknown;
  fromAddress?: unknown;
  slots?: unknown;
}

interface NonceSlotView {
  nonce?: unknown;
  status?: unknown;
  attemptNo?: unknown;
  activeAttemptId?: unknown;
  activeTxId?: unknown;
  activeTxHash?: unknown;
  consumedAttemptId?: unknown;
  consumedTxId?: unknown;
  consumedExternalEvidenceId?: unknown;
  releasedAttemptId?: unknown;
  releasedTxId?: unknown;
  updatedAt?: unknown;
}

interface SeededAdmin {
  userId: number;
  loginId: string;
  password: string;
}

interface SeededUser {
  userId: number;
  accessToken: string;
}

function buildPool(): Pool {
  const configuredUrl = envOrEmpty(ENV.DATABASE_URL) || envOrEmpty(ENV.DB_URL);
  if (configuredUrl !== "") {
    return new Pool(poolConfigFromConnectionString(normalizePostgresConnectionString(configuredUrl)));
  }
  return new Pool({
    host: ENV.DB_HOST,
    port: ENV.DB_PORT,
    database: ENV.DB_NAME,
    user: ENV.DB_USER || "postgres",
    password: ENV.DB_PASSWORD || "postgres",
  });
}

function normalizePostgresConnectionString(connectionString: string): string {
  if (connectionString.startsWith("jdbc:postgresql://")) {
    return connectionString.replace(/^jdbc:/, "");
  }
  return connectionString;
}

function poolConfigFromConnectionString(connectionString: string) {
  try {
    const url = new URL(connectionString);
    return {
      host: url.hostname,
      port: url.port === "" ? 5432 : Number.parseInt(url.port, 10),
      database: decodeURIComponent(url.pathname.replace(/^\/+/, "")),
      user:
        ENV.DB_USER ||
        decodeURIComponent(url.username) ||
        url.searchParams.get("user") ||
        "postgres",
      password:
        ENV.DB_PASSWORD ||
        decodeURIComponent(url.password) ||
        url.searchParams.get("password") ||
        "postgres",
    };
  } catch {
    return {
      connectionString,
      user: ENV.DB_USER || "postgres",
      password: ENV.DB_PASSWORD || "postgres",
    };
  }
}

function envOrEmpty(value: string | undefined): string {
  if (value == null || value.trim() === "") {
    return "";
  }
  return value.trim();
}

async function adminLogin(
  api: APIRequestContext,
  loginId: string,
  password: string
): Promise<string> {
  const response = await api.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL_ADMIN", loginId, password },
  });
  expect(response.status(), `Admin login failed: HTTP ${response.status()}`).toBe(200);
  const body = (await response.json()) as ApiEnvelope;
  expect(body.status).toBe("SUCCESS");
  expectRecord(body.data, "admin login data must be an object");
  expect(typeof body.data["accessToken"]).toBe("string");
  return body.data["accessToken"] as string;
}

async function setupAdmin(pool: Pool, api: APIRequestContext): Promise<SeededAdmin> {
  const suffix = randomSuffix();
  const email = `pw-mom458-admin-${suffix}@internal.mztk.local`;
  const loginId = String(10_000_000 + Math.floor(Math.random() * 90_000_000));
  const password = `PwMom458@${suffix}`;
  const passwordHash = await bcrypt.hash(password, 10);

  const signupResponse = await api.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname: `PwMom458Admin${suffix}` },
  });
  expect(signupResponse.status(), `admin signup failed: ${await signupResponse.text()}`).toBe(200);
  const signupBody = (await signupResponse.json()) as ApiEnvelope;
  expectRecord(signupBody.data, "admin signup data must be an object");
  expect(typeof signupBody.data["userId"]).toBe("number");
  const userId = signupBody.data["userId"] as number;

  await pool.query("UPDATE users SET role = 'ADMIN_GENERATED' WHERE id = $1", [userId]);

  await pool.query(
    `INSERT INTO admin_accounts (
        user_id, login_id, password_hash, created_by, last_login_at,
        password_last_rotated_at, deleted_at, created_at, updated_at
     )
     VALUES ($1, $2, $3, NULL, NULL, NULL, NULL, NOW(), NOW())`,
    [userId, loginId, passwordHash]
  );

  return { userId, loginId, password };
}

async function cleanupAdmin(pool: Pool, admin: SeededAdmin | null): Promise<void> {
  if (admin == null) {
    return;
  }
  await pool.query("DELETE FROM refresh_tokens WHERE user_id = $1", [admin.userId]);
  await pool.query("DELETE FROM admin_accounts WHERE user_id = $1", [admin.userId]);
  await pool.query("DELETE FROM users_account WHERE user_id = $1", [admin.userId]);
  await pool.query("DELETE FROM users WHERE id = $1", [admin.userId]);
}

async function setupUser(api: APIRequestContext): Promise<SeededUser> {
  const suffix = randomSuffix();
  const email = `pw-mom458-user-${suffix}@test.com`;
  const password = `PwMom458@${suffix}`;
  const signupResponse = await api.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname: `PwMom458User${suffix}` },
  });
  expect(signupResponse.status(), `user signup failed: ${await signupResponse.text()}`).toBe(200);
  const signupBody = (await signupResponse.json()) as ApiEnvelope;
  expectRecord(signupBody.data, "signup data must be an object");
  expect(typeof signupBody.data["userId"]).toBe("number");

  const loginResponse = await api.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL", email, password },
  });
  expect(loginResponse.status(), `user login failed: ${await loginResponse.text()}`).toBe(200);
  const loginBody = (await loginResponse.json()) as ApiEnvelope;
  expectRecord(loginBody.data, "login data must be an object");
  expect(typeof loginBody.data["accessToken"]).toBe("string");

  return {
    userId: signupBody.data["userId"] as number,
    accessToken: loginBody.data["accessToken"] as string,
  };
}

async function cleanupUser(pool: Pool, user: SeededUser | null): Promise<void> {
  if (user == null) {
    return;
  }
  await pool.query("DELETE FROM refresh_tokens WHERE user_id = $1", [user.userId]);
  await pool.query("DELETE FROM users_account WHERE user_id = $1", [user.userId]);
  await pool.query("DELETE FROM users WHERE id = $1", [user.userId]);
}

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

function envOrDefault(value: string | undefined, fallback: string): string {
  if (value == null || value.trim() === "") {
    return fallback;
  }
  return value.trim();
}

function requiredEnv(name: string, value: string | undefined): string {
  if (value == null || value.trim() === "") {
    throw new Error(`${name} 환경 변수가 필요합니다`);
  }
  return value.trim();
}

function normalizeAddress(address: string): string {
  const trimmed = address.trim();
  return trimmed.startsWith("0x") ? trimmed.toLowerCase() : `0x${trimmed.toLowerCase()}`;
}

function upperHexAddress(address: string): string {
  return `0x${address.slice(2).toUpperCase()}`;
}

function expectRecord(value: unknown, message: string): asserts value is Record<string, unknown> {
  expect(typeof value, message).toBe("object");
  expect(value, message).not.toBeNull();
}

function expectNullableNumber(value: unknown, fieldName: string): void {
  if (value === null) {
    return;
  }
  expect(typeof value, `${fieldName} must be number or null`).toBe("number");
}

function assertSlotContract(slot: NonceSlotView, index: number): number {
  expect(typeof slot.nonce, `slots[${index}].nonce`).toBe("number");
  expect(typeof slot.status, `slots[${index}].status`).toBe("string");
  expect(typeof slot.attemptNo, `slots[${index}].attemptNo`).toBe("number");
  expectNullableNumber(slot.activeAttemptId, `slots[${index}].activeAttemptId`);
  expectNullableNumber(slot.activeTxId, `slots[${index}].activeTxId`);
  if (slot.activeTxHash !== null) {
    expect(typeof slot.activeTxHash, `slots[${index}].activeTxHash`).toBe("string");
  }
  expectNullableNumber(slot.consumedAttemptId, `slots[${index}].consumedAttemptId`);
  expectNullableNumber(slot.consumedTxId, `slots[${index}].consumedTxId`);
  expectNullableNumber(
    slot.consumedExternalEvidenceId,
    `slots[${index}].consumedExternalEvidenceId`
  );
  expectNullableNumber(slot.releasedAttemptId, `slots[${index}].releasedAttemptId`);
  expectNullableNumber(slot.releasedTxId, `slots[${index}].releasedTxId`);
  expect(typeof slot.updatedAt, `slots[${index}].updatedAt`).toBe("string");
  return slot.nonce as number;
}

function randomSuffix(): string {
  return randomBytes(6).toString("hex");
}

test.describe.configure({ mode: "serial" });

test.describe("MOM-458 sponsor nonce slot admin API", () => {
  let pool: Pool | null = null;
  let seededAdmin: SeededAdmin | null = null;
  let adminAccessToken = "";
  let seededUser: SeededUser | null = null;

  test.beforeAll(async ({ request }) => {
    pool = buildPool();
    if (ENV.ADMIN_LOGIN_ID && ENV.ADMIN_PASSWORD) {
      adminAccessToken = await adminLogin(request, ENV.ADMIN_LOGIN_ID, ENV.ADMIN_PASSWORD);
    } else {
      seededAdmin = await setupAdmin(pool, request);
      adminAccessToken = await adminLogin(request, seededAdmin.loginId, seededAdmin.password);
    }

    if (ENV.USER_ACCESS_TOKEN) {
      seededUser = { userId: -1, accessToken: ENV.USER_ACCESS_TOKEN };
    } else {
      seededUser = await setupUser(request);
    }
  });

  test.afterAll(async () => {
    if (pool == null) {
      return;
    }
    await cleanupUser(pool, seededUser != null && seededUser.userId > 0 ? seededUser : null);
    await cleanupAdmin(pool, seededAdmin);
    await pool.end();
  });

  test("[P-MOM458-1] Admin can read sponsor nonce slots", async ({ request }) => {
    const response = await request.get(`${ENV.BACKEND_URL}/admin/web3/nonce-slots`, {
      headers: authHeaders(adminAccessToken),
      params: {
        chainId: String(ENV.SPONSOR_NONCE_CHAIN_ID),
        fromAddress: upperHexAddress(ENV.SPONSOR_NONCE_ADDRESS),
      },
    });

    expect(response.status(), "[P-MOM458-1] admin nonce slot read status").toBe(200);
    const body = (await response.json()) as ApiEnvelope;
    expect(body.status).toBe("SUCCESS");
    expectRecord(body.data, "nonce slot response data must be an object");

    const data = body.data as NonceSlotResponse;
    expect(data.chainId).toBe(ENV.SPONSOR_NONCE_CHAIN_ID);
    expect(data.fromAddress).toBe(ENV.SPONSOR_NONCE_ADDRESS);
    expect(Array.isArray(data.slots), "slots must be an array").toBe(true);

    const slots = data.slots as NonceSlotView[];
    let previousNonce = -1;
    for (let index = 0; index < slots.length; index += 1) {
      const nonce = assertSlotContract(slots[index], index);
      expect(nonce, "slots must be sorted by nonce ascending").toBeGreaterThan(previousNonce);
      previousNonce = nonce;
    }
  });

  test("[P-MOM458-2] Anonymous request cannot read sponsor nonce slots", async ({ request }) => {
    const response = await request.get(`${ENV.BACKEND_URL}/admin/web3/nonce-slots`, {
      params: {
        chainId: String(ENV.SPONSOR_NONCE_CHAIN_ID),
        fromAddress: ENV.SPONSOR_NONCE_ADDRESS,
      },
    });

    expect(response.status(), "[P-MOM458-2] anonymous request status").toBe(401);
  });

  test("[P-MOM458-3] USER token cannot read sponsor nonce slots", async ({ request }) => {
    expect(seededUser).not.toBeNull();

    const response = await request.get(`${ENV.BACKEND_URL}/admin/web3/nonce-slots`, {
      headers: authHeaders(seededUser!.accessToken),
      params: {
        chainId: String(ENV.SPONSOR_NONCE_CHAIN_ID),
        fromAddress: ENV.SPONSOR_NONCE_ADDRESS,
      },
    });

    expect(response.status(), "[P-MOM458-3] user request status").toBe(403);
  });
});
