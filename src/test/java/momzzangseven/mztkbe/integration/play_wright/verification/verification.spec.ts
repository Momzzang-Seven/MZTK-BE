import { randomUUID } from "crypto";
import { expect, test, APIRequestContext, APIResponse } from "@playwright/test";
import * as dotenv from "dotenv";
import * as fs from "fs";
import * as path from "path";
import { Pool, QueryResultRow } from "pg";

dotenv.config({ path: path.resolve(__dirname, "..", ".env") });

const ENV = {
  BACKEND_URL: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  DATABASE_URL: process.env.DATABASE_URL,
  DB_HOST: process.env.DB_HOST ?? "localhost",
  DB_PORT: Number.parseInt(process.env.DB_PORT ?? "5432", 10),
  DB_NAME: process.env.DB_NAME ?? "mztk_dev",
  DB_USER: process.env.DB_USER ?? process.env.DB_USERNAME ?? "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD ?? "postgres",
  VERIFICATION_PHOTO_APPROVED_IMAGE:
    process.env.VERIFICATION_PHOTO_APPROVED_IMAGE,
  VERIFICATION_RECORD_APPROVED_IMAGE:
    process.env.VERIFICATION_RECORD_APPROVED_IMAGE,
  VERIFICATION_RECORD_REJECTED_IMAGE:
    process.env.VERIFICATION_RECORD_REJECTED_IMAGE,
};

const FIXTURES_DIR = path.resolve(__dirname, "fixtures");
const PLAIN_NO_EXIF_JPEG = path.join(FIXTURES_DIR, "plain-no-exif.jpg");
const LATENCY_REPORT_PATH = path.resolve(
  __dirname,
  "verification-latency-report.md"
);
const INVALID_IMAGE_BYTES = Buffer.from(
  "this-is-not-a-real-jpeg-but-it-has-a-jpg-content-type",
  "utf8"
);

const db =
  ENV.DATABASE_URL != null && ENV.DATABASE_URL !== ""
    ? new Pool({ connectionString: ENV.DATABASE_URL })
    : new Pool({
        host: ENV.DB_HOST,
        port: ENV.DB_PORT,
        database: ENV.DB_NAME,
        user: ENV.DB_USER,
        password: ENV.DB_PASSWORD,
      });

interface ApiEnvelope<T> {
  status: string;
  message?: string | null;
  data?: T;
  code?: string | null;
  retryable?: boolean | null;
}

interface TimedApiResult<T> {
  status: number;
  body: ApiEnvelope<T>;
  elapsedMs: number;
}

interface AuthResult {
  userId: number;
  accessToken: string;
}

interface SignUpResponseData {
  userId: number;
}

interface LoginResponseData {
  accessToken: string;
}

interface PresignedUrlItem {
  presignedUrl: string;
  tmpObjectKey: string;
}

interface PresignedUrlResponseData {
  items: PresignedUrlItem[];
}

interface SubmitVerificationData {
  verificationId: string;
  verificationKind: string;
  verificationStatus: string;
  exerciseDate?: string | null;
  completionStatus: string;
  grantedXp: number;
  completedMethod?: string | null;
  rejectionReasonCode?: string | null;
  rejectionReasonDetail?: string | null;
  failureCode?: string | null;
}

interface VerificationDetailData {
  verificationId: string;
  verificationKind: string;
  verificationStatus: string;
  exerciseDate?: string | null;
  rejectionReasonCode?: string | null;
  rejectionReasonDetail?: string | null;
  failureCode?: string | null;
}

interface LatestVerificationData {
  verificationId: string;
  verificationKind: string;
  verificationStatus: string;
  rejectionReasonCode?: string | null;
  failureCode?: string | null;
}

interface TodayCompletionData {
  todayCompleted: boolean;
  completedMethod?: string | null;
  rewardGrantedToday: boolean;
  grantedXp: number;
  earnedDate: string;
  latestVerification?: LatestVerificationData | null;
}

interface VerificationRow extends QueryResultRow {
  verification_id: string;
  status: string;
  verification_kind: string;
  tmp_object_key: string;
  rejection_reason_code: string | null;
  rejection_reason_detail: string | null;
  failure_code: string | null;
  exercise_date: Date | string | null;
  created_at: Date | string;
  updated_at: Date | string;
}

interface TimingRecord {
  scenario: string;
  endpoint: "photo" | "record";
  tmpObjectKey: string;
  extension: string;
  httpStatus: number;
  apiStatus: string;
  apiCode: string | null;
  verificationStatus: string | null;
  elapsedMs: number;
  recordedAt: string;
}

interface RejectionScenario {
  scenario: string;
  reason: string;
  detail: string;
  exerciseDate?: string;
}

const timingRecords: TimingRecord[] = [];

test.describe.configure({ mode: "serial" });

test.afterAll(async () => {
  writeLatencyReport();
  await db.end();
});

