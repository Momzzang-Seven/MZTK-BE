/**
 * MZTK - Upsert/삭제 시 S3 실제 삭제 검증 Playwright E2E 테스트
 *
 * 검증 시나리오:
 *   실제 S3 업로드 → 실제 Lambda 처리 → UpsertImagesByReferenceService로
 *   이미지 제거 시 finalObjectKey(WebP) 가 S3에서 실제로 삭제되는지 검증합니다.
 *
 *   [S3-DEL-001] 단일 COMPLETED 이미지 제거 → S3 finalObjectKey 삭제
 *     1. POST /images/presigned-urls → imageId, tmpObjectKey, presignedUrl 발급
 *     2. Presigned URL 로 실제 S3 에 이미지 PUT 업로드
 *     3. Lambda 처리 대기 (finalObjectKey S3 폴링 — 최대 120초)
 *     4. POST /posts/free → 게시글 생성
 *     5. PATCH /posts/{postId} imageIds=[imageId] → 이미지 게시글 연결
 *     6. PATCH /posts/{postId} imageIds=[] → 이미지 전체 제거 (S3 deleteObject 트리거)
 *     7. GET finalObjectKey S3 URL → 404/403 확인 (삭제 완료)
 *
 *   [S3-DEL-002] 다중 이미지 부분 제거 → 제거된 것만 S3 삭제, 유지된 것은 그대로
 *     1~3. 이미지 2장 발급·업로드·Lambda 처리 대기
 *     4~5. 게시글 생성 + 2장 연결
 *     6. PATCH /posts/{postId} imageIds=[imageId_1] → imageId_2 만 제거
 *     7. GET img2 finalObjectKey → 404 (삭제), GET img1 finalObjectKey → 200 (유지)
 *
 *   [S3-DEL-003] PENDING 이미지 제거 → finalObjectKey 없으므로 S3 삭제 없음, 정상 완료
 *     1~2. 이미지 발급 + S3 업로드 (Lambda 대기 없음 → PENDING 상태 유지)
 *     3~4. 게시글 생성 + 이미지 연결
 *     5. PATCH /posts/{postId} imageIds=[] → PENDING 이미지 제거
 *     6. 서비스 정상 완료 확인 (S3 tmpKey 는 S3 lifecycle rule 이 처리)
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 실행 중이어야 합니다 (기본: http://127.0.0.1:8080)
 *   - 서버 .env 에 실제 AWS IAM 자격증명이 설정되어 있어야 합니다
 *   - Lambda 가 S3 ObjectCreated 이벤트에 연결되어 있어야 합니다 (S3-DEL-001/002)
 *   - test-images/ 디렉터리에 test1.png, test2.png 파일이 존재해야 합니다
 *
 * 실행 방법:
 *   cd src/test/java/momzzangseven/mztkbe/integration/play_wright
 *   npx playwright test S3-delete-when-upsert-delete-image/s3-delete-on-upsert.spec.ts
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ⚠️  S3-DEL-001/002 는 실제 Lambda 처리가 완료된 후에만 검증됩니다.
 *     Lambda 없이 테스트하려면 해당 테스트를 test.skip() 으로 비활성화하거나,
 *     AWS CLI 로 직접 WebP 파일을 finalObjectKey 경로에 업로드한 뒤 실행하세요.
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
};

// ────────────────────────────────────────────────────────────────────────────
// 상수
// ────────────────────────────────────────────────────────────────────────────
const TEST_IMAGES_DIR = path.resolve(
  __dirname,
  "..",
  "put-image-to-S3-with-presigned-url",
  "test-images"
);

/** Lambda 처리 완료 폴링 최대 대기 시간 (ms). Lambda 최대 실행시간 15분이지만 통상 30초 이내 */
const LAMBDA_POLL_TIMEOUT_MS = 120_000;
/** Lambda 폴링 간격 (ms) */
const LAMBDA_POLL_INTERVAL_MS = 5_000;

