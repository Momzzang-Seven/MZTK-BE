package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class TransferPrepareTest {

  @Test
  void isActiveAt_trueWhenBeforeExpiry() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null);

    assertThat(prepare.isActiveAt(LocalDateTime.now())).isTrue();
  }

  @Test
  void isActiveAt_falseWhenExpiryIsNull() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null).toBuilder().authExpiresAt(null).build();

    assertThat(prepare.isActiveAt(LocalDateTime.now())).isFalse();
  }

  @Test
  void isActiveAt_falseWhenNowEqualsExpiry() {
    LocalDateTime now = LocalDateTime.now();
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null).toBuilder().authExpiresAt(now).build();

    assertThat(prepare.isActiveAt(now)).isFalse();
  }

  @Test
  void submit_movesToSubmittedAndStoresTxId() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null);

    TransferPrepare submitted = prepare.submit(33L);

    assertThat(submitted.getStatus()).isEqualTo(TransferPrepareStatus.SUBMITTED);
    assertThat(submitted.getSubmittedTxId()).isEqualTo(33L);
    assertThat(submitted.isSubmittedWithTransaction()).isTrue();
  }

  @Test
  void isSubmittedWithTransaction_falseWhenSubmittedButTxIdNull() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.SUBMITTED, null);

    assertThat(prepare.isSubmittedWithTransaction()).isFalse();
  }

  @Test
  void expire_movesToExpired_whenCreated() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null);

    TransferPrepare expired = prepare.expire();

    assertThat(expired.getStatus()).isEqualTo(TransferPrepareStatus.EXPIRED);
  }

  @Test
  void expire_throwsWhenAlreadySubmitted() {
    TransferPrepare submitted = basePrepare(TransferPrepareStatus.SUBMITTED, 33L);

    assertThatThrownBy(submitted::expire)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("submitted transfer prepare cannot be expired");
  }

  @Test
  void expire_throwsWhenAlreadyExpired() {
    TransferPrepare expired = basePrepare(TransferPrepareStatus.EXPIRED, null);

    assertThatThrownBy(expired::expire)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transfer prepare is already expired");
  }

  @Test
  void submit_throwsWhenTxIdInvalid() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null);

    assertThatThrownBy(() -> prepare.submit(0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txId must be positive");
  }

  @Test
  void submit_throwsWhenTxIdNull() {
    TransferPrepare prepare = basePrepare(TransferPrepareStatus.CREATED, null);

    assertThatThrownBy(() -> prepare.submit(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txId must be positive");
  }

  @Test
  void submit_throwsWhenAlreadySubmitted() {
    TransferPrepare submitted = basePrepare(TransferPrepareStatus.SUBMITTED, 33L);

    assertThatThrownBy(() -> submitted.submit(34L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transfer prepare is already submitted");
  }

  @Test
  void submit_throwsWhenExpired() {
    TransferPrepare expired = basePrepare(TransferPrepareStatus.EXPIRED, null);

    assertThatThrownBy(() -> expired.submit(34L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("expired transfer prepare cannot be submitted");
  }

  private TransferPrepare basePrepare(TransferPrepareStatus status, Long submittedTxId) {
    return TransferPrepare.builder()
        .prepareId("prepare-1")
        .fromUserId(7L)
        .toUserId(22L)
        .acceptedCommentId(201L)
        .referenceType(TokenTransferReferenceType.USER_TO_USER)
        .referenceId("101")
        .idempotencyKey("domain:QUESTION_REWARD:101:7")
        .authorityAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.TEN)
        .authorityNonce(1L)
        .delegateTarget("0x" + "c".repeat(40))
        .authExpiresAt(LocalDateTime.now().plusMinutes(5))
        .payloadHashToSign("0x" + "d".repeat(64))
        .salt("0x" + "e".repeat(64))
        .status(status)
        .submittedTxId(submittedTxId)
        .build();
  }
}