test.describe("Workout Verification Playwright E2E", () => {
  test.describe("Suite A - Precheck and Contract Failures", () => {
    test("TC-V-A-01: invalid tmpObjectKey -> 400 VERIFICATION_001", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "invalid-tmp");

      const response = await submitPhoto(
        request,
        auth.accessToken,
        "public/workout/a.jpg",
        "TC-V-A-01"
      );

      expect(response.status).toBe(400);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_001");
    });

    test("TC-V-A-02: photo endpoint png extension -> 400 VERIFICATION_002", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "photo-ext");

      const response = await submitPhoto(
        request,
        auth.accessToken,
        validTmpObjectKey("png"),
        "TC-V-A-02"
      );

      expect(response.status).toBe(400);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_002");
    });

    test("TC-V-A-03: record endpoint gif extension -> 400 VERIFICATION_002", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "record-ext");

      const response = await submitRecord(
        request,
        auth.accessToken,
        validTmpObjectKey("gif"),
        "TC-V-A-03"
      );

      expect(response.status).toBe(400);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_002");
    });

    test("TC-V-A-04: upload row missing -> 404 VERIFICATION_003", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "missing-upload");

      const response = await submitPhoto(
        request,
        auth.accessToken,
        validTmpObjectKey("jpg"),
        "TC-V-A-04"
      );

      expect(response.status).toBe(404);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_003");
    });

    test("TC-V-A-05: upload row belongs to another user -> 403 VERIFICATION_004", async ({
      request,
    }) => {
      const owner = await signUpAndLogin(request, "forbidden-owner");
      const attacker = await signUpAndLogin(request, "forbidden-attacker");
      const tmpObjectKey = validTmpObjectKey("jpg");

      await insertImageRow(owner.userId, tmpObjectKey, tmpObjectKey);

      const response = await submitPhoto(
        request,
        attacker.accessToken,
        tmpObjectKey,
        "TC-V-A-05"
      );

      expect(response.status).toBe(403);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_004");
    });

    test("TC-V-A-06: existing verification kind mismatch -> 409 VERIFICATION_005", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "kind-mismatch");
      const tmpObjectKey = validTmpObjectKey("jpg");

      await insertVerificationRequest({
        verificationId: randomUUID(),
        userId: auth.userId,
        verificationKind: "WORKOUT_RECORD",
        status: "REJECTED",
        tmpObjectKey,
        rejectionReasonCode: "DATE_MISMATCH",
        rejectionReasonDetail: "visible date is not today",
      });

      const response = await submitPhoto(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-A-06"
      );

      expect(response.status).toBe(409);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_005");
    });

    test("TC-V-A-07: workout reward already exists today -> 409 VERIFICATION_006", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "already-completed");
      await insertWorkoutXpLedger(
        auth.userId,
        todayKst(),
        "workout-record-verification:already-completed"
      );

      const response = await submitPhoto(
        request,
        auth.accessToken,
        validTmpObjectKey("jpg"),
        "TC-V-A-07"
      );
      const conflictData = response.body.data as
        | { completedMethod?: string | null; earnedDate?: string | null }
        | undefined;

      expect(response.status).toBe(409);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_006");
      expect(conflictData?.completedMethod).toBe("WORKOUT_RECORD");
      expect(conflictData?.earnedDate).toBe(todayKst());
    });

    test("TC-V-A-08: verification detail not found -> 404 VERIFICATION_007", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "detail-not-found");

      const response = await getVerificationDetail(
        request,
        auth.accessToken,
        randomUUID()
      );

      expect(response.status).toBe(404);
      expect(response.body.status).toBe("FAIL");
      expect(response.body.code).toBe("VERIFICATION_007");
    });

    const photoAcceptedFormats = ["jpg", "jpeg", "heif", "heic"] as const;
    for (const extension of photoAcceptedFormats) {
      test(`TC-V-A-P-${extension}: photo final response body for accepted format ${extension}`, async ({
        request,
      }) => {
        const auth = await signUpAndLogin(
          request,
          `photo-format-final-${extension}`
        );
        const tmpObjectKey = validTmpObjectKey(extension);
        const verificationId = randomUUID();

        await insertVerificationRequest({
          verificationId,
          userId: auth.userId,
          verificationKind: "WORKOUT_PHOTO",
          status: "REJECTED",
          tmpObjectKey,
          rejectionReasonCode: "SCREEN_OR_UI",
          rejectionReasonDetail: `final response body should be stable for ${extension}`,
        });

        const response = await submitPhoto(
          request,
          auth.accessToken,
          tmpObjectKey,
          `TC-V-A-P-${extension}`
        );

        assertRejectedPhotoSubmitResponse(response, {
          verificationId,
          rejectionReasonCode: "SCREEN_OR_UI",
          rejectionReasonDetail: `final response body should be stable for ${extension}`,
        });
      });
    }

    const recordAcceptedFormats = [
      "jpg",
      "jpeg",
      "png",
      "heif",
      "heic",
    ] as const;
    for (const extension of recordAcceptedFormats) {
      test(`TC-V-A-R-${extension}: record final response body for accepted format ${extension}`, async ({
        request,
      }) => {
        const auth = await signUpAndLogin(
          request,
          `record-format-final-${extension}`
        );
        const tmpObjectKey = validTmpObjectKey(extension);
        const verificationId = randomUUID();
        const exerciseDate = daysAgoKst(1);

        await insertVerificationRequest({
          verificationId,
          userId: auth.userId,
          verificationKind: "WORKOUT_RECORD",
          status: "REJECTED",
          tmpObjectKey,
          rejectionReasonCode: "DATE_MISMATCH",
          rejectionReasonDetail: `record final response body should be stable for ${extension}`,
          exerciseDate,
        });

        const response = await submitRecord(
          request,
          auth.accessToken,
          tmpObjectKey,
          `TC-V-A-R-${extension}`
        );

        assertRejectedRecordSubmitResponse(response, {
          verificationId,
          rejectionReasonCode: "DATE_MISMATCH",
          rejectionReasonDetail: `record final response body should be stable for ${extension}`,
          exerciseDate,
        });
      });
    }
  });

  test.describe("Suite B - Existing Row Reuse and Read Models", () => {
    const reusableStates = [
      {
        title: "PENDING row is returned as-is",
        code: "TC-V-B-01",
        status: "PENDING",
      },
      {
        title: "ANALYZING row is returned as-is",
        code: "TC-V-B-02",
        status: "ANALYZING",
      },
      {
        title: "REJECTED row is returned as-is",
        code: "TC-V-B-03",
        status: "REJECTED",
      },
      {
        title: "VERIFIED row is returned as-is",
        code: "TC-V-B-04",
        status: "VERIFIED",
      },
    ] as const;

    for (const scenario of reusableStates) {
      test(`${scenario.code}: ${scenario.title}`, async ({ request }) => {
        const auth = await signUpAndLogin(request, scenario.code.toLowerCase());
        const tmpObjectKey = validTmpObjectKey("jpg");
        const verificationId = randomUUID();

        await insertVerificationRequest({
          verificationId,
          userId: auth.userId,
          verificationKind: "WORKOUT_PHOTO",
          status: scenario.status,
          tmpObjectKey,
          rejectionReasonCode:
            scenario.status === "REJECTED" ? "MISSING_EXIF_METADATA" : undefined,
          rejectionReasonDetail:
            scenario.status === "REJECTED" ? "EXIF metadata is required" : undefined,
        });

        const response = await submitPhoto(
          request,
          auth.accessToken,
          tmpObjectKey,
          scenario.code
        );

        expect(response.status).toBe(200);
        expect(response.body.status).toBe("SUCCESS");
        expect(requireData(response.body).verificationId).toBe(verificationId);
        expect(requireData(response.body).verificationStatus).toBe(scenario.status);
      });
    }

    test("TC-V-B-05: old FAILED row is returned without retry", async ({ request }) => {
      const auth = await signUpAndLogin(request, "old-failed");
      const tmpObjectKey = validTmpObjectKey("jpg");
      const verificationId = randomUUID();

      await insertVerificationRequest({
        verificationId,
        userId: auth.userId,
        verificationKind: "WORKOUT_PHOTO",
        status: "FAILED",
        tmpObjectKey,
        failureCode: "EXTERNAL_AI_UNAVAILABLE",
        createdAt: daysAgoUtc(2),
        updatedAt: minutesAgoUtc(5),
      });

      const response = await submitPhoto(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-B-05"
      );

      expect(response.status).toBe(200);
      expect(response.body.status).toBe("SUCCESS");
      expect(requireData(response.body).verificationId).toBe(verificationId);
      expect(requireData(response.body).verificationStatus).toBe("FAILED");
      expect(requireData(response.body).failureCode).toBe("EXTERNAL_AI_UNAVAILABLE");
      expect(requireData(response.body).completedMethod).toBeUndefined();
    });

    test("TC-V-B-06: GET today completion returns reward and latest verification", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "today-completion");
      const verificationId = randomUUID();
      const tmpObjectKey = validTmpObjectKey("png");

      await insertVerificationRequest({
        verificationId,
        userId: auth.userId,
        verificationKind: "WORKOUT_RECORD",
        status: "REJECTED",
        tmpObjectKey,
        rejectionReasonCode: "DATE_MISMATCH",
        rejectionReasonDetail: "visible date is not today",
        exerciseDate: todayKst(),
      });
      await insertWorkoutXpLedger(
        auth.userId,
        todayKst(),
        `workout-record-verification:${verificationId}`
      );

      const response = await getTodayCompletion(request, auth.accessToken);

      expect(response.status).toBe(200);
      expect(response.body.status).toBe("SUCCESS");
      expect(requireData(response.body).todayCompleted).toBe(true);
      expect(requireData(response.body).completedMethod).toBe("WORKOUT_RECORD");
      expect(requireData(response.body).rewardGrantedToday).toBe(true);
      expect(requireData(response.body).latestVerification?.verificationId).toBe(verificationId);
      expect(requireData(response.body).latestVerification?.verificationStatus).toBe("REJECTED");
    });

    test("TC-V-B-07: GET today completion returns empty state when nothing happened", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "today-empty");

      const response = await getTodayCompletion(request, auth.accessToken);

      expect(response.status).toBe(200);
      expect(response.body.status).toBe("SUCCESS");
      expect(requireData(response.body).todayCompleted).toBe(false);
      expect(requireData(response.body).rewardGrantedToday).toBe(false);
      expect(requireData(response.body).latestVerification).toBeNull();
    });
  });

  test.describe("Suite C - Rejection Final Response Matrix", () => {
    const photoRejections: RejectionScenario[] = [
      {
        scenario: "TC-V-C-P-01",
        reason: "MISSING_EXIF_METADATA",
        detail: "EXIF metadata is required",
      },
      {
        scenario: "TC-V-C-P-02",
        reason: "EXIF_DATE_MISMATCH",
        detail: "EXIF shot date must be today in KST",
        exerciseDate: daysAgoKst(1),
      },
      {
        scenario: "TC-V-C-P-03",
        reason: "SCREEN_OR_UI",
        detail: "image looks like a screen capture or UI",
      },
      {
        scenario: "TC-V-C-P-04",
        reason: "NO_PERSON_VISIBLE",
        detail: "no person is visible in the workout photo",
      },
      {
        scenario: "TC-V-C-P-05",
        reason: "EQUIPMENT_ONLY",
        detail: "image contains equipment only",
      },
      {
        scenario: "TC-V-C-P-06",
        reason: "INSUFFICIENT_WORKOUT_CONTEXT",
        detail: "workout context is insufficient",
      },
      {
        scenario: "TC-V-C-P-07",
        reason: "LOW_CONFIDENCE",
        detail: "AI confidence is below threshold",
      },
    ] as const;

    for (const scenario of photoRejections) {
      test(`${scenario.scenario}: photo rejection reason ${scenario.reason}`, async ({
        request,
      }) => {
        const auth = await signUpAndLogin(request, scenario.scenario.toLowerCase());
        const tmpObjectKey = validTmpObjectKey("jpg");
        const verificationId = randomUUID();

        await insertVerificationRequest({
          verificationId,
          userId: auth.userId,
          verificationKind: "WORKOUT_PHOTO",
          status: "REJECTED",
          tmpObjectKey,
          rejectionReasonCode: scenario.reason,
          rejectionReasonDetail: scenario.detail,
          exerciseDate: scenario.exerciseDate,
        });

        const response = await submitPhoto(
          request,
          auth.accessToken,
          tmpObjectKey,
          scenario.scenario
        );

        assertRejectedPhotoSubmitResponse(response, {
          verificationId,
          rejectionReasonCode: scenario.reason,
          rejectionReasonDetail: scenario.detail,
        });

        const detailResponse = await getVerificationDetail(
          request,
          auth.accessToken,
          verificationId
        );
        const detailData = requireData(detailResponse.body);

        expect(detailResponse.status).toBe(200);
        expect(detailData.verificationStatus).toBe("REJECTED");
        expect(detailData.rejectionReasonCode).toBe(scenario.reason);
        expect(detailData.rejectionReasonDetail).toBe(scenario.detail);
        assertOptionalDate(detailData.exerciseDate, scenario.exerciseDate);
      });
    }

    const recordRejections: RejectionScenario[] = [
      {
        scenario: "TC-V-C-R-01",
        reason: "NOT_WORKOUT_RECORD",
        detail: "image is not a workout record",
      },
      {
        scenario: "TC-V-C-R-02",
        reason: "DATE_NOT_VISIBLE",
        detail: "workout record date is not visible",
      },
      {
        scenario: "TC-V-C-R-03",
        reason: "DATE_MISMATCH",
        detail: "visible date does not match today in KST",
        exerciseDate: daysAgoKst(1),
      },
      {
        scenario: "TC-V-C-R-04",
        reason: "LOW_CONFIDENCE",
        detail: "AI confidence is below threshold",
      },
    ] as const;

    for (const scenario of recordRejections) {
      test(`${scenario.scenario}: record rejection reason ${scenario.reason}`, async ({
        request,
      }) => {
        const auth = await signUpAndLogin(request, scenario.scenario.toLowerCase());
        const tmpObjectKey = validTmpObjectKey("png");
        const verificationId = randomUUID();

        await insertVerificationRequest({
          verificationId,
          userId: auth.userId,
          verificationKind: "WORKOUT_RECORD",
          status: "REJECTED",
          tmpObjectKey,
          rejectionReasonCode: scenario.reason,
          rejectionReasonDetail: scenario.detail,
          exerciseDate: scenario.exerciseDate,
        });

        const response = await submitRecord(
          request,
          auth.accessToken,
          tmpObjectKey,
          scenario.scenario
        );

        assertRejectedRecordSubmitResponse(response, {
          verificationId,
          rejectionReasonCode: scenario.reason,
          rejectionReasonDetail: scenario.detail,
          exerciseDate: scenario.exerciseDate,
        });

        const detailResponse = await getVerificationDetail(
          request,
          auth.accessToken,
          verificationId
        );
        const detailData = requireData(detailResponse.body);

        expect(detailResponse.status).toBe(200);
        expect(detailData.verificationStatus).toBe("REJECTED");
        expect(detailData.rejectionReasonCode).toBe(scenario.reason);
        expect(detailData.rejectionReasonDetail).toBe(scenario.detail);
        assertOptionalDate(detailData.exerciseDate, scenario.exerciseDate);
      });
    }
  });

  test.describe("Suite D - Real Upload Flow with Deterministic Outcomes", () => {
    test("TC-V-C-01: workout photo without usable EXIF -> REJECTED MISSING_EXIF_METADATA", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "photo-missing-exif");
      const tmpObjectKey = await uploadWorkoutFile(
        request,
        auth.accessToken,
        "photo-missing-exif.jpg",
        fs.readFileSync(PLAIN_NO_EXIF_JPEG)
      );

      const response = await submitPhoto(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-D-01"
      );
      const submitData = requireData(response.body);

      expect(response.status).toBe(200);
      expect(response.body.status).toBe("SUCCESS");
      assertRejectedPhotoSubmitResponse(response, {
        verificationId: submitData.verificationId,
        rejectionReasonCode: "MISSING_EXIF_METADATA",
        rejectionReasonDetail: "EXIF metadata is required",
      });

      const detailResponse = await getVerificationDetail(
        request,
        auth.accessToken,
        submitData.verificationId
      );
      const detailData = requireData(detailResponse.body);

      expect(detailResponse.status).toBe(200);
      expect(detailData.verificationStatus).toBe("REJECTED");
      expect(detailData.rejectionReasonCode).toBe("MISSING_EXIF_METADATA");
    });

    test("TC-V-C-02: invalid jpg bytes -> FAILED ANALYSIS_IMAGE_GENERATION_FAILED", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "record-invalid-bytes");
      const tmpObjectKey = await uploadWorkoutBuffer(
        request,
        auth.accessToken,
        "record-invalid-bytes.jpg",
        INVALID_IMAGE_BYTES,
        "image/jpeg"
      );

      const response = await submitRecord(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-D-02"
      );
      const submitData = requireData(response.body);

      expect(response.status).toBe(200);
      expect(response.body.status).toBe("SUCCESS");
      expect(submitData.verificationStatus).toBe("FAILED");
      expect(submitData.failureCode).toBe("ANALYSIS_IMAGE_GENERATION_FAILED");
      expect(submitData.completedMethod).toBeUndefined();
    });

    test("TC-V-C-03: duplicate photo submit converges to one verification row", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "duplicate-photo");
      const tmpObjectKey = await uploadWorkoutFile(
        request,
        auth.accessToken,
        "duplicate-photo.jpg",
        fs.readFileSync(PLAIN_NO_EXIF_JPEG)
      );

      const [first, second] = await Promise.all([
        submitPhoto(request, auth.accessToken, tmpObjectKey, "TC-V-D-03#1"),
        submitPhoto(request, auth.accessToken, tmpObjectKey, "TC-V-D-03#2"),
      ]);

      const firstData = requireData(first.body);
      const secondData = requireData(second.body);
      const terminal = await waitForTerminalVerification(
        request,
        auth.accessToken,
        firstData.verificationId
      );

      expect(first.status).toBe(200);
      expect(second.status).toBe(200);
      expect(firstData.verificationId).toBe(secondData.verificationId);
      expect(["ANALYZING", "REJECTED"]).toContain(firstData.verificationStatus);
      expect(["ANALYZING", "REJECTED"]).toContain(secondData.verificationStatus);
      expect(terminal.verificationStatus).toBe("REJECTED");
      expect(terminal.rejectionReasonCode).toBe("MISSING_EXIF_METADATA");
      expect(await countVerificationRowsByTmpObjectKey(tmpObjectKey)).toBe(1);
      expect(await countWorkoutXpLedgers(auth.userId)).toBe(0);
    });

    test("TC-V-C-04: today FAILED row retries same verificationId", async ({
      request,
    }) => {
      const auth = await signUpAndLogin(request, "failed-retry");
      const tmpObjectKey = await uploadWorkoutBuffer(
        request,
        auth.accessToken,
        "failed-retry.jpg",
        INVALID_IMAGE_BYTES,
        "image/jpeg"
      );
      const verificationId = randomUUID();

      await insertVerificationRequest({
        verificationId,
        userId: auth.userId,
        verificationKind: "WORKOUT_RECORD",
        status: "FAILED",
        tmpObjectKey,
        failureCode: "EXTERNAL_AI_UNAVAILABLE",
        createdAt: minutesAgoUtc(10),
        updatedAt: minutesAgoUtc(10),
      });
      const response = await submitRecord(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-D-04"
      );
      const after = await getVerificationRow(verificationId);

      expect(response.status).toBe(200);
      expect(requireData(response.body).verificationId).toBe(verificationId);
      expect(requireData(response.body).verificationStatus).toBe("FAILED");
      expect(requireData(response.body).failureCode).toBe("ANALYSIS_IMAGE_GENERATION_FAILED");
      expect(after.status).toBe("FAILED");
      expect(after.failure_code).toBe("ANALYSIS_IMAGE_GENERATION_FAILED");
      expect(await countVerificationRowsByTmpObjectKey(tmpObjectKey)).toBe(1);
    });
  });

  test.describe("Suite E - Optional Asset-Driven Real AI Cases", () => {
    test("TC-V-D-01: approved workout photo asset -> VERIFIED + XP granted", async ({
      request,
    }) => {
      const assetPath = resolveOptionalAsset(
        ENV.VERIFICATION_PHOTO_APPROVED_IMAGE,
        "VERIFICATION_PHOTO_APPROVED_IMAGE"
      );
      test.skip(
        assetPath == null,
        "today KST EXIF가 포함된 승인용 운동 사진 자산이 필요합니다."
      );
      if (assetPath == null) {
        return;
      }

      await ensureWorkoutXpPolicy();
      const auth = await signUpAndLogin(request, "photo-approved");
      const tmpObjectKey = await uploadWorkoutFile(
        request,
        auth.accessToken,
        path.basename(assetPath),
        fs.readFileSync(assetPath)
      );

      const response = await submitPhoto(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-E-01"
      );
      const submitData = requireData(response.body);

      expect(response.status).toBe(200);
      expect(submitData.verificationStatus).toBe("VERIFIED");
      expect(submitData.completedMethod).toBe("WORKOUT_PHOTO");
      expect(submitData.grantedXp).toBeGreaterThan(0);
      expect(submitData.exerciseDate).toBeUndefined();

      const todayResponse = await getTodayCompletion(request, auth.accessToken);
      expect(requireData(todayResponse.body).todayCompleted).toBe(true);
      expect(requireData(todayResponse.body).completedMethod).toBe("WORKOUT_PHOTO");
    });

    test("TC-V-D-02: approved workout record asset -> VERIFIED with exerciseDate=today", async ({
      request,
    }) => {
      const assetPath = resolveOptionalAsset(
        ENV.VERIFICATION_RECORD_APPROVED_IMAGE,
        "VERIFICATION_RECORD_APPROVED_IMAGE"
      );
      test.skip(
        assetPath == null,
        "오늘 날짜가 보이는 승인용 운동 기록 자산이 필요합니다."
      );
      if (assetPath == null) {
        return;
      }

      await ensureWorkoutXpPolicy();
      const auth = await signUpAndLogin(request, "record-approved");
      const tmpObjectKey = await uploadWorkoutFile(
        request,
        auth.accessToken,
        path.basename(assetPath),
        fs.readFileSync(assetPath)
      );

      const response = await submitRecord(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-E-02"
      );
      const submitData = requireData(response.body);

      expect(response.status).toBe(200);
      expect(submitData.verificationStatus).toBe("VERIFIED");
      expect(submitData.completedMethod).toBe("WORKOUT_RECORD");
      expect(submitData.grantedXp).toBeGreaterThan(0);
      expect(submitData.exerciseDate).toBe(todayKst());
    });

    test("TC-V-D-03: rejected workout record asset -> REJECTED with reason code", async ({
      request,
    }) => {
      const assetPath = resolveOptionalAsset(
        ENV.VERIFICATION_RECORD_REJECTED_IMAGE,
        "VERIFICATION_RECORD_REJECTED_IMAGE"
      );
      test.skip(
        assetPath == null,
        "거절이 재현되는 운동 기록 자산이 필요합니다."
      );
      if (assetPath == null) {
        return;
      }

      const auth = await signUpAndLogin(request, "record-rejected");
      const tmpObjectKey = await uploadWorkoutFile(
        request,
        auth.accessToken,
        path.basename(assetPath),
        fs.readFileSync(assetPath)
      );

      const response = await submitRecord(
        request,
        auth.accessToken,
        tmpObjectKey,
        "TC-V-E-03"
      );
      const submitData = requireData(response.body);

      expect(response.status).toBe(200);
      expect(submitData.verificationStatus).toBe("REJECTED");
      expect(submitData.rejectionReasonCode).toBeTruthy();
      expect(submitData.failureCode).toBeUndefined();
    });
  });
});

