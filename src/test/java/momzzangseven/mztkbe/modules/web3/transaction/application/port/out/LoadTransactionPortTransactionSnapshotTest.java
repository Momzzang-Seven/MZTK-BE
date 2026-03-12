package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.Test;

class LoadTransactionPortTransactionSnapshotTest {

  @Test
  void constructor_acceptsValidSnapshot() {
    assertThatCode(
            () ->
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    Web3TxStatus.PENDING,
                    "0x" + "a".repeat(64),
                    null))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_acceptsNullOrBlankTxHash() {
    assertThatCode(
            () ->
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    Web3TxStatus.CREATED,
                    null,
                    null))
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    Web3TxStatus.CREATED,
                    " ",
                    null))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsNullTransactionId() {
    assertThatThrownBy(() -> snapshotWith(null, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_rejectsNonPositiveTransactionId() {
    assertThatThrownBy(() -> snapshotWith(0L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_rejectsNullIdempotencyKey() {
    assertThatThrownBy(() -> snapshotWith(1L, null, Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void constructor_rejectsBlankIdempotencyKey() {
    assertThatThrownBy(() -> snapshotWith(1L, " ", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void constructor_rejectsNullReferenceType() {
    assertThatThrownBy(() -> snapshotWith(1L, "idem-1", null, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");
  }

  @Test
  void constructor_rejectsNullReferenceId() {
    assertThatThrownBy(() -> snapshotWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsBlankReferenceId() {
    assertThatThrownBy(() -> snapshotWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsNullStatus() {
    assertThatThrownBy(
            () ->
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void constructor_rejectsInvalidTxHashFormat() {
    assertThatThrownBy(
            () ->
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    Web3TxStatus.PENDING,
                    "0x1234",
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash must be 0x-prefixed 32-byte hex");
  }

  private LoadTransactionPort.TransactionSnapshot snapshotWith(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId) {
    return new LoadTransactionPort.TransactionSnapshot(
        transactionId,
        idempotencyKey,
        referenceType,
        referenceId,
        1L,
        2L,
        Web3TxStatus.CREATED,
        null,
        null);
  }
}
