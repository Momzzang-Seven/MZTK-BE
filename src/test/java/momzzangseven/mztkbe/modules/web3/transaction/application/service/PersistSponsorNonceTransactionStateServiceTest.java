package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
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

  private PersistSponsorNonceTransactionStateService service;

  @BeforeEach
  void setUp() {
    service =
        new PersistSponsorNonceTransactionStateService(
            updateTransactionPort, nonceSlotLifecycleUseCase);
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

    InOrder inOrder = inOrder(updateTransactionPort, nonceSlotLifecycleUseCase);
    inOrder.verify(updateTransactionPort).markPending(1L, "0x" + "b".repeat(64));
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> slotCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    inOrder.verify(nonceSlotLifecycleUseCase).transition(slotCaptor.capture());
    RecordSponsorNonceSlotTransitionCommand command = slotCaptor.getValue();
    assertThat(command.getFromStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTING);
    assertThat(command.getToStatus()).isEqualTo(SponsorNonceSlotStatus.BROADCASTED);
    assertThat(command.getActiveAttemptId()).isEqualTo(1001L);
    assertThat(command.getActiveTxId()).isEqualTo(1L);
    assertThat(command.hasBroadcastEvidence()).isTrue();
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
}
