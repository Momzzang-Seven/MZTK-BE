/**
 * MZTK – Web3 지갑 연동 Playwright E2E 테스트
 *
 * 테스트 대상: 실제 블록체인(Optimism Sepolia Testnet) 기반 지갑 등록·해제 플로우
 *   A. Challenge (EIP-4361) 발급 API
 *   B. Wallet 등록 API  — ethers.js 로 EIP-712 서명 생성 → 백엔드 검증
 *   C. Wallet 해제 API
 *
 * 테스트 전략:
 *   - 실제 블록체인 트랜잭션은 발생하지 않습니다.
 *     (지갑 등록은 오프체인 서명 검증만 수행)
 *   - Playwright request context 로 HTTP 레이어만 검증합니다.
 *   - ethers.Wallet.createRandom() 으로 테스트마다 고유 지갑을 동적 생성합니다.
 *     → 고정 지갑 주소를 공유할 때 발생하는 409 Conflict 를 원천 차단합니다.
 *   - ethers.js v6 의 signTypedData() 를 사용하여 백엔드와 동일한
 *     EIP-712 서명을 로컬에서 생성합니다.
 *
 * 사전 조건:
 *   1. MZTK-BE 서버가 http://127.0.0.1:8080 에서 실행 중이어야 합니다.
 *   2. PostgreSQL 이 로컬에서 실행 중이어야 합니다.
 *   3. play_wright/.env 파일에 BACKEND_URL 이 설정되어 있어야 합니다.
 *      (미설정 시 기본값 http://127.0.0.1:8080 사용)
 *   4. 백엔드 .env 에 WEB3_EIP712_VERIFYING_CONTRACT, WEB3_CHAIN_ID 가
 *      올바르게 설정되어 있어야 합니다.
 * 
 * 지갑 주소는 각 테스트케이스에서 새로 생성됩니다.
 *    각 테스트가 ethers.Wallet.createRandom() 으로 고유 지갑을 생성합니다.
 */

import { test, expect } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";
import { ethers } from "ethers";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

// ────────────────────────────────────────────────────────────────────────────
// 환경 변수
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
};

// ────────────────────────────────────────────────────────────────────────────
// EIP-712 도메인 상수 (백엔드 application.yml 과 반드시 일치해야 함)
// ────────────────────────────────────────────────────────────────────────────
const EIP712_DOMAIN = {
  name: "MomzzangSeven",
  version: "1",
  chainId: 11155420, // Optimism Sepolia Testnet
  verifyingContract: "0x815B53fD2D56044BaC39c1f7a9C7d3E67322f0F5",
};

const EIP712_TYPES: Record<string, ethers.TypedDataField[]> = {
  AuthRequest: [
    { name: "content", type: "string" },
    { name: "nonce", type: "string" },
  ],
};

// ────────────────────────────────────────────────────────────────────────────
// 유틸리티
// ────────────────────────────────────────────────────────────────────────────

/** 회원가입 + 로그인 후 accessToken 반환 */
async function signUpAndLogin(
  request: import("@playwright/test").APIRequestContext,
  suffix: string
): Promise<{ accessToken: string; refreshToken: string }> {
  const email = `test-web3-${suffix}-${Date.now()}@mztk-test.com`;
  const password = "TestPass1234!";

  const signupRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname: `web3tester${suffix}` },
  });
  expect(
    signupRes.status(),
    `signup failed (${signupRes.status()})`
  ).toBe(200);

  const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, provider: "LOCAL" },
  });
  expect(loginRes.status(), `login failed (${loginRes.status()})`).toBe(200);

  const loginBody = await loginRes.json();
  const accessToken: string = loginBody.data.accessToken;

  // refreshToken 은 Set-Cookie 헤더에서 추출
  const setCookie = loginRes.headers()["set-cookie"] ?? "";
  const refreshToken =
    setCookie.match(/refreshToken=([^;]+)/)?.[1] ?? "";

  return { accessToken, refreshToken };
}

