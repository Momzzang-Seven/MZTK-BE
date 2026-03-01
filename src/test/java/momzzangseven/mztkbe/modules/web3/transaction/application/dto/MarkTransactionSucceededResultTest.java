package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.Test;

class MarkTransactionSucceededResultTest {

  @Test
  void builder_createsResult_whenValid() {
    MarkTransactionSucceededResult result =
        MarkTransactionSucceededResult.builder()
            .transactionId(11L)
            .status(Web3TxStatus.SUCCEEDED)
            .previousStatus(Web3TxStatus.UNCONFIRMED)
            .txHash("0x" + "a".repeat(64))
            .explorerUrl("https://explorer/tx/11")
            .build();

    assertThat(result.transactionId()).isEqualTo(11L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.SUCCEEDED);
  }

  @Test
  void constructor_throws_whenStatusNull() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    11L,
                    null,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    "https://explorer/tx/11"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void constructor_throws_whenTransactionIdInvalid() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    0L,
                    Web3TxStatus.SUCCEEDED,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    "https://explorer/tx/11"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_throws_whenPreviousStatusNull() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    11L,
                    Web3TxStatus.SUCCEEDED,
                    null,
                    "0x" + "a".repeat(64),
                    "https://explorer/tx/11"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("previousStatus is required");
  }

  @Test
  void constructor_throws_whenTxHashBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    11L,
                    Web3TxStatus.SUCCEEDED,
                    Web3TxStatus.UNCONFIRMED,
                    " ",
                    "https://explorer/tx/11"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void constructor_throws_whenExplorerUrlBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    11L,
                    Web3TxStatus.SUCCEEDED,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("explorerUrl is required");
  }
}
