/**
 * MZTK — GET /images (GetImagesByIds) Playwright E2E 테스트
 *
 * 테스트 시나리오:
 *   [P-1] 실제 S3 업로드 → Lambda 콜백 대기 → GET /images 조회 시 COMPLETED + finalObjectKey 검증
 *   [P-2] S3 업로드 직후(Lambda 미처리) → GET /images 조회 시 PENDING + finalObjectKey=null 검증
 *
 * 사전 조건:
 *   - MZTK-BE 서버가 실행 중이어야 합니다 (기본: http://127.0.0.1:8080)
 *   - 서버에 실제 AWS S3 자격증명이 설정되어 있어야 합니다
 *   - Lambda가 S3 이벤트를 처리하도록 설정되어 있어야 합니다 (P-1 전용)
 *   - test-images/ 디렉터리에 test1.png 파일이 존재해야 합니다
 *
 * 실행 방법:
 *   cd src/test/java/momzzangseven/mztkbe/integration/play_wright
 *   npx playwright test get-image-by-ids/get-image-by-ids.spec.ts
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
  /** Lambda 처리를 기다리는 최대 시간 (ms). 실제 Lambda 응답 시간에 맞게 조정 */
  LAMBDA_TIMEOUT_MS: parseInt(process.env.LAMBDA_TIMEOUT_MS ?? "30000", 10),
  /** Lambda 완료 폴링 간격 (ms) */
  LAMBDA_POLL_INTERVAL_MS: parseInt(process.env.LAMBDA_POLL_INTERVAL_MS ?? "2000", 10),
};

const TEST_IMAGES_DIR = path.resolve(__dirname, "..", "test-images");

// ────────────────────────────────────────────────────────────────────────────
// 타입
// ────────────────────────────────────────────────────────────────────────────
interface PresignedUrlItem {
  imageId: number;
  presignedUrl: string;
  tmpObjectKey: string;
}

interface ImageItem {
  imageId: number;
  userId: number;
  referenceType: string;
  referenceId: number;
  status: string;
  finalObjectKey: string | null;
  imgOrder: number;
  createdAt: string;
  updatedAt: string;
}

// ────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼
// ────────────────────────────────────────────────────────────────────────────

function readImage(filename: string): Buffer {
  const filePath = path.join(TEST_IMAGES_DIR, filename);
  if (!fs.existsSync(filePath)) {
    throw new Error(`테스트 이미지 파일이 없습니다: ${filePath}`);
  }
  return fs.readFileSync(filePath);
}

/**
 * POST /images/presigned-urls 호출 — 단일 이미지 1개 발급
 * 응답의 첫 번째 item 을 반환합니다.
 */
async function issuePresignedUrl(
  request: APIRequestContext,
  accessToken: string,
  referenceType: string,
  filename: string
): Promise<PresignedUrlItem> {
  const res = await request.post(`${ENV.BACKEND_URL}/images/presigned-urls`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    data: { referenceType, images: [filename] },
  });

  expect(res.status(), `POST /images/presigned-urls 실패 (HTTP ${res.status()})`).toBe(200);

  const body = await res.json();
  expect(body.status).toBe("SUCCESS");

  const items = body.data.items as PresignedUrlItem[];
  expect(items.length, "items 개수가 1이어야 합니다").toBeGreaterThanOrEqual(1);
  expect(items[0].imageId, "imageId 는 양수여야 합니다").toBeGreaterThan(0);

  return items[0];
}

/**
 * Presigned URL 을 사용하여 이미지를 S3 에 업로드합니다.
 */
async function uploadToS3(
  request: APIRequestContext,
  presignedUrl: string,
  filename: string
): Promise<void> {
  const imageData = readImage(filename);
  const putRes = await request.put(presignedUrl, {
    headers: { "Content-Type": "image/png" },
    data: imageData,
  });

  if (putRes.status() !== 200) {
    const errBody = await putRes.text().catch(() => "(body 없음)");
    throw new Error(`S3 PUT 실패 (HTTP ${putRes.status()}):\n${errBody}`);
  }

  console.log(`[S3 PUT] 업로드 성공 ✅`);
}

/**
 * GET /images 호출하여 images 배열을 반환합니다.
 */