function validTmpObjectKey(extension: string): string {
  return `private/workout/${randomUUID()}.${extension}`;
}

function todayKst(): string {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(new Date());
}

function daysAgoKst(days: number): string {
  const now = new Date();
  now.setUTCDate(now.getUTCDate() - days);
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(now);
}

function minutesAgoUtc(minutes: number): Date {
  return new Date(Date.now() - minutes * 60_000);
}

function daysAgoUtc(days: number): Date {
  return new Date(Date.now() - days * 24 * 60 * 60_000);
}

function toEpochMillis(value: Date | string): number {
  return value instanceof Date ? value.getTime() : new Date(value).getTime();
}

function resolveOptionalAsset(
  filePath: string | undefined,
  envName: string
): string | null {
  if (filePath == null || filePath.trim() === "") {
    return null;
  }
  if (!fs.existsSync(filePath)) {
    throw new Error(`${envName} points to a missing file: ${filePath}`);
  }
  return filePath;
}

function detectContentType(filename: string): string {
  const extension = path.extname(filename).toLowerCase();
  switch (extension) {
    case ".png":
      return "image/png";
    case ".gif":
      return "image/gif";
    case ".heic":
      return "image/heic";
    case ".heif":
      return "image/heif";
    default:
      return "image/jpeg";
  }
}

