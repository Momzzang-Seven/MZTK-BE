package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase.SponsorNonceTerminalReservedSlotFailureCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorChainNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecision;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.SponsorNonceProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
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
  private static final long CHAIN_ID = 11155111L;

  @Mock private LoadTransactionWorkPort loadTransactionWorkPort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  @Mock private LoadSponsorChainNoncePort loadSponsorChainNoncePort;
  @Mock private CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase;
  @Mock private ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;

  @Mock
  private PersistSponsorNonceTransactionStateUseCase persistSponsorNonceTransactionStateUseCase;

  @Mock private Web3ContractPort web3ContractPort;
  @Mock private RetryStrategy retryStrategy;

  private TransactionRewardTokenProperties rewardProperties;
  private SponsorNonceProperties sponsorNonceProperties;
  private TransactionIssuerWorker worker;

  @BeforeEach
  void setUp() {
    rewardProperties = new TransactionRewardTokenProperties();
    rewardProperties.getWorker().setClaimTtlSeconds(120);
    rewardProperties.setTokenContractAddress("0x" + "a".repeat(40));
    sponsorNonceProperties = new SponsorNonceProperties();

    worker =
        new TransactionIssuerWorker(
            loadTransactionWorkPort,
            updateTransactionPort,
            recordTransactionAuditPort,
            loadRewardTreasuryWalletPort,
            verifyTreasuryWalletForSignPort,
            loadSponsorChainNoncePort,
            coordinateSponsorNonceUseCase,
            nonceSlotLifecycleUseCase,
            persistSponsorNonceTransactionStateUseCase,
            web3ContractPort,
            sponsorNonceProperties,
            rewardProperties,
            retryStrategy);
  }

  @Test
  void processBatch_noClaimedItems_doesNothing() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of());

    worker.processBatch(1);

    verifyNoInteractions(
        updateTransactionPort,
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
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
    verifyNoInteractions(
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
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
    verifyNoInteractions(
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
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
    verifyNoInteractions(
        verifyTreasuryWalletForSignPort,
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
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
    verifyNoInteractions(
        verifyTreasuryWalletForSignPort,
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
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
    verifyNoInteractions(
        verifyTreasuryWalletForSignPort,
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
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
    verifyNoInteractions(
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
  }

  @Test
  void processBatch_verifyForSignThrowsTransientKmsKeyDescribe_schedulesKmsKeyNotEnabledRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(60);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    // No AWS cause attached → KmsClientErrorClassifier returns transient → retryable path.
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
    verifyNoInteractions(
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
  }

  @Test
  void processBatch_verifyForSignThrowsTerminalKmsKeyDescribe_terminatesBatch() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(2), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L), item(2L, 6L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    // Terminal AWS cause (NotFoundException) → classifier returns true → batch must terminate
    // via failPrevalidate(retryable=false), which calls scheduleRetry(.., null) — sentinel for
    // terminal. Without the split catch, this would be retried forever as KMS_KEY_NOT_ENABLED.
    software.amazon.awssdk.services.kms.model.KmsException terminalCause =
        (software.amazon.awssdk.services.kms.model.KmsException)
            software.amazon.awssdk.services.kms.model.KmsException.builder()
                .awsErrorDetails(
                    software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("NotFoundException")
                        .build())
                .build();
    doThrow(new KmsKeyDescribeFailedException("key missing", terminalCause))
        .when(verifyTreasuryWalletForSignPort)
        .verify(WALLET_ALIAS);

    worker.processBatch(2);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code(), null);
    verify(updateTransactionPort)
        .scheduleRetry(2L, Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code(), null);
    verifyNoInteractions(
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase,
        nonceSlotLifecycleUseCase);
  }

  @Test
  void processBatch_prevalidateRetryableFailure_schedulesRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    stubExistingNonceSlot(5L, 1L, 1001L);
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, true, "RPC_TEMP", null, null, null, Map.of()));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "RPC_TEMP", retryAt);
    verify(nonceSlotLifecycleUseCase, never())
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void processBatch_prevalidateRetryableFailure_keepsNewReservationForRetry() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    stubSponsorNonceReservation(44L, 1L, 1001L);
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, true, "RPC_TEMP", null, null, null, Map.of()));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "RPC_TEMP", retryAt);
    verify(nonceSlotLifecycleUseCase, never())
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void processBatch_prevalidateNonRetryableFailure_marksFailedWithoutRetryAt() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    stubExistingNonceSlot(5L, 1L, 1001L);
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, false, "PREVALIDATE_TRANSFER_FALSE", null, null, null, Map.of()));

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "PREVALIDATE_TRANSFER_FALSE", null);
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void processBatch_prevalidateNonRetryableFailure_dropsNewReservationWithReleaseReason() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    stubSponsorNonceReservation(45L, 1L, 1002L);
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(
            new Web3ContractPort.PrevalidateResult(
                false, false, "PREVALIDATE_TRANSFER_FALSE", null, null, null, Map.of()));

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, "PREVALIDATE_TRANSFER_FALSE", null);
    List<RecordSponsorNonceSlotTransitionCommand> transitions = captureSlotTransitions();
    assertThat(transitions).hasSize(1);
    assertThat(transitions.get(0).getToStatus()).isEqualTo(SponsorNonceSlotStatus.DROPPED);
    assertThat(transitions.get(0).getReleaseReason()).isEqualTo("PREVALIDATE_TRANSFER_FALSE");
    assertThat(transitions.get(0).getReleasedAttemptId()).isEqualTo(1002L);
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
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
    stubExistingNonceSlot(5L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new KmsSignFailedException("kms throttled"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.KMS_SIGN_FAILED.code(), retryAt);
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void processBatch_signTransferThrowsKmsTerminal_dropsReservedSlot_whenNonceReservedThisTurn() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubSponsorNonceReservation(99L, 1L, 1001L);

    software.amazon.awssdk.services.kms.model.KmsException kmsDenied =
        (software.amazon.awssdk.services.kms.model.KmsException)
            software.amazon.awssdk.services.kms.model.KmsException.builder()
                .awsErrorDetails(
                    software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("AccessDeniedException")
                        .build())
                .build();
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new KmsSignFailedException("kms denied", kmsDenied));

    worker.processBatch(1);

    SponsorNonceTerminalReservedSlotFailureCommand command = captureTerminalFailureCommand();
    assertThat(command.nonce()).isEqualTo(99L);
    assertThat(command.attemptId()).isEqualTo(1001L);
    assertThat(command.failureReason())
        .isEqualTo(Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code());
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void processBatch_signTransferThrowsKmsTerminal_dropsReservedSlot_whenItemAlreadyHasNonce() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 12L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubExistingNonceSlot(12L, 1L, 1001L);

    software.amazon.awssdk.services.kms.model.KmsException kmsDenied =
        (software.amazon.awssdk.services.kms.model.KmsException)
            software.amazon.awssdk.services.kms.model.KmsException.builder()
                .awsErrorDetails(
                    software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("AccessDeniedException")
                        .build())
                .build();
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new KmsSignFailedException("kms denied", kmsDenied));

    worker.processBatch(1);

    SponsorNonceTerminalReservedSlotFailureCommand command = captureTerminalFailureCommand();
    assertThat(command.nonce()).isEqualTo(12L);
    assertThat(command.attemptId()).isEqualTo(1001L);
    assertThat(command.transactionId()).isEqualTo(1L);
    assertThat(command.failureReason())
        .isEqualTo(Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code());
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void processBatch_signTransferThrowsKmsTransient_schedulesRetryWithoutReleasingNonce() {
    LocalDateTime retryAt = LocalDateTime.now().plusSeconds(45);
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubSponsorNonceReservation(99L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(
            new KmsSignFailedException(
                "network",
                software.amazon.awssdk.core.exception.SdkClientException.create(
                    "connect timeout")));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.KMS_SIGN_FAILED.code(), retryAt);
    verify(nonceSlotLifecycleUseCase, never())
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
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
    stubExistingNonceSlot(5L, 1L, 1001L);
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
  void processBatch_fromAddressMismatch_marksTerminalAndSkipsPrevalidate() {
    String mintedFromAddress = "0x" + "f".repeat(40);
    String currentSignerAddress = "0x" + "a".repeat(40);
    LoadTransactionWorkPort.TransactionWorkItem item =
        new LoadTransactionWorkPort.TransactionWorkItem(
            1L,
            "idem-1",
            Web3ReferenceType.LEVEL_UP_REWARD,
            "101",
            1L,
            2L,
            CHAIN_ID,
            mintedFromAddress, // minted under the old treasury
            "0x" + "d".repeat(40),
            BigInteger.ONE,
            null,
            "0x" + "f".repeat(64),
            null,
            null,
            LocalDateTime.now());

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadRewardTreasuryWalletPort.load())
        .thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID, currentSignerAddress)));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code(), null);
    verifyNoInteractions(web3ContractPort);
    verifyNoInteractions(
        loadSponsorChainNoncePort, coordinateSponsorNonceUseCase, nonceSlotLifecycleUseCase);
  }

  @Test
  void processBatch_fromAddressDifferentCase_normalizesAndProceeds() {
    LoadTransactionWorkPort.TransactionWorkItem item =
        new LoadTransactionWorkPort.TransactionWorkItem(
            1L,
            "idem-1",
            Web3ReferenceType.LEVEL_UP_REWARD,
            "101",
            1L,
            2L,
            CHAIN_ID,
            TREASURY_ADDRESS.toUpperCase(), // mixed-case minted address
            "0x" + "d".repeat(40),
            BigInteger.ONE,
            7L,
            "0x" + "f".repeat(64),
            null,
            null,
            LocalDateTime.now());

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubExistingNonceSlot(7L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    stubSignedBroadcastClaim(1L);
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(
            new Web3ContractPort.BroadcastResult(true, "0x" + "e".repeat(64), null, "main"));

    worker.processBatch(1);

    verify(persistSponsorNonceTransactionStateUseCase)
        .markPending(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals(TREASURY_ADDRESS)
                        && command.nonce() == 7L
                        && command.attemptId().equals(1001L)
                        && command.txHash().equals("0x" + "e".repeat(64))));
    verify(updateTransactionPort, never())
        .scheduleRetry(eq(1L), eq(Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code()), any());
  }

  @Test
  void
      processBatch_signTransferThrowsSignatureRecovery_dropsReservedSlot_whenItemAlreadyHasNonce() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubExistingNonceSlot(5L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new SignatureRecoveryException("recover mismatch"));

    worker.processBatch(1);

    SponsorNonceTerminalReservedSlotFailureCommand command = captureTerminalFailureCommand();
    assertThat(command.nonce()).isEqualTo(5L);
    assertThat(command.failureReason()).isEqualTo(Web3TxFailureReason.SIGNATURE_INVALID.code());
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
  }

  @Test
  void
      processBatch_signTransferThrowsSignatureRecovery_dropsReservedSlot_whenNonceReservedThisTurn() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubSponsorNonceReservation(42L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenThrow(new SignatureRecoveryException("recover mismatch"));

    worker.processBatch(1);

    SponsorNonceTerminalReservedSlotFailureCommand command = captureTerminalFailureCommand();
    assertThat(command.nonce()).isEqualTo(42L);
    assertThat(command.attemptId()).isEqualTo(1001L);
    assertThat(command.failureReason()).isEqualTo(Web3TxFailureReason.SIGNATURE_INVALID.code());
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
    stubExistingNonceSlot(7L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    stubSignedBroadcastClaim(1L);
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, " ", null, "main"));

    worker.processBatch(1);

    verify(persistSponsorNonceTransactionStateUseCase)
        .markSigned(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals(TREASURY_ADDRESS)
                        && command.nonce() == 7L
                        && command.attemptId().equals(1001L)
                        && command.signedRawTx().equals("0xdeadbeef")
                        && command.txHash().equals("0x" + "d".repeat(64))));
    verify(persistSponsorNonceTransactionStateUseCase)
        .markPending(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals(TREASURY_ADDRESS)
                        && command.nonce() == 7L
                        && command.attemptId().equals(1001L)
                        && command.txHash().equals("0x" + "d".repeat(64))));
    verify(updateTransactionPort, never()).assignNonce(any(), any(Long.class));
    List<RecordSponsorNonceSlotTransitionCommand> transitions = captureSlotTransitions();
    assertThat(transitions)
        .extracting(RecordSponsorNonceSlotTransitionCommand::getToStatus)
        .containsExactly(SponsorNonceSlotStatus.BROADCASTING);
    assertThat(transitions).allMatch(transition -> transition.getActiveAttemptId().equals(1001L));
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
    stubSponsorNonceReservation(33L, 1L, 1001L);
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, TREASURY_ADDRESS, 33L))
        .thenReturn(Optional.of(slotView(33L, SponsorNonceSlotStatus.RESERVED, 1001L, 1L)));
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    stubSignedBroadcastClaim(1L);
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, "0x" + "e".repeat(64), null, "sub"));

    worker.processBatch(1);

    ArgumentCaptor<Web3ContractPort.SignTransferCommand> commandCaptor =
        ArgumentCaptor.forClass(Web3ContractPort.SignTransferCommand.class);
    verify(web3ContractPort).signTransfer(commandCaptor.capture());
    assertThat(commandCaptor.getValue().nonce()).isEqualTo(33L);
    assertThat(commandCaptor.getValue().treasurySigner().walletAddress())
        .isEqualTo(TREASURY_ADDRESS);
    assertThat(commandCaptor.getValue().treasurySigner().kmsKeyId()).isEqualTo(KMS_KEY_ID);
    verify(persistSponsorNonceTransactionStateUseCase)
        .markSigned(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals(TREASURY_ADDRESS)
                        && command.nonce() == 33L
                        && command.attemptId().equals(1001L)
                        && command.signedRawTx().equals("0xdeadbeef")
                        && command.txHash().equals("0x" + "d".repeat(64))));
    verify(persistSponsorNonceTransactionStateUseCase)
        .markPending(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals(TREASURY_ADDRESS)
                        && command.nonce() == 33L
                        && command.attemptId().equals(1001L)
                        && command.txHash().equals("0x" + "e".repeat(64))));
    List<RecordSponsorNonceSlotTransitionCommand> transitions = captureSlotTransitions();
    assertThat(transitions)
        .extracting(RecordSponsorNonceSlotTransitionCommand::getToStatus)
        .containsExactly(SponsorNonceSlotStatus.BROADCASTING);
    assertThat(transitions).allMatch(transition -> transition.getActiveAttemptId().equals(1001L));
  }

  @Test
  void processBatch_reservationChangedAfterSign_marksStaleWithoutPersistingSignedRawTx() {
    long chainId = CHAIN_ID;
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    when(loadSponsorChainNoncePort.loadSnapshot(chainId, TREASURY_ADDRESS))
        .thenReturn(
            new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(33, 33, 33L, 33L, 33L, 33L));
    when(coordinateSponsorNonceUseCase.execute(any(SponsorNonceCoordinationCommand.class)))
        .thenReturn(
            new SponsorNonceCoordinationResult(
                SponsorNonceDecision.issue(33L),
                new SponsorNonceSlotReservation(
                    chainId,
                    TREASURY_ADDRESS,
                    33L,
                    1,
                    1001L,
                    1L,
                    SponsorNonceSlotStatus.RESERVED)));
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(chainId, TREASURY_ADDRESS, 33L))
        .thenReturn(Optional.of(slotView(33L, SponsorNonceSlotStatus.DROPPED, null, null)));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            1L, Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code());
    verify(persistSponsorNonceTransactionStateUseCase, never()).markSigned(any());
    verify(nonceSlotLifecycleUseCase, never())
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
    verify(web3ContractPort, never()).broadcast(any(Web3ContractPort.BroadcastCommand.class));
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
    stubExistingNonceSlot(5L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    stubSignedBroadcastClaim(1L);
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, null, "main"));
    when(retryStrategy.nextRetryAt(any(TransactionRewardTokenProperties.class), any()))
        .thenReturn(retryAt);

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.BROADCAST_FAILED.code(), retryAt);
  }

  @Test
  void processBatch_broadcastNonceTooLow_marksSlotAndTransactionForOperatorReview() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubExistingNonceSlot(5L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    stubSignedBroadcastClaim(1L);
    when(web3ContractPort.broadcast(any(Web3ContractPort.BroadcastCommand.class)))
        .thenReturn(
            new Web3ContractPort.BroadcastResult(
                false, null, Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code(), "main"));

    worker.processBatch(1);

    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    assertThat(transitionCaptor.getValue().getToStatus())
        .isEqualTo(SponsorNonceSlotStatus.BROADCASTING);
    verify(persistSponsorNonceTransactionStateUseCase)
        .markBroadcastingOperatorReview(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.transactionId().equals(1L)
                        && command.chainId() == CHAIN_ID
                        && command.fromAddress().equals(TREASURY_ADDRESS)
                        && command.nonce() == 5L
                        && command.attemptId().equals(1001L)
                        && command
                            .slotTerminalReason()
                            .equals(Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code())
                        && command
                            .transactionFailureReason()
                            .equals(
                                Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED
                                    .code())));
    verify(updateTransactionPort, never())
        .scheduleRetry(eq(1L), eq(Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code()), any());
  }

  @Test
  void processBatch_whenSignedTxClaimLost_skipsDirectBroadcastForRecoveryWorker() {
    LoadTransactionWorkPort.TransactionWorkItem item = item(1L, 7L);

    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(web3ContractPort.prevalidate(any(Web3ContractPort.PrevalidateCommand.class)))
        .thenReturn(prevalidateOk());
    stubExistingNonceSlot(7L, 1L, 1001L);
    when(web3ContractPort.signTransfer(any(Web3ContractPort.SignTransferCommand.class)))
        .thenReturn(new Web3ContractPort.SignedTransaction("0xdeadbeef", "0x" + "d".repeat(64)));
    when(updateTransactionPort.claimForProcessing(
            eq(1L), eq(Web3TxStatus.SIGNED), anyString(), any(LocalDateTime.class)))
        .thenReturn(false);

    worker.processBatch(1);

    verify(persistSponsorNonceTransactionStateUseCase)
        .markSigned(
            any(PersistSponsorNonceTransactionStateUseCase.SponsorNonceSignedCommand.class));
    verify(web3ContractPort, never()).broadcast(any(Web3ContractPort.BroadcastCommand.class));
    verify(persistSponsorNonceTransactionStateUseCase, never())
        .markPending(
            any(PersistSponsorNonceTransactionStateUseCase.SponsorNoncePendingCommand.class));
    verify(nonceSlotLifecycleUseCase, never())
        .transition(any(RecordSponsorNonceSlotTransitionCommand.class));
  }

  @Test
  void processBatch_existingNonceWithMismatchedSlot_marksStaleAndSkipsSigning() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, TREASURY_ADDRESS, 5L))
        .thenReturn(Optional.of(slotView(5L, SponsorNonceSlotStatus.RESERVED, 1001L, 2L)));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            1L, Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code());
    verify(web3ContractPort, never()).signTransfer(any(Web3ContractPort.SignTransferCommand.class));
    verifyNoInteractions(loadSponsorChainNoncePort, coordinateSponsorNonceUseCase);
  }

  @Test
  void processBatch_existingNonceConsumedOnChain_marksStaleWithoutSigning() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, 5L)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, TREASURY_ADDRESS, 5L))
        .thenReturn(
            Optional.of(slotView(5L, SponsorNonceSlotStatus.RESERVED, 1001L, 1L)),
            Optional.of(slotView(5L, SponsorNonceSlotStatus.CONSUMED_UNKNOWN, null, null)));
    when(loadSponsorChainNoncePort.loadSnapshot(CHAIN_ID, TREASURY_ADDRESS))
        .thenReturn(new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(6, 6, 6L, 6L, 6L, 6L));
    when(coordinateSponsorNonceUseCase.execute(any(SponsorNonceCoordinationCommand.class)))
        .thenReturn(
            new SponsorNonceCoordinationResult(
                SponsorNonceDecision.of(
                    SponsorNonceDecisionType.CONSUME_UNKNOWN_NONCE,
                    5L,
                    "LATEST_PASSED_WITH_RPC_SNAPSHOT"),
                null));

    worker.processBatch(1);

    verify(coordinateSponsorNonceUseCase)
        .execute(
            org.mockito.ArgumentMatchers.argThat(
                command -> command.transactionId() == null && command.chainLatestNonce() == 6L));
    verify(updateTransactionPort)
        .markUnconfirmedForSponsorNonceReview(
            1L, Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code());
    verify(web3ContractPort, never()).signTransfer(any(Web3ContractPort.SignTransferCommand.class));
  }

  @Test
  void processBatch_operatorReviewNonceDecision_marksTerminalWithoutRetryLoop() {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(loadSponsorChainNoncePort.loadSnapshot(CHAIN_ID, TREASURY_ADDRESS))
        .thenReturn(new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(5, 5, 5L, 5L, 5L, 5L));
    when(coordinateSponsorNonceUseCase.execute(any(SponsorNonceCoordinationCommand.class)))
        .thenReturn(
            new SponsorNonceCoordinationResult(
                SponsorNonceDecision.of(
                    SponsorNonceDecisionType.OPERATOR_REVIEW_REQUIRED,
                    5L,
                    "SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED"),
                null));

    worker.processBatch(1);

    verify(updateTransactionPort)
        .scheduleRetry(1L, Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code(), null);
    verify(web3ContractPort, never()).signTransfer(any(Web3ContractPort.SignTransferCommand.class));
  }

  @Test
  void processBatch_waitForOpenWindowNonceDecisionSchedulesWindowRetry() {
    assertUnreservedDecisionSchedulesRetry(
        SponsorNonceDecisionType.WAIT_FOR_OPEN_WINDOW,
        Web3TxFailureReason.SPONSOR_NONCE_WAIT_FOR_OPEN_WINDOW);
  }

  @Test
  void processBatch_waitForInFlightSlotNonceDecisionSchedulesWindowRetry() {
    assertUnreservedDecisionSchedulesRetry(
        SponsorNonceDecisionType.WAIT_FOR_IN_FLIGHT_SLOT,
        Web3TxFailureReason.SPONSOR_NONCE_WAIT_FOR_OPEN_WINDOW);
  }

  @Test
  void processBatch_waitForInFlightReplacementNonceDecisionSchedulesWindowRetry() {
    assertUnreservedDecisionSchedulesRetry(
        SponsorNonceDecisionType.WAIT_FOR_IN_FLIGHT_REPLACEMENT,
        Web3TxFailureReason.SPONSOR_NONCE_WAIT_FOR_OPEN_WINDOW);
  }

  @Test
  void processBatch_rpcDisagreementNonceDecisionSchedulesRpcDisagreementRetry() {
    assertUnreservedDecisionSchedulesRetry(
        SponsorNonceDecisionType.RPC_DISAGREEMENT,
        Web3TxFailureReason.SPONSOR_NONCE_RPC_DISAGREEMENT);
  }

  private void stubSponsorNonceReservation(long nonce, Long transactionId, Long attemptId) {
    long chainId = CHAIN_ID;
    when(loadSponsorChainNoncePort.loadSnapshot(chainId, TREASURY_ADDRESS))
        .thenReturn(
            new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(
                nonce, nonce, nonce, nonce, nonce, nonce));
    when(coordinateSponsorNonceUseCase.execute(any(SponsorNonceCoordinationCommand.class)))
        .thenReturn(
            new SponsorNonceCoordinationResult(
                SponsorNonceDecision.issue(nonce),
                new SponsorNonceSlotReservation(
                    chainId,
                    TREASURY_ADDRESS,
                    nonce,
                    1,
                    attemptId,
                    transactionId,
                    SponsorNonceSlotStatus.RESERVED)));
  }

  private void assertUnreservedDecisionSchedulesRetry(
      SponsorNonceDecisionType decisionType, Web3TxFailureReason expectedFailureReason) {
    when(loadTransactionWorkPort.claimByStatus(
            eq(Web3TxStatus.CREATED), eq(1), anyString(), any(Duration.class)))
        .thenReturn(List.of(item(1L, null)));
    when(loadRewardTreasuryWalletPort.load()).thenReturn(Optional.of(walletInfo(true, KMS_KEY_ID)));
    when(loadSponsorChainNoncePort.loadSnapshot(CHAIN_ID, TREASURY_ADDRESS))
        .thenReturn(new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(5, 5, 5L, 5L, 5L, 5L));
    when(coordinateSponsorNonceUseCase.execute(any(SponsorNonceCoordinationCommand.class)))
        .thenReturn(
            new SponsorNonceCoordinationResult(
                SponsorNonceDecision.of(decisionType, 5L, decisionType.name()), null));

    worker.processBatch(1);

    verify(updateTransactionPort).scheduleRetry(1L, expectedFailureReason.code(), null);
    verify(web3ContractPort, never()).prevalidate(any(Web3ContractPort.PrevalidateCommand.class));
    verify(web3ContractPort, never()).signTransfer(any(Web3ContractPort.SignTransferCommand.class));
  }

  private void stubExistingNonceSlot(long nonce, Long transactionId, Long attemptId) {
    when(loadSponsorChainNoncePort.loadSnapshot(CHAIN_ID, TREASURY_ADDRESS))
        .thenReturn(
            new LoadSponsorChainNoncePort.SponsorChainNonceSnapshot(
                nonce, nonce, nonce, nonce, nonce, nonce));
    when(nonceSlotLifecycleUseCase.loadSlotForReview(CHAIN_ID, TREASURY_ADDRESS, nonce))
        .thenReturn(
            Optional.of(
                slotView(nonce, SponsorNonceSlotStatus.RESERVED, attemptId, transactionId)));
  }

  private SponsorNonceSlotView slotView(
      long nonce, SponsorNonceSlotStatus status, Long attemptId, Long transactionId) {
    return new SponsorNonceSlotView(
        CHAIN_ID,
        TREASURY_ADDRESS,
        nonce,
        status,
        1,
        attemptId,
        transactionId,
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
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private void stubSignedBroadcastClaim(Long transactionId) {
    when(updateTransactionPort.claimForProcessing(
            eq(transactionId), eq(Web3TxStatus.SIGNED), anyString(), any(LocalDateTime.class)))
        .thenReturn(true);
  }

  private RecordSponsorNonceSlotTransitionCommand captureOnlySlotTransition() {
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    return transitionCaptor.getValue();
  }

  private SponsorNonceTerminalReservedSlotFailureCommand captureTerminalFailureCommand() {
    ArgumentCaptor<SponsorNonceTerminalReservedSlotFailureCommand> commandCaptor =
        ArgumentCaptor.forClass(SponsorNonceTerminalReservedSlotFailureCommand.class);
    verify(persistSponsorNonceTransactionStateUseCase)
        .failTerminalAndDropReservedSlot(commandCaptor.capture());
    return commandCaptor.getValue();
  }

  private List<RecordSponsorNonceSlotTransitionCommand> captureSlotTransitions() {
    ArgumentCaptor<RecordSponsorNonceSlotTransitionCommand> transitionCaptor =
        ArgumentCaptor.forClass(RecordSponsorNonceSlotTransitionCommand.class);
    verify(nonceSlotLifecycleUseCase).transition(transitionCaptor.capture());
    return transitionCaptor.getAllValues();
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
        CHAIN_ID,
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