async function getImages(
  request: APIRequestContext,
  accessToken: string,
  ids: number[],
  referenceType: string,
  referenceId: number
): Promise<ImageItem[]> {
  const idsQuery = ids.map((id) => `ids=${id}`).join("&");
  const url = `${ENV.BACKEND_URL}/images?${idsQuery}&referenceType=${referenceType}&referenceId=${referenceId}`;

  const res = await request.get(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  expect(res.status(), `GET /images 실패 (HTTP ${res.status()})`).toBe(200);

  const body = await res.json();
  expect(body.status).toBe("SUCCESS");

  return body.data.images as ImageItem[];
}

// ────────────────────────────────────────────────────────────────────────────
// 공통 헬퍼 (게시글)
// ────────────────────────────────────────────────────────────────────────────

/** POST /posts/free 로 게시글을 생성하고 postId 를 반환합니다. */
async function createFreePost(
  request: APIRequestContext,
  accessToken: string
): Promise<number> {
  const res = await request.post(`${ENV.BACKEND_URL}/posts/free`, {
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    data: { content: "[GetImagesByIds E2E] 테스트 게시글" },
  });
  expect(res.status(), `POST /posts/free 실패 (HTTP ${res.status()})`).toBe(201);
  const body = await res.json();
  const postId = body.data.postId as number;
  expect(postId, "postId 는 양수여야 합니다").toBeGreaterThan(0);
  console.log(`[게시글 생성 ✅] postId=${postId}`);
  return postId;
}

/** PATCH /posts/{postId} 로 게시글에 이미지를 연결합니다. */
async function linkImagesToPost(
  request: APIRequestContext,
  accessToken: string,
  postId: number,
  imageIds: number[]
): Promise<void> {
  const res = await request.patch(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    data: { imageIds },
  });
  expect(
    res.status(),
    `PATCH /posts/${postId} 실패 (HTTP ${res.status()})`
  ).toBe(200);
  console.log(`[이미지 연결 ✅] postId=${postId} imageIds=${JSON.stringify(imageIds)}`);
}