function requireData<T>(body: ApiEnvelope<T>): T {
  if (body.data == null) {
    throw new Error(`API response data is missing: ${JSON.stringify(body)}`);
  }
  return body.data;
}

async function parseApiResponse<T>(
  response: APIResponse
): Promise<{ status: number; body: ApiEnvelope<T> }> {
  return {
    status: response.status(),
    body: (await response.json()) as ApiEnvelope<T>,
  };
}

async function signUpAndLogin(
  request: APIRequestContext,
  label: string
): Promise<AuthResult> {
  const compactLabel = label.replace(/[^a-z0-9]/gi, "").toLowerCase().slice(0, 8);
  const randomToken = randomUUID().replace(/-/g, "").slice(0, 12);
  const email = `pwv-${compactLabel}-${randomToken}@example.com`;
  const password = "Test@1234!";
  const nickname = `veri-${compactLabel}-${randomToken.slice(0, 4)}`.slice(0, 20);

  const signUpResponse = await request.post(`${ENV.BACKEND_URL}/auth/signup`, {
    headers: { "Content-Type": "application/json" },
    data: { email, password, nickname },
  });
  const signUp = await parseApiResponse<SignUpResponseData>(signUpResponse);
  expect(signUp.status).toBe(200);
  expect(signUp.body.status).toBe("SUCCESS");

  const loginResponse = await request.post(`${ENV.BACKEND_URL}/auth/login`, {
    headers: { "Content-Type": "application/json" },
    data: { provider: "LOCAL", email, password },
  });
  const login = await parseApiResponse<LoginResponseData>(loginResponse);
  expect(login.status).toBe(200);
  expect(login.body.status).toBe("SUCCESS");

  return {
    userId: requireData(signUp.body).userId,
    accessToken: requireData(login.body).accessToken,
  };
}

