package momzzangseven.mztkbe.modules.web3.transaction.domain.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import org.junit.jupiter.api.Test;

class Web3TransactionFailedOnchainEventTest {

  @Test
  void constructor_acceptsValidPayload() {
    assertThatCode(
            () ->
                new Web3TransactionFailedOnchainEvent(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(64),
                    "REVERT"))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsInvalidCoreFields() {
    assertThatThrownBy(
            () -> eventWith(null, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101", "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    assertThatThrownBy(
            () -> eventWith(0L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101", "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    assertThatThrownBy(
            () -> eventWith(1L, null, Web3ReferenceType.LEVEL_UP_REWARD, "101", "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");

    assertThatThrownBy(() -> eventWith(1L, " ", Web3ReferenceType.LEVEL_UP_REWARD, "101", "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");

    assertThatThrownBy(() -> eventWith(1L, "idem-1", null, "101", "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");

    assertThatThrownBy(
            () -> eventWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, null, "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");

    assertThatThrownBy(
            () -> eventWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, " ", "REVERT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsNullOrBlankFailureReason() {
    assertThatThrownBy(
            () ->
                new Web3TransactionFailedOnchainEvent(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(64),
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required");

    assertThatThrownBy(
            () ->
                new Web3TransactionFailedOnchainEvent(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(64),
                    " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required");
  }

  private Web3TransactionFailedOnchainEvent eventWith(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      String failureReason) {
    return new Web3TransactionFailedOnchainEvent(
        transactionId, idempotencyKey, referenceType, referenceId, 1L, 2L, null, failureReason);
  }
}
