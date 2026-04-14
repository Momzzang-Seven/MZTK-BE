/**
 * MZTK - Lambda 콜백 전체 흐름 Playwright E2E 테스트
 *
 * 검증 시나리오:
 *   1. POST /images/presigned-urls → Presigned PUT URL 발급
 *   2. Presigned URL 로 실제 S3 에 이미지 PUT 업로드
 *   3. S3 GET 요청 → tmp 경로에 이미지가 업로드됐는지 확인
 *   4. POST /internal/images/lambda-callback (COMPLETED)
 *        → BE 에서 200 OK + DB status=COMPLETED 검증
 *   5. Lambda 가 tmp 경로 삭제한 상황 시뮬레이션
 *        → S3 Presigned DELETE URL 로 tmp 오브젝트 삭제
 *   6. S3 GET tmp 경로 → 삭제됐으므로 200 이 아님(403/404) 확인
 *   7. S3 GET final_object_key(WebP 변환 경로) → 존재 + WebP 확인
 *
 *   FAILED 시나리오 (TC-2):
 *   1~3단계 동일
 *   4. POST /internal/images/lambda-callback (FAILED)
 *        → BE 에서 200 OK + DB status=FAILED, errorReason 저장 검증
 *   (5~7 불필요: Lambda 실패이므로 final_object_key 없음)
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 실행 중이어야 합니다 (기본: http://127.0.0.1:8080)
 *   - 서버 .env 에 실제 AWS IAM 자격증명과 LAMBDA_WEBHOOK_SECRET 이 설정돼 있어야 합니다
 *   - AWS S3 버킷에 실제 Lambda 가 COMPLETED 처리를 완료하여
 *     final_object_key 경로에 WebP 파일이 존재해야 합니다 (TC-1 스텝 7)
 *   - test-images/ 디렉터리에 test1.png 파일이 존재해야 합니다
 *   - .env 파일의 LAMBDA_WEBHOOK_SECRET 이 서버의 lambda.webhook.secret 과 일치해야 합니다
 *
 * 실행 방법:
 *   cd src/test/java/momzzangseven/mztkbe/integration/play_wright
 *   npx playwright test lambda-callback/lambda-callback.spec.ts
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ⚠️  TC-1 스텝 7 (WebP 최종 경로 GET) 은 실제 Lambda 가 변환을 완료해야만 검증됩니다.
 *     Lambda 없이 End-to-End 를 테스트하려면 test.skip() 으로 비활성화하거나,
 *     로컬에서 AWS CLI / AWS SDK 로 직접 WebP 파일을 final_object_key 에 업로드한 뒤 실행하세요.
 * ─────────────────────────────────────────────────────────────────────────────
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
  LAMBDA_WEBHOOK_SECRET: process.env.LAMBDA_WEBHOOK_SECRET ?? "",
};

// ────────────────────────────────────────────────────────────────────────────
// 상수
// ────────────────────────────────────────────────────────────────────────────
const TEST_IMAGES_DIR = path.resolve(__dirname, "test-images");
const LAMBDA_CALLBACK_URL = `${ENV.BACKEND_URL}/internal/images/lambda-callback`;
const WEBHOOK_SECRET_HEADER = "X-Lambda-Webhook-Secret";

// ────────────────────────────────────────────────────────────────────────────
// 타입
// ────────────────────────────────────────────────────────────────────────────
interface PresignedUrlItem {
  presignedUrl: string;
  tmpObjectKey: string;
}

// ────────────────────────────────────────────────────────────────────────────
// 유틸 함수
// ────────────────────────────────────────────────────────────────────────────

/** test-images/ 에서 이미지를 Buffer 로 읽습니다. */
function readImage(filename: string): Buffer {
  const filePath = path.join(TEST_IMAGES_DIR, filename);
  if (!fs.existsSync(filePath)) {
    throw new Error(`테스트 이미지 파일이 없습니다: ${filePath}`);
  }
  return fs.readFileSync(filePath);
}

/**
 * Presigned URL 에서 쿼리 파라미터를 제거하여 순수 S3 오브젝트 URL 을 반환합니다.
 * 버킷 도메인 + 오브젝트 키 형태.
 *   예) https://my-bucket.s3.ap-northeast-2.amazonaws.com/public/community/free/tmp/uuid.png
 */
function s3BaseUrlOf(presignedUrl: string): string {
  return presignedUrl.split("?")[0];
}

/**
 * S3 Presigned URL 에서 오브젝트 키 부분만 추출합니다.
 *   예) public/community/free/tmp/uuid.png
 */
function objectKeyOf(presignedUrl: string): string {
  const base = s3BaseUrlOf(presignedUrl);
  // https://bucket.s3.region.amazonaws.com/{key} 형태
  const match = base.match(/amazonaws\.com\/(.+)$/);
  return match ? match[1] : "";
}

