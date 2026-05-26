package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.MarkExecutionIntentPendingOnchainPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistSponsorNonceTransactionStateServiceTest {

  private static final long CHAIN_ID = 11155111L;
  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-24T12:00:00");

  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  @Mock private MarkExecutionIntentPendingOnchainPort markExecutionIntentPendingOnchainPort;

  private PersistSponsorNonceTransactionStateService service;

  @BeforeEach
  void setUp() {
    service =
        new PersistSponsorNonceTransactionStateService(
            updateTransactionPort,
            nonceSlotLifecycleUseCase,
            markExecutionIntentPendingOnchainPort);
  }

  @Test
  void markSigned_persistsTransactionAndSlotInOrder() {
    service.markSigned(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNonceSignedCommand(
            1L, CHAIN_ID, FROM_ADDRESS, 7L, 1001L, "0xdeadbeef", "0x" + "b".repeat(64), NOW));

    InOrder inOrder = inOrder(updateTransactionPort, nonceSlotLifecycleUseCase);
    inOrder.verify(updateTransactionPort).markSigned(1L, 7L, "0xdeadbeef", "0x" + "b".repeat(64));
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    inOrder.verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    RecordSponsorNonceSlotTransitionCommand command = slotCaptor.getValue();
    assertThat(command.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.RESERVED);
    assertThat(command.getToStatus()).isEqualTo(SponsorNonceSlotStatus.SIGNED);
    assertThat(command.getActiveAttemptId()).isEqualTo(1001L);
    assertThat(command.getActiveTxId()).isEqualTo(1L);
    assertThat(command.hasSigningEvidence()).isTrue();
  }

  @Test
  void markPending_persistsTransactionAndSlotInOrder() {
    service.markPending(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNoncePendingCommand(
            1L, CHAIN_ID, FROM_ADDRESS, 7L, 1001L, "0x" + "b".repeat(64), NOW));

    InOrder inOrder =
        inOrder(
            updateTransactionPort,
            nonceSlotLifecycleUseCase,
            markExecutionIntentPendingOnchainPort);
    inOrder.verify(updateTransactionPort).markPending(1L, "0x" + "b".repeat(64));
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    inOrder.verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    inOrder.verify(markExecutionIntentPendingOnchainPort).markPendingOnchain(1L);
    RecordSponsorNonceSlotTransitionCommand command = slotCaptor.getValue();
    assertThat(command.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTING);
    assertThat(command.getToStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTED);
    assertThat(command.getActiveAttemptId()).isEqualTo(1001L);
    assertThat(command.getActiveTxId()).isEqualTo(1L);
    assertThat(command.hasBroadcastEvidence()).isTrue();
  }

  @Test
  void markPendingWithoutSlotTransition_persistsTransactionAndExecutionIntentOnly() {
    service.markPendingWithoutSlotTransition(
        new PersistSponsorNonceTransactionStateUseCase.TransactionPendingCommand(
            1L, "0x" + "b".repeat(64)));

    InOrder inOrder = inOrder(updateTransactionPort, markExecutionIntentPendingOnchainPort);
    inOrder.verify(updateTransactionPort).markPending(1L, "0x" + "b".repeat(64));
    inOrder.verify(markExecutionIntentPendingOnchainPort).markPendingOnchain(1L);
    verify(nonceSlotLifecycleUseCase, never()).transition(any());
  }

  @Test
  void markUnconfirmed_marksSlotStuckThenTransactionUnconfirmed() {
    service.markUnconfirmed(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNonceUnconfirmedCommand(
            1L, CHAIN_ID, FROM_ADDRESS, 7L, "0x" + "b".repeat(64), "RECEIPT_TIMEOUT_60S", NOW));

    InOrder inOrder = inOrder(nonceSlotLifecycleUseCase, updateTransactionPort);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    inOrder.verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    inOrder
        .verify(updateTransactionPort)
        .updateStatus(1L, Web3TxStatus.UNCONFIRMED, "0x" + "b".repeat(64), "RECEIPT_TIMEOUT_60S");
    RecordSponsorNonceSlotTransitionCommand command = slotCaptor.getValue();
    assertThat(command.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTED);
    assertThat(command.getToStatus()).isEqualTo(SponsorNonceSlotStatus.STUCK);
    assertThat(command.getStuckReason()).isEqualTo("RECEIPT_TIMEOUT_60S");
  }

  @Test
  void markUnconfirmed_updatesTransaction_whenSlotIsMissing() {
    doThrow(new Web3TransactionStateInvalidException("nonce slot not found"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));

    service.markUnconfirmed(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNonceUnconfirmedCommand(
            1L, CHAIN_ID, FROM_ADDRESS, 7L, null, "RECEIPT_TIMEOUT_MISSING_TX_HASH", NOW));

    verify(updateTransactionPort)
        .updateStatus(1L, Web3TxStatus.UNCONFIRMED, null, "RECEIPT_TIMEOUT_MISSING_TX_HASH");
  }

  @Test
  void markUnconfirmed_updatesTransaction_whenSlotAlreadyStuckBySameTransaction() {
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=BROADCASTED, actual=STUCK"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, FROM_ADDRESS, 7L))
        .thenReturn(Optional.of(slotView(SponsorNonceSlotStatus.STUCK, 1L)));

    service.markUnconfirmed(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNonceUnconfirmedCommand(
            1L, CHAIN_ID, FROM_ADDRESS, 7L, "0x" + "b".repeat(64), "RECEIPT_TIMEOUT_60S", NOW));

    verify(updateTransactionPort)
        .updateStatus(1L, Web3TxStatus.UNCONFIRMED, "0x" + "b".repeat(64), "RECEIPT_TIMEOUT_60S");
  }

  @Test
  void markUnconfirmed_throws_whenStuckSlotBelongsToOtherTransaction() {
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=BROADCASTED, actual=STUCK"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, FROM_ADDRESS, 7L))
        .thenReturn(Optional.of(slotView(SponsorNonceSlotStatus.STUCK, 99L)));

    assertThatThrownBy(
            () ->
                service.markUnconfirmed(
                    new PersistSponsorNonceTransactionStateUseCase.SponsorNonceUnconfirmedCommand(
                        1L,
                        CHAIN_ID,
                        FROM_ADDRESS,
                        7L,
                        "0x" + "b".repeat(64),
                        "RECEIPT_TIMEOUT_60S",
                        NOW)))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("actual=STUCK");

    verify(updateTransactionPort, never()).updateStatus(any(), any(), any(), any());
  }

  @Test
  void failTerminalAndDropReservedSlot_schedulesFailureAndDropsSlotInOneUseCase() {
    service.failTerminalAndDropReservedSlot(
        new PersistSponsorNonceTransactionStateUseCase
            .SponsorNonceTerminalReservedSlotFailureCommand(
            1L, CHAIN_ID, FROM_ADDRESS, 7L, 1001L, "SIGNATURE_INVALID", NOW));

    InOrder inOrder = inOrder(updateTransactionPort, nonceSlotLifecycleUseCase);
    inOrder.verify(updateTransactionPort).scheduleRetry(1L, "SIGNATURE_INVALID", null);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    inOrder.verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    RecordSponsorNonceSlotTransitionCommand command = slotCaptor.getValue();
    assertThat(command.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.RESERVED);
    assertThat(command.getToStatus()).isEqualTo(SponsorNonceSlotStatus.DROPPED);
    assertThat(command.getActiveAttemptId()).isEqualTo(1001L);
    assertThat(command.getActiveTxId()).isEqualTo(1L);
    assertThat(command.getReleasedAttemptId()).isEqualTo(1001L);
    assertThat(command.getReleasedTxId()).isEqualTo(1L);
    assertThat(command.getReleaseReason()).isEqualTo("SIGNATURE_INVALID");
  }

  @Test
  void markBroadcastingOperatorReview_marksSlotAndTransactionInOneUseCase() {
    service.markBroadcastingOperatorReview(
        new PersistSponsorNonceTransactionStateUseCase
            .SponsorNonceBroadcastingOperatorReviewCommand(
            1L,
            CHAIN_ID,
            FROM_ADDRESS,
            7L,
            1001L,
            "BROADCAST_NONCE_TOO_LOW",
            "SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED",
            true,
            true,
            true,
            true,
            NOW));

    InOrder inOrder = inOrder(nonceSlotLifecycleUseCase, updateTransactionPort);
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    inOrder.verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    inOrder
        .verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            1L, "SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED");
    RecordSponsorNonceSlotTransitionCommand command = slotCaptor.getValue();
    assertThat(command.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTING);
    assertThat(command.getToStatus()).isEqualTo(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);
    assertThat(command.getActiveAttemptId()).isEqualTo(1001L);
    assertThat(command.getActiveTxId()).isEqualTo(1L);
    assertThat(command.getTerminalReason()).isEqualTo("BROADCAST_NONCE_TOO_LOW");
    assertThat(command.hasBroadcastEvidence()).isTrue();
  }

  private SponsorNonceSlotView slotView(SponsorNonceSlotStatus status, Long activeTxId) {
    return new SponsorNonceSlotView(
        CHAIN_ID,
        FROM_ADDRESS,
        7L,
        status,
        1,
        1001L,
        activeTxId,
        "0x" + "b".repeat(64),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "RECEIPT_TIMEOUT_60S",
        null,
        null,
        null,
        0,
        null,
        null,
        null,
        null,
        0,
        NOW,
        NOW);
  }
}
