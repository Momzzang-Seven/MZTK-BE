package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RunTransactionStateUpdatePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkTransactionSucceededServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private LoadTransactionPort loadTransactionPort;
  @Mock private TransactionOutcomePublisher transactionOutcomePublisher;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final RunTransactionStateUpdatePort transactionPort =
      new RunTransactionStateUpdatePort() {
        @Override
        public <T> T requiresNew(java.util.function.Supplier<T> action) {
          return action.get();
        }
      };

  private MarkTransactionSucceededService service;

  @BeforeEach
  void setUp() {
    service =
        new MarkTransactionSucceededService(
            loadTransactionPort,
            transactionOutcomePublisher,
            recordTransactionAuditPort,
            web3ContractPort,
            nonceSlotLifecycleUseCase,
            transactionPort,
            FIXED_CLOCK);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_throws_whenCurrentStatusNotUnconfirmed() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    Web3TxStatus.PENDING,
                    "0x" + "a".repeat(64),
                    null)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("only UNCONFIRMED");
  }

  @Test
  void execute_throws_whenTransactionNotFound() {
    when(loadTransactionPort.loadById(22L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(validCommand()))
        .isInstanceOf(Web3TransactionNotFoundException.class);
  }

  @Test
  void execute_throws_whenTxHashMismatchedWithSnapshot() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    84532L,
                    "0x" + "c".repeat(40),
                    112L,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "b".repeat(64),
                    null)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("txHash mismatch");
  }

  @Test
  void execute_throws_whenReceiptLookupReturnsRpcError() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    84532L,
                    "0x" + "c".repeat(40),
                    112L,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    null)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), false, null, "main", true, "RPC_UNAVAILABLE"));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("receipt lookup failed");
  }

  @Test
  void execute_throws_whenReceiptProofMissing() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    84532L,
                    "0x" + "c".repeat(40),
                    112L,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    null)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), false, null, "main", false, null));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("receipt proof is required");
  }

  @Test
  void execute_throws_whenReceiptFoundButStatusNotSuccess() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    84532L,
                    "0x" + "c".repeat(40),
                    112L,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    null)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, false, "main", false, null));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("receipt proof is required");
  }

  @Test
  void execute_marksSucceeded_andRecordsAudit_whenReceiptProofValid() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    84532L,
                    "0x" + "c".repeat(40),
                    112L,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    null)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, true, "main", false, null));

    MarkTransactionSucceededResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(22L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.SUCCEEDED);
    assertThat(result.previousStatus()).isEqualTo(Web3TxStatus.UNCONFIRMED);

    verify(transactionOutcomePublisher)
        .markSucceededAndPublish(
            22L, "idem-22", Web3ReferenceType.USER_TO_USER, "101", 7L, 9L, "0x" + "a".repeat(64));
    verify(nonceSlotLifecycleUseCase)
        .transition(
            org.mockito.ArgumentMatchers.argThat(
                transition ->
                    transition.getFromStatus() == SponsorNonceSlotStatus.STUCK
                        && transition.getToStatus() == SponsorNonceSlotStatus.CONSUMED
                        && transition.getConsumedTxId().equals(22L)
                        && transition.hasReceiptEvidence()));

    ArgumentCaptor<RecordTransactionAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordTransactionAuditPort.AuditCommand.class);
    verify(recordTransactionAuditPort).record(captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo(Web3TransactionAuditEventType.CS_OVERRIDE);
    assertThat(captor.getValue().detail()).containsEntry("toStatus", Web3TxStatus.SUCCEEDED.name());
  }

  @Test
  void execute_marksSucceeded_whenNonceSlotAlreadyConsumedBySameTransaction() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L)).thenReturn(Optional.of(unconfirmedSnapshotWithSlot()));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, true, "main", false, null));
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=STUCK, actual=CONSUMED"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    when(nonceSlotLifecycleUseCase.loadSlotsForReview(84532L, "0x" + "c".repeat(40)))
        .thenReturn(List.of(slotView(SponsorNonceSlotStatus.CONSUMED, 22L, 22L)));

    MarkTransactionSucceededResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.SUCCEEDED);
    verify(transactionOutcomePublisher)
        .markSucceededAndPublish(
            22L, "idem-22", Web3ReferenceType.USER_TO_USER, "101", 7L, 9L, "0x" + "a".repeat(64));
  }

  @Test
  void execute_throws_whenConsumedNonceSlotBelongsToOtherTransaction() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L)).thenReturn(Optional.of(unconfirmedSnapshotWithSlot()));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, true, "main", false, null));
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=STUCK, actual=CONSUMED"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    when(nonceSlotLifecycleUseCase.loadSlotsForReview(84532L, "0x" + "c".repeat(40)))
        .thenReturn(List.of(slotView(SponsorNonceSlotStatus.CONSUMED, 99L, 99L)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("actual=CONSUMED");

    verify(transactionOutcomePublisher, never())
        .markSucceededAndPublish(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_revalidatesSnapshotAfterReceiptLookupBeforeMutatingState() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(Optional.of(unconfirmedSnapshotWithSlot()))
        .thenReturn(Optional.of(snapshotWithStatus(Web3TxStatus.PENDING)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, true, "main", false, null));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("only UNCONFIRMED");

    verify(transactionOutcomePublisher, never())
        .markSucceededAndPublish(any(), any(), any(), any(), any(), any(), any());
    verify(nonceSlotLifecycleUseCase, never())
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
  }

  @Test
  void execute_allowsOverride_whenSnapshotTxHashIsNull() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    Web3TxStatus.UNCONFIRMED,
                    null,
                    null)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, true, "main", false, null));

    MarkTransactionSucceededResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.SUCCEEDED);
    verify(transactionOutcomePublisher)
        .markSucceededAndPublish(
            22L, "idem-22", Web3ReferenceType.USER_TO_USER, "101", 7L, 9L, "0x" + "a".repeat(64));
  }

  private MarkTransactionSucceededCommand validCommand() {
    return new MarkTransactionSucceededCommand(
        1L, 22L, "0x" + "a".repeat(64), "https://explorer/tx/22", "manual proof", "ticket-22");
  }

  private LoadTransactionPort.TransactionSnapshot unconfirmedSnapshotWithSlot() {
    return snapshotWithStatus(Web3TxStatus.UNCONFIRMED);
  }

  private LoadTransactionPort.TransactionSnapshot snapshotWithStatus(Web3TxStatus status) {
    return new LoadTransactionPort.TransactionSnapshot(
        22L,
        "idem-22",
        Web3ReferenceType.USER_TO_USER,
        "101",
        7L,
        9L,
        84532L,
        "0x" + "c".repeat(40),
        112L,
        status,
        "0x" + "a".repeat(64),
        null);
  }

  private SponsorNonceSlotView slotView(
      SponsorNonceSlotStatus status, Long activeTxId, Long consumedTxId) {
    return new SponsorNonceSlotView(
        84532L,
        "0x" + "c".repeat(40),
        112L,
        status,
        1,
        1001L,
        activeTxId,
        "0x" + "a".repeat(64),
        null,
        consumedTxId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        null,
        null,
        null,
        0,
        java.time.LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone()),
        java.time.LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone()));
  }
}