async function submitPhoto(
  request: APIRequestContext,
  accessToken: string,
  tmpObjectKey: string,
  scenario = "unlabeled"
): Promise<TimedApiResult<SubmitVerificationData>> {
  const startedAt = Date.now();
  const response = await request.post(
    `${ENV.BACKEND_URL}/verification/photo`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      data: { tmpObjectKey },
      failOnStatusCode: false,
    }
  );
  const parsed = await parseApiResponse<SubmitVerificationData>(response);
  const elapsedMs = Date.now() - startedAt;
  recordTiming(scenario, "photo", tmpObjectKey, parsed, elapsedMs);
  console.log(
    `[verification][photo] submit ${tmpObjectKey} -> ${parsed.status} in ${elapsedMs}ms`
  );
  return { ...parsed, elapsedMs };
}

async function submitRecord(
  request: APIRequestContext,
  accessToken: string,
  tmpObjectKey: string,
  scenario = "unlabeled"
): Promise<TimedApiResult<SubmitVerificationData>> {
  const startedAt = Date.now();
  const response = await request.post(
    `${ENV.BACKEND_URL}/verification/record`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      data: { tmpObjectKey },
      failOnStatusCode: false,
    }
  );
  const parsed = await parseApiResponse<SubmitVerificationData>(response);
  const elapsedMs = Date.now() - startedAt;
  recordTiming(scenario, "record", tmpObjectKey, parsed, elapsedMs);
  console.log(
    `[verification][record] submit ${tmpObjectKey} -> ${parsed.status} in ${elapsedMs}ms`
  );
  return { ...parsed, elapsedMs };
}

