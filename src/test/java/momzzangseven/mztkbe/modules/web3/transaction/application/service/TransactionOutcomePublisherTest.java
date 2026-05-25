package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TransactionOutcomePublisherTest {

  private static final Long TX_ID = 10L;
  private static final String IDEMPOTENCY_KEY = "domain:QUESTION_REWARD:101:1";
  private static final Web3ReferenceType REFERENCE_TYPE = Web3ReferenceType.USER_TO_USER;
  private static final String REFERENCE_ID = "101";
  private static final Long FROM_USER_ID = 1L;
  private static final Long TO_USER_ID = 2L;
  private static final long CHAIN_ID = 11155111L;
  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);
  private static final String TX_HASH =
      "0x1234567890123456789012345678901234567890123456789012345678901234";

  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  @Mock private ApplicationEventPublisher eventPublisher;

  private TransactionOutcomePublisher service;

  @BeforeEach
  void setUp() {
    service =
        new TransactionOutcomePublisher(
            updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_updatesStatusAndPublishesSucceededEvent() {
    service.markSucceededAndPublish(
        TX_ID, IDEMPOTENCY_KEY, REFERENCE_TYPE, REFERENCE_ID, FROM_USER_ID, TO_USER_ID, TX_HASH);

    verify(updateTransactionPort).updateStatus(TX_ID, Web3TxStatus.SUCCEEDED, TX_HASH, null);
    ArgumentCaptor<Web3TransactionSucceededEvent> captor =
        ArgumentCaptor.forClass(Web3TransactionSucceededEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Web3TransactionSucceededEvent event = captor.getValue();
    assertThat(event.transactionId()).isEqualTo(TX_ID);
    assertThat(event.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    assertThat(event.referenceType()).isEqualTo(REFERENCE_TYPE);
    assertThat(event.referenceId()).isEqualTo(REFERENCE_ID);
    assertThat(event.fromUserId()).isEqualTo(FROM_USER_ID);
    assertThat(event.toUserId()).isEqualTo(TO_USER_ID);
    assertThat(event.txHash()).isEqualTo(TX_HASH);
  }

  @Test
  void markSucceededWithNonceSlotAndPublish_consumesSlotThenPublishesSucceededEvent() {
    TransactionOutcomePublisher.SponsorNonceReceiptCommand nonceCommand =
        nonceReceiptCommand("RECEIPT_STATUS_1");

    service.markSucceededWithNonceSlotAndPublish(
        TX_ID,
        IDEMPOTENCY_KEY,
        REFERENCE_TYPE,
        REFERENCE_ID,
        FROM_USER_ID,
        TO_USER_ID,
        TX_HASH,
        nonceCommand);

    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    RecordSponsorNonceSlotTransitionCommand slotCommand = slotCaptor.getValue();
    assertThat(slotCommand.getChainId()).isEqualTo(CHAIN_ID);
    assertThat(slotCommand.getFromAddress()).isEqualTo(FROM_ADDRESS);
    assertThat(slotCommand.getNonce()).isEqualTo(7L);
    assertThat(slotCommand.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTED);
    assertThat(slotCommand.getToStatus()).isEqualTo(SponsorNonceSlotStatus.CONSUMED);
    assertThat(slotCommand.getActiveTxId()).isEqualTo(TX_ID);
    assertThat(slotCommand.getConsumedTxId()).isEqualTo(TX_ID);
    assertThat(slotCommand.getConsumedReason()).isEqualTo("RECEIPT_STATUS_1");
    assertThat(slotCommand.hasReceiptEvidence()).isTrue();
    verify(updateTransactionPort).updateStatus(TX_ID, Web3TxStatus.SUCCEEDED, TX_HASH, null);
    verify(eventPublisher).publishEvent(any(Web3TransactionSucceededEvent.class));
  }

  @Test
  void markSucceededWithNonceSlotAndPublish_updatesStatus_whenSlotAlreadyConsumed() {
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=BROADCASTED, actual=CONSUMED"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));

    service.markSucceededWithNonceSlotAndPublish(
        TX_ID,
        IDEMPOTENCY_KEY,
        REFERENCE_TYPE,
        REFERENCE_ID,
        FROM_USER_ID,
        TO_USER_ID,
        TX_HASH,
        nonceReceiptCommand("RECEIPT_STATUS_1"));

    verify(updateTransactionPort).updateStatus(TX_ID, Web3TxStatus.SUCCEEDED, TX_HASH, null);
    verify(eventPublisher).publishEvent(any(Web3TransactionSucceededEvent.class));
  }

  @Test
  void markFailedOnchainAndPublish_updatesStatusAndPublishesFailedEvent() {
    service.markFailedOnchainAndPublish(
        TX_ID,
        IDEMPOTENCY_KEY,
        REFERENCE_TYPE,
        REFERENCE_ID,
        FROM_USER_ID,
        TO_USER_ID,
        TX_HASH,
        "RECEIPT_STATUS_0");

    verify(updateTransactionPort)
        .updateStatus(TX_ID, Web3TxStatus.FAILED_ONCHAIN, TX_HASH, "RECEIPT_STATUS_0");
    ArgumentCaptor<Web3TransactionFailedOnchainEvent> captor =
        ArgumentCaptor.forClass(Web3TransactionFailedOnchainEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Web3TransactionFailedOnchainEvent event = captor.getValue();
    assertThat(event.transactionId()).isEqualTo(TX_ID);
    assertThat(event.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    assertThat(event.referenceType()).isEqualTo(REFERENCE_TYPE);
    assertThat(event.referenceId()).isEqualTo(REFERENCE_ID);
    assertThat(event.fromUserId()).isEqualTo(FROM_USER_ID);
    assertThat(event.toUserId()).isEqualTo(TO_USER_ID);
    assertThat(event.txHash()).isEqualTo(TX_HASH);
    assertThat(event.failureReason()).isEqualTo("RECEIPT_STATUS_0");
  }

  @Test
  void markFailedOnchainWithNonceSlotAndPublish_consumesSlotThenPublishesFailedEvent() {
    service.markFailedOnchainWithNonceSlotAndPublish(
        TX_ID,
        IDEMPOTENCY_KEY,
        REFERENCE_TYPE,
        REFERENCE_ID,
        FROM_USER_ID,
        TO_USER_ID,
        TX_HASH,
        "RECEIPT_STATUS_0",
        nonceReceiptCommand("RECEIPT_STATUS_0"));

    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    assertThat(slotCaptor.getValue().getToStatus()).isEqualTo(SponsorNonceSlotStatus.CONSUMED);
    assertThat(slotCaptor.getValue().getConsumedReason()).isEqualTo("RECEIPT_STATUS_0");
    verify(updateTransactionPort)
        .updateStatus(TX_ID, Web3TxStatus.FAILED_ONCHAIN, TX_HASH, "RECEIPT_STATUS_0");
    verify(eventPublisher).publishEvent(any(Web3TransactionFailedOnchainEvent.class));
  }

  @Test
  void markSucceededAndPublish_throws_whenTransactionIdIsInvalid() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    0L,
                    IDEMPOTENCY_KEY,
                    REFERENCE_TYPE,
                    REFERENCE_ID,
                    FROM_USER_ID,
                    TO_USER_ID,
                    TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    verifyNoInteractions(updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_throws_whenIdempotencyKeyIsBlank() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    TX_ID, " ", REFERENCE_TYPE, REFERENCE_ID, FROM_USER_ID, TO_USER_ID, TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");

    verifyNoInteractions(updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_throws_whenReferenceTypeIsNull() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    TX_ID, IDEMPOTENCY_KEY, null, REFERENCE_ID, FROM_USER_ID, TO_USER_ID, TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");

    verifyNoInteractions(updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_throws_whenReferenceIdIsBlank() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    TX_ID, IDEMPOTENCY_KEY, REFERENCE_TYPE, " ", FROM_USER_ID, TO_USER_ID, TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");

    verifyNoInteractions(updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
  }

  @Test
  void markFailedOnchainAndPublish_throws_whenFailureReasonIsBlank() {
    assertThatThrownBy(
            () ->
                service.markFailedOnchainAndPublish(
                    TX_ID,
                    IDEMPOTENCY_KEY,
                    REFERENCE_TYPE,
                    REFERENCE_ID,
                    FROM_USER_ID,
                    TO_USER_ID,
                    TX_HASH,
                    " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required");

    verifyNoInteractions(updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
  }

  private TransactionOutcomePublisher.SponsorNonceReceiptCommand nonceReceiptCommand(
      String consumedReason) {
    return new TransactionOutcomePublisher.SponsorNonceReceiptCommand(
        CHAIN_ID, FROM_ADDRESS, 7L, consumedReason, LocalDateTime.parse("2026-05-24T12:00:00"));
  }
}
