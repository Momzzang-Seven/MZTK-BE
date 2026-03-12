package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
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
  private static final String TX_HASH =
      "0x1234567890123456789012345678901234567890123456789012345678901234";

  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private TransactionOutcomePublisher service;

  @BeforeEach
  void setUp() {
    service = new TransactionOutcomePublisher(updateTransactionPort, eventPublisher);
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

    verifyNoInteractions(updateTransactionPort, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_throws_whenIdempotencyKeyIsBlank() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    TX_ID, " ", REFERENCE_TYPE, REFERENCE_ID, FROM_USER_ID, TO_USER_ID, TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("idempotencyKey is required");

    verifyNoInteractions(updateTransactionPort, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_throws_whenReferenceTypeIsNull() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    TX_ID, IDEMPOTENCY_KEY, null, REFERENCE_ID, FROM_USER_ID, TO_USER_ID, TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");

    verifyNoInteractions(updateTransactionPort, eventPublisher);
  }

  @Test
  void markSucceededAndPublish_throws_whenReferenceIdIsBlank() {
    assertThatThrownBy(
            () ->
                service.markSucceededAndPublish(
                    TX_ID, IDEMPOTENCY_KEY, REFERENCE_TYPE, " ", FROM_USER_ID, TO_USER_ID, TX_HASH))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");

    verifyNoInteractions(updateTransactionPort, eventPublisher);
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

    verifyNoInteractions(updateTransactionPort, eventPublisher);
  }
}