async function getVerificationDetail(
  request: APIRequestContext,
  accessToken: string,
  verificationId: string
): Promise<{ status: number; body: ApiEnvelope<VerificationDetailData> }> {
  const response = await request.get(
    `${ENV.BACKEND_URL}/verification/${verificationId}`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      failOnStatusCode: false,
    }
  );
  return parseApiResponse<VerificationDetailData>(response);
}

async function getTodayCompletion(
  request: APIRequestContext,
  accessToken: string
): Promise<{ status: number; body: ApiEnvelope<TodayCompletionData> }> {
  const response = await request.get(
    `${ENV.BACKEND_URL}/verification/today-completion`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      failOnStatusCode: false,
    }
  );
  return parseApiResponse<TodayCompletionData>(response);
}

async function issueWorkoutPresignedUrl(
  request: APIRequestContext,
  accessToken: string,
  filename: string
): Promise<PresignedUrlItem> {
  const response = await request.post(`${ENV.BACKEND_URL}/images/presigned-urls`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: {
      referenceType: "WORKOUT",
      images: [filename],
    },
  });
  const body = await parseApiResponse<PresignedUrlResponseData>(response);
  expect(body.status).toBe(200);
  expect(body.body.status).toBe("SUCCESS");
  expect(requireData(body.body).items).toHaveLength(1);
  return requireData(body.body).items[0];
}

