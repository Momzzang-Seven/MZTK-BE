package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class MarkTransactionSucceededCommandTest {

  @Test
  void constructor_acceptsValidPayload() {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(
            1L, 2L, "0x" + "a".repeat(64), "https://explorer/tx/1", "manual proof", "ticket-1");

    assertThat(command.operatorId()).isEqualTo(1L);
    assertThat(command.transactionId()).isEqualTo(2L);
  }

  @Test
  void constructor_throws_whenTxHashFormatInvalid() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededCommand(
                    1L, 2L, "0x1234", "https://explorer/tx/1", "manual proof", "ticket-1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash must be 0x-prefixed 32-byte hex");
  }

  @Test
  void constructor_throws_whenTxHashBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededCommand(
                    1L, 2L, " ", "https://explorer/tx/1", "manual proof", "ticket-1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }
}
