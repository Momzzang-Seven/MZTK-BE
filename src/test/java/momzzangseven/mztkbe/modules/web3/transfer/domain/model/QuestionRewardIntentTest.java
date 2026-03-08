package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class QuestionRewardIntentTest {

  @Test
  void assertResolvableBy_passesForOwnerWithValidPayload() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    intent.assertResolvableBy(7L);

    assertThat(intent.isImmutableForRegister()).isFalse();
  }

  @Test
  void assertResolvableBy_throwsWhenRequesterIsNotOwner() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(999L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("only question owner can prepare question reward");
  }

  @Test
  void assertResolvableBy_throwsWhenRequesterNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("only question owner can prepare question reward");
  }

  @Test
  void assertResolvableBy_throwsWhenSubmitted() {
    QuestionRewardIntent intent = baseIntent().status(QuestionRewardIntentStatus.SUBMITTED).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward is already in submitted state");
  }

  @Test
  void assertResolvableBy_throwsWhenSucceeded() {
    QuestionRewardIntent intent = baseIntent().status(QuestionRewardIntentStatus.SUCCEEDED).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward is already settled for this post");
  }

  @Test
  void assertResolvableBy_throwsWhenCanceled() {
    QuestionRewardIntent intent = baseIntent().status(QuestionRewardIntentStatus.CANCELED).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent is canceled");
  }

  @Test
  void assertResolvableBy_throwsWhenFailedOnchain() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.FAILED_ONCHAIN).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward failed onchain; re-register intent");
  }

  @Test
  void assertResolvableBy_throwsWhenFromUserInvalid() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).fromUserId(0L).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid fromUserId");
  }

  @Test
  void assertResolvableBy_throwsWhenFromUserNegative() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).fromUserId(-1L).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid fromUserId");
  }

  @Test
  void assertResolvableBy_throwsWhenFromUserNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).fromUserId(null).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid fromUserId");
  }

  @Test
  void assertResolvableBy_throwsWhenToUserInvalid() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).toUserId(0L).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("accepted answer has invalid writer userId");
  }

  @Test
  void assertResolvableBy_throwsWhenToUserNegative() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).toUserId(-1L).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("accepted answer has invalid writer userId");
  }

  @Test
  void assertResolvableBy_throwsWhenToUserNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).toUserId(null).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("accepted answer has invalid writer userId");
  }

  @Test
  void assertResolvableBy_throwsWhenAcceptedCommentInvalid() {
    QuestionRewardIntent intent =
        baseIntent()
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .acceptedCommentId(0L)
            .build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid acceptedCommentId");
  }

  @Test
  void assertResolvableBy_throwsWhenAcceptedCommentNull() {
    QuestionRewardIntent intent =
        baseIntent()
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .acceptedCommentId(null)
            .build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid acceptedCommentId");
  }

  @Test
  void assertResolvableBy_throwsWhenAmountInvalid() {
    QuestionRewardIntent intent =
        baseIntent()
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .amountWei(BigInteger.ZERO)
            .build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid amountWei");
  }

  @Test
  void assertResolvableBy_throwsWhenAmountNegative() {
    QuestionRewardIntent intent =
        baseIntent()
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .amountWei(BigInteger.valueOf(-1))
            .build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid amountWei");
  }

  @Test
  void assertResolvableBy_throwsWhenAmountNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).amountWei(null).build();

    assertThatThrownBy(() -> intent.assertResolvableBy(7L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent has invalid amountWei");
  }

  @Test
  void cancel_andCannotCancel_stateBehavior() {
    QuestionRewardIntent canceled =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build().cancel();

    assertThat(canceled.getStatus()).isEqualTo(QuestionRewardIntentStatus.CANCELED);
    assertThat(canceled.cannotCancel()).isTrue();
  }

  @Test
  void withPrepareRequired_updatesPayloadAndStatus() {
    QuestionRewardIntent intent = baseIntent().status(QuestionRewardIntentStatus.SUCCEEDED).build();

    QuestionRewardIntent updated =
        intent.withPrepareRequired(301L, 70L, 220L, new BigInteger("2000"));

    assertThat(updated.getAcceptedCommentId()).isEqualTo(301L);
    assertThat(updated.getFromUserId()).isEqualTo(70L);
    assertThat(updated.getToUserId()).isEqualTo(220L);
    assertThat(updated.getAmountWei()).isEqualTo(new BigInteger("2000"));
    assertThat(updated.getStatus()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
  }

  @Test
  void isSamePayload_returnsTrue_whenAllFieldsMatch() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    boolean same = intent.isSamePayload(201L, 7L, 22L, new BigInteger("1000"));

    assertThat(same).isTrue();
  }

  @Test
  void isSamePayload_returnsFalse_whenAmountInIntentIsNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).amountWei(null).build();

    boolean same = intent.isSamePayload(201L, 7L, 22L, new BigInteger("1000"));

    assertThat(same).isFalse();
  }

  @Test
  void isSamePayload_returnsFalse_whenAnyFieldDiffers() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThat(intent.isSamePayload(999L, 7L, 22L, new BigInteger("1000"))).isFalse();
    assertThat(intent.isSamePayload(201L, 999L, 22L, new BigInteger("1000"))).isFalse();
    assertThat(intent.isSamePayload(201L, 7L, 999L, new BigInteger("1000"))).isFalse();
    assertThat(intent.isSamePayload(201L, 7L, 22L, new BigInteger("999"))).isFalse();
  }

  @Test
  void isStaleCancelRequest_returnsFalse_whenRequestAcceptedCommentIdNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThat(intent.isStaleCancelRequest(null)).isFalse();
  }

  @Test
  void isStaleCancelRequest_returnsTrue_whenAcceptedCommentDifferent() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThat(intent.isStaleCancelRequest(999L)).isTrue();
  }

  @Test
  void isStaleCancelRequest_returnsFalse_whenAcceptedCommentSame() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThat(intent.isStaleCancelRequest(201L)).isFalse();
  }

  @Test
  void cannotCancel_trueForSubmittedAndSucceeded_falseForPrepareRequired() {
    QuestionRewardIntent submitted =
        baseIntent().status(QuestionRewardIntentStatus.SUBMITTED).build();
    QuestionRewardIntent succeeded =
        baseIntent().status(QuestionRewardIntentStatus.SUCCEEDED).build();
    QuestionRewardIntent prepareRequired =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    assertThat(submitted.cannotCancel()).isTrue();
    assertThat(succeeded.cannotCancel()).isTrue();
    assertThat(prepareRequired.cannotCancel()).isFalse();
  }

  @Test
  void isImmutableForRegister_trueForSubmittedAndSucceeded_falseForFailedOnchain() {
    QuestionRewardIntent submitted =
        baseIntent().status(QuestionRewardIntentStatus.SUBMITTED).build();
    QuestionRewardIntent succeeded =
        baseIntent().status(QuestionRewardIntentStatus.SUCCEEDED).build();
    QuestionRewardIntent failed =
        baseIntent().status(QuestionRewardIntentStatus.FAILED_ONCHAIN).build();

    assertThat(submitted.isImmutableForRegister()).isTrue();
    assertThat(succeeded.isImmutableForRegister()).isTrue();
    assertThat(failed.isImmutableForRegister()).isFalse();
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenPrepareStale() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();
    TransferPrepare stalePrepare =
        TransferPrepare.builder()
            .prepareId("p-1")
            .fromUserId(7L)
            .toUserId(30L)
            .acceptedCommentId(201L)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey("domain:QUESTION_REWARD:101:7")
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(new BigInteger("500"))
            .authorityNonce(1L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(java.time.LocalDateTime.now().plusMinutes(5))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .status(TransferPrepareStatus.CREATED)
            .build();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(stalePrepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("stale");
  }

  @Test
  void assertSubmittableByPrepare_passes_whenPrepareAcceptedCommentIsNullButOthersMatch() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();
    TransferPrepare prepare =
        TransferPrepare.builder()
            .prepareId("p-1")
            .fromUserId(7L)
            .toUserId(22L)
            .acceptedCommentId(null)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey("domain:QUESTION_REWARD:101:7")
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(new BigInteger("1000"))
            .authorityNonce(1L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(java.time.LocalDateTime.now().plusMinutes(5))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .status(TransferPrepareStatus.CREATED)
            .build();

    intent.assertSubmittableByPrepare(prepare);
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenIntentCanceled() {
    QuestionRewardIntent intent = baseIntent().status(QuestionRewardIntentStatus.CANCELED).build();
    TransferPrepare prepare = matchingPrepare();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent is canceled");
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenIntentSucceeded() {
    QuestionRewardIntent intent = baseIntent().status(QuestionRewardIntentStatus.SUCCEEDED).build();
    TransferPrepare prepare = matchingPrepare();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward is already settled");
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenToUserMismatch() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();
    TransferPrepare prepare = matchingPrepare().toBuilder().toUserId(999L).build();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepared transfer session is stale");
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenAmountMismatch() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();
    TransferPrepare prepare =
        matchingPrepare().toBuilder().amountWei(new BigInteger("999")).build();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepared transfer session is stale");
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenAcceptedCommentMismatchOnly() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();
    TransferPrepare prepare = matchingPrepare().toBuilder().acceptedCommentId(999L).build();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepared transfer session is stale");
  }

  @Test
  void assertSubmittableByPrepare_throwsWhenIntentAmountNull() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).amountWei(null).build();
    TransferPrepare prepare = matchingPrepare();

    assertThatThrownBy(() -> intent.assertSubmittableByPrepare(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepared transfer session is stale");
  }

  @Test
  void assertSubmittableByPrepare_passes_whenAllFieldsMatch() {
    QuestionRewardIntent intent =
        baseIntent().status(QuestionRewardIntentStatus.PREPARE_REQUIRED).build();

    intent.assertSubmittableByPrepare(matchingPrepare());
  }

  private QuestionRewardIntent.QuestionRewardIntentBuilder baseIntent() {
    return QuestionRewardIntent.builder()
        .id(1L)
        .postId(101L)
        .acceptedCommentId(201L)
        .fromUserId(7L)
        .toUserId(22L)
        .amountWei(new BigInteger("1000"));
  }

  private TransferPrepare matchingPrepare() {
    return TransferPrepare.builder()
        .prepareId("p-1")
        .fromUserId(7L)
        .toUserId(22L)
        .acceptedCommentId(201L)
        .referenceType(TokenTransferReferenceType.USER_TO_USER)
        .referenceId("101")
        .idempotencyKey("domain:QUESTION_REWARD:101:7")
        .authorityAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(new BigInteger("1000"))
        .authorityNonce(1L)
        .delegateTarget("0x" + "c".repeat(40))
        .authExpiresAt(java.time.LocalDateTime.now().plusMinutes(5))
        .payloadHashToSign("0x" + "d".repeat(64))
        .salt("0x" + "e".repeat(64))
        .status(TransferPrepareStatus.CREATED)
        .build();
  }
}
