package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SubmitTokenTransferCommand unit test")
class SubmitTokenTransferCommandTest {

  @Test
  @DisplayName("validate rejects non-positive userId")
  void validate_invalidUserId_throwsException() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(0L, "prepare-1", "auth-sig", "exec-sig");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  @DisplayName("validate rejects blank prepareId")
  void validate_blankPrepareId_throwsException() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(1L, " ", "auth-sig", "exec-sig");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepareId is required");
  }

  @Test
  @DisplayName("validate rejects blank authorization signature")
  void validate_blankAuthorizationSignature_throwsException() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(1L, "prepare-1", " ", "exec-sig");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorizationSignature is required");
  }

  @Test
  @DisplayName("validate rejects blank execution signature")
  void validate_blankExecutionSignature_throwsException() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(1L, "prepare-1", "auth-sig", " ");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("executionSignature is required");
  }

  @Test
  @DisplayName("validate passes for valid command")
  void validate_validCommand_doesNotThrow() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(1L, "prepare-1", "auth-sig", "exec-sig");

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
