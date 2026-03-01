package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class SubmitTokenTransferResultTest {

  @Test
  void builder_createsRecord_whenValid() {
    SubmitTokenTransferResult result =
        SubmitTokenTransferResult.builder()
            .transactionId(30L)
            .status("PENDING")
            .txHash("0x" + "a".repeat(64))
            .build();

    assertThat(result.transactionId()).isEqualTo(30L);
    assertThat(result.status()).isEqualTo("PENDING");
  }

  @Test
  void constructor_throws_whenTxHashBlank() {
    assertThatThrownBy(() -> new SubmitTokenTransferResult(30L, "PENDING", " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void constructor_throws_whenTxHashNull() {
    assertThatThrownBy(() -> new SubmitTokenTransferResult(30L, "PENDING", null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void constructor_throws_whenStatusBlank() {
    assertThatThrownBy(() -> new SubmitTokenTransferResult(30L, " ", "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void constructor_throws_whenStatusNull() {
    assertThatThrownBy(() -> new SubmitTokenTransferResult(30L, null, "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void constructor_throws_whenTransactionIdInvalid() {
    assertThatThrownBy(() -> new SubmitTokenTransferResult(0L, "PENDING", "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_throws_whenTransactionIdNull() {
    assertThatThrownBy(() -> new SubmitTokenTransferResult(null, "PENDING", "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }
}
