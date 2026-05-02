package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
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
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.VerifyTreasuryWalletForSignPort;
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

  private static final String TREASURY_ADDRESS = "0x" + "c".repeat(40);
  private static final String KMS_KEY_ID = "alias/reward-treasury";
  private static final String WALLET_ALIAS = "reward-treasury";

  @Mock private LoadTransactionWorkPort loadTransactionWorkPort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
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

    web3CoreProperties = new Web3CoreProperties();
    web3CoreProperties.setChainId(11155111L);

    worker =
        new TransactionIssuerWorker(
            loadTransactionWorkPort,
            updateTransactionPort,
            recordTransactionAuditPort,
            loadRewardTreasuryWalletPort,
            verifyTreasuryWalletForSignPort,
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
  void processBatch_missingTreasuryWallet_schedulesTreasuryKeyMissingForEachItem() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.empty());

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verifyNoInteractions(web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_walletNotActive_schedulesTreasuryWalletInactiveForEachItem() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load())
        .thenReturn(Optional.of(walletInfo(false, KMS_KEY_ID)));

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.TREASURY_WALLET_INACTIVE.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.TREASURY_WALLET_INACTIVE.code(), null);
    verifyNoInteractions(web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_walletActiveButKmsKeyIdMissing_schedulesTreasuryKeyMissingWithoutVerifying() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, null)));

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verifyNoInteractions(verifyTreasuryWalletForSignPort, web3ContractPort, reserveNoncePort);
  }

  @Test
  void
      processBatch_walletActiveButWalletAddressMissing_schedulesTreasuryKeyMissingWithoutVerifying() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load())
        .thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID, null)));

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verifyNoInteractions(verifyTreasuryWalletForSignPort, web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_walletAddressMalformed_schedulesTreasuryKeyMissingWithoutVerifying() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load())
        .thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID, "0xnot-a-real-address")));

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.TREASURY_KEY_MISSING.code(), null);
    verifyNoInteractions(verifyTreasuryWalletForSignPort, web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_verifyForSignThrowsTreasuryState_schedulesKmsKeyNotEnabledRetryWithBackoff() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(60);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    doThrow(new TreasuryWalletStateException("KMS key disabled"))
        .when(verifyTreasuryWalletForSignPort)
        .verify(WALLET_ALIAS);
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(2);

    // KMS_KEY_NOT_ENABLED is retryable; failBatch must route through the backoff path so
    // processing_until is non-null. A null processing_until would let the SQL claim filter
    // re-pick the same rows on the next worker tick (every second), causing a hot loop that
    // hammers KMS while the operator restores the key.
    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.KMS_KEY_NOT_ENABLED.code(), retryAt);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.KMS_KEY_NOT_ENABLED.code(), retryAt);
    verifyNoInteractions(web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_verifyForSignThrowsKmsKeyDescribeFailed_schedulesKmsKeyNotEnabledRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(60);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    doThrow(new KmsKeyDescribeFailedException("describe throttled"))
        .when(verifyTreasuryWalletForSignPort)
        .verify(WALLET_ALIAS);
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(2);

    // DescribeKey throttling/IAM/timeout must not propagate past processBatchItems — otherwise
    // the batch aborts without per-row audit and worker tick keeps retrying immediately.
    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.KMS_KEY_NOT_ENABLED.code(), retryAt);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.KMS_KEY_NOT_ENABLED.code(), retryAt);
    verifyNoInteractions(web3ContractPort, reserveNoncePort);
  }

  @Test
  void processBatch_prevalidateRetryableFailure_schedulesRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, true, "RPC_TEMP", null, null, null, Map.of()));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "RPC_TEMP", retryAt);
    verify(updateTransactionPort, never()).markSigned(any(), any(Long.class), any(), any());
  }

  @Test
  void processBatch_prevalidateNonRetryableFailure_marksFailedWithoutRetryAt() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, false, "PREVALIDATE_TRANSFER_FALSE", null, null, null, Map.of()));

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "PREVALIDATE_TRANSFER_FALSE", null);
    verify(updateTransactionPort, never()).markSigned(any(), any(Long.class), any(), any());
  }

  @Test
  void processBatch_signTransferThrowsKmsSignFailed_schedulesRetryWithKmsSignFailedCode() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new KmsSignFailedException("kms throttled"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.KMS_SIGN_FAILED.code(), retryAt);
    verify(updateTransactionPort, never()).markSigned(any(), any(Long.class), any(), any());
  }

  @Test
  void processBatch_signTransferThrowsKmsSignFailed_auditDetailsDoNotLeakKmsKeyId() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new KmsSignFailedException("kms throttled"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    ArgumentCaptor<RecordTransactionAuditPort.AuditCommand> auditCaptor =
        ArgumentCaptor.forClass(RecordTransactionAuditPort.AuditCommand.class);
    verify(recordTransactionAuditPort, atLeastOnce()).record(auditCaptor.capture());
    for (RecordTransactionAuditPort.AuditCommand cmd : auditCaptor.getAllValues()) {
      Map<String, Object> detail = cmd.detail();
      assertThat(detail).doesNotContainKey("kmsKeyId");
      detail.values().forEach(v -> assertThat(String.valueOf(v)).doesNotContain(KMS_KEY_ID));
    }
  }

  @Test
  void processBatch_signTransferThrowsSignatureRecovery_marksSignatureInvalidNonRetryable() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new SignatureRecoveryException("recover mismatch"));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.SIGNATURE_INVALID.code(), null);
    verify(updateTransactionPort, never()).markSigned(any(), any(Long.class), any(), any());
    // re-entry case (item already has nonce) — worker did not reserve, must not release.
    verify(reserveNoncePort, never()).releaseNonce(anyString(), anyLong());
  }

  @Test
  void
      processBatch_signTransferThrowsSignatureRecovery_releasesReservedNonce_whenItemNonceMissing() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(reserveNoncePort.reserveNextNonce(TREASURY_ADDRESS)).thenReturn(42L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new SignatureRecoveryException("recover mismatch"));
    when(reserveNoncePort.releaseNonce(TREASURY_ADDRESS, 42L)).thenReturn(true);

    worker.processBatch(1);

    verify(reserveNoncePort).releaseNonce(TREASURY_ADDRESS, 42L);
    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.SIGNATURE_INVALID.code(), null);
  }

  @Test
  void processBatch_signatureRecoveryReleaseCasMisses_logsErrorButStillTerminals() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(reserveNoncePort.reserveNextNonce(TREASURY_ADDRESS)).thenReturn(42L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new SignatureRecoveryException("recover mismatch"));
    when(reserveNoncePort.releaseNonce(TREASURY_ADDRESS, 42L)).thenReturn(false);

    worker.processBatch(1);

    verify(reserveNoncePort).releaseNonce(TREASURY_ADDRESS, 42L);
    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.SIGNATURE_INVALID.code(), null);
  }

  @Test
  void processBatch_successPath_usesExistingNonce_andFallsBackToSignedHashWhenBroadcastHashBlank() {
    LoadTransactionWorkPort.TransactionWorkItem item = item(1L, 7L);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
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
    LoadTransactionWorkPort.TransactionWorkItem item = item(1L, null);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(reserveNoncePort.reserveNextNonce(TREASURY_ADDRESS)).thenReturn(33L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, "0x" + "e".repeat(64), null, "sub"));

    worker.processBatch(1);

    verify(updateTransactionPort).assignNonce(1L, 33L);

    ArgumentCaptor<Web3ContractPort.SignTransferCommand> commandCaptor =
        ArgumentCaptor.forClass(Web3ContractPort.SignTransferCommand.class);
    verify(web3ContractPort).signTransfer(commandCaptor.capture());
    assertThat(commandCaptor.getValue().nonce()).isEqualTo(33L);
    assertThat(commandCaptor.getValue().treasurySigner().walletAddress())
        .isEqualTo(TREASURY_ADDRESS);
    assertThat(commandCaptor.getValue().treasurySigner().kmsKeyId()).isEqualTo(KMS_KEY_ID);
    verify(updateTransactionPort).markPending(1L, "0x" + "e".repeat(64));
  }

  @Test
  void processBatch_broadcastFailureWithoutReason_usesDefaultBroadcastReason() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
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

  private Web3ContractPort.PrevalidateResult prevalidateOk() {
    return new Web3ContractPort.PrevalidateResult(
        true,
        false,
        null,
        BigInteger.valueOf(55_000),
        BigInteger.valueOf(1_000_000_000L),
        BigInteger.valueOf(2_000_000_000L),
        Map.of());
  }

  private TreasuryWalletInfo walletInfo(boolean active, String kmsKeyId) {
    return walletInfo(active, kmsKeyId, TREASURY_ADDRESS);
  }

  private TreasuryWalletInfo walletInfo(boolean active, String kmsKeyId, String walletAddress) {
    return new TreasuryWalletInfo(WALLET_ALIAS, kmsKeyId, walletAddress, active);
  }

  private LoadTransactionWorkPort.TransactionWorkItem item(Long transactionId, Long nonce) {
    return new LoadTransactionWorkPort.TransactionWorkItem(
        transactionId,
        "idem-" + transactionId,
        Web3ReferenceType.LEVEL_UP_REWARD,
        "101",
        1L,
        2L,
        TREASURY_ADDRESS,
        "0x" + "d".repeat(40),
        BigInteger.ONE,
        nonce,
        "0x" + "f".repeat(64),
        null,
        null,
        LocalDateTime.now());
  }
}
