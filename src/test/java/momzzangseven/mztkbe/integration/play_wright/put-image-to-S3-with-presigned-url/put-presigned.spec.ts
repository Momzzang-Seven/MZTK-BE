/**
 * MZTK - 이미지 Presigned URL 발급 + 실제 AWS S3 PUT 업로드 Playwright E2E 테스트
 *
 * 테스트 시나리오:
 *   모든 request-facing reference_type 에 대해 아래 흐름을 검증합니다.
 *     1. POST /images/presigned-urls → Presigned PUT URL 발급
 *     2. 응답 구조 검증 (imageId, tmpObjectKey prefix·개수, presignedUrl)
 *     3. 로컬 테스트 이미지 파일을 Presigned URL 로 실제 S3 에 PUT 업로드
 *     4. public 경로는 GET 으로 업로드된 오브젝트의 실존 확인
 *        (버킷 퍼블릭 읽기 정책 여부에 따라 200 / 403)
 *
 * 커버되는 reference_type:
 *   COMMUNITY_FREE      → public/community/free/tmp/{uuid}.ext
 *   COMMUNITY_QUESTION  → public/community/question/tmp/{uuid}.ext
 *   COMMUNITY_ANSWER    → public/community/answer/tmp/{uuid}.ext
 *   USER_PROFILE        → public/user/profile/tmp/{uuid}.ext
 *   MARKET_CLASS        → CLASS_THUMB: public/market/class/thumb/tmp/{uuid}.ext
 *                         CLASS_DETAIL: public/market/class/detail/tmp/{uuid}.ext  (n+1 확장)
 *   MARKET_STORE        → STORE_THUMB: public/market/store/thumb/tmp/{uuid}.ext
 *                         STORE_DETAIL: public/market/store/detail/tmp/{uuid}.ext  (n+1 확장)
 *   WORKOUT             → private/workout/{uuid}.ext  (GET 검증 생략)
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 실행 중이어야 합니다
 *     (기본: http://127.0.0.1:8080, .env 의 BACKEND_URL 로 변경 가능)
 *   - 서버 .env 에 실제 AWS IAM 자격증명(AWS_IAM_ACCESS_KEY, AWS_IAM_SECRET_KEY, AWS_S3_BUCKET)
 *     이 설정되어 있어야 합니다
 *   - test-images/ 디렉터리에 test1.png ~ test5.png 파일이 존재해야 합니다
 *
 * 실행 방법:
 *   cd src/test/java/momzzangseven/mztkbe/integration/play_wright
 *   npx playwright test put-image-to-S3-with-presigned-url/put-presigned.spec.ts
 */

import { test, expect, APIRequestContext } from "@playwright/test";
import * as dotenv from "dotenv";
import * as fs from "fs";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

// ────────────────────────────────────────────────────────────────────────────
// 환경 변수
// ────────────────────────────────────────────────────────────────────────────
const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
};

// ────────────────────────────────────────────────────────────────────────────
// 상수
// ────────────────────────────────────────────────────────────────────────────
const TEST_IMAGES_DIR = path.resolve(__dirname, "test-images");

const CONTENT_TYPE_MAP: Record<string, string> = {
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".png": "image/png",
  ".gif": "image/gif",
  ".heic": "image/heic",
  ".heif": "image/heif",
};

// ────────────────────────────────────────────────────────────────────────────
// 타입
// ────────────────────────────────────────────────────────────────────────────
interface PresignedUrlItem {
  /** DB 에 저장된 Image 레코드의 PK. 양수 정수여야 합니다. */
  imageId: number;
  presignedUrl: string;
  tmpObjectKey: string;
}

// ────────────────────────────────────────────────────────────────────────────
// 유틸 함수
// ────────────────────────────────────────────────────────────────────────────

/** 파일 확장자로 MIME Content-Type 을 반환합니다. */
function contentTypeOf(filename: string): string {
  const ext = path.extname(filename).toLowerCase();
  return CONTENT_TYPE_MAP[ext] ?? "application/octet-stream";
}

/** test-images/ 디렉터리에서 이미지 파일을 Buffer 로 읽습니다. */
function readImage(filename: string): Buffer {
  const filePath = path.join(TEST_IMAGES_DIR, filename);
  if (!fs.existsSync(filePath)) {
    throw new Error(`테스트 이미지 파일이 없습니다: ${filePath}`);
  }
  return fs.readFileSync(filePath);
}