/**
 * Challenge 발급 (POST /web3/challenges)
 * @returns { nonce, message, expiresIn }
 */
async function issueChallenge(
  request: import("@playwright/test").APIRequestContext,
  accessToken: string,
  walletAddress: string
): Promise<{ nonce: string; message: string; expiresIn: number }> {
  const res = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: {
      purpose: "WALLET_REGISTRATION",
      walletAddress,
    },
  });
  expect(res.status(), `challenge 발급 실패 (${res.status()})`).toBe(200);
  const body = await res.json();
  return body.data as { nonce: string; message: string; expiresIn: number };
}

/**
 * EIP-712 서명 생성 (ethers.js v6)
 * 백엔드 EIP712SignatureVerifier 와 동일한 로직으로 서명을 만듭니다.
 */
async function signEip712(
  privateKey: string,
  challengeMessage: string,
  nonce: string
): Promise<string> {
  const wallet = new ethers.Wallet(privateKey);
  const signature = await wallet.signTypedData(
    EIP712_DOMAIN,
    EIP712_TYPES,
    { content: challengeMessage, nonce }
  );
  return signature;
}

// ────────────────────────────────────────────────────────────────────────────
// A. Challenge API 테스트
// ────────────────────────────────────────────────────────────────────────────

test.describe("Suite A — Challenge 발급 API", () => {
  test(
    "TC-W3-A-01 | 유효한 지갑 주소로 챌린지 발급 → 200 OK + EIP-4361 메시지 반환",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "a01");

      // 테스트마다 고유한 랜덤 지갑 주소 생성
      const testWallet = ethers.Wallet.createRandom();

      const res = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          purpose: "WALLET_REGISTRATION",
          walletAddress: testWallet.address,
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();

      // 응답 구조 검증
      expect(body.data).toHaveProperty("nonce");
      expect(body.data).toHaveProperty("message");
      expect(body.data).toHaveProperty("expiresIn");
      expect(typeof body.data.nonce).toBe("string");
      expect(body.data.nonce.length).toBeGreaterThan(0);

      // EIP-4361 메시지 형식 검증
      const msg: string = body.data.message;
      expect(msg).toContain("MZTK wants you to register your wallet");
      expect(msg).toContain(testWallet.address.toLowerCase());
      expect(msg).toContain("Nonce:");
      expect(msg).toContain("Chain ID:");
      expect(msg).toContain("URI:");

      console.log(`  ✓ nonce       = ${body.data.nonce}`);
      console.log(`  ✓ expiresIn   = ${body.data.expiresIn}s`);
      console.log(`  ✓ message(첫줄) = ${msg.split("\n")[0]}`);
    }
  );

  test(
    "TC-W3-A-02 | 인증 없이 챌린지 요청 → 401 Unauthorized",
    async ({ request }) => {
      const testWallet = ethers.Wallet.createRandom();

      const res = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
        // Authorization 헤더 없음
        data: {
          purpose: "WALLET_REGISTRATION",
          walletAddress: testWallet.address,
        },
      });

      expect(res.status()).toBe(401);
      console.log(`  ✓ 인증 없는 요청 → ${res.status()} Unauthorized`);
    }
  );

  test(
    "TC-W3-A-03 | purpose 필드 누락 → 400 Bad Request",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "a03");
      const testWallet = ethers.Wallet.createRandom();

      const res = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: {
          // purpose 필드 의도적으로 누락
          walletAddress: testWallet.address,
        },
      });

      expect(res.status()).toBe(400);
      console.log(`  ✓ purpose 누락 → ${res.status()} Bad Request`);
    }
  );

  test(
    "TC-W3-A-04 | 존재하지 않는 purpose 값 → 400 Bad Request",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "a04");
      const testWallet = ethers.Wallet.createRandom();

      const res = await request.post(`${ENV.BACKEND_URL}/web3/challenges`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: {
          purpose: "INVALID_PURPOSE",
          walletAddress: testWallet.address,
        },
      });

      expect(res.status()).toBe(400);
      console.log(
        `  ✓ 잘못된 purpose 값 → ${res.status()} Bad Request`
      );
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// B. Wallet 등록 API 테스트 (실제 EIP-712 서명 포함)
// ────────────────────────────────────────────────────────────────────────────

