package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterQuestionRewardIntentCommand unit test")
class RegisterQuestionRewardIntentCommandTest {

  @Test
  @DisplayName("validate rejects non-positive postId")
  void validate_invalidPostId_throwsException() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(0L, 10L, 1L, 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("postId must be positive");
  }

  @Test
  @DisplayName("validate rejects non-positive acceptedCommentId")
  void validate_invalidAcceptedCommentId_throwsException() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(100L, 0L, 1L, 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("acceptedCommentId must be positive");
  }

  @Test
  @DisplayName("validate rejects non-positive amount")
  void validate_nonPositiveAmount_throwsException() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(100L, 10L, 1L, 2L, BigInteger.ZERO);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be > 0");
  }

  @Test
  @DisplayName("validate rejects non-positive fromUserId")
  void validate_invalidFromUserId_throwsException() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(100L, 10L, 0L, 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromUserId must be positive");
  }

  @Test
  @DisplayName("validate rejects non-positive toUserId")
  void validate_invalidToUserId_throwsException() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(100L, 10L, 1L, 0L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toUserId must be positive");
  }

  @Test
  @DisplayName("referenceId returns postId as string")
  void referenceId_returnsPostIdString() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(123L, 10L, 1L, 2L, BigInteger.ONE);

    assertThat(command.referenceId()).isEqualTo("123");
  }

  @Test
  @DisplayName("validate passes for valid payload")
  void validate_validCommand_doesNotThrow() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(100L, 10L, 1L, 2L, BigInteger.ONE);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
