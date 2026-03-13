package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.exception.AiTimeoutException;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationAnalysisServiceTest {

  @Mock private VerificationSourceImageService verificationSourceImageService;
  @Mock private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @Mock private WorkoutImageAiPort workoutImageAiPort;
  @Mock private VerificationSubmissionValidator verificationSubmissionValidator;

  private VerificationAnalysisService service;

  @BeforeEach
  void setUp() {
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    service =
        new VerificationAnalysisService(
            verificationSourceImageService,
            prepareAnalysisImagePort,
            workoutImageAiPort,
            verificationSubmissionValidator,
            timePolicy);
  }

  @Test
  void failsWithSchemaInvalidWhenAiRejectsWithoutReasonCode() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.png")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.png"), 1536, 0.85d))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutRecord(Path.of("analysis.webp")))
        .thenReturn(
            AiVerificationDecision.builder().approved(false).rejectionReasonCode(null).build());

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.failed()).isTrue();
    assertThat(result.failureCode()).isEqualTo(FailureCode.AI_RESPONSE_SCHEMA_INVALID);
    assertThat(result.rejected()).isFalse();
    assertThat(result.verified()).isFalse();
  }

  @Test
  void failsWhenOriginalImagePreparationThrowsIOException() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenThrow(new java.io.IOException("read fail"));

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.failed()).isTrue();
    assertThat(result.failureCode()).isEqualTo(FailureCode.ORIGINAL_IMAGE_READ_FAILED);
  }

  @Test
  void returnsRejectedWhenAiRejectsWithReasonCode() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.png")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.png"), 1536, 0.85d))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutRecord(Path.of("analysis.webp")))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(false)
                .rejectionReasonCode(RejectionReasonCode.DATE_MISMATCH)
                .rejectionReasonDetail("exercise date must be today")
                .exerciseDate(LocalDate.of(2026, 3, 12))
                .build());

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.rejected()).isTrue();
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_MISMATCH);
    assertThat(result.rejectionReasonDetail()).isEqualTo("exercise date must be today");
    assertThat(result.exerciseDate()).isEqualTo(LocalDate.of(2026, 3, 12));
  }

  @Test
  void returnsVerifiedWhenAiApproves() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.png")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.png"), 1536, 0.85d))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutRecord(Path.of("analysis.webp")))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(true)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .build());

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.verified()).isTrue();
    assertThat(result.exerciseDate()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(result.shotAtKst()).isNull();
  }

  @Test
  void failsWhenAnalysisImagePreparationThrowsIOException() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.png")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.png"), 1536, 0.85d))
        .thenThrow(new java.io.IOException("convert fail"));

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.failed()).isTrue();
    assertThat(result.failureCode()).isEqualTo(FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
  }

  @Test
  void keepsExifShotAtWhenAiRejectsPhoto() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    LocalDateTime shotAt = LocalDateTime.of(2026, 3, 13, 10, 10);
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("jpg");
    when(verificationSourceImageService.extractExif(upload.readObjectKey(), "jpg"))
        .thenReturn(Optional.of(new ExifMetadataInfo(shotAt)));
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "jpg"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.jpg")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.jpg"), 1024, 0.80d))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutPhoto(Path.of("analysis.webp")))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(false)
                .rejectionReasonCode(RejectionReasonCode.SCREEN_OR_UI)
                .rejectionReasonDetail("exercise evidence is not visible")
                .build());

    var result = service.evaluate(command, upload, new WorkoutPhotoVerificationPolicy());

    assertThat(result.rejected()).isTrue();
    assertThat(result.shotAtKst()).isEqualTo(shotAt);
  }

  @Test
  void keepsExifShotAtWhenAiApprovesPhoto() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.jpg", VerificationKind.WORKOUT_PHOTO);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    LocalDateTime shotAt = LocalDateTime.of(2026, 3, 13, 10, 10);
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("jpg");
    when(verificationSourceImageService.extractExif(upload.readObjectKey(), "jpg"))
        .thenReturn(Optional.of(new ExifMetadataInfo(shotAt)));
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "jpg"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.jpg")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.jpg"), 1024, 0.80d))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutPhoto(Path.of("analysis.webp")))
        .thenReturn(AiVerificationDecision.builder().approved(true).build());

    var result = service.evaluate(command, upload, new WorkoutPhotoVerificationPolicy());

    assertThat(result.verified()).isTrue();
    assertThat(result.exerciseDate()).isNull();
    assertThat(result.shotAtKst()).isEqualTo(shotAt);
  }

  @Test
  void failsWhenAiTimesOutAfterAnalysisImagePrepared() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.png")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.png"), 1536, 0.85d))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(workoutImageAiPort.analyzeWorkoutRecord(Path.of("analysis.webp")))
        .thenThrow(new AiTimeoutException("timeout"));

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.failed()).isTrue();
    assertThat(result.failureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT);
  }

  @Test
  void failsWhenAnalysisImageCleanupThrowsIOException() throws Exception {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            1L, "private/workout/a.png", VerificationKind.WORKOUT_RECORD);
    WorkoutUploadReference upload =
        new WorkoutUploadReference(1L, command.tmpObjectKey(), command.tmpObjectKey());
    when(verificationSubmissionValidator.extractExtension(command.tmpObjectKey()))
        .thenReturn("png");
    when(verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.png")));
    when(prepareAnalysisImagePort.prepare(Path.of("original.png"), 1536, 0.85d))
        .thenReturn(
            new PreparedAnalysisImage(
                Path.of("analysis.webp"),
                () -> throwUncheckedIOException(new IOException("cleanup failed"))));
    when(workoutImageAiPort.analyzeWorkoutRecord(Path.of("analysis.webp")))
        .thenReturn(
            AiVerificationDecision.builder()
                .approved(true)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .build());

    var result = service.evaluate(command, upload, new WorkoutRecordVerificationPolicy());

    assertThat(result.failed()).isTrue();
    assertThat(result.failureCode()).isEqualTo(FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
  }

  private static void throwUncheckedIOException(IOException ex) {
    VerificationAnalysisServiceTest.<RuntimeException>sneakyThrow(ex);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
    throw (T) throwable;
  }
}