test.describe("Suite B — Wallet 등록 API (EIP-712 서명 검증)", () => {
  test(
    "TC-W3-B-01 | 챌린지 발급 → EIP-712 서명 → 지갑 등록 성공 → 201 Created",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "b01");

      // 테스트마다 고유 지갑 생성 → 409 충돌 없음
      const testWallet = ethers.Wallet.createRandom();

      // 1. Challenge 발급
      const { nonce, message } = await issueChallenge(
        request,
        accessToken,
        testWallet.address
      );

      // 2. EIP-712 서명 생성 (ethers.js)
      const signature = await signEip712(
        testWallet.privateKey,
        message,
        nonce
      );

      // 3. 지갑 등록
      const registerRes = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          walletAddress: testWallet.address,
          signature,
          nonce,
        },
      });

      expect(registerRes.status()).toBe(201);
      const registerBody = await registerRes.json();
      expect(registerBody.data).toHaveProperty("walletAddress");
      expect(
        registerBody.data.walletAddress.toLowerCase()
      ).toBe(testWallet.address.toLowerCase());

      console.log(
        `  ✓ 지갑 등록 성공: address=${registerBody.data.walletAddress}`
      );
    }
  );

  test(
    "TC-W3-B-02 | 잘못된 서명 포맷으로 지갑 등록 → 400 Bad Request",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "b02");
      const testWallet = ethers.Wallet.createRandom();

      const { nonce } = await issueChallenge(
        request,
        accessToken,
        testWallet.address
      );

      // 잘못된 서명 (130자 hex 아님)
      const badSignature = "0x" + "ab".repeat(32); // 65자 — 형식 불일치

      const res = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          walletAddress: testWallet.address,
          signature: badSignature,
          nonce,
        },
      });

      expect(res.status()).toBe(400);
      console.log(`  ✓ 잘못된 서명 포맷 → ${res.status()} Bad Request`);
    }
  );

  test(
    "TC-W3-B-03 | 다른 개인키로 생성한 서명 → 서명 검증 실패 → 400 Bad Request",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "b03");

      // 챌린지 발급 대상 지갑 (A 지갑)
      const walletA = ethers.Wallet.createRandom();
      // 잘못된 서명에 사용할 지갑 (B 지갑)
      const walletB = ethers.Wallet.createRandom();

      const { nonce, message } = await issueChallenge(
        request,
        accessToken,
        walletA.address
      );

      // B 지갑 키로 서명 → 백엔드는 A 주소와 비교하므로 검증 실패
      const wrongSignature = await signEip712(
        walletB.privateKey,
        message,
        nonce
      );

      const res = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          walletAddress: walletA.address,
          signature: wrongSignature,
          nonce,
        },
      });

      // 서명 주소 불일치 → 서비스에서 400 처리
      expect([400, 401]).toContain(res.status());
      console.log(`  ✓ 서명 불일치 → ${res.status()}`);
    }
  );

  test(
    "TC-W3-B-04 | 인증 없이 지갑 등록 요청 → 401 Unauthorized",
    async ({ request }) => {
      const testWallet = ethers.Wallet.createRandom();

      const res = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
        // Authorization 헤더 없음
        data: {
          walletAddress: testWallet.address,
          signature: "0x" + "ab".repeat(65),
          nonce: "some-nonce",
        },
      });

      expect(res.status()).toBe(401);
      console.log(`  ✓ 인증 없는 등록 요청 → ${res.status()} Unauthorized`);
    }
  );
});

// ────────────────────────────────────────────────────────────────────────────
// C. Wallet 해제 API 테스트
// ────────────────────────────────────────────────────────────────────────────

