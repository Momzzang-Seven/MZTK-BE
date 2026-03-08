package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import org.junit.jupiter.api.Test;

class Web3TransactionTest {

  @Test
  void createIntent_setsCreatedStatusAndReferenceKey() {
    LocalDateTime now = LocalDateTime.now();

    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-1",
            Web3ReferenceType.LEVEL_UP_REWARD,
            "123",
            null,
            7L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ZERO,
            now);

    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.CREATED);
    assertThat(tx.referenceKey()).isEqualTo("LEVEL_UP_REWARD:123");
    assertThat(tx.getCreatedAt()).isEqualTo(now);
  }

  @Test
  void createIntent_throws_whenReferenceTypeNull() {
    LocalDateTime now = LocalDateTime.now();

    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem-1",
                    null,
                    "123",
                    1L,
                    7L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ZERO,
                    now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");
  }

  @Test
  void createIntent_throws_whenReferenceIdBlank() {
    LocalDateTime now = LocalDateTime.now();

    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    " ",
                    1L,
                    7L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ZERO,
                    now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void createIntent_throws_whenNowNull() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "123",
                    1L,
                    7L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ZERO,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("now is required");
  }

  @Test
  void reconstitute_throws_whenStatusNull() {
    LocalDateTime now = LocalDateTime.now();

    assertThatThrownBy(
            () ->
                Web3Transaction.reconstitute(
                    1L,
                    "idem-1",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    10L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    now,
                    now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void markSigned_andMarkPending_progressStateAndTimestamps() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    tx.markSigned(10L, "0xf86c", "0x" + "c".repeat(64), now.plusSeconds(1));
    tx.markPending("0x" + "d".repeat(64), now.plusSeconds(2));

    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.PENDING);
    assertThat(tx.getNonce()).isEqualTo(10L);
    assertThat(tx.getSignedAt()).isNotNull();
    assertThat(tx.getBroadcastedAt()).isNotNull();
  }

  @Test
  void markSigned_throws_whenRawTxBlank() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.markSigned(10L, " ", "0x" + "c".repeat(64), now.plusSeconds(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signedRawTx is required");
  }

  @Test
  void updateStatus_requiresFailureReasonForUnconfirmed() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            1L,
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            10L,
            Web3TxStatus.PENDING,
            "0x" + "c".repeat(64),
            now,
            now,
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(
            () -> tx.updateStatus(Web3TxStatus.UNCONFIRMED, null, " ", now.plusSeconds(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required for UNCONFIRMED status");
  }

  @Test
  void assignNonce_throwsWhenAlreadyAssignedDifferentNonce() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            1L,
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            5L,
            Web3TxStatus.CREATED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(() -> tx.assignNonce(6L))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("nonce already assigned");
  }

  @Test
  void assignNonce_throwsWhenNonceNegative() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.assignNonce(-1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void assignNonce_throwsWhenStatusIsNotCreated() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            1L,
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            5L,
            Web3TxStatus.SIGNED,
            null,
            null,
            null,
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(() -> tx.assignNonce(5L))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("nonce can only be assigned in CREATED status");
  }

  @Test
  void assignNonce_allowsSameAssignedNonce_whenAlreadySet() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            1L,
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            5L,
            Web3TxStatus.CREATED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            now);

    tx.assignNonce(5L);

    assertThat(tx.getNonce()).isEqualTo(5L);
  }

  @Test
  void assignNonce_setsNonce_whenInitiallyNull() {
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-set",
            Web3ReferenceType.USER_TO_USER,
            "ref-set",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            LocalDateTime.now());

    tx.assignNonce(9L);

    assertThat(tx.getNonce()).isEqualTo(9L);
  }

  @Test
  void scheduleRetry_setsFieldsForNonFinalStatus() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime until = now.plusMinutes(3);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            1L,
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            5L,
            Web3TxStatus.PENDING,
            "0x" + "d".repeat(64),
            now,
            now,
            null,
            "0xf86c",
            null,
            now.plusMinutes(1),
            "worker-1",
            now,
            now);

    tx.scheduleRetry("retry reason", until);

    assertThat(tx.getFailureReason()).isEqualTo("retry reason");
    assertThat(tx.getProcessingBy()).isNull();
    assertThat(tx.getProcessingUntil()).isEqualTo(until);
  }

  @Test
  void scheduleRetry_noopsForFailedOnchain() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            6L,
            "idem-15",
            Web3ReferenceType.USER_TO_USER,
            "ref-15",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            5L,
            Web3TxStatus.FAILED_ONCHAIN,
            "0x" + "d".repeat(64),
            now.minusMinutes(2),
            now.minusMinutes(1),
            now,
            "0xf86c",
            "failed",
            null,
            null,
            now,
            now);

    tx.scheduleRetry("retry", now.plusMinutes(1));

    assertThat(tx.getFailureReason()).isEqualTo("failed");
  }

  @Test
  void createIntent_throws_whenIdempotencyKeyBlank() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    " ",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void createIntent_throws_whenFromAddressBlank() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    " ",
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void createIntent_throws_whenToAddressBlank() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    " ",
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toAddress is required");
  }

  @Test
  void createIntent_throws_whenAmountNegative() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.valueOf(-1),
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be >= 0");
  }

  @Test
  void createIntent_throws_whenReferenceIdNull() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    null,
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void createIntent_throws_whenIdempotencyKeyNull() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    null,
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void createIntent_throws_whenFromAddressNull() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    null,
                    "0x" + "b".repeat(40),
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void createIntent_throws_whenToAddressNull() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    null,
                    BigInteger.ONE,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toAddress is required");
  }

  @Test
  void createIntent_throws_whenAmountNull() {
    assertThatThrownBy(
            () ->
                Web3Transaction.createIntent(
                    "idem",
                    Web3ReferenceType.USER_TO_USER,
                    "ref-1",
                    1L,
                    2L,
                    "0x" + "a".repeat(40),
                    "0x" + "b".repeat(40),
                    null,
                    LocalDateTime.now()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be >= 0");
  }

  @Test
  void markSigned_throws_whenNonceNegative() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-9",
            Web3ReferenceType.USER_TO_USER,
            "ref-9",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.markSigned(-1L, "0xraw", null, now.plusSeconds(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void markSigned_throws_whenCurrentStatusIsNotCreated() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            2L,
            "idem-10",
            Web3ReferenceType.USER_TO_USER,
            "ref-10",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            3L,
            Web3TxStatus.SIGNED,
            null,
            now.minusMinutes(1),
            null,
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(() -> tx.markSigned(3L, "0xraw", null, now.plusSeconds(1)))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("markSigned requires CREATED status");
  }

  @Test
  void markSigned_doesNotOverwriteHash_whenBlankHashProvided() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-hash",
            Web3ReferenceType.USER_TO_USER,
            "ref-hash",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    tx.markSigned(10L, "0xraw", " ", now.plusSeconds(1));

    assertThat(tx.getTxHash()).isNull();
  }

  @Test
  void markPending_throws_whenCurrentStatusIsNotSigned() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-11",
            Web3ReferenceType.USER_TO_USER,
            "ref-11",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.markPending("0x" + "d".repeat(64), now.plusSeconds(1)))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("markPending requires SIGNED status");
  }

  @Test
  void markPending_throws_whenHashBlank() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            3L,
            "idem-12",
            Web3ReferenceType.USER_TO_USER,
            "ref-12",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            4L,
            Web3TxStatus.SIGNED,
            null,
            now.minusMinutes(1),
            null,
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(() -> tx.markPending(" ", now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void markPending_keepsBroadcastedAt_whenAlreadySet() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime broadcastedAt = now.minusMinutes(1);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            7L,
            "idem-pending",
            Web3ReferenceType.USER_TO_USER,
            "ref-pending",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            4L,
            Web3TxStatus.SIGNED,
            null,
            now.minusMinutes(2),
            broadcastedAt,
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    tx.markPending("0x" + "f".repeat(64), now);

    assertThat(tx.getBroadcastedAt()).isEqualTo(broadcastedAt);
  }

  @Test
  void updateStatus_throws_whenNowNull() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            4L,
            "idem-13",
            Web3ReferenceType.USER_TO_USER,
            "ref-13",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            4L,
            Web3TxStatus.PENDING,
            "0x" + "d".repeat(64),
            now.minusMinutes(2),
            now.minusMinutes(1),
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.SUCCEEDED, null, null, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("now is required");
  }

  @Test
  void updateStatus_setsConfirmedAt_forFailedOnchain() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            5L,
            "idem-14",
            Web3ReferenceType.USER_TO_USER,
            "ref-14",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            4L,
            Web3TxStatus.PENDING,
            "0x" + "d".repeat(64),
            now.minusMinutes(2),
            now.minusMinutes(1),
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    tx.updateStatus(Web3TxStatus.FAILED_ONCHAIN, null, "failed", now);

    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.FAILED_ONCHAIN);
    assertThat(tx.getConfirmedAt()).isEqualTo(now);
  }

  @Test
  void updateStatus_allowsCreatedToSignedAndSignedToPending() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-trans",
            Web3ReferenceType.USER_TO_USER,
            "ref-trans",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now.minusMinutes(1));

    tx.updateStatus(Web3TxStatus.SIGNED, null, null, now);
    tx.updateStatus(Web3TxStatus.PENDING, "0x" + "a".repeat(64), null, now.plusSeconds(1));

    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.PENDING);
  }

  @Test
  void updateStatus_throws_whenInvalidTransitionFromCreatedToPending() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-invalid",
            Web3ReferenceType.USER_TO_USER,
            "ref-invalid",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.PENDING, null, null, now.plusSeconds(1)))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("invalid transition");
  }

  @Test
  void scheduleRetry_noopsForFinalStatus() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            1L,
            "idem-1",
            Web3ReferenceType.USER_TO_USER,
            "ref-1",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            5L,
            Web3TxStatus.SUCCEEDED,
            "0x" + "d".repeat(64),
            now,
            now,
            now,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    tx.scheduleRetry("retry reason", now.plusMinutes(5));

    assertThat(tx.getFailureReason()).isNull();
    assertThat(tx.getProcessingUntil()).isNull();
  }

  @Test
  void markSigned_throws_whenNowNullAndSignedAtMissing() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-2",
            Web3ReferenceType.USER_TO_USER,
            "ref-2",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.markSigned(3L, "0xabc", "0x" + "c".repeat(64), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("now is required");
  }

  @Test
  void markSigned_keepsExistingSignedAt_whenAlreadySet() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime signedAt = now.minusMinutes(1);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            10L,
            "idem-3",
            Web3ReferenceType.USER_TO_USER,
            "ref-3",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            null,
            Web3TxStatus.CREATED,
            null,
            signedAt,
            null,
            null,
            null,
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.markSigned(4L, "0xdef", null, now.plusMinutes(1));

    assertThat(tx.getSignedAt()).isEqualTo(signedAt);
  }

  @Test
  void markPending_throws_whenNowNullAndBroadcastedAtMissing() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            11L,
            "idem-4",
            Web3ReferenceType.USER_TO_USER,
            "ref-4",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.SIGNED,
            null,
            now.minusMinutes(2),
            null,
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.markPending("0x" + "d".repeat(64), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("now is required");
  }

  @Test
  void updateStatus_throws_whenNextStatusNull() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            12L,
            "idem-5",
            Web3ReferenceType.USER_TO_USER,
            "ref-5",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.PENDING,
            "0x" + "e".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(null, null, null, now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nextStatus is required");
  }

  @Test
  void updateStatus_throws_whenSameStateTransition() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-6",
            Web3ReferenceType.USER_TO_USER,
            "ref-6",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.CREATED, null, null, now.plusSeconds(1)))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("same-state transition is not allowed");
  }

  @Test
  void updateStatus_allowsPendingToUnconfirmed_thenUnconfirmedToSucceeded() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            13L,
            "idem-7",
            Web3ReferenceType.USER_TO_USER,
            "ref-7",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            3L,
            Web3TxStatus.PENDING,
            "0x" + "f".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.updateStatus(Web3TxStatus.UNCONFIRMED, null, "poll timeout", now.minusMinutes(1));
    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.UNCONFIRMED);
    assertThat(tx.getFailureReason()).isEqualTo("poll timeout");

    tx.updateStatus(Web3TxStatus.SUCCEEDED, "0x" + "1".repeat(64), null, now);
    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.SUCCEEDED);
    assertThat(tx.getConfirmedAt()).isEqualTo(now);
  }

  @Test
  void updateStatus_throws_whenFinalStateTransitions() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            14L,
            "idem-8",
            Web3ReferenceType.USER_TO_USER,
            "ref-8",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            3L,
            Web3TxStatus.SUCCEEDED,
            "0x" + "f".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            now.minusMinutes(1),
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.PENDING, null, null, now))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("invalid transition");
  }

  @Test
  void updateStatus_throws_whenFailedOnchainTransitions() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            21L,
            "idem-final-failed",
            Web3ReferenceType.USER_TO_USER,
            "ref-final-failed",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            3L,
            Web3TxStatus.FAILED_ONCHAIN,
            "0x" + "f".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            now.minusMinutes(1),
            "0xf86c",
            "onchain failed",
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.PENDING, null, null, now))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("invalid transition");
  }

  @Test
  void markSigned_allowsNowNull_whenSignedAtAlreadyPresent() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime signedAt = now.minusMinutes(1);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            10L,
            "idem-3b",
            Web3ReferenceType.USER_TO_USER,
            "ref-3b",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            null,
            Web3TxStatus.CREATED,
            null,
            signedAt,
            null,
            null,
            null,
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.markSigned(4L, "0xdef", null, null);

    assertThat(tx.getSignedAt()).isEqualTo(signedAt);
  }

  @Test
  void markPending_throws_whenHashNull() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            3L,
            "idem-12b",
            Web3ReferenceType.USER_TO_USER,
            "ref-12b",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            4L,
            Web3TxStatus.SIGNED,
            null,
            now.minusMinutes(1),
            null,
            null,
            "0xf86c",
            null,
            null,
            null,
            now,
            now);

    assertThatThrownBy(() -> tx.markPending(null, now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void updateStatus_throws_whenUnconfirmedReasonNull() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            12L,
            "idem-5b",
            Web3ReferenceType.USER_TO_USER,
            "ref-5b",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.PENDING,
            "0x" + "e".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.UNCONFIRMED, null, null, now))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required for UNCONFIRMED status");
  }

  @Test
  void updateStatus_keepsExistingTimestampsAndHash_whenBlankHashProvided() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime signedAt = now.minusMinutes(3);
    LocalDateTime broadcastedAt = now.minusMinutes(2);
    LocalDateTime confirmedAt = now.minusMinutes(1);
    String originalHash = "0x" + "f".repeat(64);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            15L,
            "idem-15b",
            Web3ReferenceType.USER_TO_USER,
            "ref-15b",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            3L,
            Web3TxStatus.PENDING,
            originalHash,
            signedAt,
            broadcastedAt,
            confirmedAt,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.updateStatus(Web3TxStatus.SUCCEEDED, " ", null, now);

    assertThat(tx.getTxHash()).isEqualTo(originalHash);
    assertThat(tx.getSignedAt()).isEqualTo(signedAt);
    assertThat(tx.getBroadcastedAt()).isEqualTo(broadcastedAt);
    assertThat(tx.getConfirmedAt()).isEqualTo(confirmedAt);
  }

  @Test
  void markSigned_throws_whenRawTxNull() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.createIntent(
            "idem-raw-null",
            Web3ReferenceType.USER_TO_USER,
            "ref-raw-null",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            now);

    assertThatThrownBy(() -> tx.markSigned(1L, null, null, now.plusSeconds(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signedRawTx is required");
  }

  @Test
  void updateStatus_keepsSignedAt_whenNextSignedAndSignedAtAlreadySet() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime signedAt = now.minusMinutes(1);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            16L,
            "idem-signed-keep",
            Web3ReferenceType.USER_TO_USER,
            "ref-signed-keep",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            null,
            Web3TxStatus.CREATED,
            null,
            signedAt,
            null,
            null,
            null,
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.updateStatus(Web3TxStatus.SIGNED, null, null, now);

    assertThat(tx.getSignedAt()).isEqualTo(signedAt);
  }

  @Test
  void updateStatus_keepsBroadcastedAt_whenNextPendingAndAlreadySet() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime signedAt = now.minusMinutes(2);
    LocalDateTime broadcastedAt = now.minusMinutes(1);
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            17L,
            "idem-pending-keep",
            Web3ReferenceType.USER_TO_USER,
            "ref-pending-keep",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.SIGNED,
            "0x" + "e".repeat(64),
            signedAt,
            broadcastedAt,
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.updateStatus(Web3TxStatus.PENDING, null, null, now);

    assertThat(tx.getBroadcastedAt()).isEqualTo(broadcastedAt);
  }

  @Test
  void updateStatus_allowsPendingToSucceeded_directly() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            18L,
            "idem-pending-succeeded",
            Web3ReferenceType.USER_TO_USER,
            "ref-pending-succeeded",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.PENDING,
            "0x" + "e".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    tx.updateStatus(Web3TxStatus.SUCCEEDED, null, null, now);

    assertThat(tx.getStatus()).isEqualTo(Web3TxStatus.SUCCEEDED);
    assertThat(tx.getConfirmedAt()).isEqualTo(now);
  }

  @Test
  void updateStatus_throws_whenSignedToSucceededIsInvalid() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            19L,
            "idem-signed-invalid",
            Web3ReferenceType.USER_TO_USER,
            "ref-signed-invalid",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.SIGNED,
            "0x" + "e".repeat(64),
            now.minusMinutes(3),
            null,
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.SUCCEEDED, null, null, now))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("invalid transition");
  }

  @Test
  void updateStatus_throws_whenPendingToSignedIsInvalid() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            22L,
            "idem-pending-invalid",
            Web3ReferenceType.USER_TO_USER,
            "ref-pending-invalid",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.PENDING,
            "0x" + "e".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            null,
            "0xf86c",
            null,
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.SIGNED, null, null, now))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("invalid transition");
  }

  @Test
  void updateStatus_throws_whenUnconfirmedToPendingIsInvalid() {
    LocalDateTime now = LocalDateTime.now();
    Web3Transaction tx =
        Web3Transaction.reconstitute(
            20L,
            "idem-unconfirmed-invalid",
            Web3ReferenceType.USER_TO_USER,
            "ref-unconfirmed-invalid",
            1L,
            2L,
            "0x" + "a".repeat(40),
            "0x" + "b".repeat(40),
            BigInteger.ONE,
            7L,
            Web3TxStatus.UNCONFIRMED,
            "0x" + "e".repeat(64),
            now.minusMinutes(3),
            now.minusMinutes(2),
            null,
            "0xf86c",
            "retry",
            null,
            null,
            now.minusDays(1),
            now.minusDays(1));

    assertThatThrownBy(() -> tx.updateStatus(Web3TxStatus.PENDING, null, null, now))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("invalid transition");
  }
}
