package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificationSubmissionResultFactoryTest {

  private VerificationSubmissionResultFactory factory;

  @BeforeEach
  void setUp() {
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    factory = new VerificationSubmissionResultFactory(timePolicy);
  }

  @Test
  void hidesCompletedMethodWhenSourceRefIsBlank() {
    VerificationRequest request =
        VerificationRequest.builder()
            .verificationId("verification-1")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.REJECTED)
            .tmpObjectKey("private/workout/a.jpg")
            .build();

    var result = factory.from(request, 0, " ");

    assertThat(result.completedMethod()).isNull();
    assertThat(result.completionStatus()).isEqualTo(CompletionStatus.NOT_COMPLETED);
    assertThat(result.exerciseDate()).isNull();
  }

  @Test
  void exposesExerciseDateOnlyForWorkoutRecord() {
    VerificationRequest photo =
        VerificationRequest.builder()
            .verificationId("photo-1")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.VERIFIED)
            .rewardStatus(VerificationRewardStatus.SUCCEEDED)
            .exerciseDate(LocalDate.of(2026, 3, 13))
            .tmpObjectKey("private/workout/a.jpg")
            .build();
    VerificationRequest record =
        VerificationRequest.builder()
            .verificationId("record-1")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_RECORD)
            .status(VerificationStatus.VERIFIED)
            .rewardStatus(VerificationRewardStatus.SUCCEEDED)
            .exerciseDate(LocalDate.of(2026, 3, 13))
            .tmpObjectKey("private/workout/a.png")
            .build();

    var photoResult = factory.from(photo, 100, "workout-photo-verification:photo-1");
    var recordResult = factory.from(record, 100, "workout-record-verification:record-1");

    assertThat(photoResult.exerciseDate()).isNull();
    assertThat(recordResult.exerciseDate()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(photoResult.completionStatus()).isEqualTo(CompletionStatus.COMPLETED);
    assertThat(recordResult.completionStatus()).isEqualTo(CompletionStatus.COMPLETED);
  }
}
