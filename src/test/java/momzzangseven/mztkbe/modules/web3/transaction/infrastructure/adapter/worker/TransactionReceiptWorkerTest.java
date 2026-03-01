package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
import momzzangseven.mztkbe.modules.web3.transaction.application.service.TransactionOutcomePublisher;
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
class TransactionReceiptWorkerTest {

  private static final String DEFAULT_REASON = Web3TxFailureReason.RPC_UNAVAILABLE.code();

  @Mock private LoadTransactionWorkPort loadTransactionWorkPort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private TransactionOutcomePublisher transactionOutcomePublisher;
  @Mock private RetryStrategy retryStrategy;

  private TransactionRewardTokenProperties properties;
  private TransactionReceiptWorker worker;

  @BeforeEach
  void setUp() {
    properties = new TransactionRewardTokenProperties();
    properties.getWorker().setClaimTtlSeconds(120);
    properties.getWorker().setReceiptTimeoutSeconds(60);
    properties.getWorker().setReceiptPollMinSeconds(2);
    properties.getWorker().setReceiptPollMaxSeconds(5);
    worker =
        new TransactionReceiptWorker(
            loadTransactionWorkPort,
            updateTransactionPort,
            recordTransactionAuditPort,
            web3ContractPort,
            transactionOutcomePublisher,
            properties,
            retryStrategy);
  }

  @Test
  void processBatch_noClaimedItems_doesNothing() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of());

    worker.processBatch(1);

    verifyNoInteractions(updateTransactionPort, web3ContractPort, transactionOutcomePublisher);
  }

  @Test
  void processBatch_missingTxHash_marksUnconfirmedImmediately() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(" ", LocalDateTime.now().minusSeconds(1))));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .updateStatus(
            1L,
            Web3TxStatus.UNCONFIRMED,
            " ",
            Web3TxFailureReason.RECEIPT_TIMEOUT.code());
    verifyNoInteractions(web3ContractPort, transactionOutcomePublisher);
  }

  @Test
  void processBatch_nullTxHash_marksUnconfirmedImmediately() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(null, LocalDateTime.now().minusSeconds(1))));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .updateStatus(
            1L, Web3TxStatus.UNCONFIRMED, null, Web3TxFailureReason.RECEIPT_TIMEOUT.code());
    verifyNoInteractions(web3ContractPort, transactionOutcomePublisher);
  }

  @Test
  void processBatch_timeoutWhenConfiguredAsNonPositive_marksUnconfirmed() {
    properties.getWorker().setReceiptTimeoutSeconds(0);
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now())));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .updateStatus(1L, Web3TxStatus.UNCONFIRMED, txHash, "RECEIPT_TIMEOUT_0S");
    verifyNoInteractions(web3ContractPort, transactionOutcomePublisher);
  }

  @Test
  void processBatch_timeoutReached_marksUnconfirmedWithTimeoutReason() {
    properties.getWorker().setReceiptTimeoutSeconds(5);
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(5))));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .updateStatus(1L, Web3TxStatus.UNCONFIRMED, txHash, "RECEIPT_TIMEOUT_5S");
    verifyNoInteractions(web3ContractPort, transactionOutcomePublisher);
  }

  @Test
  void processBatch_timeoutWhenBaselineMissing_marksUnconfirmed() {
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, null)));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .updateStatus(1L, Web3TxStatus.UNCONFIRMED, txHash, "RECEIPT_TIMEOUT_60S");
    verifyNoInteractions(web3ContractPort, transactionOutcomePublisher);
  }

  @Test
  void processBatch_receiptFoundAndSuccess_publishesSucceeded() {
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(1))));
    when(web3ContractPort.getReceipt(txHash))
        .thenReturn(new Web3ContractPort.ReceiptResult(txHash, true, true, "rpc-a", false, null));

    worker.processBatch(1);

    verify(transactionOutcomePublisher)
        .markSucceededAndPublish(
            1L, "idem-1", Web3ReferenceType.LEVEL_UP_REWARD, "101", 1L, 2L, txHash);
    verify(updateTransactionPort, never()).scheduleRetry(eq(1L), anyString(), any());
  }

  @Test
  void processBatch_receiptFoundAndFailed_publishesFailedOnchain() {
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(1))));
    when(web3ContractPort.getReceipt(txHash))
        .thenReturn(new Web3ContractPort.ReceiptResult(txHash, true, false, "rpc-a", false, null));

    worker.processBatch(1);

    verify(transactionOutcomePublisher)
        .markFailedOnchainAndPublish(
            1L,
            "idem-1",
            Web3ReferenceType.LEVEL_UP_REWARD,
            "101",
            1L,
            2L,
            txHash,
            "RECEIPT_STATUS_0");
  }

  @Test
  void processBatch_receiptRpcError_retriesWithFailureReason() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(1))));
    when(web3ContractPort.getReceipt(txHash))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                txHash, false, null, "rpc-a", true, "RPC_TEMP_UNAVAILABLE"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "RPC_TEMP_UNAVAILABLE", retryAt);
  }

  @Test
  void processBatch_receiptNotFoundWithoutRpcError_schedulesNextPoll() {
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(1))));
    when(web3ContractPort.getReceipt(txHash))
        .thenReturn(new Web3ContractPort.ReceiptResult(txHash, false, null, "rpc-a", false, null));

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(eq(1L), isNull(), any(LocalDateTime.class));
  }

  @Test
  void processBatch_exceptionAndShouldNotRetry_failsPermanently() {
    String txHash = "0x" + "a".repeat(64);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(1))));
    when(web3ContractPort.getReceipt(txHash)).thenThrow(new RuntimeException("boom"));
    when(retryStrategy.shouldRetry(any(Throwable.class), anyList())).thenReturn(false);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, DEFAULT_REASON, null);
  }

  @Test
  void processBatch_exceptionAndShouldRetry_schedulesRetry() {
    String txHash = "0x" + "a".repeat(64);
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.PENDING), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(txHash, LocalDateTime.now().minusSeconds(1))));
    when(web3ContractPort.getReceipt(txHash)).thenThrow(new RuntimeException("boom"));
    when(retryStrategy.shouldRetry(any(Throwable.class), anyList())).thenReturn(true);
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, DEFAULT_REASON, retryAt);
  }

  private LoadTransactionWorkPort.TransactionWorkItem item(
      String txHash, LocalDateTime broadcastedAt) {
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
        "0xdeadbeef",
        null,
        broadcastedAt);
  }
}
