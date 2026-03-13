package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
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
}
