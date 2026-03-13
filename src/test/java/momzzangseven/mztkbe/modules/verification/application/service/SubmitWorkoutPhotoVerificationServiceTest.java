package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.verification.InvalidVerificationImageExtensionException;
import momzzangseven.mztkbe.global.error.verification.VerificationAlreadyCompletedTodayException;
import momzzangseven.mztkbe.global.error.verification.VerificationKindMismatchException;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiTimeoutException;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitWorkoutPhotoVerificationServiceTest {

  @Mock private VerificationRequestPort verificationRequestPort;
  @Mock private WorkoutUploadLookupPort workoutUploadLookupPort;
  @Mock private ObjectStoragePort objectStoragePort;
  @Mock private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @Mock private ExifMetadataPort exifMetadataPort;
  @Mock private WorkoutImageAiPort workoutImageAiPort;
  @Mock private GrantXpPort grantXpPort;
  @Mock private XpLedgerQueryPort xpLedgerQueryPort;
  @Mock private ImageCodecSupportPort imageCodecSupportPort;

  private SubmitWorkoutPhotoVerificationService service;
  private VerificationImagePolicy verificationImagePolicy;

  @BeforeEach
  void setUp() {
    verificationImagePolicy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    service =
        new SubmitWorkoutPhotoVerificationService(
            verificationRequestPort,
            workoutUploadLookupPort,
            objectStoragePort,
            prepareAnalysisImagePort,
            exifMetadataPort,
            workoutImageAiPort,
            grantXpPort,
            xpLedgerQueryPort,
            imageCodecSupportPort,
            timePolicy,
            verificationImagePolicy);
    lenient().when(imageCodecSupportPort.isHeifDecodeAvailable()).thenReturn(true);
  }

  @Test
  @DisplayName("오늘 WORKOUT 보상이 이미 있으면 선차단한다")
  void blocksWhenRewardAlreadyGrantedToday() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "location-verification:1"));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(VerificationAlreadyCompletedTodayException.class);

    verify(verificationRequestPort, never()).findByTmpObjectKey(any());
  }

  @Test
  @DisplayName("today XP 선차단은 invalid tmp key보다 우선한다")
  void blocksWhenRewardAlreadyGrantedBeforeTmpKeyValidation() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(1L, "invalid-key", VerificationKind.WORKOUT_PHOTO);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "location-verification:1"));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(VerificationAlreadyCompletedTodayException.class);

    verify(verificationRequestPort, never()).findByTmpObjectKey(any());
  }

  @Test
  @DisplayName("같은 tmpObjectKey가 다른 kind에 바인딩된 경우 mismatch를 반환한다")
  void rejectsOnKindMismatch() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/a.jpg"))
        .thenReturn(
            Optional.of(
                VerificationRequest.newPending(
                    1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.jpg")));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(VerificationKindMismatchException.class);
  }

  @Test
  @DisplayName("신규 요청 성공 시 VERIFIED + COMPLETED를 반환한다")
  void verifiesNewPhotoRequest() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/a.jpg"))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate("private/workout/a.jpg"))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, "private/workout/a.jpg", "private/workout/a.jpg")));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate("private/workout/a.jpg"))
        .thenReturn(Optional.empty());

    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toVerified(LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 10, 0)));
    when(objectStoragePort.exists("private/workout/a.jpg")).thenReturn(true);
    when(objectStoragePort.readBytes("private/workout/a.jpg")).thenReturn(new byte[] {1, 2, 3});
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 10, 0))));
    when(prepareAnalysisImagePort.prepare(any(), eq("jpg"), eq(1024), eq(0.80d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(true)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .build());
    when(grantXpPort.grantWorkoutXp(
            1L,
            VerificationKind.WORKOUT_PHOTO,
            pending.getVerificationId(),
            "workout-photo-verification:" + pending.getVerificationId()))
        .thenReturn(100);

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(result.completionStatus()).isEqualTo(CompletionStatus.COMPLETED);
    assertThat(result.grantedXp()).isEqualTo(100);
  }

  @Test
  @DisplayName("인증 성공 후 prepared analysis image는 정리된다")
  void closesPreparedImageOnSuccess() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();
    when(prepareAnalysisImagePort.prepare(any(), eq("jpg"), eq(1024), eq(0.80d)))
        .thenReturn(new PreparedAnalysisImage(Path.of("analysis.webp"), () -> closed.set(true)));
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(true)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .build());
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toVerified(LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 10, 0)));

    service.execute(command);

    assertThat(closed.get()).isTrue();
  }

  @Test
  @DisplayName("AI 실패 후에도 prepared analysis image는 정리된다")
  void closesPreparedImageOnFailure() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();
    when(prepareAnalysisImagePort.prepare(any(), eq("jpg"), eq(1024), eq(0.80d)))
        .thenReturn(new PreparedAnalysisImage(Path.of("analysis.webp"), () -> closed.set(true)));
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenThrow(new AiTimeoutException("timeout"));
    VerificationRequest failed = pending.toAnalyzing().toFailed(FailureCode.EXTERNAL_AI_TIMEOUT);
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), failed);

    service.execute(command);

    assertThat(closed.get()).isTrue();
  }

  @Test
  @DisplayName("기존 REJECTED row는 재분석하지 않고 기존 결과를 반환한다")
  void reusesRejectedRow() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/rejected.jpg", VerificationKind.WORKOUT_PHOTO);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    VerificationRequest rejected =
        VerificationRequest.builder()
            .verificationId("rejected-id")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.REJECTED)
            .tmpObjectKey("private/workout/rejected.jpg")
            .updatedAt(Instant.parse("2026-03-13T01:00:00Z"))
            .build();
    when(verificationRequestPort.findByTmpObjectKey("private/workout/rejected.jpg"))
        .thenReturn(Optional.of(rejected));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationId()).isEqualTo("rejected-id");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    verify(workoutUploadLookupPort, never()).findByTmpObjectKey(any());
  }

  @Test
  @DisplayName("EXIF가 없으면 REJECTED(EXIF_MISSING)로 저장한다")
  void rejectsWhenExifIsMissing() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/no-exif.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_PHOTO, "private/workout/no-exif.jpg");

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey())));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toRejected(
                RejectionReasonCode.EXIF_MISSING, "EXIF metadata is required", null, null));
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(objectStoragePort.readBytes(command.tmpObjectKey())).thenReturn(new byte[] {1, 2, 3});
    when(exifMetadataPort.extract(any())).thenReturn(Optional.empty());

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.EXIF_MISSING);
    verifyNoInteractions(prepareAnalysisImagePort);
  }

  @Test
  @DisplayName("EXIF 촬영일이 오늘이 아니면 REJECTED(EXIF_NOT_TODAY)로 저장한다")
  void rejectsWhenExifShotDateIsNotToday() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/old-exif.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_PHOTO, "private/workout/old-exif.jpg");

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey())));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toRejected(
                RejectionReasonCode.EXIF_NOT_TODAY,
                "EXIF shot date must be today in KST",
                LocalDate.of(2026, 3, 12),
                LocalDateTime.of(2026, 3, 12, 23, 59)));
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(objectStoragePort.readBytes(command.tmpObjectKey())).thenReturn(new byte[] {1, 2, 3});
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 12, 23, 59))));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.EXIF_NOT_TODAY);
    verifyNoInteractions(prepareAnalysisImagePort);
  }

  @Test
  @DisplayName("원본 object read가 실패하면 FAILED(ORIGINAL_IMAGE_READ_FAILED)로 저장한다")
  void failsWhenOriginalImageCannotBeRead() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/read-fail.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_PHOTO, "private/workout/read-fail.jpg");
    VerificationRequest failed =
        pending.toAnalyzing().toFailed(FailureCode.ORIGINAL_IMAGE_READ_FAILED);

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey())));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), failed);
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(objectStoragePort.readBytes(command.tmpObjectKey()))
        .thenThrow(new RuntimeException("boom"));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.ORIGINAL_IMAGE_READ_FAILED);
    verifyNoInteractions(
        exifMetadataPort, prepareAnalysisImagePort, workoutImageAiPort, grantXpPort);
  }

  @Test
  @DisplayName("AI가 운동 사진이 아니라고 판단하면 REJECTED(NOT_EXERCISE_PHOTO)로 저장한다")
  void rejectsWhenAiDeniesExercisePhoto() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/not-exercise.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_PHOTO, "private/workout/not-exercise.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(false)
                .rejectionReasonDetail("not workout")
                .build());
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toRejected(
                RejectionReasonCode.NOT_EXERCISE_PHOTO,
                "not workout",
                null,
                LocalDateTime.of(2026, 3, 13, 10, 0)));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.NOT_EXERCISE_PHOTO);
  }

  @Test
  @DisplayName("분석 이미지 생성이 실패하면 FAILED(ANALYSIS_IMAGE_GENERATION_FAILED)로 저장한다")
  void failsWhenAnalysisImageGenerationFails() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/prepare-fail.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_PHOTO, "private/workout/prepare-fail.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    when(prepareAnalysisImagePort.prepare(any(), eq("jpg"), eq(1024), eq(0.80d)))
        .thenThrow(new java.io.IOException("encode fail"));
    VerificationRequest failed =
        pending.toAnalyzing().toFailed(FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), failed);

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  @DisplayName("XP 지급이 0이면 today reward source_ref로 completedMethod를 다시 계산한다")
  void derivesCompletedMethodFromTodayRewardWhenGrantReturnsZero() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/source-ref.jpg", VerificationKind.WORKOUT_PHOTO);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "workout-record-verification:other-id"));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/source-ref.jpg"))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate("private/workout/source-ref.jpg"))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(
                    1L, "private/workout/source-ref.jpg", "private/workout/source-ref.jpg")));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate("private/workout/source-ref.jpg"))
        .thenReturn(Optional.empty());
    VerificationRequest pending =
        VerificationRequest.newPending(
            1L, VerificationKind.WORKOUT_PHOTO, "private/workout/source-ref.jpg");
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toVerified(LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 10, 0)));
    when(objectStoragePort.exists("private/workout/source-ref.jpg")).thenReturn(true);
    when(objectStoragePort.readBytes("private/workout/source-ref.jpg"))
        .thenReturn(new byte[] {1, 2, 3});
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 10, 0))));
    when(prepareAnalysisImagePort.prepare(any(), eq("jpg"), eq(1024), eq(0.80d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(true)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .build());
    when(grantXpPort.grantWorkoutXp(
            1L,
            VerificationKind.WORKOUT_PHOTO,
            pending.getVerificationId(),
            "workout-photo-verification:" + pending.getVerificationId()))
        .thenReturn(0);

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(result.grantedXp()).isZero();
    assertThat(result.completedMethod())
        .isEqualTo(
            momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod.WORKOUT_RECORD);
  }

  @Test
  @DisplayName("image row lock 이후 기존 VERIFIED row가 보이면 기존 결과로 수렴한다")
  void convergesToExistingVerifiedRowAfterImageLock() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/race.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest verified =
        VerificationRequest.builder()
            .verificationId("verified-id")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.VERIFIED)
            .exerciseDate(LocalDate.of(2026, 3, 13))
            .tmpObjectKey("private/workout/race.jpg")
            .updatedAt(Instant.parse("2026-03-13T01:00:00Z"))
            .build();

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/race.jpg"))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate("private/workout/race.jpg"))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(
                    1L, "private/workout/race.jpg", "private/workout/race.jpg")));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate("private/workout/race.jpg"))
        .thenReturn(Optional.of(verified));
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "workout-photo-verification:verified-id"));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationId()).isEqualTo("verified-id");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(result.completedMethod()).isNotNull();
    verify(workoutUploadLookupPort, never()).findByTmpObjectKey(any());
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  @DisplayName("FAILED 재시도 중 lock 이후 VERIFIED가 보이면 재분석하지 않는다")
  void doesNotRetryWhenFailedRowTurnsVerifiedAfterRowLock() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/retry.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest failed =
        VerificationRequest.builder()
            .id(7L)
            .verificationId("retry-id")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.FAILED)
            .tmpObjectKey("private/workout/retry.jpg")
            .updatedAt(Instant.parse("2026-03-13T01:00:00Z"))
            .build();
    VerificationRequest verifiedAfterLock =
        failed.toVerified(LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 10, 0));

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "workout-photo-verification:retry-id"));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/retry.jpg"))
        .thenReturn(Optional.of(failed));
    when(verificationRequestPort.transitionFailedToAnalyzing("retry-id")).thenReturn(false);
    when(verificationRequestPort.findByVerificationId("retry-id"))
        .thenReturn(Optional.of(verifiedAfterLock));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationId()).isEqualTo("retry-id");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    verifyNoInteractions(prepareAnalysisImagePort);
    verify(workoutImageAiPort, never()).analyzeWorkoutPhoto(any());
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  @DisplayName("HEIF 정책이 비활성화되면 heic 업로드를 차단한다")
  void blocksHeicWhenPolicyDisabled() {
    VerificationImagePolicy disabledPolicy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(false, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    SubmitWorkoutPhotoVerificationService disabledService =
        new SubmitWorkoutPhotoVerificationService(
            verificationRequestPort,
            workoutUploadLookupPort,
            objectStoragePort,
            prepareAnalysisImagePort,
            exifMetadataPort,
            workoutImageAiPort,
            grantXpPort,
            xpLedgerQueryPort,
            imageCodecSupportPort,
            timePolicy,
            disabledPolicy);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));

    assertThatThrownBy(
            () ->
                disabledService.execute(
                    new SubmitWorkoutVerificationCommand(
                        1L, "private/workout/a.heic", VerificationKind.WORKOUT_PHOTO)))
        .isInstanceOf(InvalidVerificationImageExtensionException.class);
  }

  @Test
  @DisplayName("AI malformed response는 FAILED(EXTERNAL_AI_MALFORMED_RESPONSE)로 저장한다")
  void mapsAiMalformedResponseToFailureCode() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenThrow(new AiMalformedResponseException("bad json"));
    VerificationRequest failed =
        pending.toAnalyzing().toFailed(FailureCode.EXTERNAL_AI_MALFORMED_RESPONSE);
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), failed);

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.EXTERNAL_AI_MALFORMED_RESPONSE);
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  @DisplayName("AI schema invalid는 FAILED(AI_RESPONSE_SCHEMA_INVALID)로 저장한다")
  void mapsAiSchemaInvalidToFailureCode() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenThrow(new AiResponseSchemaInvalidException("schema invalid"));
    VerificationRequest failed =
        pending.toAnalyzing().toFailed(FailureCode.AI_RESPONSE_SCHEMA_INVALID);
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), failed);

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.AI_RESPONSE_SCHEMA_INVALID);
  }

  @Test
  @DisplayName("AI timeout은 FAILED(EXTERNAL_AI_TIMEOUT)로 저장한다")
  void mapsAiTimeoutToFailureCode() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    stubSuccessfulPreAiFlow(command, pending);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenThrow(new AiTimeoutException("timeout"));
    VerificationRequest failed = pending.toAnalyzing().toFailed(FailureCode.EXTERNAL_AI_TIMEOUT);
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), failed);

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT);
  }

  private void stubSuccessfulPreAiFlow(
      SubmitWorkoutVerificationCommand command, VerificationRequest pending) throws Exception {
    when(xpLedgerQueryPort.findTodayWorkoutReward(command.userId(), LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey())));
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(objectStoragePort.readBytes(command.tmpObjectKey())).thenReturn(new byte[] {1, 2, 3});
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 10, 0))));
    lenient()
        .when(prepareAnalysisImagePort.prepare(any(), eq("jpg"), eq(1024), eq(0.80d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
  }
}
