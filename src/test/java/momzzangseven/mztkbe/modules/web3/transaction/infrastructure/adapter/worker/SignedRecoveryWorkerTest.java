package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
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

  @Mock private LoadTransactionWorkPort loadTransactionWorkPort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Web3ContractPort web3ContractPort;
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
            properties,
            retryStrategy);
  }

  @Test
  void processBatch_noClaimedItems_doesNothing() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of());

    worker.processBatch(1);

    verifyNoInteractions(updateTransactionPort, web3ContractPort, recordTransactionAuditPort);
  }

  @Test
  void processBatch_signedRawTxMissing_schedulesInvalidSignedTx() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.SIGNED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(null, "0x" + "a".repeat(64))));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.INVALID_SIGNED_TX.code(), null);
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

    verify(updateTransactionPort).markPending(1L, "0x" + "b".repeat(64));
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

    verify(updateTransactionPort).markPending(1L, existingHash);
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

    verify(updateTransactionPort).markPending(1L, existingHash);
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

    verify(updateTransactionPort).scheduleRetry(1L, DEFAULT_REASON, null);
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
        "0x" + "a".repeat(40),
        "0x" + "b".repeat(40),
        BigInteger.ONE,
        0L,
        txHash,
        signedRawTx,
        null,
        LocalDateTime.now());
  }
}