test.describe("Suite C — Wallet 해제 API", () => {
  /**
   * 지갑 등록까지 완료된 상태의 { accessToken, walletAddress, privateKey } 를 반환하는 헬퍼.
   * 테스트마다 고유 지갑을 생성하므로 409 Conflict 가 발생하지 않습니다.
   */
  async function setupRegisteredWallet(
    request: import("@playwright/test").APIRequestContext,
    suffix: string
  ): Promise<{ accessToken: string; walletAddress: string; privateKey: string }> {
    const { accessToken } = await signUpAndLogin(request, suffix);

    // 테스트마다 고유 지갑 생성
    const testWallet = ethers.Wallet.createRandom();

    const { nonce, message } = await issueChallenge(
      request,
      accessToken,
      testWallet.address
    );
    const signature = await signEip712(
      testWallet.privateKey,
      message,
      nonce
    );

    const registerRes = await request.post(`${ENV.BACKEND_URL}/web3/wallets`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      data: { walletAddress: testWallet.address, signature, nonce },
    });
    expect(registerRes.status(), `지갑 등록 실패 (${registerRes.status()})`).toBe(201);

    return { accessToken, walletAddress: testWallet.address, privateKey: testWallet.privateKey };
  }

  test(
    "TC-W3-C-01 | 등록된 지갑 해제 성공 → 200 OK",
    async ({ request }) => {
      const { accessToken, walletAddress } = await setupRegisteredWallet(
        request,
        "c01"
      );

      const unlinkRes = await request.delete(
        `${ENV.BACKEND_URL}/web3/wallets/${walletAddress}`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );

      expect(unlinkRes.status()).toBe(200);
      console.log(`  ✓ 지갑 해제 성공: address=${walletAddress}`);
    }
  );

  test(
    "TC-W3-C-02 | 등록되지 않은 지갑 해제 → 404 또는 400",
    async ({ request }) => {
      const { accessToken } = await signUpAndLogin(request, "c02");

      // 이 유저는 지갑을 등록한 적 없음 — 임의 주소로 해제 시도
      const unregisteredAddress = ethers.Wallet.createRandom().address;

      const res = await request.delete(
        `${ENV.BACKEND_URL}/web3/wallets/${unregisteredAddress}`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );

      expect([400, 404]).toContain(res.status());
      console.log(`  ✓ 미등록 지갑 해제 시도 → ${res.status()}`);
    }
  );

  test(
    "TC-W3-C-03 | 인증 없이 지갑 해제 → 401 Unauthorized",
    async ({ request }) => {
      const randomAddress = ethers.Wallet.createRandom().address;

      const res = await request.delete(
        `${ENV.BACKEND_URL}/web3/wallets/${randomAddress}`
        // Authorization 헤더 없음
      );

      expect(res.status()).toBe(401);
      console.log(`  ✓ 인증 없는 해제 요청 → ${res.status()} Unauthorized`);
    }
  );

  test(
    "TC-W3-C-04 | 지갑 해제 후 동일 지갑 재등록 가능",
    async ({ request }) => {
      const { accessToken, walletAddress, privateKey } =
        await setupRegisteredWallet(request, "c04");

      // 1단계: 해제
      const unlinkRes = await request.delete(
        `${ENV.BACKEND_URL}/web3/wallets/${walletAddress}`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
      expect(unlinkRes.status()).toBe(200);

      // 2단계: 재등록 — 해제 후 UNLINKED 상태이므로 재등록 가능
      // 새로운 챌린지를 발급받아야 함 (기존 nonce 는 USED 처리됨)
      const { nonce, message } = await issueChallenge(
        request,
        accessToken,
        walletAddress
      );
      const signature = await signEip712(privateKey, message, nonce);

      const reRegisterRes = await request.post(
        `${ENV.BACKEND_URL}/web3/wallets`,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          data: { walletAddress, signature, nonce },
        }
      );

      expect(reRegisterRes.status()).toBe(201);
      console.log(`  ✓ 해제 후 재등록 성공: address=${walletAddress}`);
    }
  );
});
