package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarkTransactionSucceededCommand unit test")
class MarkTransactionSucceededCommandTest {

  @Test
  @DisplayName("validate rejects invalid operatorId")
  void validate_invalidOperatorId_throwsException() {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(0L, 1L, txHash(), "https://scan", "reason", "evidence");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");
  }

  @Test
  @DisplayName("validate rejects invalid txHash format")
  void validate_invalidTxHash_throwsException() {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(1L, 1L, "0x1234", "https://scan", "reason", "evidence");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash must be 0x-prefixed 32-byte hex");
  }

  @Test
  @DisplayName("validate rejects blank reason")
  void validate_blankReason_throwsException() {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(1L, 1L, txHash(), "https://scan", " ", "evidence");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reason is required");
  }

  @Test
  @DisplayName("validate passes for valid command")
  void validate_validCommand_doesNotThrow() {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(1L, 1L, txHash(), "https://scan", "reason", "evidence");

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  private String txHash() {
    return "0x" + "a".repeat(64);
  }
}
