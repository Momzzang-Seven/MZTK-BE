package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionIssuerWorkerTest {

  @Mock private LoadTransactionWorkPort loadTransactionWorkPort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private LoadTreasuryKeyPort loadTreasuryKeyPort;
  @Mock private ReserveNoncePort reserveNoncePort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private RetryStrategy retryStrategy;

  private TransactionRewardTokenProperties rewardProperties;
  private Web3CoreProperties web3CoreProperties;
  private TransactionIssuerWorker worker;

  @BeforeEach
  void setUp() {
    rewardProperties = new TransactionRewardTokenProperties();
    rewardProperties.getWorker().setClaimTtlSeconds(120);
    rewardProperties.setTokenContractAddress("0x" + "a".repeat(40));
    rewardProperties.getTreasury().setWalletAlias("reward-treasury");
    rewardProperties.getTreasury().setKeyEncryptionKeyB64("kek");

    web3CoreProperties = new Web3CoreProperties();
    web3CoreProperties.setChainId(11155111L);

    worker =
        new TransactionIssuerWorker(
            loadTransactionWorkPort,
            updateTransactionPort,
            recordTransactionAuditPort,
            loadTreasuryKeyPort,
            reserveNoncePort,
            web3ContractPort,
            rewardProperties,
            retryStrategy,
            web3CoreProperties);
  }

  @Test
  void processBatch_noClaimedItems_doesNothing() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of());

    worker.processBatch(1);

    verifyNoInteractions(updateTransactionPort, web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_missingTreasuryKey_schedulesTreasuryKeyMissingForEachItem() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadTreasuryKeyPort.loadByAlias("reward-treasury", "kek")).thenReturn(Optional.empty());

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verifyNoInteractions(web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_prevalidateRetryableFailure_schedulesRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);
    LoadTreasuryKeyPort.TreasuryKeyMaterial keyMaterial =
        LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "c".repeat(40), "0x" + "1".repeat(64));

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadTreasuryKeyPort.loadByAlias("reward-treasury", "kek"))
        .thenReturn(Optional.of(keyMaterial));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(new Web3ContractPort.PrevalidateResult(false, true, "RPC_TEMP", null, null, null, Map.of()));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "RPC_TEMP", retryAt);
    verify(updateTransactionPort, never()).markSigned(any(), any(Long.class), any(), any());
  }

  @Test
  void processBatch_prevalidateNonRetryableFailure_marksFailedWithoutRetryAt() {
    LoadTreasuryKeyPort.TreasuryKeyMaterial keyMaterial =
        LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "c".repeat(40), "0x" + "1".repeat(64));

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadTreasuryKeyPort.loadByAlias("reward-treasury", "kek"))
        .thenReturn(Optional.of(keyMaterial));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, false, "PREVALIDATE_TRANSFER_FALSE", null, null, null, Map.of()));

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "PREVALIDATE_TRANSFER_FALSE", null);
    verify(updateTransactionPort, never()).markSigned(any(), any(Long.class), any(), any());
  }

  @Test
  void processBatch_successPath_usesExistingNonce_andFallsBackToSignedHashWhenBroadcastHashBlank() {
    LoadTreasuryKeyPort.TreasuryKeyMaterial keyMaterial =
        LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "c".repeat(40), "0x" + "1".repeat(64));
    LoadTransactionWorkPort.TransactionWorkItem item = item(1L, 7L);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadTreasuryKeyPort.loadByAlias("reward-treasury", "kek"))
        .thenReturn(Optional.of(keyMaterial));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                true,
                false,
                null,
                BigInteger.valueOf(55_000),
                BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(2_000_000_000L),
                Map.of("ok", true)));
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, " ", null, "main"));

    worker.processBatch(1);

    verify(updateTransactionPort).markSigned(1L, 7L, "0xdeadbeef", "0x" + "d".repeat(64));
    verify(updateTransactionPort).markPending(1L, "0x" + "d".repeat(64));
    verify(updateTransactionPort, never()).assignNonce(any(), any(Long.class));
    verify(recordTransactionAuditPort, atLeastOnce())
        .record(any(RecordTransactionAuditPort.AuditCommand.class));
  }

  @Test
  void processBatch_successPath_reservesNonce_whenItemNonceMissing() {
    LoadTreasuryKeyPort.TreasuryKeyMaterial keyMaterial =
        LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "c".repeat(40), "0x" + "1".repeat(64));
    LoadTransactionWorkPort.TransactionWorkItem item = item(1L, null);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadTreasuryKeyPort.loadByAlias("reward-treasury", "kek"))
        .thenReturn(Optional.of(keyMaterial));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                true,
                false,
                null,
                BigInteger.valueOf(55_000),
                BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(2_000_000_000L),
                Map.of()));
    when(reserveNoncePort.reserveNextNonce("0x" + "c".repeat(40))).thenReturn(33L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(
            new Web3ContractPort.BroadcastResult(true, "0x" + "e".repeat(64), null, "sub"));

    worker.processBatch(1);

    verify(updateTransactionPort).assignNonce(1L, 33L);

    ArgumentCaptor<Web3ContractPort.SignTransferCommand> commandCaptor =
        ArgumentCaptor.forClass(Web3ContractPort.SignTransferCommand.class);
    verify(web3ContractPort).signTransfer(commandCaptor.capture());
    assertThat(commandCaptor.getValue().nonce()).isEqualTo(33L);
    verify(updateTransactionPort).markPending(1L, "0x" + "e".repeat(64));
  }

  @Test
  void processBatch_broadcastFailureWithoutReason_usesDefaultBroadcastReason() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);
    LoadTreasuryKeyPort.TreasuryKeyMaterial keyMaterial =
        LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "c".repeat(40), "0x" + "1".repeat(64));

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadTreasuryKeyPort.loadByAlias("reward-treasury", "kek"))
        .thenReturn(Optional.of(keyMaterial));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                true,
                false,
                null,
                BigInteger.valueOf(55_000),
                BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(2_000_000_000L),
                Map.of()));
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, null, "main"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.BROADCAST_FAILED.code(), retryAt);
  }

  private LoadTransactionWorkPort.TransactionWorkItem item(Long transactionId, Long nonce) {
    return new LoadTransactionWorkPort.TransactionWorkItem(
        transactionId,
        "idem-" + transactionId,
        Web3ReferenceType.LEVEL_UP_REWARD,
        "101",
        1L,
        2L,
        "0x" + "c".repeat(40),
        "0x" + "d".repeat(40),
        BigInteger.ONE,
        nonce,
        "0x" + "f".repeat(64),
        null,
        null,
        LocalDateTime.now());
  }
}