async function uploadWorkoutFile(
  request: APIRequestContext,
  accessToken: string,
  filename: string,
  bytes: Buffer
): Promise<string> {
  const item = await issueWorkoutPresignedUrl(request, accessToken, filename);
  await putPresignedObject(
    request,
    item.presignedUrl,
    bytes,
    detectContentType(filename)
  );
  return item.tmpObjectKey;
}

async function uploadWorkoutBuffer(
  request: APIRequestContext,
  accessToken: string,
  filename: string,
  bytes: Buffer,
  contentType: string
): Promise<string> {
  const item = await issueWorkoutPresignedUrl(request, accessToken, filename);
  await putPresignedObject(request, item.presignedUrl, bytes, contentType);
  return item.tmpObjectKey;
}

async function putPresignedObject(
  request: APIRequestContext,
  presignedUrl: string,
  bytes: Buffer,
  contentType: string
): Promise<void> {
  const response = await request.put(presignedUrl, {
    headers: { "Content-Type": contentType },
    data: bytes,
    failOnStatusCode: false,
  });
  const responseText = await response.text();
  expect(
    response.status(),
    `Presigned PUT failed with body: ${responseText}`
  ).toBe(200);
}

async function ensureWorkoutXpPolicy(): Promise<void> {
  await db.query(
    `
      INSERT INTO xp_policies (
        type,
        xp_amount,
        daily_cap,
        effective_from,
        effective_to,
        enabled,
        created_at
      )
      SELECT 'WORKOUT', 100, 1, NOW() - INTERVAL '1 day', NULL, TRUE, NOW()
      WHERE NOT EXISTS (
        SELECT 1
          FROM xp_policies
         WHERE type = 'WORKOUT'
           AND enabled = TRUE
           AND effective_from <= NOW()
           AND (effective_to IS NULL OR effective_to >= NOW())
      )
    `
  );
}

async function insertImageRow(
  userId: number,
  tmpObjectKey: string,
  finalObjectKey: string | null
): Promise<void> {
  await db.query(
    `
      INSERT INTO images (
        user_id,
        reference_type,
        status,
        tmp_object_key,
        final_object_key,
        created_at,
        updated_at
      )
      VALUES ($1, 'WORKOUT', 'COMPLETED', $2, $3, NOW(), NOW())
    `,
    [userId, tmpObjectKey, finalObjectKey]
  );
}

interface VerificationInsertParams {
  verificationId: string;
  userId: number;
  verificationKind: "WORKOUT_PHOTO" | "WORKOUT_RECORD";
  status: "PENDING" | "ANALYZING" | "REJECTED" | "VERIFIED" | "FAILED";
  tmpObjectKey: string;
  exerciseDate?: string;
  rejectionReasonCode?: string;
  rejectionReasonDetail?: string;
  failureCode?: string;
  createdAt?: Date;
  updatedAt?: Date;
}

async function insertVerificationRequest(
  params: VerificationInsertParams
): Promise<void> {
  await db.query(
    `
      INSERT INTO verification_requests (
        verification_id,
        user_id,
        verification_kind,
        status,
        exercise_date,
        shot_at_kst,
        tmp_object_key,
        rejection_reason_code,
        rejection_reason_detail,
        failure_code,
        created_at,
        updated_at
      )
      VALUES (
        $1,
        $2,
        $3,
        $4,
        $5,
        NULL,
        $6,
        $7,
        $8,
        $9,
        $10,
        $11
      )
    `,
    [
      params.verificationId,
      params.userId,
      params.verificationKind,
      params.status,
      params.exerciseDate ?? null,
      params.tmpObjectKey,
      params.rejectionReasonCode ?? null,
      params.rejectionReasonDetail ?? null,
      params.failureCode ?? null,
      params.createdAt ?? new Date(),
      params.updatedAt ?? new Date(),
    ]
  );
}

async function insertWorkoutXpLedger(
  userId: number,
  earnedOn: string,
  sourceRef: string
): Promise<void> {
  await ensureWorkoutXpPolicy();
  await db.query(
    `
      INSERT INTO xp_ledger (
        user_id,
        type,
        xp_amount,
        earned_on,
        occurred_at,
        idempotency_key,
        source_ref,
        created_at
      )
      VALUES (
        $1,
        'WORKOUT',
        100,
        $2,
        NOW(),
        $3,
        $4,
        NOW()
      )
    `,
    [userId, earnedOn, `pw-verification-${randomUUID()}`, sourceRef]
  );
}

async function countVerificationRowsByTmpObjectKey(
  tmpObjectKey: string
): Promise<number> {
  const result = await db.query<{ count: string }>(
    `SELECT COUNT(*)::text AS count FROM verification_requests WHERE tmp_object_key = $1`,
    [tmpObjectKey]
  );
  return Number.parseInt(result.rows[0].count, 10);
}

async function countWorkoutXpLedgers(userId: number): Promise<number> {
  const result = await db.query<{ count: string }>(
    `SELECT COUNT(*)::text AS count FROM xp_ledger WHERE user_id = $1 AND type = 'WORKOUT'`,
    [userId]
  );
  return Number.parseInt(result.rows[0].count, 10);
}

async function getVerificationRow(
  verificationId: string
): Promise<VerificationRow> {
  const result = await db.query<VerificationRow>(
    `
      SELECT verification_id,
             status,
             verification_kind,
             tmp_object_key,
             rejection_reason_code,
             rejection_reason_detail,
             failure_code,
             exercise_date,
             created_at,
             updated_at
        FROM verification_requests
       WHERE verification_id = $1
    `,
    [verificationId]
  );
  if (result.rows.length !== 1) {
    throw new Error(`verification row not found: ${verificationId}`);
  }
  return result.rows[0];
}

