package momzzangseven.mztkbe.modules.verification.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubmitWorkoutVerificationCommandTest {

  @Test
  void validatePassesForPositiveUserIdAndTempObjectKey() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(7L, "tmp/workout/photo-1.jpg");

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  void validateRejectsMissingUserId() {
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(null, "tmp/workout/photo-1.jpg");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId must be positive");
  }

  @Test
  void validateRejectsBlankTempObjectKey() {
    SubmitWorkoutVerificationCommand command = new SubmitWorkoutVerificationCommand(7L, " ");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tmpObjectKey must not be blank");
  }
}
