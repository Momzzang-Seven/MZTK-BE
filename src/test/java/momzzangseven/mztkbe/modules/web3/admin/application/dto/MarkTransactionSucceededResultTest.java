package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class MarkTransactionSucceededResultTest {

  @Test
  void constructor_storesFields_whenValid() {
    MarkTransactionSucceededResult result =
        new MarkTransactionSucceededResult(
            10L, "SUCCEEDED", "UNCONFIRMED", "0x" + "a".repeat(64), "https://explorer/tx/1");

    assertThat(result.transactionId()).isEqualTo(10L);
    assertThat(result.status()).isEqualTo("SUCCEEDED");
    assertThat(result.previousStatus()).isEqualTo("UNCONFIRMED");
  }

  @Test
  void constructor_throws_whenStatusBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, " ", "UNCONFIRMED", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void constructor_throws_whenTransactionIdInvalid() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    0L, "SUCCEEDED", "UNCONFIRMED", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_throws_whenPreviousStatusBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", " ", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("previousStatus is required");
  }

  @Test
  void constructor_throws_whenTxHashBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "UNCONFIRMED", " ", "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void constructor_throws_whenExplorerUrlBlank() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "UNCONFIRMED", "0x" + "a".repeat(64), " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("explorerUrl is required");
  }
}