function assertRejectedPhotoSubmitResponse(
  response: TimedApiResult<SubmitVerificationData>,
  expected: {
    verificationId: string;
    rejectionReasonCode: string;
    rejectionReasonDetail: string;
  }
): void {
  const data = requireData(response.body);

  expect(response.status).toBe(200);
  expect(response.body.status).toBe("SUCCESS");
  expect(data.verificationId).toBe(expected.verificationId);
  expect(data.verificationKind).toBe("WORKOUT_PHOTO");
  expect(data.verificationStatus).toBe("REJECTED");
  expect(data.exerciseDate).toBeUndefined();
  expect(data.completionStatus).toBe("NOT_COMPLETED");
  expect(data.grantedXp).toBe(0);
  expect(data.completedMethod).toBeUndefined();
  expect(data.rejectionReasonCode).toBe(expected.rejectionReasonCode);
  expect(data.rejectionReasonDetail).toBe(expected.rejectionReasonDetail);
  expect(data.failureCode).toBeUndefined();
}

function assertRejectedRecordSubmitResponse(
  response: TimedApiResult<SubmitVerificationData>,
  expected: {
    verificationId: string;
    rejectionReasonCode: string;
    rejectionReasonDetail: string;
    exerciseDate?: string;
  }
): void {
  const data = requireData(response.body);

  expect(response.status).toBe(200);
  expect(response.body.status).toBe("SUCCESS");
  expect(data.verificationId).toBe(expected.verificationId);
  expect(data.verificationKind).toBe("WORKOUT_RECORD");
  expect(data.verificationStatus).toBe("REJECTED");
  if (expected.exerciseDate == null) {
    expect(data.exerciseDate).toBeUndefined();
  } else {
    expect(data.exerciseDate).toBe(expected.exerciseDate);
  }
  expect(data.completionStatus).toBe("NOT_COMPLETED");
  expect(data.grantedXp).toBe(0);
  expect(data.completedMethod).toBeUndefined();
  expect(data.rejectionReasonCode).toBe(expected.rejectionReasonCode);
  expect(data.rejectionReasonDetail).toBe(expected.rejectionReasonDetail);
  expect(data.failureCode).toBeUndefined();
}

function recordTiming(
  scenario: string,
  endpoint: "photo" | "record",
  tmpObjectKey: string,
  response: { status: number; body: ApiEnvelope<SubmitVerificationData> },
  elapsedMs: number
): void {
  timingRecords.push({
    scenario,
    endpoint,
    tmpObjectKey,
    extension: path.extname(tmpObjectKey).replace(".", "").toLowerCase() || "(none)",
    httpStatus: response.status,
    apiStatus: response.body.status,
    apiCode: response.body.code ?? null,
    verificationStatus:
      (response.body.data as SubmitVerificationData | undefined)?.verificationStatus ?? null,
    elapsedMs,
    recordedAt: new Date().toISOString(),
  });
}

function writeLatencyReport(): void {
  const lines: string[] = [
    "# Workout Verification Latency Report",
    "",
    `- Generated at: ${new Date().toISOString()}`,
    `- Backend URL: ${ENV.BACKEND_URL}`,
    `- Record count: ${timingRecords.length}`,
    "",
  ];

  if (timingRecords.length > 0) {
    const endpoints: Array<"photo" | "record"> = ["photo", "record"];
    lines.push("## Summary", "");
    lines.push("| Endpoint | Count | Min ms | Avg ms | Max ms |");
    lines.push("|---|---:|---:|---:|---:|");
    for (const endpoint of endpoints) {
      const rows = timingRecords.filter((record) => record.endpoint === endpoint);
      if (rows.length === 0) {
        lines.push(`| ${endpoint} | 0 | - | - | - |`);
        continue;
      }
      const values = rows.map((record) => record.elapsedMs);
      const total = values.reduce((sum, value) => sum + value, 0);
      const avg = Math.round(total / values.length);
      lines.push(
        `| ${endpoint} | ${rows.length} | ${Math.min(...values)} | ${avg} | ${Math.max(...values)} |`
      );
    }
    lines.push("");
  }

  lines.push("## Measurements", "");
  lines.push(
    "| Scenario | Endpoint | Ext | HTTP | API status | API code | Verification status | Elapsed ms | Recorded at |"
  );
  lines.push("|---|---|---|---:|---|---|---|---:|---|");

  for (const record of timingRecords) {
    lines.push(
      `| ${record.scenario} | ${record.endpoint} | ${record.extension} | ${record.httpStatus} | ${record.apiStatus} | ${record.apiCode ?? "-"} | ${record.verificationStatus ?? "-"} | ${record.elapsedMs} | ${record.recordedAt} |`
    );
  }

  if (timingRecords.length === 0) {
    lines.push("| - | - | - | - | - | - | - | - | - |");
  }

  fs.writeFileSync(LATENCY_REPORT_PATH, `${lines.join("\n")}\n`, "utf8");
}

function assertOptionalDate(
  actual: string | null | undefined,
  expected: string | undefined
): void {
  if (expected == null) {
    expect(actual ?? null).toBeNull();
    return;
  }
  expect(actual).toBe(expected);
}

async function waitForTerminalVerification(
  request: APIRequestContext,
  accessToken: string,
  verificationId: string
): Promise<VerificationDetailData> {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const response = await getVerificationDetail(request, accessToken, verificationId);
    const data = requireData(response.body);
    if (["REJECTED", "VERIFIED", "FAILED"].includes(data.verificationStatus)) {
      return data;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(
    `verification did not reach terminal status in time: ${verificationId}`
  );
}