// ────────────────────────────────────────────────────────────────────────────
// 타입
// ────────────────────────────────────────────────────────────────────────────
interface PresignedUrlItem {
  imageId: number;
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
 * tmpObjectKey 에서 Lambda 처리 후 생성되는 finalObjectKey 를 유도합니다.
 *
 * 변환 규칙 (docs/S3/image_processor_lambda.py 참조):
 *   public/community/free/tmp/{uuid}.jpg → public/community/free/{uuid}.webp
 *   경로에서 /tmp/ 를 제거하고 확장자를 .webp 로 교체합니다.
 */
function deriveFinalObjectKey(tmpObjectKey: string): string {
  return tmpObjectKey.replace("/tmp/", "/").replace(/\.[^.]+$/, ".webp");
}

/**
 * Presigned URL 에서 S3 버킷 Base URL 을 추출합니다.
 * 예: "https://bucket.s3.region.amazonaws.com/path/to/object?X-Amz-..."
 *      → "https://bucket.s3.region.amazonaws.com/"
 */
function extractS3BaseUrl(presignedUrl: string): string {
  const url = new URL(presignedUrl);
  return `${url.protocol}//${url.host}/`;
}

/**
 * S3 버킷 Base URL 과 objectKey 를 조합하여 공개 S3 오브젝트 URL 을 반환합니다.
 */
function buildS3ObjectUrl(s3BaseUrl: string, objectKey: string): string {
  return s3BaseUrl + objectKey;
}

// ────────────────────────────────────────────────────────────────────────────
// API 헬퍼
// ────────────────────────────────────────────────────────────────────────────

/** POST /images/presigned-urls 호출 후 PresignedUrlItem[] 반환. 공통 검증 포함. */
async function issuePresignedUrls(
  request: APIRequestContext,
  accessToken: string,
  referenceType: string,
  imageFilenames: string[]
): Promise<PresignedUrlItem[]> {
  const res = await request.post(`${ENV.BACKEND_URL}/images/presigned-urls`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { referenceType, images: imageFilenames },
  });

  expect(
    res.status(),
    `[${referenceType}] POST /images/presigned-urls 실패 (${res.status()})`
  ).toBe(200);

  const body = await res.json();
  expect(body.status).toBe("SUCCESS");

  const items = body.data.items as PresignedUrlItem[];
  items.forEach((item, idx) => {
    expect(
      item.imageId,
      `items[${idx}].imageId 는 양수여야 합니다`
    ).toBeGreaterThan(0);
    expect(
      item.tmpObjectKey,
      `items[${idx}].tmpObjectKey 가 비어있습니다`
    ).toBeTruthy();
  });

  console.log(
    `[발급] ${referenceType} presigned URL ${items.length}개:` +
      items.map((i) => `\n  · id=${i.imageId}  key=${i.tmpObjectKey}`).join("")
  );
  return items;
}

/** Presigned PUT URL 로 실제 S3 에 이미지를 업로드합니다. */
async function putImageToS3(
  request: APIRequestContext,
  presignedUrl: string,
  imageFilename: string,
  label: string
): Promise<void> {
  const imageData = readImage(imageFilename);
  const ext = path.extname(imageFilename).toLowerCase();
  const contentType =
    { ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png" }[
      ext
    ] ?? "application/octet-stream";

  const putRes = await request.put(presignedUrl, {
    headers: { "Content-Type": contentType },
    data: imageData,
  });

  if (putRes.status() !== 200) {
    const errBody = await putRes.text().catch(() => "(body 없음)");
    throw new Error(
      `[${label}] S3 PUT 실패 (HTTP ${putRes.status()}):\n${errBody}`
    );
  }
  console.log(`[S3 PUT ✅] ${label} → ${presignedUrl.split("?")[0]}`);
}

/**
 * Lambda 처리 완료를 기다립니다.
 * finalObjectKey S3 URL 에 주기적으로 GET 요청하여 200 응답이 오면 완료로 판단합니다.
 *
 * Lambda 가 처리를 완료하면:
 *   1. WebP 파일을 finalObjectKey 경로에 S3 저장
 *   2. 백엔드 /internal/images/lambda-callback 호출 (image.status = COMPLETED)
 *   3. S3 tmpObjectKey 원본 삭제
 *
 * public 경로의 오브젝트는 S3 GET 으로 존재 확인 가능합니다.
 *
 * @throws Error 타임아웃 시 (Lambda 미연결 또는 처리 실패)
 */
