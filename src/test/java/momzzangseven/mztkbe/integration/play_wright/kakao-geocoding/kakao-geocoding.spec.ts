/**
 * MZTK - 카카오 Geocoding API Playwright E2E 테스트
 *
 * 테스트 대상: 실제 Kakao Geocoding / Reverse Geocoding API 연동 플로우
 *   1. 주소 기반 위치 등록 → 백엔드가 Kakao Geocoding API 호출 → 좌표 변환 후 DB 저장
 *   2. 좌표 기반 위치 등록 → 백엔드가 Kakao Reverse Geocoding API 호출 → 주소 변환 후 DB 저장
 *   3. 유효하지 않은 주소 → Geocoding API 결과 없음 → 에러 반환
 *   4. 인코딩된 결과 없는 좌표(해상) → Reverse Geocoding 결과 없음 → 에러 반환
 *   5. 등록된 위치 목록 조회
 *   6. 등록된 위치 삭제
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 http://127.0.0.1:8080 에서 실행 중이어야 합니다.
 *   - 백엔드 .env 파일에 유효한 KAKAO_REST_API_KEY 가 설정되어 있어야 합니다.
 *     (application.yml: kakao.api.rest-api-key)
 *   - PostgreSQL 이 로컬에서 실행 중이어야 합니다.
 *
 * 주의:
 *   이 테스트는 KakaoGeocodingAdapter 에 @MockBean 을 적용한 LocationE2ETest 와 달리,
 *   실제 Kakao API 서버에 HTTP 요청을 보냅니다.
 *   따라서 네트워크 상태 및 Kakao API 키 유효성에 따라 결과가 달라질 수 있습니다.
 */

import { test, expect } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

// ────────────────────────────────────────────────────────────────────────────
// 환경 변수
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
};

// ────────────────────────────────────────────────────────────────────────────
// 테스트 데이터 상수
// ────────────────────────────────────────────────────────────────────────────

/** 실제로 존재하는 한국 도로명 주소 (Kakao API 검색 가능) */
const VALID_ADDRESS = "경기 안산시 상록구 해안로 689 한양대학교 에리카캠퍼스";

/** 실제 좌표: 경기 안산시 상록구 해안로 689 부근 */
const VALID_COORDINATE = {
  latitude: 37.293,
  longitude: 126.831,
};

/** Kakao Geocoding API 가 결과를 반환하지 않을 것으로 예상되는 무의미한 주소 */
const NONEXISTENT_ADDRESS = "가나다라마바사아자차카타파하00000000000없는주소";

/** 한국 육지에서 매우 멀리 떨어진 해상 좌표 (Reverse Geocoding 결과 없음 예상) */
const OCEAN_COORDINATE = {
  latitude: 5.0,
  longitude: 100.0,
};

// ────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼
// ────────────────────────────────────────────────────────────────────────────

/**
 * 테스트 전용 로컬 계정을 생성하고 로그인하여 accessToken 을 반환합니다.
 * (Kakao OAuth 가 아닌 LOCAL provider 로그인 — 소셜 로그인 없이 순수 API 호출만 필요)
 */
async function signUpAndLogin(
  apiContext: import("@playwright/test").APIRequestContext,
  email: string
): Promise<string> {
  // 1. 회원가입
  const signupRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      nickname: "지오코딩테스터",
    },
  });
  expect(signupRes.status(), `회원가입 실패: ${email}`).toBe(200);

  // 2. 로그인
  const loginRes = await apiContext.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: {
      email,
      password: "TestPass1234!",
      provider: "LOCAL",
    },
  });
  expect(loginRes.status(), `로그인 실패: ${email}`).toBe(200);

  const loginBody = await loginRes.json();
  const accessToken = loginBody?.data?.accessToken as string | undefined;
  expect(accessToken, "accessToken 이 없음").toBeTruthy();
  return accessToken!;
}

// ════════════════════════════════════════════════════════════════════════════
// 주소 기반 위치 등록 테스트 스위트
// ════════════════════════════════════════════════════════════════════════════