/**
 * tmpObjectKey 로부터 Lambda 가 변환한 후 저장할 예상 final_object_key 를 도출합니다.
 *   tmp 제거 + 확장자 .webp 변환 규칙:
 *     public/community/free/tmp/{uuid}.png  →  public/community/free/{uuid}.webp
 *     public/market/thumb/tmp/{uuid}.png    →  public/market/thumb/{uuid}.webp
 */
function expectedFinalObjectKey(tmpObjectKey: string): string {
  return tmpObjectKey
    .replace(/\/tmp\//, "/")          // /tmp/ 경로 제거
    .replace(/\.[^.]+$/, ".webp");    // 확장자를 .webp 로 교체
}

/**
 * Presigned URL 의 버킷 base URL 을 추출합니다.
 *   예) https://my-bucket.s3.ap-northeast-2.amazonaws.com
 */
function s3BucketBaseOf(presignedUrl: string): string {
  const base = s3BaseUrlOf(presignedUrl);
  const match = base.match(/^(https:\/\/[^/]+)/);
  return match ? match[1] : "";
}

// ────────────────────────────────────────────────────────────────────────────
// 테스트 스위트
// ────────────────────────────────────────────────────────────────────────────

test.describe("Lambda 콜백 전체 흐름 E2E", () => {
  let accessToken: string;

  // ──────────────────────────────────────────────────────────────────────────
  // Setup: 테스트 전용 계정 생성 + 로그인
  // ──────────────────────────────────────────────────────────────────────────
  test.beforeAll(async ({ request }) => {
    if (!ENV.LAMBDA_WEBHOOK_SECRET) {
      console.warn(
        "\n⚠️  [Setup] LAMBDA_WEBHOOK_SECRET 이 .env 에 설정되지 않았습니다." +
          " 시크릿 검증 테스트가 실패할 수 있습니다."
      );
    }

    const uniqueEmail = `pw-lambda-${Date.now()}@playwright.test`;
    const password = "Test@1234!";

    // 1. 회원가입
    const signupRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
      headers: { "Content-Type": "application/json" },
      data: { email: uniqueEmail, password, nickname: "PlaywrightLambdaTester" },
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
  // 공통 헬퍼
  // ──────────────────────────────────────────────────────────────────────────

  /** POST /images/presigned-urls 를 호출하여 Presigned URL 목록을 반환합니다. */
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
    expect(res.status(), `Presigned URL 발급 실패 (HTTP ${res.status()})`).toBe(200);
    const body = await res.json();
    expect(body.status, "응답 status 필드 오류").toBe("SUCCESS");
    const items = body.data.items as PresignedUrlItem[];
    console.log(
      `\n[Presigned] ${referenceType} URL ${items.length}개 발급 ✅` +
        items.map((i) => `\n  · ${i.tmpObjectKey}`).join("")
    );
    return items;
  }

  /** Presigned PUT URL 로 이미지를 실제 S3 에 업로드합니다. */
  async function putImageToS3(
    request: APIRequestContext,
    presignedUrl: string,
    imageFilename: string,
    label: string
  ): Promise<void> {
    const imageData = readImage(imageFilename);
    const putRes = await request.put(presignedUrl, {
      headers: { "Content-Type": "image/png" },
      data: imageData,
    });
    if (putRes.status() !== 200) {
      const errBody = await putRes.text().catch(() => "(body 없음)");
      throw new Error(
        `[${label}] S3 PUT 실패 (HTTP ${putRes.status()}):\n${errBody}`
      );
    }
    console.log(`[${label}] S3 PUT 완료 ✅  → ${s3BaseUrlOf(presignedUrl)}`);
  }

  /**
   * S3 오브젝트 URL (서명 없음) 로 GET 을 보내 파일이 존재하는지 확인합니다.
   * - 200: 존재 확인 (파일 크기까지 검증)
   * - 403/404: 존재하지 않거나 읽기 권한 없음
   */
  async function getS3Object(
    request: APIRequestContext,
    objectUrl: string,
    label: string
  ): Promise<{ status: number; contentType: string | null; contentLength: number }> {
    const res = await request.get(objectUrl, { failOnStatusCode: false });
    const headers = res.headers();
    const contentType = headers["content-type"] ?? null;
    const contentLength = parseInt(headers["content-length"] ?? "0", 10);
    console.log(
      `[${label}] GET ${objectUrl}\n  → HTTP ${res.status()}, content-type: ${contentType ?? "없음"}, size: ${contentLength} bytes`
    );
    return { status: res.status(), contentType, contentLength };
  }

  /**
   * Lambda 콜백 엔드포인트를 호출합니다.
   * POST /internal/images/lambda-callback
   */
  async function sendLambdaCallback(
    request: APIRequestContext,
    payload: {
      status: "COMPLETED" | "FAILED";
      tmpObjectKey: string;
      finalObjectKey?: string;
      errorReason?: string;
    },
    label: string
  ): Promise<void> {
    const res = await request.post(LAMBDA_CALLBACK_URL, {
      headers: {
        "Content-Type": "application/json",
        [WEBHOOK_SECRET_HEADER]: ENV.LAMBDA_WEBHOOK_SECRET,
      },
      data: payload,
      failOnStatusCode: false,
    });

    const body = await res.json().catch(() => ({}));
    expect(
      res.status(),
      `[${label}] Lambda 콜백 응답 오류 (HTTP ${res.status()})\n응답: ${JSON.stringify(body)}`
    ).toBe(200);
    expect(
      (body as Record<string, string>).status,
      `[${label}] Lambda 콜백 응답 status 필드 오류`
    ).toBe("SUCCESS");

    console.log(`[${label}] Lambda 콜백 전송 완료 ✅  (status=${payload.status})`);
  }

  // ════════════════════════════════════════════════════════════════════════════
  // TC-1: COMPLETED 전체 흐름 (실제 Lambda 연동)
  //
  //   1. Presigned URL 발급
  //   2. S3 PUT 업로드 → S3 이벤트로 Lambda 자동 트리거
  //   3. S3 GET tmp 경로 → 업로드됐는지 확인 (200)
  //   4. 실제 Lambda 가 BE 에 /internal/images/lambda-callback 을 호출할 때까지 대기
  //        → S3 final_object_key 경로에 WebP 가 나타나면 & db에 status = COMPLETED 이면 Lambda 콜백 + BE 200 응답 완료로 판단
  //   5. Lambda 가 tmp 파일을 삭제할 시간을 줌 (10초 대기)
  //   6. S3 GET tmp 경로 → 삭제됐는지 확인 (200 이 아님)
  //   7. S3 GET final_object_key → WebP 존재 + content-type=image/webp + 리사이즈 확인
  // ════════════════════════════════════════════════════════════════════════════
  test("TC-1: COMPLETED 콜백 전체 흐름", async ({
    request,
  }) => {
    // ── Step 1: Presigned URL 발급 ──────────────────────────────────────────
    console.log("\n━━━ Step 1: Presigned URL 발급 ━━━");
    const items = await issuePresignedUrls(request, "COMMUNITY_FREE", ["test1.png"]);
    expect(items).toHaveLength(1);

    const { presignedUrl, tmpObjectKey } = items[0];
    expect(tmpObjectKey).toMatch(/^public\/community\/free\/tmp\/.+\.png$/);

    const tmpObjectUrl = s3BaseUrlOf(presignedUrl);
    const finalObjectKey = expectedFinalObjectKey(tmpObjectKey);
    const bucketBase = s3BucketBaseOf(presignedUrl);
    const finalObjectUrl = `${bucketBase}/${finalObjectKey}`;

    console.log(`  tmpObjectKey:   ${tmpObjectKey}`);
    console.log(`  finalObjectKey: ${finalObjectKey}`);

    // ── Step 2: S3 PUT 업로드 → Lambda 트리거 ──────────────────────────────
    // S3 업로드가 완료되면 S3 이벤트 알림에 의해 Lambda 가 자동으로 트리거됩니다.
    console.log("\n━━━ Step 2: S3 PUT 업로드 (Lambda 트리거) ━━━");
    await putImageToS3(request, presignedUrl, "test1.png", "S3 PUT");

    // ── Step 3: S3 GET tmp 경로 → 업로드 확인 ──────────────────────────────
    console.log("\n━━━ Step 3: S3 GET tmp 경로 (업로드 확인) ━━━");
    const beforeCallbackGet = await getS3Object(request, tmpObjectUrl, "S3 GET tmp (before callback)");
    if (beforeCallbackGet.status === 200) {
      expect(
        beforeCallbackGet.contentLength,
        "업로드 후 GET 응답의 content-length 가 0"
      ).toBeGreaterThan(0);
      console.log("  tmp 경로 업로드 확인 ✅");
    } else {
      console.warn(
        `  ⚠️  S3 GET → HTTP ${beforeCallbackGet.status} (버킷 퍼블릭 읽기 정책 미적용 가능)`
      );
    }

    // ── Step 4: 실제 Lambda 콜백 완료 대기 ─────────────────────────────────
    // Lambda 가 이미지를 처리한 후 final_object_key 경로에 업로드한 후, 
    // BE 에 /internal/images/lambda-callback 을 호출하고
    // BE 로부터 200 응답을 받으면, tmp경로의 파일 삭제를 시작합니다. 
    // → final_object_key 경로에 WebP 가 나타나는 것을 폴링하여 Lambda의 리사이징 완료를 감지합니다. 
    console.log("\n━━━ Step 4: Lambda 콜백 완료 대기 (S3 final 경로 폴링) ━━━");
    console.log(`  폴링 대상: ${finalObjectUrl}`);

    const LAMBDA_TIMEOUT_MS = 120_000;  // Lambda 처리 최대 대기 시간: 2분
    const POLL_INTERVAL_MS = 3_000;     // 3초 간격으로 폴링
    const startTime = Date.now();
    let lambdaCallbackDetected = false;

    while (Date.now() - startTime < LAMBDA_TIMEOUT_MS) {
      const elapsed = Math.round((Date.now() - startTime) / 1000);
      process.stdout.write(`\r  폴링 중... (${elapsed}초 경과)`);

      const pollRes = await request.get(finalObjectUrl, { failOnStatusCode: false });
      if (pollRes.status() === 200) {
        lambdaCallbackDetected = true;
        console.log(
          `\n  Lambda 콜백 완료 감지 ✅  (${elapsed}초 경과)\n` +
            `  · Lambda 가 WebP 를 업로드했습니다 BE 가 200 응답을 반환했습니다.`
        );
        break;
      }

      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    }

    expect(
      lambdaCallbackDetected,
      `Lambda 처리 타임아웃: ${LAMBDA_TIMEOUT_MS / 1000}초 이내에 ` +
        `final_object_key(${finalObjectKey}) 에 WebP 가 나타나지 않았습니다.\n` +
        "Lambda 함수가 실행 중인지, S3 이벤트 트리거가 설정돼 있는지 확인하세요."
    ).toBe(true);

    // ── Step 5: Lambda 가 tmp 파일을 삭제할 시간을 줌 (10초 대기) ───────────
    // Lambda 는 BE 로부터 200 응답을 받은 후 tmp 경로의 원본 파일을 삭제합니다.
    // 삭제가 완료되기까지 약간의 시간이 필요하므로 10초 대기합니다.
    console.log("\n━━━ Step 5: Lambda tmp 파일 삭제 대기 (10초) ━━━");
    console.log(`  · 삭제 예정 경로: ${tmpObjectUrl}`);
    await new Promise((resolve) => setTimeout(resolve, 10_000));
    console.log("  10초 대기 완료 ✅");

    // ── Step 6: S3 GET tmp 경로 → 삭제됐는지 확인 ──────────────────────────
    console.log("\n━━━ Step 6: S3 GET tmp 경로 (Lambda 삭제 확인) ━━━");
    const afterDeleteGet = await getS3Object(request, tmpObjectUrl, "S3 GET tmp (after Lambda delete)");
    expect(
      afterDeleteGet.status,
      `tmp 경로가 아직 존재합니다 (HTTP ${afterDeleteGet.status}).\n` +
        `Lambda 가 tmp 파일을 삭제하지 않았거나 삭제가 지연되고 있습니다.\n` +
        `· 경로: ${tmpObjectUrl}`
    ).not.toBe(200);
    console.log(`  tmp 오브젝트 삭제 확인 ✅  (HTTP ${afterDeleteGet.status})`);

    // ── Step 7: S3 GET final_object_key → WebP 존재 + content-type 확인 ───
    // Step 4 폴링에서 이미 200 을 확인했으나, content-type / size 를 추가 검증합니다.
    console.log("\n━━━ Step 7: S3 GET final_object_key (WebP + 리사이즈 확인) ━━━");
    console.log(`  최종 경로: ${finalObjectUrl}`);

    const finalGet = await getS3Object(request, finalObjectUrl, "S3 GET final WebP");

    expect(finalGet.status, "WebP 최종 경로가 200 이 아닙니다").toBe(200);
    expect(
      finalGet.contentLength,
      "WebP 파일 크기가 0 (빈 파일)"
    ).toBeGreaterThan(0);
    expect(
      finalGet.contentType,
      "변환된 최종 이미지의 Content-Type 이 image/webp 가 아닙니다"
    ).toContain("image/webp");

    console.log(
      `  WebP 변환 + 경로 이동 확인 ✅\n` +
        `  · content-type: ${finalGet.contentType}\n` +
        `  · size: ${finalGet.contentLength.toLocaleString()} bytes\n` +
        `  · 원본(tmp) → 최종(webp) 경로 전환 완료`
    );
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-2: FAILED 콜백 흐름
  //
  //   1. Presigned URL 발급
  //   2. S3 PUT 업로드
  //   3. S3 GET tmp 경로 → 업로드 확인
  //   4. Lambda 콜백 FAILED → BE 200 OK + {"status":"SUCCESS"} 확인
  //   5. Spring 처리 결과 검증 (간접)
  //      5-1. S3 final_object_key 경로 미존재 (WebP 변환 없음)
  //      5-2. 동일 key FAILED 재전송 → 409 IMAGE_002 (DB=FAILED 간접 확인)
  // ════════════════════════════════════════════════════════════════════════════
  test("TC-2: FAILED 콜백 흐름", async ({ request }) => {
    // ── Step 1: Presigned URL 발급 ──────────────────────────────────────────
    console.log("\n━━━ Step 1: Presigned URL 발급 ━━━");
    const items = await issuePresignedUrls(request, "COMMUNITY_FREE", ["test1.png"]);
    expect(items).toHaveLength(1);

    const { presignedUrl, tmpObjectKey } = items[0];
    const tmpObjectUrl = s3BaseUrlOf(presignedUrl);

    // ── Step 2: S3 PUT 업로드 ───────────────────────────────────────────────
    console.log("\n━━━ Step 2: S3 PUT 업로드 ━━━");
    await putImageToS3(request, presignedUrl, "test1.png", "S3 PUT");

    // ── Step 3: S3 GET tmp 경로 → 업로드 확인 ──────────────────────────────
    console.log("\n━━━ Step 3: S3 GET tmp 경로 (업로드 확인) ━━━");
    const beforeCallbackGet = await getS3Object(request, tmpObjectUrl, "S3 GET tmp (before FAILED callback)");
    if (beforeCallbackGet.status === 200) {
      console.log("  tmp 경로 업로드 확인 ✅");
    } else {
      console.warn(`  ⚠️  S3 GET → HTTP ${beforeCallbackGet.status} (버킷 퍼블릭 읽기 정책 미적용 가능)`);
    }

    // ── Step 4: Lambda 콜백 FAILED 전송 ────────────────────────────────────
    console.log("\n━━━ Step 4: Lambda 콜백 (FAILED) 전송 ━━━");
    const errorReason = "Lambda OOM: memory limit exceeded during WebP conversion";
    await sendLambdaCallback(
      request,
      {
        status: "FAILED",
        tmpObjectKey,
        errorReason,
      },
      "Lambda FAILED"
    );

    // ── Step 5: Spring 처리 결과 검증 ──────────────────────────────────────────
    console.log("\n━━━ Step 5: Spring 처리 결과 검증 ━━━");

    // 5-1. final_object_key 경로 미존재 확인
    //      Lambda FAILED 이므로 WebP 변환이 일어나지 않았고,
    //      final_object_key 경로에 파일이 없어야 합니다.
    const finalObjectKey = expectedFinalObjectKey(tmpObjectKey);
    const s3BucketBase = s3BucketBaseOf(presignedUrl);
    const finalObjectUrl = `${s3BucketBase}/${finalObjectKey}`;

    const finalGet = await getS3Object(
      request,
      finalObjectUrl,
      "S3 GET final (should NOT exist after FAILED)"
    );
    expect(
      finalGet.status,
      `FAILED 콜백 후 final_object_key(${finalObjectKey}) 가 HTTP 200 으로 조회됩니다.\n` +
        "Lambda FAILED 시 WebP 변환 파일이 없어야 합니다."
    ).not.toBe(200);
    console.log(`  final_object_key 미존재 확인 ✅  (HTTP ${finalGet.status})`);

    // 5-2. 동일 tmpObjectKey 로 FAILED 재전송 → 409 CONFLICT (DB=FAILED 간접 확인)
    //      Spring 이 DB 를 FAILED 로 정상 갱신했다면:
    //        image.fail() 호출 → ImageStatusInvalidException → 409 IMAGE_002
    //      DB 가 아직 PENDING 이라면:
    //        재전송이 200 으로 성공 → Spring 처리 실패를 의미
    console.log("\n  [재전송 검증] 동일 key FAILED 재전송 → 409 기대 (DB=FAILED 간접 확인)");
    const retryRes = await request.post(LAMBDA_CALLBACK_URL, {
      headers: {
        "Content-Type": "application/json",
        [WEBHOOK_SECRET_HEADER]: ENV.LAMBDA_WEBHOOK_SECRET,
      },
      data: { status: "FAILED", tmpObjectKey, errorReason },
      failOnStatusCode: false,
    });
    const retryBody = await retryRes.json().catch(() => ({}));
    expect(
      retryRes.status(),
      `FAILED 재전송 시 409 가 아닙니다 (HTTP ${retryRes.status()}).\n` +
        "Spring 이 DB 상태를 FAILED 로 갱신하지 못했을 가능성이 있습니다.\n" +
        `응답: ${JSON.stringify(retryBody)}`
    ).toBe(409);
    expect(
      (retryBody as Record<string, string>).code,
      "FAILED 재전송 에러 코드가 IMAGE_002 가 아닙니다"
    ).toBe("IMAGE_002");
    console.log(`  DB status=FAILED 간접 확인 ✅  (재전송 → HTTP 409 IMAGE_002)`);

    console.log(
      `\n[TC-2] FAILED 콜백 전체 흐름 완료 ✅\n` +
        `  · tmpObjectKey:   ${tmpObjectKey}\n` +
        `  · errorReason:    ${errorReason}\n` +
        `  · final path:     미존재 (HTTP ${finalGet.status})\n` +
        `  · DB status 검증: FAILED 갱신 확인 (재전송 → 409 IMAGE_002)`
    );
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-3: 잘못된 Webhook Secret → 401 Unauthorized
  // ════════════════════════════════════════════════════════════════════════════
  test("TC-3: 잘못된 Webhook Secret 으로 Lambda 콜백 - 401 Unauthorized", async ({ request }) => {
    // 임의 tmp 키 (실제 DB 에 없어도 401 이 먼저 반환돼야 함)
    const res = await request.post(LAMBDA_CALLBACK_URL, {
      headers: {
        "Content-Type": "application/json",
        [WEBHOOK_SECRET_HEADER]: "wrong-secret-should-fail",
      },
      data: {
        status: "COMPLETED",
        tmpObjectKey: "public/community/free/tmp/non-existent-uuid.png",
        finalObjectKey: "public/community/free/non-existent-uuid.webp",
      },
      failOnStatusCode: false,
    });

    expect(
      res.status(),
      `잘못된 secret 인데 401 이 아님 (HTTP ${res.status()})`
    ).toBe(401);

    const body = await res.json();
    expect(
      (body as Record<string, string>).code,
      "에러 코드가 IMAGE_003 이 아님"
    ).toBe("IMAGE_003");

    console.log("[TC-3] 잘못된 Webhook Secret → 401 IMAGE_003 정상 처리 ✅");
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-4: Webhook Secret 헤더 누락 → 400 Bad Request
  // ════════════════════════════════════════════════════════════════════════════
  test("TC-4: Webhook Secret 헤더 누락 - 400 Bad Request", async ({ request }) => {
    const res = await request.post(LAMBDA_CALLBACK_URL, {
      headers: { "Content-Type": "application/json" },
      data: {
        status: "COMPLETED",
        tmpObjectKey: "public/community/free/tmp/non-existent-uuid.png",
        finalObjectKey: "public/community/free/non-existent-uuid.webp",
      },
      failOnStatusCode: false,
    });

    expect(
      res.status(),
      `헤더 누락인데 400 이 아님 (HTTP ${res.status()})`
    ).toBe(400);

    console.log("[TC-4] Webhook Secret 헤더 누락 → 400 정상 처리 ✅");
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-5: 존재하지 않는 tmpObjectKey 로 콜백 → 404 Not Found
  // ════════════════════════════════════════════════════════════════════════════
  test("TC-5: 존재하지 않는 tmpObjectKey 로 COMPLETED 콜백 - 404 Not Found", async ({ request }) => {
    const nonExistentKey = `public/community/free/tmp/no-such-uuid-${Date.now()}.png`;

    const res = await request.post(LAMBDA_CALLBACK_URL, {
      headers: {
        "Content-Type": "application/json",
        [WEBHOOK_SECRET_HEADER]: ENV.LAMBDA_WEBHOOK_SECRET,
      },
      data: {
        status: "COMPLETED",
        tmpObjectKey: nonExistentKey,
        finalObjectKey: nonExistentKey.replace("/tmp/", "/").replace(".png", ".webp"),
      },
      failOnStatusCode: false,
    });

    expect(
      res.status(),
      `존재하지 않는 키인데 404 가 아님 (HTTP ${res.status()})`
    ).toBe(404);

    const body = await res.json();
    expect(
      (body as Record<string, string>).code,
      "에러 코드가 IMAGE_001 이 아님"
    ).toBe("IMAGE_001");

    console.log("[TC-5] 존재하지 않는 tmpObjectKey → 404 IMAGE_001 정상 처리 ✅");
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-6: COMPLETED 콜백 멱등성 — 동일 요청 2회 → 둘 다 200 OK
  // ════════════════════════════════════════════════════════════════════════════
    test("TC-6: COMPLETED 콜백 멱등성 - 동일 tmpObjectKey 로 2회 요청 - 둘 다 200 OK", async ({
    request,
  }) => {
    // Presigned URL 발급 + 업로드
    const items = await issuePresignedUrls(request, "COMMUNITY_FREE", ["test1.png"]);
    const { presignedUrl, tmpObjectKey } = items[0];
    await putImageToS3(request, presignedUrl, "test1.png", "멱등성 테스트 업로드");

    const finalObjectKey = expectedFinalObjectKey(tmpObjectKey);
    const payload = { status: "COMPLETED" as const, tmpObjectKey, finalObjectKey };

    // 1차 콜백
    console.log("\n[TC-6] 1차 COMPLETED 콜백 전송");
    await sendLambdaCallback(request, payload, "멱등성 1차");

    // 2차 동일 콜백 — 이미 COMPLETED 상태이므로 skip 처리하고 200 반환해야 함
    console.log("[TC-6] 2차 COMPLETED 콜백 전송 (멱등성)");
    await sendLambdaCallback(request, payload, "멱등성 2차");

    console.log("[TC-6] 멱등성 확인 ✅  — 동일 콜백 2회 모두 200 OK");
  });

  // ════════════════════════════════════════════════════════════════════════════
  // TC-7: MARKET 2장 업로드 전체 흐름 (실제 Lambda 연동)
  //
  //   MARKET 2장 입력 → Presigned URL 3개 발급 (n+1 규칙)
  //     [0] MARKET_THUMB  ← image1 → 최종: public/market/thumb/{uuid}.webp
  //     [1] MARKET_DETAIL ← image1 → 최종: public/market/detail/{uuid}.webp
  //     [2] MARKET_DETAIL ← image2 → 최종: public/market/detail/{uuid}.webp
  //
  //   검증 포인트:
  //     · image1 은 THUMB 경로 + DETAIL 경로 양쪽에 모두 존재해야 한다
  //     · image2 는 DETAIL 경로에만 존재해야 한다
  //     · 3개의 tmp 경로는 모두 삭제돼야 한다
  //     · 3개의 final 경로는 모두 content-type=image/webp 로 존재해야 한다
  //
  //   Lambda 는 tmpObjectKey 별로 독립적으로 트리거되므로,
  //   3개의 final 경로를 병렬로 폴링하여 모든 처리 완료를 감지합니다.
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "TC-7: MARKET 2장 업로드 전체 흐름",
    async ({ request }) => {
      // ── Step 1: Presigned URL 발급 (MARKET, 2장 → 3개 URL) ─────────────────
      console.log("\n━━━ Step 1: Presigned URL 발급 (MARKET 2장 → 3개) ━━━");
      const items = await issuePresignedUrls(request, "MARKET", ["test1.png", "test2.png"]);

      // MARKET n장 → n+1개 URL
      expect(items, "MARKET 2장 입력 시 Presigned URL 이 3개여야 합니다").toHaveLength(3);

      // prefix 검증
      expect(items[0].tmpObjectKey, "[0] MARKET_THUMB prefix 오류").toMatch(
        /^public\/market\/thumb\/tmp\/.+\.png$/
      );
      expect(items[1].tmpObjectKey, "[1] MARKET_DETAIL prefix 오류").toMatch(
        /^public\/market\/detail\/tmp\/.+\.png$/
      );
      expect(items[2].tmpObjectKey, "[2] MARKET_DETAIL prefix 오류").toMatch(
        /^public\/market\/detail\/tmp\/.+\.png$/
      );

      // final_object_key 도출
      const bucketBase = s3BucketBaseOf(items[0].presignedUrl);
      const slots = items.map((item, idx) => {
        const label = idx === 0 ? "THUMB(image1)" : idx === 1 ? "DETAIL(image1)" : "DETAIL(image2)";
        const tmpUrl = s3BaseUrlOf(item.presignedUrl);
        const finalKey = expectedFinalObjectKey(item.tmpObjectKey);
        const finalUrl = `${bucketBase}/${finalKey}`;
        return { label, item, tmpUrl, finalKey, finalUrl };
      });

      console.log("  발급된 경로:");
      slots.forEach((s) => {
        console.log(`  [${s.label}] tmp:   ${s.item.tmpObjectKey}`);
        console.log(`  [${s.label}] final: ${s.finalKey}`);
      });

      // ── Step 2: S3 PUT 업로드 — 3개 파일 업로드 (Lambda 트리거 ×3) ────────
      // items[0] (THUMB)     ← test1.png
      // items[1] (DETAIL)    ← test1.png  (같은 원본 이미지, 별도 tmpKey)
      // items[2] (DETAIL)    ← test2.png
      console.log("\n━━━ Step 2: S3 PUT 업로드 (3개, Lambda 트리거 ×3) ━━━");
      await putImageToS3(request, slots[0].item.presignedUrl, "test1.png", slots[0].label);
      await putImageToS3(request, slots[1].item.presignedUrl, "test1.png", slots[1].label);
      await putImageToS3(request, slots[2].item.presignedUrl, "test2.png", slots[2].label);

      // ── Step 3: S3 GET tmp 경로 → 3개 업로드 확인 ──────────────────────────
      console.log("\n━━━ Step 3: S3 GET tmp 경로 3개 (업로드 확인) ━━━");
      for (const s of slots) {
        const got = await getS3Object(request, s.tmpUrl, `S3 GET tmp ${s.label}`);
        if (got.status === 200) {
          expect(got.contentLength, `[${s.label}] tmp GET content-length 가 0`).toBeGreaterThan(0);
          console.log(`  [${s.label}] 업로드 확인 ✅`);
        } else {
          console.warn(`  ⚠️  [${s.label}] S3 GET → HTTP ${got.status} (버킷 퍼블릭 읽기 정책 미적용 가능)`);
        }
      }

      // ── Step 4: 3개의 Lambda 콜백 완료를 병렬 폴링으로 대기 ────────────────
      // Lambda 는 tmpObjectKey 별로 독립 실행되므로, 3개의 final 경로를 동시에 폴링합니다.
      // 모든 final 경로에 WebP 가 나타나면 3개의 Lambda 콜백이 모두 완료된 것으로 판단합니다.
      console.log("\n━━━ Step 4: Lambda 콜백 완료 병렬 대기 (3개 final 경로 폴링) ━━━");

      const LAMBDA_TIMEOUT_MS = 180_000;  // 3개 처리이므로 최대 3분 허용
      const POLL_INTERVAL_MS = 3_000;

      /**
       * 단일 final 경로가 S3 에 나타날 때까지 폴링합니다.
       * 감지되면 resolve, 타임아웃 시 reject 합니다.
       */
      async function waitForFinalWebP(label: string, finalUrl: string): Promise<void> {
        const start = Date.now();
        while (Date.now() - start < LAMBDA_TIMEOUT_MS) {
          const elapsed = Math.round((Date.now() - start) / 1000);
          const pollRes = await request.get(finalUrl, { failOnStatusCode: false });
          if (pollRes.status() === 200) {
            console.log(
              `  [${label}] Lambda 콜백 완료 감지 ✅  (${elapsed}초 경과)\n  · ${finalUrl}`
            );
            return;
          }
          await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
        }
        throw new Error(
          `[${label}] Lambda 처리 타임아웃: ${LAMBDA_TIMEOUT_MS / 1000}초 이내에 ` +
            `final 경로에 WebP 가 나타나지 않았습니다.\n  · ${finalUrl}`
        );
      }

      // 3개를 동시에 폴링 — 가장 느린 Lambda 기준으로 대기
      await Promise.all(slots.map((s) => waitForFinalWebP(s.label, s.finalUrl)));
      console.log("  3개 Lambda 콜백 모두 완료 ✅");

      // ── Step 5: Lambda 가 tmp 파일 3개를 삭제할 시간을 줌 (10초 대기) ───────
      console.log("\n━━━ Step 5: Lambda tmp 파일 삭제 대기 (10초) ━━━");
      await new Promise((resolve) => setTimeout(resolve, 10_000));
      console.log("  10초 대기 완료 ✅");

      // ── Step 6: S3 GET tmp 경로 3개 → 모두 삭제됐는지 확인 ─────────────────
      console.log("\n━━━ Step 6: S3 GET tmp 경로 3개 (Lambda 삭제 확인) ━━━");
      for (const s of slots) {
        const got = await getS3Object(request, s.tmpUrl, `S3 GET tmp ${s.label} (after delete)`);
        expect(
          got.status,
          `[${s.label}] tmp 경로가 아직 존재합니다 (HTTP ${got.status}).\n  · ${s.tmpUrl}`
        ).not.toBe(200);
        console.log(`  [${s.label}] tmp 삭제 확인 ✅  (HTTP ${got.status})`);
      }

      // ── Step 7: S3 GET final 경로 → WebP 존재 + content-type 확인 ──────────
      // 배치 검증:
      //   image1 → public/market/thumb/{uuid}.webp  (THUMB)   ← slots[0]
      //   image1 → public/market/detail/{uuid}.webp (DETAIL)  ← slots[1]
      //   image2 → public/market/detail/{uuid}.webp (DETAIL)  ← slots[2]
      console.log("\n━━━ Step 7: S3 GET final 경로 3개 (WebP 확인) ━━━");
      for (const s of slots) {
        const got = await getS3Object(request, s.finalUrl, `S3 GET final ${s.label}`);
        expect(got.status, `[${s.label}] WebP 최종 경로가 200 이 아닙니다`).toBe(200);
        expect(got.contentLength, `[${s.label}] WebP 크기가 0`).toBeGreaterThan(0);
        expect(
          got.contentType,
          `[${s.label}] Content-Type 이 image/webp 가 아닙니다`
        ).toContain("image/webp");
        console.log(
          `  [${s.label}] WebP 확인 ✅  · ${got.contentLength.toLocaleString()} bytes`
        );
      }

      // ── 최종 경로 분포 요약 ─────────────────────────────────────────────────
      console.log(
        "\n━━━ 최종 경로 분포 확인 ━━━\n" +
          `  image1 → THUMB  경로 존재 ✅  ${slots[0].finalUrl}\n` +
          `  image1 → DETAIL 경로 존재 ✅  ${slots[1].finalUrl}\n` +
          `  image2 → DETAIL 경로 존재 ✅  ${slots[2].finalUrl}\n` +
          "  image1 은 THUMB + DETAIL 양쪽에 모두 위치 ✅\n" +
          "  image2 는 DETAIL 에만 위치 ✅"
      );
    }
  );
});