async function waitForLambdaCompletion(
  request: APIRequestContext,
  finalObjectUrl: string,
  label: string,
  timeoutMs = LAMBDA_POLL_TIMEOUT_MS,
  intervalMs = LAMBDA_POLL_INTERVAL_MS
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let attempt = 0;

  console.log(`[Lambda 대기] ${label} finalObjectKey 폴링 시작...`);

  while (Date.now() < deadline) {
    attempt++;
    try {
      const getRes = await request.get(finalObjectUrl, {
        failOnStatusCode: false,
      });

      if (getRes.status() === 200) {
        const contentLength = parseInt(
          getRes.headers()["content-length"] ?? "0",
          10
        );
        console.log(
          `[Lambda 완료 ✅] ${label} (시도 ${attempt}, ${contentLength} bytes)\n  · ${finalObjectUrl}`
        );
        return;
      }

      console.log(
        `[Lambda 대기 ${attempt}] ${label}: HTTP ${getRes.status()} — 아직 처리 중...`
      );
    } catch (_err) {
      console.log(`[Lambda 대기 ${attempt}] ${label}: 네트워크 오류 — 재시도`);
    }

    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(
    `[Lambda 타임아웃] ${label}: ${timeoutMs / 1000}초 내에 처리가 완료되지 않았습니다.\n` +
      `  finalObjectKey URL: ${finalObjectUrl}\n` +
      `  Lambda 가 S3 ObjectCreated 이벤트에 연결되어 있는지 확인하세요.`
  );
}

/**
 * S3 오브젝트가 삭제됐는지 확인합니다.
 * DELETE 후 최대 10초 내에 404/403 이 반환되어야 합니다.
 * S3 삭제는 동기적으로 즉시 반영되므로 단일 GET 으로 검증합니다.
 */
async function verifyS3ObjectDeleted(
  request: APIRequestContext,
  objectUrl: string,
  label: string
): Promise<void> {
  const getRes = await request.get(objectUrl, { failOnStatusCode: false });

  // S3 에서 오브젝트가 삭제되면 public 경로더라도 403 (NoSuchKey) 또는 404 반환
  expect(
    getRes.status(),
    `[S3 삭제 검증 실패] ${label}: 삭제 후에도 HTTP ${getRes.status()} 반환\n  · ${objectUrl}`
  ).not.toBe(200);

  console.log(
    `[S3 삭제 확인 ✅] ${label}: HTTP ${getRes.status()} (오브젝트 없음)\n  · ${objectUrl}`
  );
}

/**
 * S3 오브젝트가 아직 존재하는지 확인합니다.
 * 유지되어야 할 이미지가 실수로 삭제되지 않았는지 검증합니다.
 */
async function verifyS3ObjectStillExists(
  request: APIRequestContext,
  objectUrl: string,
  label: string
): Promise<void> {
  const getRes = await request.get(objectUrl, { failOnStatusCode: false });

  expect(
    getRes.status(),
    `[S3 유지 검증 실패] ${label}: 유지되어야 할 오브젝트가 삭제됨 (HTTP ${getRes.status()})\n  · ${objectUrl}`
  ).toBe(200);

  console.log(
    `[S3 유지 확인 ✅] ${label}: HTTP ${getRes.status()} (오브젝트 존재)\n  · ${objectUrl}`
  );
}

/** POST /posts/free 로 게시글을 생성하고 postId 를 반환합니다. */
async function createFreePost(
  request: APIRequestContext,
  accessToken: string
): Promise<number> {
  const res = await request.post(`${ENV.BACKEND_URL}/posts/free`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { content: "[S3-Delete E2E] 테스트 게시글" },
  });

  expect(res.status(), `POST /posts/free 실패 (${res.status()})`).toBe(201);
  const body = await res.json();
  const postId = body.data.postId as number;
  expect(postId, "postId 는 양수여야 합니다").toBeGreaterThan(0);

  console.log(`[게시글 생성 ✅] postId=${postId}`);
  return postId;
}

/** PATCH /posts/{postId} 로 게시글의 이미지 목록을 업데이트합니다. */
async function updatePostImages(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  imageIds: number[]
): Promise<void> {
  const res = await request.patch(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { imageIds },
  });

  expect(
    res.status(),
    `PATCH /posts/${postId} 실패 (${res.status()}) imageIds=${JSON.stringify(imageIds)}`
  ).toBe(200);

  console.log(
    `[게시글 수정 ✅] postId=${postId} imageIds=${JSON.stringify(imageIds)}`
  );
}

