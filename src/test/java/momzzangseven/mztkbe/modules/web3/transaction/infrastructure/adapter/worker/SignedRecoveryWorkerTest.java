package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignedRecoveryWorkerTest {

  private static final String DEFAULT_REASON = Web3TxFailureReason.BROADCAST_FAILED.code();
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final long CHAIN_ID = 84532L;

  @Mock private LoadTransactionWorkPort loadTransactionWorkPort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;

  @Mock
  private PersistSponsorNonceTransactionStateUseCase persistSponsorNonceTransactionStateUseCase;

  @Mock private RetryStrategy retryStrategy;

  private SignedRecoveryWorker worker;

  @BeforeEach
  void setUp() {
    TransactionRewardTokenProperties properties = new TransactionRewardTokenProperties();
    properties.getWorker().setClaimTtlSeconds(120);
    worker =
        new SignedRecoveryWorker(
            loadTransactionWorkPort,
            updateTransactionPort,
            recordTransactionAuditPort,
            web3ContractPort,
            nonceSlotLifecycleUseCase,
            persistSponsorNonceTransactionStateUseCase,
            properties,
            retryStrategy,
            FIXED_CLOCK);
  }

  @Test
  void processBatch_noClaimedItems_doesNothing() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of());

    worker.processBatch(1);

    verifyNoInteractions(
        updateTransactionPort,
        web3ContractPort,
        recordTransactionAuditPort,
        nonceSlotLifecycleUseCase,
        persistSponsorNonceTransactionStateUseCase);
  }

  @Test
  void processBatch_signedRawTxMissing_schedulesInvalidSignedTx() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(null, "0x" + "a".repeat(64))));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.INVALID_SIGNED_TX.code(), null);
    verifyInvalidSignedSlotReviewTransition();
    verifyNoInteractions(web3ContractPort);
  }

  @Test
  void processBatch_signedRawTxBlank_schedulesInvalidSignedTx() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(" ", "0x" + "a".repeat(64))));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.INVALID_SIGNED_TX.code(), null);
    verifyInvalidSignedSlotReviewTransition();
    verifyNoInteractions(web3ContractPort);
  }

  @Test
  void processBatch_broadcastSuccess_usesBroadcastHash() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(
            new Web3ContractPort.BroadcastResult(true, "0x" + "b".repeat(64), null, "rpc-a"));

    worker.processBatch(1);

    verify(persistSponsorNonceTransactionStateUseCase)
        .markPending(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals("0x" + "a".repeat(40))
                        && command.nonce() == 0L
                        && command.attemptId() == null
                        && command.txHash().equals("0x" + "b".repeat(64))));
    verify(nonceSlotLifecycleUseCase)
        .transition(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.getFromStatus() == SponsorNonceSlotStatus.SIGNED
                        && command.getToStatus() == SponsorNonceSlotStatus.BROADCASTING
                        && command.getActiveTxId().equals(1L)));
    verify(recordTransactionAuditPort, times(2))
        .record(any(RecordTransactionAuditPort.AuditCommand.class));
    verify(updateTransactionPort, never()).scheduleRetry(eq(1L), eq(DEFAULT_REASON), any());
  }

  @Test
  void processBatch_broadcastSuccessWithoutHash_usesItemHashFallback() {
    String existingHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", existingHash)));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, " ", null, "rpc-a"));

    worker.processBatch(1);

    verify(persistSponsorNonceTransactionStateUseCase)
        .markPending(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L) && command.txHash().equals(existingHash)));
  }

  @Test
  void processBatch_broadcastSuccessWithNullHash_usesItemHashFallback() {
    String existingHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", existingHash)));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, null, null, "rpc-a"));

    worker.processBatch(1);

    verify(persistSponsorNonceTransactionStateUseCase)
        .markPending(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L) && command.txHash().equals(existingHash)));
  }

  @Test
  void processBatch_slotAlreadyBroadcasted_marksPendingWithoutRebroadcasting() {
    String existingHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", existingHash)));
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=SIGNED, actual=BROADCASTED"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    when(nonceSlotLifecycleUseCase.loadSlotsForReview(CHAIN_ID, "0x" + "a".repeat(40)))
        .thenReturn(List.of(slot(SponsorNonceSlotStatus.BROADCASTED, 1L)));

    worker.processBatch(1);

    verify(web3ContractPort, never()).broadcast(any(Web3ContractPort.BroadcastCommand.class));
    verify(persistSponsorNonceTransactionStateUseCase)
        .markPendingWithoutSlotTransition(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L) && command.txHash().equals(existingHash)));
    verify(updateTransactionPort, never()).scheduleRetry(eq(1L), anyString(), any());
  }

  @Test
  void processBatch_staleBroadcastingSlotOwnedByAnotherTx_stopsWithoutBroadcasting() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=SIGNED, actual=BROADCASTING"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    when(nonceSlotLifecycleUseCase.loadSlotsForReview(CHAIN_ID, "0x" + "a".repeat(40)))
        .thenReturn(List.of(slot(SponsorNonceSlotStatus.BROADCASTING, 99L)));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(), null);
    verify(web3ContractPort, never()).broadcast(any(Web3ContractPort.BroadcastCommand.class));
    verifyNoInteractions(persistSponsorNonceTransactionStateUseCase);
  }

  @Test
  void processBatch_staleNonceSlotBeforeBroadcast_stopsWithoutBroadcasting() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    doThrow(
            new Web3TransactionStateInvalidException(
                "stale nonce slot transition: expected=SIGNED, actual=DROPPED"))
        .when(nonceSlotLifecycleUseCase)
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(), null);
    verify(web3ContractPort, never()).broadcast(any(Web3ContractPort.BroadcastCommand.class));
    verifyNoInteractions(persistSponsorNonceTransactionStateUseCase);
  }

  @Test
  void processBatch_broadcastFailureWithReason_retriesWithGivenReason() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, "RPC_TIMEOUT", "rpc-a"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "RPC_TIMEOUT", retryAt);
  }

  @Test
  void processBatch_broadcastFailureWithoutReason_retriesWithDefaultReason() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, null, "rpc-a"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, DEFAULT_REASON, retryAt);
  }

  @Test
  void processBatch_exceptionAndShouldNotRetry_failsPermanently() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenThrow(new RuntimeException("boom"));
    when(retryStrategy.shouldRetry(any(Throwable.class), anyList())).thenReturn(false);

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code(), null);
  }

  @Test
  void processBatch_exceptionAndShouldRetry_schedulesRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item("0xdeadbeef", "0x" + "a".repeat(64))));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenThrow(new RuntimeException("boom"));
    when(retryStrategy.shouldRetry(any(Throwable.class), anyList())).thenReturn(true);
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, DEFAULT_REASON, retryAt);
  }

  private LoadTransactionWorkPort.TransactionWorkItem item(String signedRawTx, String txHash) {
    return new LoadTransactionWorkPort.TransactionWorkItem(
        1L,
        "idem-1",
        Web3ReferenceType.LEVEL_UP_REWARD,
        "101",
        1L,
        2L,
        CHAIN_ID,
        "0x" + "a".repeat(40),
        "0x" + "b".repeat(40),
        BigInteger.ONE,
        0L,
        txHash,
        signedRawTx,
        null,
        LocalDateTime.now());
  }

  private SponsorNonceSlotView slot(SponsorNonceSlotStatus status, Long activeTxId) {
    LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
    return new SponsorNonceSlotView(
        CHAIN_ID,
        "0x" + "a".repeat(40),
        0L,
        status,
        1,
        null,
        activeTxId,
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
        null,
        null,
        null,
        0,
        null,
        null,
        null,
        null,
        0,
        now,
        now);
  }

  private void verifyInvalidSignedSlotReviewTransition() {
    verify(nonceSlotLifecycleUseCase)
        .transition(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.getFromStatus() == SponsorNonceSlotStatus.SIGNED
                        && command.getToStatus() == SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED
                        && command.getActiveTxId().equals(1L)
                        && command
                            .getTerminalReason()
                            .equals(Web3TxFailureReason.INVALID_SIGNED_TX.code())));
  }
}
