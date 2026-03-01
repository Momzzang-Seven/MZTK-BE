package momzzangseven.mztkbe.modules.web3.transaction.domain.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import org.junit.jupiter.api.Test;

class Web3TransactionSucceededEventTest {

  @Test
  void constructor_acceptsValidPayloadWithAndWithoutTxHash() {
    assertThatCode(
            () ->
                new Web3TransactionSucceededEvent(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    null))
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                new Web3TransactionSucceededEvent(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(64)))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsInvalidCoreFields() {
    assertThatThrownBy(() -> eventWith(null, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    assertThatThrownBy(() -> eventWith(0L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    assertThatThrownBy(() -> eventWith(1L, null, Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");

    assertThatThrownBy(() -> eventWith(1L, " ", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");

    assertThatThrownBy(() -> eventWith(1L, "idem-1", null, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");

    assertThatThrownBy(() -> eventWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");

    assertThatThrownBy(() -> eventWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsInvalidTxHashFormatWhenProvided() {
    assertThatThrownBy(
            () ->
                new Web3TransactionSucceededEvent(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x1234"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash must be 0x-prefixed 32-byte hex");
  }

  private Web3TransactionSucceededEvent eventWith(
      Long transactionId, String idempotencyKey, Web3ReferenceType referenceType, String referenceId) {
    return new Web3TransactionSucceededEvent(
        transactionId, idempotencyKey, referenceType, referenceId, 1L, 2L, null);
  }
}

