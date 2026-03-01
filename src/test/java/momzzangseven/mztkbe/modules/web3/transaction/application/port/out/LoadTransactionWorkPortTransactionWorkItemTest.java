package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import org.junit.jupiter.api.Test;

class LoadTransactionWorkPortTransactionWorkItemTest {

  @Test
  void constructor_acceptsValidItem() {
    assertThatCode(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ZERO,
                    null,
                    "0x" + "c".repeat(64),
                    "0xdeadbeef",
                    null,
                    LocalDateTime.now()))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsNullTransactionId() {
    assertThatThrownBy(() -> itemWith(null, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_rejectsNonPositiveTransactionId() {
    assertThatThrownBy(() -> itemWith(0L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void constructor_rejectsNullIdempotencyKey() {
    assertThatThrownBy(() -> itemWith(1L, null, Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void constructor_rejectsBlankIdempotencyKey() {
    assertThatThrownBy(() -> itemWith(1L, " ", Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void constructor_rejectsNullReferenceType() {
    assertThatThrownBy(() -> itemWith(1L, "idem-1", null, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");
  }

  @Test
  void constructor_rejectsNullReferenceId() {
    assertThatThrownBy(() -> itemWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsBlankReferenceId() {
    assertThatThrownBy(() -> itemWith(1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsNullFromAddress() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    null,
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    0L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void constructor_rejectsBlankFromAddress() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    " ",
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    0L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void constructor_rejectsNullToAddress() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    null,
                    BigInteger.ONE,
                    0L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toAddress is required");
  }

  @Test
  void constructor_rejectsBlankToAddress() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    " ",
                    BigInteger.ONE,
                    0L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toAddress is required");
  }

  @Test
  void constructor_rejectsNullAmountWei() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    null,
                    0L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be non-negative");
  }

  @Test
  void constructor_rejectsNegativeAmountWei() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.valueOf(-1),
                    0L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be non-negative");
  }

  @Test
  void constructor_rejectsNegativeNonce() {
    assertThatThrownBy(
            () ->
                new LoadTransactionWorkPort.TransactionWorkItem(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    -1L,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  private LoadTransactionWorkPort.TransactionWorkItem itemWith(
      Long transactionId, String idempotencyKey, Web3ReferenceType referenceType, String referenceId) {
    return new LoadTransactionWorkPort.TransactionWorkItem(
        transactionId,
        idempotencyKey,
        referenceType,
        referenceId,
        1L,
        2L,
        "0x" + "a".repeat(40),
        "0x" + "b".repeat(40),
        BigInteger.ONE,
        0L,
        "0x" + "c".repeat(64),
        "0xdeadbeef",
        null,
        LocalDateTime.now());
  }
}

