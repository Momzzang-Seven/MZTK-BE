package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class MarkTransactionSucceededResultTest {

  @Test
  void constructor_acceptsValidPayload() {
    assertThatCode(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "PENDING", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsInvalidTransactionId() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    null, "SUCCEEDED", "PENDING", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    0L, "SUCCEEDED", "PENDING", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_rejectsStatusAndPreviousStatus() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, null, "PENDING", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, " ", "PENDING", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", null, "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("previousStatus is required");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", " ", "0x" + "a".repeat(64), "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("previousStatus is required");
  }

  @Test
  void constructor_rejectsTxHashAndExplorerUrl() {
    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "PENDING", null, "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "PENDING", " ", "https://explorer/tx/1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "PENDING", "0x" + "a".repeat(64), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("explorerUrl is required");

    assertThatThrownBy(
            () ->
                new MarkTransactionSucceededResult(
                    1L, "SUCCEEDED", "PENDING", "0x" + "a".repeat(64), " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("explorerUrl is required");
  }
}
