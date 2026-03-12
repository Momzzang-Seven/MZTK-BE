package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CancelQuestionRewardIntentCommand unit test")
class CancelQuestionRewardIntentCommandTest {

  @Test
  @DisplayName("validate rejects non-positive postId")
  void validate_invalidPostId_throwsException() {
    CancelQuestionRewardIntentCommand command = new CancelQuestionRewardIntentCommand(0L, 1L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("postId must be positive");
  }

  @Test
  @DisplayName("validate rejects null postId")
  void validate_nullPostId_throwsException() {
    CancelQuestionRewardIntentCommand command = new CancelQuestionRewardIntentCommand(null, 1L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("postId must be positive");
  }

  @Test
  @DisplayName("validate rejects non-positive acceptedCommentId when provided")
  void validate_invalidAcceptedCommentId_throwsException() {
    CancelQuestionRewardIntentCommand command = new CancelQuestionRewardIntentCommand(1L, -1L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("acceptedCommentId must be positive when provided");
  }

  @Test
  @DisplayName("validate allows null acceptedCommentId")
  void validate_nullAcceptedCommentId_doesNotThrow() {
    CancelQuestionRewardIntentCommand command = new CancelQuestionRewardIntentCommand(1L, null);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