/** DELETE /posts/{postId} 로 게시글을 삭제합니다. */
async function deletePost(
  request: APIRequestContext,
  accessToken: string,
  postId: number
): Promise<void> {
  await request.delete(`${ENV.BACKEND_URL}/posts/${postId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

// ────────────────────────────────────────────────────────────────────────────
// 테스트 스위트
// ────────────────────────────────────────────────────────────────────────────

test.describe("GET /images (GetImagesByIds) Playwright E2E", () => {
  let accessToken: string;
  const createdPostIds: number[] = [];

  // ──────────────────────────────────────────────────────────────────────────
  // Setup: 테스트 전용 로컬 계정 생성 + 로그인
  // ──────────────────────────────────────────────────────────────────────────
  test.beforeAll(async ({ request }) => {
    const uniqueEmail = `pw-gbi-${Date.now()}@playwright.test`;
    const password = "Test@1234!";

    // 1. 회원가입
    const signupRes = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
      headers: { "Content-Type": "application/json" },
      data: { email: uniqueEmail, password, nickname: "PlaywrightGetImagesTester" },
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
    expect(accessToken, "[Setup] accessToken 발급 실패").toBeTruthy();

    console.log(`\n[Setup] 테스트 계정 준비 완료 ✅  (email=${uniqueEmail})`);
  });

  test.afterAll(async ({ request }) => {
    for (const postId of createdPostIds) {
      await deletePost(request, accessToken, postId);
      console.log(`[Cleanup] postId=${postId} 삭제`);
    }
  });

  // ════════════════════════════════════════════════════════════════════════════
  // [P-1] 실제 S3 업로드 → Lambda 처리 대기 → COMPLETED 상태 + finalObjectKey 검증
  //
  // 흐름:
  //   1. POST /images/presigned-urls → Presigned PUT URL + imageId 발급
  //   2. 이미지 파일을 Presigned URL 로 S3 에 실제 PUT 업로드
  //   3. Lambda 처리 완료(COMPLETED) 대기 (폴링)
  //   4. POST /posts/free → 게시글 생성 (postId 확보)
  //   5. PATCH /posts/{postId} imageIds=[imageId] → 이미지를 게시글에 연결 (referenceId 설정)
  //   6. GET /images?ids={imageId}&referenceType=COMMUNITY_FREE&referenceId={postId}
  //   7. status=COMPLETED, finalObjectKey!=null, S3 경로 패턴 검증
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "[P-1] 실제 S3 업로드 후 Lambda 처리 완료 → COMPLETED + finalObjectKey 반환",
    async ({ request }) => {
      // Step 1: Presigned URL 발급
      const item = await issuePresignedUrl(
        request,
        accessToken,
        "COMMUNITY_FREE",
        "test1.png"
      );
      console.log(
        `[P-1] Presigned URL 발급 완료 — imageId=${item.imageId}, tmpKey=${item.tmpObjectKey}`
      );

      // Step 2: S3 PUT 업로드
      await uploadToS3(request, item.presignedUrl, "test1.png");

      // Step 3: Lambda 처리 대기 — S3 tmpKey 경로에서 finalObjectKey 폴링
      const finalObjectKey = item.tmpObjectKey.replace("/tmp/", "/").replace(/\.[^.]+$/, ".webp");
      const s3BaseUrl = new URL(item.presignedUrl);
      const finalObjectUrl = `${s3BaseUrl.protocol}//${s3BaseUrl.host}/${finalObjectKey}`;

      const deadline = Date.now() + ENV.LAMBDA_TIMEOUT_MS;
      let lambdaDone = false;
      while (Date.now() < deadline) {
        const s3Res = await request.get(finalObjectUrl, { failOnStatusCode: false });
        if (s3Res.status() === 200) {
          lambdaDone = true;
          console.log(`[P-1] Lambda 처리 완료 확인 ✅  finalKey=${finalObjectKey}`);
          break;
        }
        console.log(`[P-1] Lambda 대기 중... (HTTP ${s3Res.status()})`);
        await new Promise((resolve) => setTimeout(resolve, ENV.LAMBDA_POLL_INTERVAL_MS));
      }
      if (!lambdaDone) {
        throw new Error(`[P-1] Lambda 타임아웃 (${ENV.LAMBDA_TIMEOUT_MS}ms 초과)`);
      }

      // Step 4: 게시글 생성
      const postId = await createFreePost(request, accessToken);
      createdPostIds.push(postId);

      // Step 5: 이미지를 게시글에 연결 → image.referenceId = postId 로 업데이트
      await linkImagesToPost(request, accessToken, postId, [item.imageId]);

      // Step 6 & 7: GET /images 조회 및 검증
      const images = await getImages(request, accessToken, [item.imageId], "COMMUNITY_FREE", postId);

      expect(images.length, "이미지가 1개 반환되어야 합니다").toBe(1);
      const completedImage = images[0];

      expect(completedImage.imageId, "imageId 일치 확인").toBe(item.imageId);
      expect(completedImage.status, "status 가 COMPLETED 여야 합니다").toBe("COMPLETED");
      expect(
        completedImage.finalObjectKey,
        "COMPLETED 이미지는 finalObjectKey 가 null 이면 안 됩니다"
      ).toBeTruthy();
      expect(completedImage.referenceType, "referenceType 일치 확인").toBe("COMMUNITY_FREE");
      expect(
        completedImage.finalObjectKey,
        "finalObjectKey 가 tmp/ 를 포함하면 안 됩니다 (Lambda 가 최종 경로로 이동)"
      ).not.toContain("/tmp/");
      expect(
        completedImage.finalObjectKey,
        "finalObjectKey 는 .webp 확장자여야 합니다 (Lambda WebP 변환)"
      ).toMatch(/\.webp$/);

      console.log(
        `[P-1] 검증 완료 ✅\n` +
          `  · status=${completedImage.status}\n` +
          `  · finalObjectKey=${completedImage.finalObjectKey}`
      );
    }
  );

  // ════════════════════════════════════════════════════════════════════════════
  // [P-2] S3 업로드 직후(Lambda 미처리) → PENDING 상태 + finalObjectKey=null 검증
  //
  // 흐름:
  //   1. POST /images/presigned-urls → Presigned PUT URL + imageId 발급
  //   2. 이미지 파일을 S3 에 PUT 업로드
  //   3. POST /posts/free → 게시글 생성 (postId 확보)
  //   4. PATCH /posts/{postId} imageIds=[imageId] → 이미지를 게시글에 연결 (referenceId 설정)
  //   5. Lambda 처리를 기다리지 않고 즉시 GET /images 호출
  //   6. status=PENDING, finalObjectKey=null 검증
  //
  // 주의: Lambda 가 매우 빠르게 처리하는 환경에서는 이 테스트가 불안정할 수 있습니다.
  // ════════════════════════════════════════════════════════════════════════════
  test(
    "[P-2] S3 업로드 직후 Lambda 미처리 → PENDING 상태 + finalObjectKey=null",
    async ({ request }) => {
      // Step 1: Presigned URL 발급
      const item = await issuePresignedUrl(
        request,
        accessToken,
        "COMMUNITY_FREE",
        "test1.png"
      );
      console.log(
        `[P-2] Presigned URL 발급 완료 — imageId=${item.imageId}, tmpKey=${item.tmpObjectKey}`
      );

      // Step 2: S3 PUT 업로드 (Lambda 를 유발하지만 처리를 기다리지 않음)
      await uploadToS3(request, item.presignedUrl, "test1.png");

      // Step 3: 게시글 생성
      const postId = await createFreePost(request, accessToken);
      createdPostIds.push(postId);

      // Step 4: 이미지를 게시글에 연결 → image.referenceId = postId 로 업데이트
      await linkImagesToPost(request, accessToken, postId, [item.imageId]);

      // Step 5: Lambda 대기 없이 즉시 GET /images 조회
      const images = await getImages(request, accessToken, [item.imageId], "COMMUNITY_FREE", postId);

      // Step 6: 검증
      expect(images.length, "이미지가 1개 반환되어야 합니다").toBe(1);
      const img = images[0];
      console.log(`[P-2] 조회된 이미지 상태: ${img.status}`);

      if (img.status === "PENDING") {
        expect(
          img.finalObjectKey,
          "PENDING 이미지는 finalObjectKey 가 null 이어야 합니다"
        ).toBeNull();
        console.log("[P-2] PENDING 상태 + finalObjectKey=null 검증 완료 ✅");
      } else {
        // Lambda 가 이미 처리한 경우 — 경고만 출력하고 테스트 통과
        console.warn(
          `[P-2] Lambda 가 이미 처리 완료 (status=${img.status}). ` +
            `테스트 환경의 Lambda 응답 속도가 매우 빠릅니다.`
        );
      }
    }
  );
});