/**
 * Presigned URL 에서 쿼리 파라미터를 제거하여 순수 S3 오브젝트 URL 을 반환합니다.
 * GET 검증 시 서명 없이 버킷 퍼블릭 정책만으로 접근 가능한지 확인하는 데 사용합니다.
 */
function s3BaseUrlOf(presignedUrl: string): string {
  return presignedUrl.split("?")[0];
}

// ────────────────────────────────────────────────────────────────────────────
// 테스트 스위트
// ────────────────────────────────────────────────────────────────────────────

test.describe("이미지 Presigned URL 발급 + 실제 S3 PUT/GET E2E", () => {
  /** beforeAll → 각 test 에서 공유하는 액세스 토큰 */
  let accessToken: string;

  // ──────────────────────────────────────────────────────────────────────────
  // Setup: 테스트 전용 로컬 계정 생성 + 로그인 → accessToken 확보
  // ──────────────────────────────────────────────────────────────────────────
  test.beforeAll(async ({ request }) => {
    const uniqueEmail = `pw-img-${Date.now()}@playwright.test`;
    const password = "Test@1234!";

    // 1. 회원가입
    const signupRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
      headers: { "Content-Type": "application/json" },
      data: { email: uniqueEmail, password, nickname: "PlaywrightImgTester" },
    });
    expect(
      signupRes.ok(),
      `[Setup] 회원가입 실패 (HTTP ${signupRes.status()}). ` +
        `서버가 ${ENV.BACKEND_URL} 에서 실행 중인지 확인하세요.`
    ).toBeTruthy();

    // 2. 로그인 → accessToken 발급
    const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: { provider: "LOCAL", email: uniqueEmail, password },
    });
    expect(loginRes.ok(), `[Setup] 로그인 실패 (HTTP ${loginRes.status()})`).toBeTruthy();

    const loginBody = await loginRes.json();
    accessToken = (loginBody.data as Record<string, string>).accessToken;
    expect(accessToken, "[Setup] accessToken 발급 실패 — 로그인 응답을 확인하세요").toBeTruthy();

    console.log(`\n[Setup] 테스트 계정 준비 완료 ✅  (email=${uniqueEmail})`);
  });

  // ──────────────────────────────────────────────────────────────────────────
  // 공통 헬퍼 — Presigned URL 발급
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * POST /images/presigned-urls 를 호출하여 Presigned URL 목록을 반환합니다.
   * HTTP 200 + status:"SUCCESS" 를 자동으로 검증합니다.
   * 각 item 의 imageId 가 양수인지도 공통으로 검증합니다.
   */
  async function issuePresignedUrls(
    request: APIRequestContext,
    referenceType: string,
    images: string[]
  ): Promise<PresignedUrlItem[]> {
    const res = await request.post(`${ENV.BACKEND_URL}/images/presigned-urls`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      data: { referenceType, images },
    });

    expect(
      res.status(),
      `[${referenceType}] POST /images/presigned-urls 실패 (HTTP ${res.status()})`
    ).toBe(200);

    const body = await res.json();
    expect(body.status, `[${referenceType}] 응답 status 필드 오류`).toBe("SUCCESS");

    const items = body.data.items as PresignedUrlItem[];

    // 모든 item 의 imageId 가 양수 정수인지 공통 검증
    items.forEach((item, idx) => {
      expect(
        typeof item.imageId,
        `[${referenceType}] items[${idx}].imageId 타입 오류 — number 여야 합니다`
      ).toBe("number");
      expect(
        item.imageId,
        `[${referenceType}] items[${idx}].imageId 는 양수여야 합니다`
      ).toBeGreaterThan(0);
    });

    console.log(
      `[${referenceType}] Presigned URL ${items.length}개 발급 ✅` +
        items.map((i) => `\n  · id=${i.imageId}  key=${i.tmpObjectKey}`).join("")
    );
    return items;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 공통 헬퍼 — S3 PUT 업로드
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Presigned PUT URL 을 사용하여 실제 이미지 파일을 S3 에 업로드합니다.
   * HTTP 200 응답으로 업로드 성공을 검증합니다.
   */
  async function putImageToS3(
    request: APIRequestContext,
    presignedUrl: string,
    imageFilename: string,
    label: string
  ): Promise<void> {
    const imageData = readImage(imageFilename);
    const contentType = contentTypeOf(imageFilename);

    const putRes = await request.put(presignedUrl, {
      headers: { "Content-Type": contentType },
      data: imageData,
    });

    // S3 PUT 성공: HTTP 200 (빈 바디)
    // 실패 시 S3 는 XML 에러 바디와 함께 4xx 를 반환합니다
    if (putRes.status() !== 200) {
      const errBody = await putRes.text().catch(() => "(body 없음)");
      expect.soft(
        putRes.status(),
        `[${label}] S3 PUT 실패 (HTTP ${putRes.status()}):\n${errBody}`
      ).toBe(200);
    } else {
      console.log(`[${label}] S3 PUT 성공 ✅  → ${s3BaseUrlOf(presignedUrl)}`);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 공통 헬퍼 — S3 GET 검증 (public 경로 전용)
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * S3 오브젝트 URL (서명 없음) 로 GET 요청을 보내 실제 파일이 존재하는지 확인합니다.
   *
   * - HTTP 200: 파일 존재 확인 (content-length > 0 추가 검증)
   * - HTTP 403/404: 버킷 퍼블릭 읽기 정책 비활성화이거나 오브젝트 경로 불일치
   *   → 경고만 출력하고 테스트를 실패시키지 않습니다 (PUT 성공이 주 검증 포인트)
   */
  async function verifyS3ObjectExists(
    request: APIRequestContext,
    presignedUrl: string,
    label: string
  ): Promise<void> {
    const objectUrl = s3BaseUrlOf(presignedUrl);
    let getRes;

    try {
      getRes = await request.get(objectUrl, {
        // S3 오류 XML 을 body 로 수신하기 위해 failOnStatusCode 비활성화
        failOnStatusCode: false,
      });
    } catch (err) {
      console.warn(`[${label}] S3 GET 네트워크 오류: ${err}`);
      return;
    }

    if (getRes.status() === 200) {
      const contentLength = parseInt(getRes.headers()["content-length"] ?? "0", 10);
      expect(
        contentLength,
        `[${label}] S3 GET 200 이지만 content-length = 0 (빈 파일)`
      ).toBeGreaterThan(0);
      console.log(
        `[${label}] S3 GET 성공 ✅  (${contentLength.toLocaleString()} bytes)\n  · ${objectUrl}`
      );
    } else {
      // 버킷 퍼블릭 읽기 비활성화, 또는 경로 접두사별 ACL 제한인 경우 → 테스트 실패 아님
      console.warn(
        `[${label}] S3 GET → HTTP ${getRes.status()} ` +
          `(버킷 퍼블릭 읽기 정책이 비활성화 상태일 수 있습니다)\n  · ${objectUrl}`
      );
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // TC-1: COMMUNITY_FREE — 단일 이미지
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-1: COMMUNITY_FREE 단일 이미지 → public/community/free/tmp/ 업로드 및 GET 검증",
    async ({ request }) => {
      // ── Presigned URL 발급 (imageId 포함 공통 검증은 issuePresignedUrls 내부)
      const items = await issuePresignedUrls(request, "COMMUNITY_FREE", ["test1.png"]);

      // ── 응답 구조 검증
      expect(items).toHaveLength(1);
      expect(
        items[0].tmpObjectKey,
        "COMMUNITY_FREE tmpObjectKey prefix 불일치"
      ).toMatch(/^public\/community\/free\/tmp\/.+\.png$/);
      expect(items[0].presignedUrl, "presignedUrl 이 비어 있음").toBeTruthy();

      // ── S3 PUT 업로드
      await putImageToS3(request, items[0].presignedUrl, "test1.png", "COMMUNITY_FREE");

      // ── S3 GET 검증 (public 경로)
      await verifyS3ObjectExists(request, items[0].presignedUrl, "COMMUNITY_FREE");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-2: COMMUNITY_QUESTION — 단일 이미지
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-2: COMMUNITY_QUESTION 단일 이미지 → public/community/question/tmp/ 업로드 및 GET 검증",
    async ({ request }) => {
      const items = await issuePresignedUrls(request, "COMMUNITY_QUESTION", ["test2.png"]);

      expect(items).toHaveLength(1);
      expect(items[0].tmpObjectKey).toMatch(/^public\/community\/question\/tmp\/.+\.png$/);

      await putImageToS3(request, items[0].presignedUrl, "test2.png", "COMMUNITY_QUESTION");
      await verifyS3ObjectExists(request, items[0].presignedUrl, "COMMUNITY_QUESTION");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-3: COMMUNITY_ANSWER — 단일 이미지
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-3: COMMUNITY_ANSWER 단일 이미지 → public/community/answer/tmp/ 업로드 및 GET 검증",
    async ({ request }) => {
      const items = await issuePresignedUrls(request, "COMMUNITY_ANSWER", ["test3.png"]);

      expect(items).toHaveLength(1);
      expect(items[0].tmpObjectKey).toMatch(/^public\/community\/answer\/tmp\/.+\.png$/);

      await putImageToS3(request, items[0].presignedUrl, "test3.png", "COMMUNITY_ANSWER");
      await verifyS3ObjectExists(request, items[0].presignedUrl, "COMMUNITY_ANSWER");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-4: USER_PROFILE — 단일 이미지
  //
  // USER_PROFILE 은 public/user/profile/tmp/{uuid}.ext 형식입니다.
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-4: USER_PROFILE 단일 이미지, public/user/profile/tmp/ 업로드 및 GET 검증",
    async ({ request }) => {
      const items = await issuePresignedUrls(request, "USER_PROFILE", ["test1.png"]);

      expect(items).toHaveLength(1);
      expect(
        items[0].tmpObjectKey,
        "USER_PROFILE tmpObjectKey prefix 불일치"
      ).toMatch(/^public\/user\/profile\/tmp\/.+\.png$/);
      expect(items[0].presignedUrl, "presignedUrl 이 비어 있음").toBeTruthy();

      await putImageToS3(request, items[0].presignedUrl, "test1.png", "USER_PROFILE");
      await verifyS3ObjectExists(request, items[0].presignedUrl, "USER_PROFILE");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-5: MARKET_CLASS — 3장 입력 → 4개 Presigned URL (n+1 확장)
  //
  // 흐름:
  //   입력: ["test1.png", "test2.png", "test3.png"]
  //   출력:
  //     [0] MARKET_CLASS_THUMB  ← test1.png  (public/market/class/thumb/tmp/{uuid}.png)
  //     [1] MARKET_CLASS_DETAIL ← test1.png  (public/market/class/detail/tmp/{uuid}.png)
  //     [2] MARKET_CLASS_DETAIL ← test2.png  (public/market/class/detail/tmp/{uuid}.png)
  //     [3] MARKET_CLASS_DETAIL ← test3.png  (public/market/class/detail/tmp/{uuid}.png)
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-5: MARKET_CLASS 3장 → CLASS_THUMB×1 + CLASS_DETAIL×3 = 4개 Presigned URL 발급 및 각각 S3 업로드",
    async ({ request }) => {
      const inputImages = ["test1.png", "test2.png", "test3.png"];
      const items = await issuePresignedUrls(request, "MARKET_CLASS", inputImages);

      // MARKET_CLASS n장 → n+1개 URL
      expect(items, "MARKET_CLASS: n+1 items 아님").toHaveLength(4);

      // prefix 분기 검증
      expect(items[0].tmpObjectKey, "MARKET_CLASS_THUMB prefix 오류").toMatch(
        /^public\/market\/class\/thumb\/tmp\/.+\.png$/
      );
      expect(items[1].tmpObjectKey, "MARKET_CLASS_DETAIL[0] prefix 오류").toMatch(
        /^public\/market\/class\/detail\/tmp\/.+\.png$/
      );
      expect(items[2].tmpObjectKey, "MARKET_CLASS_DETAIL[1] prefix 오류").toMatch(
        /^public\/market\/class\/detail\/tmp\/.+\.png$/
      );
      expect(items[3].tmpObjectKey, "MARKET_CLASS_DETAIL[2] prefix 오류").toMatch(
        /^public\/market\/class\/detail\/tmp\/.+\.png$/
      );

      // 모든 tmpObjectKey 가 서로 달라야 함 (UUID 고유성)
      const allKeys = items.map((i) => i.tmpObjectKey);
      expect(new Set(allKeys).size, "tmpObjectKey 중복 존재 — UUID 충돌").toBe(4);

      // 모든 imageId 가 서로 달라야 함 (각각 별도 DB row)
      const allIds = items.map((i) => i.imageId);
      expect(new Set(allIds).size, "imageId 중복 존재 — 서로 다른 DB row 여야 합니다").toBe(4);

      // S3 PUT: 각 URL 에 대응하는 파일 업로드
      await putImageToS3(request, items[0].presignedUrl, "test1.png", "MARKET_CLASS_THUMB[test1]");
      await putImageToS3(request, items[1].presignedUrl, "test1.png", "MARKET_CLASS_DETAIL[test1]");
      await putImageToS3(request, items[2].presignedUrl, "test2.png", "MARKET_CLASS_DETAIL[test2]");
      await putImageToS3(request, items[3].presignedUrl, "test3.png", "MARKET_CLASS_DETAIL[test3]");

      // GET 검증 (THUMB + DETAIL 대표 1개)
      await verifyS3ObjectExists(request, items[0].presignedUrl, "MARKET_CLASS_THUMB");
      await verifyS3ObjectExists(request, items[1].presignedUrl, "MARKET_CLASS_DETAIL");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-6: MARKET_STORE — 3장 입력 → 4개 Presigned URL (n+1 확장)
  //
  // 흐름:
  //   입력: ["test1.png", "test2.png", "test3.png"]
  //   출력:
  //     [0] MARKET_STORE_THUMB  ← test1.png  (public/market/store/thumb/tmp/{uuid}.png)
  //     [1] MARKET_STORE_DETAIL ← test1.png  (public/market/store/detail/tmp/{uuid}.png)
  //     [2] MARKET_STORE_DETAIL ← test2.png  (public/market/store/detail/tmp/{uuid}.png)
  //     [3] MARKET_STORE_DETAIL ← test3.png  (public/market/store/detail/tmp/{uuid}.png)
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-6: MARKET_STORE 3장 → STORE_THUMB×1 + STORE_DETAIL×3 = 4개 Presigned URL 발급 및 각각 S3 업로드",
    async ({ request }) => {
      const inputImages = ["test1.png", "test2.png", "test3.png"];
      const items = await issuePresignedUrls(request, "MARKET_STORE", inputImages);

      // MARKET_STORE n장 → n+1개 URL
      expect(items, "MARKET_STORE: n+1 items 아님").toHaveLength(4);

      // prefix 분기 검증
      expect(items[0].tmpObjectKey, "MARKET_STORE_THUMB prefix 오류").toMatch(
        /^public\/market\/store\/thumb\/tmp\/.+\.png$/
      );
      expect(items[1].tmpObjectKey, "MARKET_STORE_DETAIL[0] prefix 오류").toMatch(
        /^public\/market\/store\/detail\/tmp\/.+\.png$/
      );
      expect(items[2].tmpObjectKey, "MARKET_STORE_DETAIL[1] prefix 오류").toMatch(
        /^public\/market\/store\/detail\/tmp\/.+\.png$/
      );
      expect(items[3].tmpObjectKey, "MARKET_STORE_DETAIL[2] prefix 오류").toMatch(
        /^public\/market\/store\/detail\/tmp\/.+\.png$/
      );

      // 모든 tmpObjectKey 가 서로 달라야 함 (UUID 고유성)
      const allKeys = items.map((i) => i.tmpObjectKey);
      expect(new Set(allKeys).size, "tmpObjectKey 중복 존재 — UUID 충돌").toBe(4);

      // 모든 imageId 가 서로 달라야 함 (각각 별도 DB row)
      const allIds = items.map((i) => i.imageId);
      expect(new Set(allIds).size, "imageId 중복 존재 — 서로 다른 DB row 여야 합니다").toBe(4);

      // S3 PUT: 각 URL 에 대응하는 파일 업로드
      await putImageToS3(request, items[0].presignedUrl, "test1.png", "MARKET_STORE_THUMB[test1]");
      await putImageToS3(request, items[1].presignedUrl, "test1.png", "MARKET_STORE_DETAIL[test1]");
      await putImageToS3(request, items[2].presignedUrl, "test2.png", "MARKET_STORE_DETAIL[test2]");
      await putImageToS3(request, items[3].presignedUrl, "test3.png", "MARKET_STORE_DETAIL[test3]");

      // GET 검증 (THUMB + DETAIL 대표 1개)
      await verifyS3ObjectExists(request, items[0].presignedUrl, "MARKET_STORE_THUMB");
      await verifyS3ObjectExists(request, items[1].presignedUrl, "MARKET_STORE_DETAIL");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-7: WORKOUT — private 경로 (GET 검증 생략)
  //
  // WORKOUT 은 private/workout/{uuid}.ext 형식으로 tmp/ 서브폴더가 없습니다.
  // 버킷 정책상 퍼블릭 GET 이 불가능하므로 PUT 성공만 검증합니다.
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-7: WORKOUT 단일 이미지 → private/workout/ (tmp/ 없음) + S3 PUT 성공 검증",
    async ({ request }) => {
      const items = await issuePresignedUrls(request, "WORKOUT", ["test4.png"]);

      expect(items).toHaveLength(1);

      // WORKOUT: private/workout/{uuid}.png — tmp/ 서브폴더 없음
      expect(items[0].tmpObjectKey, "WORKOUT prefix 오류").toMatch(
        /^private\/workout\/.+\.png$/
      );
      expect(
        items[0].tmpObjectKey,
        "WORKOUT tmpObjectKey 에 예상치 않은 /tmp/ 포함"
      ).not.toContain("/tmp/");

      // S3 PUT 업로드
      await putImageToS3(request, items[0].presignedUrl, "test4.png", "WORKOUT");

      // WORKOUT 은 private 경로 → 퍼블릭 GET 불가, 검증 생략
      console.log(
        `[WORKOUT] private 경로 업로드 완료. GET 은 버킷 정책상 불가 → 생략\n` +
          `  · ${s3BaseUrlOf(items[0].presignedUrl)}`
      );
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-8: COMMUNITY_FREE 5장 복수 업로드
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-8: COMMUNITY_FREE 5장 복수 업로드 → 5개 Presigned URL 발급 및 S3 PUT 전체 성공",
    async ({ request }) => {
      const inputImages = [
        "test1.png",
        "test2.png",
        "test3.png",
        "test4.png",
        "test5.png",
      ];

      const items = await issuePresignedUrls(request, "COMMUNITY_FREE", inputImages);

      expect(items, "COMMUNITY_FREE 5장: items 개수 불일치").toHaveLength(5);

      // 모든 imageId 가 서로 달라야 함
      const allIds = items.map((i) => i.imageId);
      expect(new Set(allIds).size, "imageId 중복 존재 — 각 파일은 별도 DB row 여야 합니다").toBe(5);

      // imgOrder 대응: items[i] → inputImages[i]
      for (let i = 0; i < items.length; i++) {
        expect(
          items[i].tmpObjectKey,
          `COMMUNITY_FREE[${i + 1}] tmpObjectKey prefix 오류`
        ).toMatch(/^public\/community\/free\/tmp\/.+\.png$/);

        await putImageToS3(
          request,
          items[i].presignedUrl,
          inputImages[i],
          `COMMUNITY_FREE[${i + 1}/5]`
        );
      }

      console.log("[TC-8] 5장 복수 업로드 완료 ✅");
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // TC-9: 인증 없이 요청 → 401 Unauthorized
  // ════════════════════════════════════════════════════════════════════════════
  test("TC-9: Authorization 헤더 없이 POST /images/presigned-urls → 401", async ({ request }) => {
    const res = await request.post(`${ENV.BACKEND_URL}/images/presigned-urls`, {
      headers: { "Content-Type": "application/json" },
      data: { referenceType: "COMMUNITY_FREE", images: ["photo.png"] },
    });

    expect(res.status(), "인증 없는 요청이 401 이 아님").toBe(401);
    console.log("[TC-9] 미인증 요청 → 401 정상 처리 ✅");
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-10: 허용되지 않는 확장자 → 400 IMAGE_005
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-10: 허용되지 않는 확장자(webp) 포함 시 → 400 + IMAGE_005 에러코드",
    async ({ request }) => {
      const res = await request.post(`${ENV.BACKEND_URL}/images/presigned-urls`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        data: { referenceType: "COMMUNITY_FREE", images: ["valid.png", "invalid.webp"] },
      });

      expect(res.status(), "허용 안 되는 확장자인데 400 이 아님").toBe(400);

      const body = await res.json();
      expect(body.code, "IMAGE_005 에러코드 불일치").toBe("IMAGE_005");

      console.log("[TC-10] 허용 안 되는 확장자 → 400 IMAGE_005 정상 처리 ✅");
    }
  );
});
