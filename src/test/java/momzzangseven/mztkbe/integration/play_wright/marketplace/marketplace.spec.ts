/**
 * MZTK - 마켓플레이스 API Playwright E2E 테스트
 *
 * 테스트 대상:
 *   1. 트레이너 상점 등록 및 수정 (Upsert)
 *   2. 트레이너 상점 단건 조회
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 http://127.0.0.1:8080 에서 실행 중이어야 합니다.
 *   - 일반 사용자를 생성한 후 TRAINER 권한으로 승급하는 플로우가 필요합니다.
 */

import { test, expect } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
};

/**
 * 테스트 전용 로컬 계정을 생성하고, TRAINER 역할을 부여한 후,
 * 새로운 accessToken을 받아 반환합니다.
 */
async function signUpAndLoginAsTrainer(
  apiContext: import("@playwright/test").APIRequestContext,
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

test.describe("마켓플레이스 API 테스트", () => {
  let trainerToken: string;
  let userToken: string; // 일반 USER 권한 토큰 (접근 거부 테스트용)

  test.beforeAll(async ({ request }) => {
    const trainerEmail = `trainer-${Date.now()}@mztk-test.com`;
    const userEmail = `user-${Date.now()}@mztk-test.com`;

    // 트레이너 계정 발급
    trainerToken = await signUpAndLoginAsTrainer(request, trainerEmail);

    // 일반 유저 계정 발급 (역할 변경 안 함)
    const userSignup = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
      headers: { "Content-Type": "application/json" },
      data: {
        email: userEmail,
        password: "TestPass1234!",
        nickname: "일반사용자",
      },
    });
    expect(userSignup.status(), "유저 회원가입 실패").toBe(200);

    const userLogin = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: {
        email: userEmail,
        password: "TestPass1234!",
        provider: "LOCAL",
      },
    });
    userToken = (await userLogin.json()).data.accessToken;
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-01: [실패] 일반 USER는 상점을 등록할 수 없다 (403 Forbidden 권한 에러)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-01: 일반 사용자는 상점을 등록할 수 없다 (403)", async ({ request }) => {
    const response = await request.put(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
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
    });

    // 권한 부족에 대해서는 401/403 응답 예상
    expect([401, 403], `예상치 못한 상태코드: ${response.status()}`).toContain(response.status());
    console.log(`[TC-MP-01] 일반 사용자 접근 거부: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-01-A: [실패] 상점을 아직 등록하지 않은 트레이너가 조회할 경우 (404 Not Found)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-01-A: 상점 미등록 상태에서 조회 시 404 에러를 반환한다", async ({ request }) => {
    // trainerToken 계정은 아직 상점을 만들지 않았습니다 (TC-MP-02 이전).
    const response = await request.get(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: { Authorization: `Bearer ${trainerToken}` },
    });

    expect(response.status(), "상점이 없으므로 404를 반환해야 합니다").toBe(404);

    const body = await response.json();
    expect(body).toHaveProperty("status", "FAIL");
    console.log(`[TC-MP-01-A] 미등록 상점 조회 실패: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-02: [성공] 트레이너가 신규 상점을 등록한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-02: 트레이너가 정상적으로 신규 상점을 등록한다", async ({ request }) => {
    const reqData = {
      storeName: "몸짱 트레이닝 센터",
      address: "서울시 강남구 테헤란로 123",
      detailAddress: "지하 1층",
      latitude: 37.501,
      longitude: 127.039,
      phoneNumber: "02-1234-5678",
      instagramUrl: "https://instagram.com/mztk_trainer",
    };

    const response = await request.put(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${trainerToken}`,
      },
      data: reqData,
    });

    expect(response.status(), "상점 등록 실패").toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty("status", "SUCCESS");
    
    // 응답값에 입력한 내용이 반영되었는지 확인
    const data = body.data;
    expect(data.storeId, "상점 ID가 응답에 없습니다").toBeDefined();
    expect(data.storeName).toBeUndefined(); // 응답 스펙상 UpsertStoreResponseDTO는 storeId만 반환함
    console.log(`[TC-MP-02] 상점 등록 성공. storeId=${data.storeId}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-03: [성공] 트레이너가 자신이 등록한 상점을 조회한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-03: 상점을 등록한 후 단건 조회가 정상 동작한다", async ({ request }) => {
    const response = await request.get(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: { Authorization: `Bearer ${trainerToken}` },
    });

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
  // TC-MP-04: [성공] 트레이너가 기존 상점의 정보를 수정(Upsert)한다
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-04: 등록된 상점의 정보를 PUT 방식으로 수정한다", async ({ request }) => {
    const updateData = {
      storeName: "우주최강 트레이닝 센터 (수정됨)",
      address: "서울시 강남구 테헤란로 456", // 주소 변경
      latitude: 37.511, // 좌표 변경
      longitude: 127.049,
      phoneNumber: "010-9999-8888", // 번호 변경
    };

    const updateResponse = await request.put(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${trainerToken}`,
      },
      data: updateData,
    });

    expect(updateResponse.status(), "상점 수정 실패").toBe(200);

    const updateBody = await updateResponse.json();
    expect(updateBody.data.storeId, "수정 후 storeId 응답 없음").toBeDefined();

    // 수정 완료 후 조회해서 정확히 반영되었는지 교차 검증
    const getResponse = await request.get(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: { Authorization: `Bearer ${trainerToken}` },
    });
    
    const getBody = await getResponse.json();
    expect(getBody.data.storeName).toBe(updateData.storeName);
    
    console.log(`[TC-MP-04] 상점 수정 성공. 새 이름=${getBody.data.storeName}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-05: [실패] 필수 항목 누락 시 400 Bad Request 에러 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-05: 필수 파라미터(이름, 좌표 등) 누락 시 에러를 반환한다", async ({ request }) => {
    const invalidData = {
      storeName: "", // 비어있음
      address: "주소만 있음",
      // 위경도 없음
    };

    const response = await request.put(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${trainerToken}`,
      },
      data: invalidData,
    });

    expect(response.status(), "필수 파라미터 누락인데 200 응답이 왔습니다.").toBe(400);

    const body = await response.json();
    expect(body).toHaveProperty("status", "FAIL");
    console.log(`[TC-MP-05] 필수값 누락 요청: ${response.status()} 예외 처리 확인 (정상)`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-06: [실패] 잘못된 위경도 값 입력 시 400 Bad Request
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-06: 위경도 범위를 벗어난 잘못된 값 입력 시 400 에러를 반환한다", async ({ request }) => {
    const invalidCoordsData = {
      storeName: "위경도 이상한 상점",
      address: "서울",
      latitude: 999.0, // 올바르지 않은 위도 (정상적인 범위: -90 ~ +90)
      longitude: -200.0, // 올바르지 않은 경도 (정상적인 범위: -180 ~ +180)
    };

    const response = await request.put(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${trainerToken}`,
      },
      data: invalidCoordsData,
    });

    expect(response.status(), "유효하지 않은 좌표입니다").toBe(400);
    console.log(`[TC-MP-06] 좌표 범위 밖 요청: ${response.status()}`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-MP-07: [실패] 올바르지 않은 URL 형식 입력 시 400 Bad Request
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-MP-07: 올바르지 않은 URL 형식 입력 시 400 에러를 반환한다", async ({ request }) => {
    const invalidUrlData = {
      storeName: "URL 이상한 상점",
      address: "서울",
      latitude: 37.5,
      longitude: 127.0,
      homepageUrl: "not-a-valid-url-_-", // http/https 없음
      instagramUrl: "ftp://instagram.weird", // 잘못된 프로토콜
    };

    const response = await request.put(`${ENV.BACKEND_URL}/marketplace/trainer/store`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${trainerToken}`,
      },
      data: invalidUrlData,
    });

    expect(response.status(), "유효하지 않은 URL 검증이 실패했습니다").toBe(400);
    console.log(`[TC-MP-07] 잘못된 URL 형식: ${response.status()}`);
  });
});
