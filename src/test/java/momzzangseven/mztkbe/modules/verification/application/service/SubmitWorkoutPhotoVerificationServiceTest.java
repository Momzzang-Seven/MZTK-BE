package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import momzzangseven.mztkbe.global.error.verification.VerificationUploadForbiddenException;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
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
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
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
  @Mock private PrepareOriginalImagePort prepareOriginalImagePort;
  @Mock private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @Mock private ExifMetadataPort exifMetadataPort;
  @Mock private WorkoutImageAiPort workoutImageAiPort;
  @Mock private GrantXpPort grantXpPort;
  @Mock private XpLedgerQueryPort xpLedgerQueryPort;
  @Mock private ImageCodecSupportPort imageCodecSupportPort;

  private SubmitWorkoutPhotoVerificationService service;
  private VerificationImagePolicy verificationImagePolicy;

  @BeforeEach
  void setUp() throws Exception {
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
            prepareOriginalImagePort,
            prepareAnalysisImagePort,
            exifMetadataPort,
            workoutImageAiPort,
            grantXpPort,
            xpLedgerQueryPort,
            imageCodecSupportPort,
            timePolicy,
            verificationImagePolicy);
    lenient().when(imageCodecSupportPort.isHeifDecodeAvailable()).thenReturn(true);
    lenient()
        .when(prepareOriginalImagePort.prepare(anyString(), anyString()))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.jpg")));
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
  @DisplayName("다른 사용자의 기존 verification row는 재사용하지 않고 403을 반환한다")
  void rejectsExistingVerificationOwnedByAnotherUser() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest existing =
        VerificationRequest.builder()
            .verificationId("verified-by-other-user")
            .userId(2L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.VERIFIED)
            .tmpObjectKey("private/workout/a.jpg")
            .build();
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/a.jpg"))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(VerificationUploadForbiddenException.class);

    verifyNoInteractions(workoutUploadLookupPort, prepareAnalysisImagePort, workoutImageAiPort);
    verifyNoInteractions(grantXpPort);
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
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(pending));
    when(objectStoragePort.exists("private/workout/a.jpg")).thenReturn(true);
    stubOpenStream("private/workout/a.jpg", "image/jpeg");
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 10, 0))));
    when(prepareAnalysisImagePort.prepare(any(Path.class), eq(1024), eq(0.80d)))
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
    assertThat(result.exerciseDate()).isNull();
    assertThat(result.completionStatus()).isEqualTo(CompletionStatus.COMPLETED);
    assertThat(result.grantedXp()).isEqualTo(100);
  }

  @Test
  @DisplayName("원본 object가 없으면 verification row를 만들지 않고 FAIL 예외를 반환한다")
  void failsBeforeCreatingVerificationRowWhenObjectDoesNotExist() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/missing-object.jpg", VerificationKind.WORKOUT_PHOTO);

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
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(false);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(
            momzzangseven.mztkbe.global.error.verification.VerificationUploadNotFoundException
                .class);

    verify(verificationRequestPort, never()).save(any());
    verifyNoInteractions(
        exifMetadataPort, prepareAnalysisImagePort, workoutImageAiPort, grantXpPort);
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
    when(prepareAnalysisImagePort.prepare(any(Path.class), eq(1024), eq(0.80d)))
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
    when(prepareAnalysisImagePort.prepare(any(Path.class), eq(1024), eq(0.80d)))
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
  @DisplayName("EXIF가 없으면 REJECTED(MISSING_EXIF_METADATA)로 저장한다")
  void rejectsWhenExifIsMissing() throws Exception {
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
                RejectionReasonCode.MISSING_EXIF_METADATA,
                "EXIF metadata is required",
                null,
                null));
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(pending));
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    stubOpenStream(command.tmpObjectKey(), "image/jpeg");
    when(exifMetadataPort.extract(any())).thenReturn(Optional.empty());

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.MISSING_EXIF_METADATA);
    verifyNoInteractions(prepareAnalysisImagePort);
  }

  @Test
  @DisplayName("EXIF 촬영일이 오늘이 아니면 REJECTED(EXIF_DATE_MISMATCH)로 저장한다")
  void rejectsWhenExifShotDateIsNotToday() throws Exception {
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
                RejectionReasonCode.EXIF_DATE_MISMATCH,
                "EXIF shot date must be today in KST",
                LocalDate.of(2026, 3, 12),
                LocalDateTime.of(2026, 3, 12, 23, 59)));
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(pending));
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    stubOpenStream(command.tmpObjectKey(), "image/jpeg");
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 12, 23, 59))));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.EXIF_DATE_MISMATCH);
    verifyNoInteractions(prepareAnalysisImagePort);
  }

  @Test
  @DisplayName("원본 object read가 실패하면 FAILED(ORIGINAL_IMAGE_READ_FAILED)로 저장한다")
  void failsWhenOriginalImageCannotBeRead() throws Exception {
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
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(pending));
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(objectStoragePort.openStream(command.tmpObjectKey())).thenThrow(new IOException("boom"));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.ORIGINAL_IMAGE_READ_FAILED);
    verifyNoInteractions(
        exifMetadataPort, prepareAnalysisImagePort, workoutImageAiPort, grantXpPort);
  }

  @Test
  @DisplayName("AI가 운동 사진이 아니라고 판단하면 REJECTED(LOW_CONFIDENCE)로 저장한다")
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
                .rejectionReasonCode(RejectionReasonCode.LOW_CONFIDENCE)
                .rejectionReasonDetail("not workout")
                .build());
    when(verificationRequestPort.save(any()))
        .thenReturn(
            pending,
            pending.toAnalyzing(),
            pending.toRejected(
                RejectionReasonCode.LOW_CONFIDENCE,
                "not workout",
                null,
                LocalDateTime.of(2026, 3, 13, 10, 0)));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.LOW_CONFIDENCE);
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
    when(prepareAnalysisImagePort.prepare(any(Path.class), eq(1024), eq(0.80d)))
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
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(pending));
    when(objectStoragePort.exists("private/workout/source-ref.jpg")).thenReturn(true);
    stubOpenStream("private/workout/source-ref.jpg", "image/jpeg");
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 10, 0))));
    when(prepareAnalysisImagePort.prepare(any(Path.class), eq(1024), eq(0.80d)))
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
            .createdAt(Instant.parse("2026-03-13T00:30:00Z"))
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
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate("private/workout/retry.jpg"))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(
                    1L, "private/workout/retry.jpg", "private/workout/retry.jpg")));
    when(objectStoragePort.exists("private/workout/retry.jpg")).thenReturn(true);
    when(verificationRequestPort.findByVerificationIdForUpdate("retry-id"))
        .thenReturn(Optional.of(verifiedAfterLock));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationId()).isEqualTo("retry-id");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    verifyNoInteractions(prepareAnalysisImagePort);
    verify(workoutImageAiPort, never()).analyzeWorkoutPhoto(any());
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  @DisplayName("FAILED 재시도 기준은 updatedAt이 아니라 createdAt의 오늘 여부다")
  void doesNotRetryFailedRowCreatedBeforeTodayEvenIfUpdatedToday() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/old-failed.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest oldFailed =
        VerificationRequest.builder()
            .id(8L)
            .verificationId("old-failed-id")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.FAILED)
            .tmpObjectKey("private/workout/old-failed.jpg")
            .createdAt(Instant.parse("2026-03-11T23:30:00Z"))
            .updatedAt(Instant.parse("2026-03-13T01:00:00Z"))
            .failureCode(FailureCode.EXTERNAL_AI_UNAVAILABLE)
            .build();

    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/old-failed.jpg"))
        .thenReturn(Optional.of(oldFailed));

    SubmitWorkoutVerificationResult result = service.execute(command);

    assertThat(result.verificationId()).isEqualTo("old-failed-id");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    verify(verificationRequestPort, never()).findByVerificationIdForUpdate(any());
    verifyNoInteractions(workoutUploadLookupPort, prepareAnalysisImagePort, workoutImageAiPort);
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
            prepareOriginalImagePort,
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
  @DisplayName("HEIF 정책이 비활성화되어도 기존 heic verification 결과는 재사용한다")
  void reusesExistingHeicVerificationWhenPolicyDisabled() {
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
            prepareOriginalImagePort,
            prepareAnalysisImagePort,
            exifMetadataPort,
            workoutImageAiPort,
            grantXpPort,
            xpLedgerQueryPort,
            imageCodecSupportPort,
            timePolicy,
            disabledPolicy);
    VerificationRequest existing =
        VerificationRequest.builder()
            .verificationId("existing-heic")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.VERIFIED)
            .tmpObjectKey("private/workout/a.heic")
            .build();
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)));
    when(verificationRequestPort.findByTmpObjectKey("private/workout/a.heic"))
        .thenReturn(Optional.of(existing));

    SubmitWorkoutVerificationResult result =
        disabledService.execute(
            new SubmitWorkoutVerificationCommand(
                1L, "private/workout/a.heic", VerificationKind.WORKOUT_PHOTO));

    assertThat(result.verificationId()).isEqualTo("existing-heic");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(result.exerciseDate()).isNull();
    verifyNoInteractions(workoutUploadLookupPort, prepareAnalysisImagePort, workoutImageAiPort);
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
    assertThat(result.completedMethod()).isNull();
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

  @Test
  @DisplayName("XP 지급 예외는 FAILED로 삼키지 않고 그대로 전파한다")
  void propagatesGrantXpRuntimeException() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");
    VerificationRequest verified =
        pending.toAnalyzing().toVerified(null, LocalDateTime.of(2026, 3, 13, 10, 0));
    stubSuccessfulPreAiFlow(command, pending);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenReturn(AiVerificationDecision.builder().approved(true).build());
    when(verificationRequestPort.save(any())).thenReturn(pending, pending.toAnalyzing(), verified);
    when(grantXpPort.grantWorkoutXp(
            1L,
            VerificationKind.WORKOUT_PHOTO,
            pending.getVerificationId(),
            "workout-photo-verification:" + pending.getVerificationId()))
        .thenThrow(new IllegalStateException("xp down"));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("xp down");

    verify(verificationRequestPort, times(3)).save(any());
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
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(pending));
    stubOpenStream(command.tmpObjectKey(), "image/jpeg");
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.of(2026, 3, 13, 10, 0))));
    lenient()
        .when(prepareAnalysisImagePort.prepare(any(Path.class), eq(1024), eq(0.80d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
  }

  private void stubOpenStream(String objectKey, String contentType) throws IOException {
    when(objectStoragePort.openStream(objectKey))
        .thenAnswer(
            invocation ->
                new StorageObjectStream(
                    new ByteArrayInputStream(new byte[] {1, 2, 3}), 3L, contentType));
  }
}