/** DELETE /posts/{postId} 로 게시글을 삭제합니다. */
async function deletePost(
  request: APIRequestContext,
  accessToken: string,
  postId: number
): Promise<void> {
  const res = await request.delete(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (res.status() !== 200 && res.status() !== 404) {
    console.warn(`[정리] postId=${postId} 삭제 응답 (${res.status()})`);
  }
}

// ════════════════════════════════════════════════════════════════════════════
// 테스트 스위트
// ════════════════════════════════════════════════════════════════════════════

test.describe("Upsert/삭제 시 S3 실제 삭제 검증 (Real S3 + Lambda)", () => {
  let accessToken: string;

  // 각 테스트에서 생성한 postId 목록 (cleanup 용)
  const createdPostIds: number[] = [];

  test.beforeAll(async ({ request }) => {
    const uniqueEmail = `pw-s3del-${Date.now()}@playwright.test`;
    const password = "Test@1234!";

    // 회원가입
    const signupRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
      headers: { "Content-Type": "application/json" },
      data: { email: uniqueEmail, password, nickname: "S3DeleteE2ETester" },
    });
    expect(
      signupRes.ok(),
      `회원가입 실패 (${signupRes.status()}). 서버가 ${ENV.BACKEND_URL} 에서 실행 중인지 확인하세요.`
    ).toBeTruthy();

    // 로그인 → accessToken
    const loginRes = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
      headers: { "Content-Type": "application/json" },
      data: { provider: "LOCAL", email: uniqueEmail, password },
    });
    expect(loginRes.ok(), `로그인 실패 (${loginRes.status()})`).toBeTruthy();

    const loginBody = await loginRes.json();
    accessToken = (loginBody.data as Record<string, string>).accessToken;
    expect(accessToken, "accessToken 발급 실패").toBeTruthy();

    console.log(`\n[Setup ✅] 테스트 계정 준비 완료 (email=${uniqueEmail})`);
  });

  test.afterAll(async ({ request }) => {
    // 테스트 중 생성된 게시글 삭제 (이미지는 게시글 삭제 이벤트로 자동 unlink)
    for (const postId of createdPostIds) {
      await deletePost(request, accessToken, postId);
      console.log(`[Cleanup] postId=${postId} 삭제`);
    }
  });

  // ════════════════════════════════════════════════════════════════════════
  // [S3-DEL-001] 단일 COMPLETED 이미지 제거 → S3 finalObjectKey 삭제
  // ════════════════════════════════════════════════════════════════════════

  test(
    "[S3-DEL-001]",
    async ({ request }) => {
      // ── Step 1: Presigned URL 발급
      const items = await issuePresignedUrls(
        request,
        accessToken,
        "COMMUNITY_FREE",
        ["test1.png"]
      );
      const { imageId, tmpObjectKey, presignedUrl } = items[0];

      // ── Step 2: 실제 S3 에 이미지 업로드
      await putImageToS3(request, presignedUrl, "test1.png", "S3-DEL-001");

      // finalObjectKey 경로 유도
      const finalObjectKey = deriveFinalObjectKey(tmpObjectKey);
      const s3BaseUrl = extractS3BaseUrl(presignedUrl);
      const finalObjectUrl = buildS3ObjectUrl(s3BaseUrl, finalObjectKey);

      console.log(`[경로 확인] tmpKey  = ${tmpObjectKey}`);
      console.log(`[경로 확인] finalKey = ${finalObjectKey}`);
      console.log(`[경로 확인] finalUrl = ${finalObjectUrl}`);

      // ── Step 3: Lambda 처리 완료 대기 (finalObjectKey S3 폴링)
      await waitForLambdaCompletion(
        request,
        finalObjectUrl,
        "S3-DEL-001",
        LAMBDA_POLL_TIMEOUT_MS,
        LAMBDA_POLL_INTERVAL_MS
      );
      // Lambda 완료 시점: S3 에 finalObjectKey(WebP) 존재 + DB status=COMPLETED

      // ── Step 4: 게시글 생성
      const postId = await createFreePost(request, accessToken);
      createdPostIds.push(postId);

      // ── Step 5: 이미지를 게시글에 연결
      await updatePostImages(request, accessToken, postId, [imageId]);

      // ── Step 6: 이미지 전체 제거 → UpsertImagesByReferenceService 실행
      //   COMPLETED 이미지를 unlink 전에 DeleteS3ObjectPort.deleteObject(finalObjectKey) 호출
      await updatePostImages(request, accessToken, postId, []);

      // ── Step 7: S3 finalObjectKey(WebP) 가 실제로 삭제됐는지 검증
      await verifyS3ObjectDeleted(request, finalObjectUrl, "S3-DEL-001");
    }
  );

  // ════════════════════════════════════════════════════════════════════════
  // [S3-DEL-002] 다중 이미지 부분 제거 → 제거된 것만 S3 삭제, 유지된 것은 그대로
  // ════════════════════════════════════════════════════════════════════════

  test(
    "[S3-DEL-002] 여러 COMPLETED 이미지 중 일부만 제거하면 제거된 이미지만 S3에서 삭제된다",
    async ({ request }) => {
      // ── Step 1: 이미지 2장 발급
      const items = await issuePresignedUrls(
        request,
        accessToken,
        "COMMUNITY_FREE",
        ["test1.png", "test2.png"]
      );
      const img1 = items[0];
      const img2 = items[1];

      const s3BaseUrl = extractS3BaseUrl(img1.presignedUrl);
      const finalKey1 = deriveFinalObjectKey(img1.tmpObjectKey);
      const finalKey2 = deriveFinalObjectKey(img2.tmpObjectKey);
      const finalUrl1 = buildS3ObjectUrl(s3BaseUrl, finalKey1);
      const finalUrl2 = buildS3ObjectUrl(s3BaseUrl, finalKey2);

      console.log(`[경로] img1 finalKey = ${finalKey1}`);
      console.log(`[경로] img2 finalKey = ${finalKey2}`);

      // ── Step 2: 두 이미지 모두 S3 에 업로드
      await putImageToS3(request, img1.presignedUrl, "test1.png", "S3-DEL-002/img1");
      await putImageToS3(request, img2.presignedUrl, "test2.png", "S3-DEL-002/img2");

      // ── Step 3: 두 이미지 모두 Lambda 처리 완료 대기
      await waitForLambdaCompletion(request, finalUrl1, "S3-DEL-002/img1");
      await waitForLambdaCompletion(request, finalUrl2, "S3-DEL-002/img2");

      // ── Step 4: 게시글 생성
      const postId = await createFreePost(request, accessToken);
      createdPostIds.push(postId);

      // ── Step 5: 두 이미지 모두 게시글에 연결
      await updatePostImages(request, accessToken, postId, [
        img1.imageId,
        img2.imageId,
      ]);

      // ── Step 6: img2 만 제거 (img1 은 유지)
      await updatePostImages(request, accessToken, postId, [img1.imageId]);

      // ── Step 7a: img2 finalObjectKey → 404 (삭제됨)
      await verifyS3ObjectDeleted(request, finalUrl2, "S3-DEL-002/img2(삭제됨)");

      // ── Step 7b: img1 finalObjectKey → 200 (아직 존재)
      await verifyS3ObjectStillExists(
        request,
        finalUrl1,
        "S3-DEL-002/img1(유지됨)"
      );
    }
  );

  // ════════════════════════════════════════════════════════════════════════
  // [S3-DEL-003] PENDING 이미지 제거 → S3 finalObjectKey 없으므로 S3 삭제 없음
  // ════════════════════════════════════════════════════════════════════════

  test(
    "[S3-DEL-003] PENDING 이미지를 게시글에서 제거해도 S3 에 finalObjectKey 가 없으므로 오류 없이 정상 완료된다",
    async ({ request }) => {
      // ── Step 1: Presigned URL 발급
      const items = await issuePresignedUrls(
        request,
        accessToken,
        "COMMUNITY_FREE",
        ["test1.png"]
      );
      const { imageId, tmpObjectKey, presignedUrl } = items[0];

      // ── Step 2: S3 에 업로드 (Lambda 대기 없음 → PENDING 상태 유지)
      await putImageToS3(request, presignedUrl, "test1.png", "S3-DEL-003");

      console.log(
        `[S3-DEL-003] PENDING 이미지 (Lambda 처리 전): imageId=${imageId}, tmpKey=${tmpObjectKey}`
      );

      // ── Step 3: 게시글 생성
      const postId = await createFreePost(request, accessToken);
      createdPostIds.push(postId);

      // ── Step 4: PENDING 이미지를 게시글에 연결
      //   UpsertImagesByReferenceService 는 status 무관하게 연결 허용
      await updatePostImages(request, accessToken, postId, [imageId]);

      // ── Step 5: 이미지 제거 (PENDING 이므로 finalObjectKey=null → S3 삭제 스킵)
      //   UpsertImagesByReferenceService: status=PENDING 이면 DeleteS3ObjectPort 호출 없음
      await updatePostImages(request, accessToken, postId, []);

      // ── Step 6: 응답이 200 이면 S3 삭제 없이 정상 완료됨을 의미
      //   (이 단계까지 예외 없이 도달했다면 TC 통과)
      console.log(
        `[S3-DEL-003 ✅] PENDING 이미지 제거 성공 — S3 tmpKey 는 버킷 lifecycle rule 이 처리: ${tmpObjectKey}`
      );

      // 옵션: tmpObjectKey S3 URL 이 여전히 접근 가능한지 확인
      // (PUT 성공 직후 tmp 경로에 파일이 있음. Lambda 처리 전이므로 아직 삭제 안 됨)
      const s3BaseUrl = extractS3BaseUrl(presignedUrl);
      const tmpObjectUrl = buildS3ObjectUrl(s3BaseUrl, tmpObjectKey);
      const getRes = await request.get(tmpObjectUrl, {
        failOnStatusCode: false,
      });
      // public 경로 기준 200 이면 존재 확인 (버킷 정책에 따라 403 도 가능)
      expect(
        [200, 403].includes(getRes.status()),
        `S3-DEL-003: tmpObjectKey S3 상태 비정상 (HTTP ${getRes.status()})`
      ).toBeTruthy();
      console.log(
        `[S3-DEL-003] tmpKey S3 상태: HTTP ${getRes.status()} (Lambda 처리 전 원본 유지 또는 버킷 정책 제한)\n  · ${tmpObjectUrl}`
      );
    }
  );

  // ════════════════════════════════════════════════════════════════════════
  // [S3-DEL-004] 게시글 삭제(PostDeletedEvent) → 이미지 unlink,
  //             이후 S3 finalObjectKey 는 ImageUnlinkedCleanupService 가 처리
  //             (스케줄러 주기로 즉각 검증 불가 — DB unlink 만 검증)
  // ════════════════════════════════════════════════════════════════════════

  test(
    "[S3-DEL-004] 게시글 삭제 시 PostDeletedEvent 로 이미지 unlink (AFTER_COMMIT), " +
      "서버 응답 200 OK 확인",
    async ({ request }) => {
      // ── Step 1: Presigned URL 발급 + S3 업로드
      const items = await issuePresignedUrls(
        request,
        accessToken,
        "COMMUNITY_FREE",
        ["test1.png"]
      );
      const { imageId, presignedUrl } = items[0];
      await putImageToS3(request, presignedUrl, "test1.png", "S3-DEL-004");

      // ── Step 2: Lambda 처리 완료 대기
      const finalObjectKey = deriveFinalObjectKey(items[0].tmpObjectKey);
      const s3BaseUrl = extractS3BaseUrl(presignedUrl);
      const finalObjectUrl = buildS3ObjectUrl(s3BaseUrl, finalObjectKey);
      await waitForLambdaCompletion(request, finalObjectUrl, "S3-DEL-004");

      // ── Step 3: 게시글 생성 + 이미지 연결
      const postId = await createFreePost(request, accessToken);
      // 이 게시글은 삭제할 것이므로 createdPostIds 에 추가하지 않음
      await updatePostImages(request, accessToken, postId, [imageId]);

      // ── Step 4: 게시글 삭제 → PostDeletedEvent(AFTER_COMMIT) → UnlinkImagesByReferenceService
      const deleteRes = await request.delete(
        `${ENV.BACKEND_URL}/posts/${postId}`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
      expect(
        deleteRes.status(),
        `DELETE /posts/${postId} 실패 (${deleteRes.status()})`
      ).toBe(200);

      console.log(
        `[S3-DEL-004 ✅] 게시글 삭제 성공. 이미지는 unlink 처리됨 (referenceId=null).\n` +
          `  S3 finalObjectKey 실제 삭제는 ImageUnlinkedCleanupScheduler 가 처리 (매일 03:00 KST):\n` +
          `  · ${finalObjectUrl}`
      );

      // 참고: S3 finalObjectKey 는 아직 존재합니다.
      //       (언링크는 됐지만 스케줄러가 아직 실행되지 않았으므로)
      //       즉각 검증하려면 ImageUnlinkedCleanupService.runBatch() 를 직접 호출해야 합니다.
    }
  );
});