test.describe("주소 기반 위치 등록 (Kakao Geocoding API)", () => {
  let accessToken: string;
  let registeredLocationId: number;

  test.beforeAll(async ({ request }) => {
    const email = `geocoding-addr-${Date.now()}@mztk-test.com`;
    accessToken = await signUpAndLogin(request, email);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-A-01: 유효한 주소로 위치 등록 → Kakao Geocoding → 좌표 변환 확인
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-A-01: 유효한 도로명 주소로 위치를 등록하면 Kakao API 가 좌표를 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/users/me/locations/register`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          locationName: "한양대학교 에리카캠퍼스",
          postalCode: "15588",
          address: VALID_ADDRESS,
          detailAddress: "한양대학교 에리카캠퍼스",
        },
      }
    );

    expect(response.status(), "위치 등록 HTTP 상태코드가 200 이 아님").toBe(
      200
    );

    const body = await response.json();
    expect(body, "응답 status 필드 누락").toHaveProperty("status", "SUCCESS");
    expect(body, "data 필드 누락").toHaveProperty("data");

    const data = body.data as Record<string, unknown>;
    expect(data, "locationId 필드 누락").toHaveProperty("locationId");
    expect(data, "address 필드 누락").toHaveProperty("address");
    expect(data, "latitude 필드 누락").toHaveProperty("latitude");
    expect(data, "longitude 필드 누락").toHaveProperty("longitude");

    // Kakao Geocoding API 가 실제로 좌표를 반환했는지 확인
    const latitude = data.latitude as number;
    const longitude = data.longitude as number;
    expect(typeof latitude, "latitude 타입 오류").toBe("number");
    expect(typeof longitude, "longitude 타입 오류").toBe("number");

    // 학교 부근의 합리적인 좌표 범위 검증 (대략 ±0.05도 허용)
    expect(latitude, "latitude 가 예상 범위를 벗어남").toBeGreaterThan(37.25);
    expect(latitude, "latitude 가 예상 범위를 벗어남").toBeLessThan(37.33);
    expect(longitude, "longitude 가 예상 범위를 벗어남").toBeGreaterThan(
      126.5
    );
    expect(longitude, "longitude 가 예상 범위를 벗어남").toBeLessThan(127.0);

    registeredLocationId = data.locationId as number;

    console.log(
      `[TC-GEO-A-01] 주소 등록 성공: locationId=${registeredLocationId}, ` +
        `lat=${latitude}, lng=${longitude}`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-A-02: 동일 주소 중복 등록 → 400 또는 409 에러
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-A-02: 이미 등록된 주소를 다시 등록하면 에러를 반환한다", async ({
    request,
  }) => {
    if (!registeredLocationId)
      test.skip(true, "TC-GEO-A-01 에서 위치를 먼저 등록해야 합니다");

    const response = await request.post(
      `${ENV.BACKEND_URL}/users/me/locations/register`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          locationName: "한양대학교 에리카캠퍼스",
          address: VALID_ADDRESS,
        },
      }
    );

    // 동일 주소 중복 등록은 에러여야 함
    expect(
      response.status(),
      "중복 주소 등록인데 200 이 반환됨"
    ).not.toBe(200);
    expect(
      [400, 409],
      `예상치 못한 상태코드: ${response.status()}`
    ).toContain(response.status());

    const body = await response.json();
    expect(body, "에러 응답에 status:FAIL 없음").toHaveProperty(
      "status",
      "FAIL"
    );

    console.log(
      `[TC-GEO-A-02] 중복 주소 등록 → ${response.status()} 정상 처리`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-A-03: 존재하지 않는 주소 → Geocoding 결과 없음 → 에러 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-A-03: 존재하지 않는 주소로 위치 등록 시 Geocoding 실패 에러를 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/users/me/locations/register`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          locationName: "없는 주소 테스트",
          address: NONEXISTENT_ADDRESS,
        },
      }
    );

    // Kakao API 가 결과를 못 찾으면 서버는 4xx 에러를 반환해야 함
    expect(
      response.status(),
      "존재하지 않는 주소인데 200 이 반환됨"
    ).not.toBe(200);
    expect(
      [400, 404, 422, 502],
      `예상치 못한 상태코드: ${response.status()}`
    ).toContain(response.status());

    const body = await response.json();
    expect(body, "에러 응답에 status:FAIL 없음").toHaveProperty(
      "status",
      "FAIL"
    );

    console.log(
      `[TC-GEO-A-03] 잘못된 주소 → ${response.status()} 정상 처리`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-A-04: 인증 없이 위치 등록 요청 → 401
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-A-04: 인증 토큰 없이 위치 등록 요청 시 401 을 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/users/me/locations/register`,
      {
        headers: { "Content-Type": "application/json" },
        data: { address: VALID_ADDRESS },
      }
    );

    expect(response.status(), "토큰 없는데 401 이 아님").toBe(401);

    console.log(`[TC-GEO-A-04] 토큰 없는 요청 → 401 정상 처리`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-A-05: 등록된 위치 목록 조회 → 방금 등록한 위치 포함 확인
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-A-05: 위치 목록 조회 시 등록된 위치가 포함된다", async ({
    request,
  }) => {
    if (!registeredLocationId)
      test.skip(true, "TC-GEO-A-01 에서 위치를 먼저 등록해야 합니다");

    const response = await request.get(
      `${ENV.BACKEND_URL}/users/me/locations`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
      }
    );

    expect(response.status(), "위치 목록 조회 상태코드가 200 이 아님").toBe(
      200
    );

    const body = await response.json();
    expect(body, "응답 status 필드 누락").toHaveProperty("status", "SUCCESS");

    const locations = body?.data?.locations as Array<
      Record<string, unknown>
    > | undefined;
    expect(Array.isArray(locations), "locations 가 배열이 아님").toBe(true);
    expect(
      locations!.some((loc) => loc.locationId === registeredLocationId),
      "등록한 위치가 목록에 없음"
    ).toBe(true);

    console.log(
      `[TC-GEO-A-05] 위치 목록 조회 성공: 총 ${locations!.length}개`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-A-06: 등록된 위치 삭제
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-A-06: 등록된 위치를 삭제하면 204 를 반환한다", async ({
    request,
  }) => {
    if (!registeredLocationId)
      test.skip(true, "TC-GEO-A-01 에서 위치를 먼저 등록해야 합니다");

    const response = await request.delete(
      `${ENV.BACKEND_URL}/users/me/locations/${registeredLocationId}`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
      }
    );

    expect(response.status(), "위치 삭제 상태코드가 200 이 아님").toBe(200);

    console.log(
      `[TC-GEO-A-06] 위치 삭제 성공: locationId=${registeredLocationId}`
    );
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 좌표 기반 위치 등록 테스트 스위트 (Kakao Reverse Geocoding API)
// ════════════════════════════════════════════════════════════════════════════

test.describe("좌표 기반 위치 등록 (Kakao Reverse Geocoding API)", () => {
  let accessToken: string;
  let registeredLocationId: number;

  test.beforeAll(async ({ request }) => {
    const email = `geocoding-coord-${Date.now()}@mztk-test.com`;
    accessToken = await signUpAndLogin(request, email);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-C-01: 유효한 좌표로 위치 등록 → Kakao Reverse Geocoding → 주소 변환 확인
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-C-01: 유효한 좌표로 위치를 등록하면 Kakao API 가 주소를 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/users/me/locations/register`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          locationName: "한양대학교 에리카캠퍼스",
          latitude: VALID_COORDINATE.latitude,
          longitude: VALID_COORDINATE.longitude,
        },
      }
    );

    expect(
      response.status(),
      "좌표 기반 위치 등록 HTTP 상태코드가 200 이 아님"
    ).toBe(200);

    const body = await response.json();
    expect(body, "응답 status 필드 누락").toHaveProperty("status", "SUCCESS");

    const data = body.data as Record<string, unknown>;
    expect(data, "locationId 필드 누락").toHaveProperty("locationId");
    expect(data, "address 필드 누락").toHaveProperty("address");
    expect(data, "latitude 필드 누락").toHaveProperty("latitude");
    expect(data, "longitude 필드 누락").toHaveProperty("longitude");

    // Kakao Reverse Geocoding API 가 실제 주소를 반환했는지 확인
    const address = data.address as string;
    expect(typeof address, "address 타입 오류").toBe("string");
    expect(address.length, "address 가 빈 문자열").toBeGreaterThan(0);

    // 한양대학교 에리카캠퍼스 부근이면 '한양' 이 포함될 것으로 예상
    expect(address, "주소에 '한양' 이 포함되지 않음").toContain("한양");

    registeredLocationId = data.locationId as number;

    console.log(
      `[TC-GEO-C-01] 좌표 등록 성공: locationId=${registeredLocationId}, address="${address}"`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-C-02: 해상 좌표 → Reverse Geocoding 결과 없음 → 에러 반환
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-C-02: 한국 육지 밖 좌표(해상)로 위치 등록 시 Reverse Geocoding 실패 에러를 반환한다", async ({
    request,
  }) => {
    const response = await request.post(
      `${ENV.BACKEND_URL}/users/me/locations/register`,
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: {
          locationName: "해상 좌표 테스트",
          latitude: OCEAN_COORDINATE.latitude,
          longitude: OCEAN_COORDINATE.longitude,
        },
      }
    );

    expect(
      response.status(),
      "해상 좌표인데 200 이 반환됨"
    ).not.toBe(200);
    expect(
      [400, 404, 422, 502, 500],
      `예상치 못한 상태코드: ${response.status()}`
    ).toContain(response.status());

    const body = await response.json();
    expect(body, "에러 응답에 status:FAIL 없음").toHaveProperty(
      "status",
      "FAIL"
    );

    console.log(
      `[TC-GEO-C-02] 해상 좌표 → ${response.status()} 정상 처리`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-C-03: 위치 인증 (등록된 위치와 현재 좌표가 근접한 경우 성공)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-C-03: 등록된 위치와 근접한 좌표로 위치 인증을 요청하면 성공한다", async ({
    request,
  }) => {
    if (!registeredLocationId)
      test.skip(true, "TC-GEO-C-01 에서 위치를 먼저 등록해야 합니다");

    // 등록한 좌표에서 아주 미세하게 이동한 좌표 (인증 반경 내)
    const nearbyLatitude = VALID_COORDINATE.latitude + 0.00001;
    const nearbyLongitude = VALID_COORDINATE.longitude + 0.00001;

    const response = await request.post(`${ENV.BACKEND_URL}/locations/verify`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      data: {
        locationId: registeredLocationId,
        currentLatitude: nearbyLatitude,
        currentLongitude: nearbyLongitude,
      },
    });

    expect(
      response.status(),
      "위치 인증 HTTP 상태코드가 200 이 아님"
    ).toBe(200);

    const body = await response.json();
    expect(body, "응답 status 필드 누락").toHaveProperty("status", "SUCCESS");

    // /locations/verify 는 항상 200을 반환하며, isVerified 필드로 성공/실패를 구분함
    const data = body.data as Record<string, unknown>;
    expect(data, "isVerified 필드 누락").toHaveProperty("isVerified", true);
    expect(data, "distance 필드 누락").toHaveProperty("distance");

    const distance = data.distance as number;
    expect(distance, "근접 좌표인데 거리가 5m 초과").toBeLessThanOrEqual(5);

    console.log(
      `[TC-GEO-C-03] 위치 인증 성공: locationId=${registeredLocationId}, distance=${distance}m`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-C-04: 위치 인증 실패 (너무 먼 거리)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-C-04: 등록된 위치에서 너무 멀리 떨어진 좌표로 인증 요청하면 실패한다", async ({
    request,
  }) => {
    if (!registeredLocationId)
      test.skip(true, "TC-GEO-C-01 에서 위치를 먼저 등록해야 합니다");

    // 등록한 좌표에서 약 50km 이상 떨어진 좌표 (인증 반경 밖)
    const farLatitude = VALID_COORDINATE.latitude + 0.5;
    const farLongitude = VALID_COORDINATE.longitude + 0.5;

    const response = await request.post(`${ENV.BACKEND_URL}/locations/verify`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      data: {
        locationId: registeredLocationId,
        currentLatitude: farLatitude,
        currentLongitude: farLongitude,
      },
    });

    // /locations/verify 는 거리에 관계없이 항상 200을 반환함
    // 인증 실패는 HTTP 에러가 아닌 응답 바디의 isVerified: false 로 표현됨
    expect(
      response.status(),
      "verify 엔드포인트 HTTP 상태코드가 200 이 아님"
    ).toBe(200);

    const body = await response.json();
    expect(body, "응답 status 필드 누락").toHaveProperty("status", "SUCCESS");

    const data = body.data as Record<string, unknown>;
    expect(data, "isVerified 필드 누락").toHaveProperty("isVerified", false);
    expect(data, "distance 필드 누락").toHaveProperty("distance");

    const distance = data.distance as number;
    // 0.5도 오프셋은 위도 55km + 경도 44km → 약 70km. 인증 반경(5m) 훨씬 초과
    expect(distance, "70km 거리인데 5m 이내로 계산됨").toBeGreaterThan(5000);

    console.log(
      `[TC-GEO-C-04] 원거리 인증 실패 확인: isVerified=false, distance=${distance}m`
    );
  });

  // ──────────────────────────────────────────────────────────────────────────
  // TC-GEO-C-05: 위치 삭제 (정리)
  // ──────────────────────────────────────────────────────────────────────────
  test("TC-GEO-C-05: 등록된 위치를 삭제하면 204 를 반환한다", async ({
    request,
  }) => {
    if (!registeredLocationId)
      test.skip(true, "TC-GEO-C-01 에서 위치를 먼저 등록해야 합니다");

    const response = await request.delete(
      `${ENV.BACKEND_URL}/users/me/locations/${registeredLocationId}`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
      }
    );

    expect(response.status(), "위치 삭제 상태코드가 200 이 아님").toBe(200);

    console.log(
      `[TC-GEO-C-05] 위치 삭제 성공: locationId=${registeredLocationId}`
    );
  });
});
