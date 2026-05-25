package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.util.KmsClientErrorClassifier;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorChainNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.BroadcastAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.PrevalidateAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.SignAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionIssuerWorker extends AbstractWeb3Worker {

  private static final int SPONSOR_NONCE_OPEN_WINDOW_SIZE = 3;

  private final LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  private final LoadSponsorChainNoncePort loadSponsorChainNoncePort;
  private final CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final Web3ContractPort web3ContractPort;
  private final Web3CoreProperties web3CoreProperties;

  private final String workerId = "issuer-" + UUID.randomUUID().toString().substring(0, 8);

  public TransactionIssuerWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort,
      VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort,
      LoadSponsorChainNoncePort loadSponsorChainNoncePort,
      CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase,
      ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase,
      Web3ContractPort web3ContractPort,
      TransactionRewardTokenProperties rewardTokenProperties,
      RetryStrategy retryStrategy,
      Web3CoreProperties web3CoreProperties) {
    super(
        loadTransactionWorkPort,
        updateTransactionPort,
        recordTransactionAuditPort,
        rewardTokenProperties,
        retryStrategy);
    this.loadRewardTreasuryWalletPort = loadRewardTreasuryWalletPort;
    this.verifyTreasuryWalletForSignPort = verifyTreasuryWalletForSignPort;
    this.loadSponsorChainNoncePort = loadSponsorChainNoncePort;
    this.coordinateSponsorNonceUseCase = coordinateSponsorNonceUseCase;
    this.nonceSlotLifecycleUseCase = nonceSlotLifecycleUseCase;
    this.web3ContractPort = web3ContractPort;
    this.web3CoreProperties = web3CoreProperties;
  }

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(20);
  }

  void processBatch(int limit) {
    processBatchByStatus(
        Web3TxStatus.CREATED,
        limit,
        workerId,
        claimTtlSeconds(),
        Web3TxFailureReason.RPC_UNAVAILABLE.code(),
        this::processBatchItems);
  }

  void processBatchItems(List<LoadTransactionWorkPort.TransactionWorkItem> items) {
    Optional<TreasuryWalletInfo> walletOpt = loadRewardTreasuryWalletPort.load();
    if (walletOpt.isEmpty()) {
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }
    TreasuryWalletInfo walletInfo = walletOpt.get();

    if (!walletInfo.active()) {
      failBatch(items, Web3TxFailureReason.TREASURY_WALLET_INACTIVE);
      return;
    }
    // Structural guards (cheap, local) precede the remote verify call so that incomplete wallet
    // rows fail fast with TREASURY_KEY_MISSING instead of leaking into TreasurySigner's compact
    // constructor (which would otherwise raise Web3InvalidInputException and abort the entire
    // batch with no per-item audit trail). The address is checked for both blank AND format —
    // a malformed-but-non-blank address would otherwise pass these guards and reach
    // EvmAddress.of() inside TreasurySigner, propagating outside forEachItem's per-item catch.
    if (walletInfo.kmsKeyId() == null || walletInfo.kmsKeyId().isBlank()) {
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }
    if (walletInfo.walletAddress() == null || walletInfo.walletAddress().isBlank()) {
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }
    try {
      EvmAddress.of(walletInfo.walletAddress());
    } catch (Web3InvalidInputException e) {
      log.warn(
          "Treasury wallet '{}' has malformed walletAddress: {}",
          walletInfo.walletAlias(),
          e.getMessage());
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }

    try {
      verifyTreasuryWalletForSignPort.verify(walletInfo.walletAlias());
    } catch (TreasuryWalletStateException e) {
      log.warn(
          "Treasury wallet '{}' verify-for-sign failed: {}",
          walletInfo.walletAlias(),
          e.getMessage());
      failBatch(items, Web3TxFailureReason.KMS_KEY_NOT_ENABLED);
      return;
    } catch (KmsKeyDescribeFailedException e) {
      // Mirrors execute-path KMS classification (#4 / commit 5670c1b3): terminal AWS errors
      // (AccessDenied, NotFound, Disabled, KeyUnavailable) must terminate the batch with a
      // non-retryable reason so a broken IAM/key state does not get retried indefinitely. The
      // existing KMS_KEY_NOT_ENABLED reason is retryable=true and was being applied to *all*
      // DescribeKey failures, including terminal ones — letting the worker hammer KMS until
      // manual intervention. Transient throttling/5xx still go through the retryable path.
      if (KmsClientErrorClassifier.isTerminal(e)) {
        log.error(
            "event=ISSUER_KMS_DESCRIBE_TERMINAL alias={} message={}",
            walletInfo.walletAlias(),
            e.getMessage());
        failBatch(items, Web3TxFailureReason.KMS_DESCRIBE_TERMINAL);
      } else {
        log.warn(
            "event=ISSUER_KMS_DESCRIBE_TRANSIENT alias={} message={}",
            walletInfo.walletAlias(),
            e.getMessage());
        failBatch(items, Web3TxFailureReason.KMS_KEY_NOT_ENABLED);
      }
      return;
    }

    TreasurySigner signer =
        new TreasurySigner(
            walletInfo.walletAlias(), walletInfo.kmsKeyId(), walletInfo.walletAddress());

    forEachItem(
        items, item -> processItem(item, signer), Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  // Routes batch-wide failures through retry/terminal based on the failure reason's retryable flag.
  // Critical for retryable reasons (e.g. KMS_KEY_NOT_ENABLED) — a terminal write would leave
  // processing_until=null while the SQL claim filter only excludes non-retryable reasons,
  // letting the same rows be re-claimed every worker tick and hammer KMS until manual intervention.
  private void failBatch(
      List<LoadTransactionWorkPort.TransactionWorkItem> items, Web3TxFailureReason reason) {
    if (reason.isRetryable()) {
      items.forEach(item -> retry(item.transactionId(), reason.code(), item));
    } else {
      items.forEach(item -> failPrevalidate(item.transactionId(), reason.code(), false));
    }
  }

  private void processItem(
      LoadTransactionWorkPort.TransactionWorkItem item, TreasurySigner signer) {
    // Treasury rotation guard: a CREATED row carries the from_address it was minted with.
    // After a treasury rotation, the active signer can be a different wallet — proceeding would
    // sign with a wallet that does not match item.fromAddress() and the broadcast would silently
    // fail on-chain (or worse, drift state). Cheaper than prevalidate's RPC round-trip, so this
    // guard runs first.
    if (!isFromAddressMatch(item.fromAddress(), signer.walletAddress())) {
      log.warn(
          "fromAddress mismatch: txId={}, expected={}, signer={}",
          item.transactionId(),
          item.fromAddress(),
          signer.walletAddress());
      failPrevalidate(
          item.transactionId(), Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code(), false);
      return;
    }

    Web3ContractPort.PrevalidateResult prevalidateResult =
        web3ContractPort.prevalidate(
            new Web3ContractPort.PrevalidateCommand(
                signer.walletAddress(), item.toAddress(), item.amountWei()));

    Map<String, Object> prevalidateDetail =
        new PrevalidateAuditDetail(
                prevalidateResult.detail(),
                prevalidateResult.ok(),
                prevalidateResult.failureReason())
            .toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.PREVALIDATE, null, prevalidateDetail);

    if (!prevalidateResult.ok()) {
      failPrevalidate(
          item.transactionId(), prevalidateResult.failureReason(), prevalidateResult.retryable());
      return;
    }

    NonceReservation nonceReservation = resolveNonce(item, signer.walletAddress());
    if (nonceReservation == null) {
      return;
    }
    long nonce = nonceReservation.nonce();
    Web3ContractPort.SignedTransaction signed;
    try {
      signed =
          web3ContractPort.signTransfer(
              new Web3ContractPort.SignTransferCommand(
                  signer,
                  rewardTokenProperties.getTokenContractAddress(),
                  item.toAddress(),
                  item.amountWei(),
                  nonce,
                  web3CoreProperties.getChainId(),
                  prevalidateResult.gasLimit(),
                  prevalidateResult.maxPriorityFeePerGas(),
                  prevalidateResult.maxFeePerGas()));
    } catch (KmsSignFailedException e) {
      log.warn("KMS sign failed for txId={}: {}", item.transactionId(), e.getMessage());
      if (KmsClientErrorClassifier.isTerminal(e)) {
        terminalFailAndDropReservedSlot(
            item,
            signer.walletAddress(),
            nonceReservation,
            Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL);
      } else {
        retry(item.transactionId(), Web3TxFailureReason.KMS_SIGN_FAILED.code(), item);
      }
      return;
    } catch (SignatureRecoveryException e) {
      log.warn("Signature recovery failed for txId={}: {}", item.transactionId(), e.getMessage());
      terminalFailAndDropReservedSlot(
          item, signer.walletAddress(), nonceReservation, Web3TxFailureReason.SIGNATURE_INVALID);
      return;
    }

    if (!isReservationStillOwnedByTransaction(item, signer.walletAddress(), nonceReservation)) {
      log.warn(
          "sponsor nonce reservation changed after signing: txId={}, nonce={}, attemptId={}",
          item.transactionId(),
          nonceReservation.nonce(),
          nonceReservation.attemptId());
      failPrevalidate(
          item.transactionId(), Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(), false);
      return;
    }

    updateTransactionPort.markSigned(item.transactionId(), nonce, signed.rawTx(), signed.txHash());
    markSlotSigned(item, signer.walletAddress(), nonceReservation, signed);
    Map<String, Object> signDetail = new SignAuditDetail(nonce, signed.txHash()).toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.SIGN, null, signDetail);
    auditStateChange(item.transactionId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    markSlotBroadcasting(item, signer.walletAddress(), nonceReservation, signed);
    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(signed.rawTx()));
    Map<String, Object> broadcastDetail =
        new BroadcastAuditDetail(broadcast.success(), broadcast.txHash(), broadcast.failureReason())
            .toMap();
    audit(
        item.transactionId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail);

    if (broadcast.success()) {
      String txHash =
          (broadcast.txHash() == null || broadcast.txHash().isBlank())
              ? signed.txHash()
              : broadcast.txHash();
      updateTransactionPort.markPending(item.transactionId(), txHash);
      markSlotBroadcasted(item, signer.walletAddress(), nonceReservation);
      auditStateChange(item.transactionId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      return;
    }

    retry(
        item.transactionId(),
        broadcast.failureReason() != null
            ? broadcast.failureReason()
            : Web3TxFailureReason.BROADCAST_FAILED.code());
  }

  private void failPrevalidate(Long transactionId, String failureReason, boolean retryable) {
    if (retryable) {
      retry(transactionId, failureReason);
      return;
    }
    updateTransactionPort.scheduleRetry(transactionId, failureReason, null);
  }

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        new StateChangeAuditDetail(from, to).toMap());
  }

  private static boolean isFromAddressMatch(String itemFromAddress, String signerAddress) {
    if (itemFromAddress == null || signerAddress == null) {
      return false;
    }
    return itemFromAddress.trim().toLowerCase().equals(signerAddress.trim().toLowerCase());
  }

  private NonceReservation resolveNonce(
      LoadTransactionWorkPort.TransactionWorkItem item, String treasuryAddress) {
    if (item.nonce() != null) {
      return resolveExistingNonceReservation(item, treasuryAddress);
    }

    long chainId = web3CoreProperties.getChainId();
    LoadSponsorChainNoncePort.SponsorChainNonceSnapshot snapshot =
        loadSponsorChainNoncePort.loadSnapshot(chainId, treasuryAddress);
    SponsorNonceCoordinationResult result =
        coordinateSponsorNonceUseCase.execute(
            new SponsorNonceCoordinationCommand(
                chainId,
                treasuryAddress,
                snapshot.chainPendingNonce(),
                snapshot.chainLatestNonce(),
                snapshot.mainPendingNonce(),
                snapshot.subPendingNonce(),
                snapshot.mainLatestNonce(),
                snapshot.subLatestNonce(),
                SPONSOR_NONCE_OPEN_WINDOW_SIZE,
                item.transactionId(),
                null,
                LocalDateTime.now()));
    if (!result.reserved() || result.reservation() == null) {
      handleUnreservedNonceDecision(item, result);
      return null;
    }
    return new NonceReservation(result.reservation().nonce(), result.reservation().attemptId());
  }

  private NonceReservation resolveExistingNonceReservation(
      LoadTransactionWorkPort.TransactionWorkItem item, String treasuryAddress) {
    SponsorNonceSlotView slot =
        nonceSlotLifecycleUseCase
            .loadSlotsForReview(web3CoreProperties.getChainId(), treasuryAddress)
            .stream()
            .filter(candidate -> candidate.nonce() == item.nonce())
            .findFirst()
            .orElse(null);

    if (isActiveReservationOwnedByTransaction(slot, item.transactionId())) {
      return new NonceReservation(item.nonce(), slot.activeAttemptId());
    }

    log.warn(
        "stale sponsor nonce reservation: txId={}, nonce={}, slotStatus={}, slotActiveTxId={}",
        item.transactionId(),
        item.nonce(),
        slot == null ? null : slot.status(),
        slot == null ? null : slot.activeTxId());
    failPrevalidate(
        item.transactionId(), Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(), false);
    return null;
  }

  private boolean isActiveReservationOwnedByTransaction(
      SponsorNonceSlotView slot, Long transactionId) {
    return slot != null
        && slot.status() == SponsorNonceSlotStatus.RESERVED
        && slot.activeAttemptId() != null
        && transactionId != null
        && transactionId.equals(slot.activeTxId());
  }

  private boolean isReservationStillOwnedByTransaction(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String treasuryAddress,
      NonceReservation nonceReservation) {
    SponsorNonceSlotView slot =
        nonceSlotLifecycleUseCase
            .loadSlotsForReview(web3CoreProperties.getChainId(), treasuryAddress)
            .stream()
            .filter(candidate -> candidate.nonce() == nonceReservation.nonce())
            .findFirst()
            .orElse(null);
    return isActiveReservationOwnedByTransaction(slot, item.transactionId())
        && nonceReservation.attemptId() != null
        && nonceReservation.attemptId().equals(slot.activeAttemptId());
  }

  private void handleUnreservedNonceDecision(
      LoadTransactionWorkPort.TransactionWorkItem item, SponsorNonceCoordinationResult result) {
    switch (result.decision().type()) {
      case WAIT_FOR_OPEN_WINDOW, WAIT_FOR_IN_FLIGHT_SLOT, WAIT_FOR_IN_FLIGHT_REPLACEMENT ->
          retry(
              item.transactionId(),
              Web3TxFailureReason.SPONSOR_NONCE_WAIT_FOR_OPEN_WINDOW.code(),
              item);
      case RPC_DISAGREEMENT ->
          retry(
              item.transactionId(),
              Web3TxFailureReason.SPONSOR_NONCE_RPC_DISAGREEMENT.code(),
              item);
      default ->
          failPrevalidate(
              item.transactionId(),
              Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code(),
              false);
    }
  }

  private void markSlotSigned(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String fromAddress,
      NonceReservation nonceReservation,
      Web3ContractPort.SignedTransaction signed) {
    nonceSlotLifecycleUseCase.transition(
        baseTransition(item, fromAddress, nonceReservation, SponsorNonceSlotStatus.RESERVED)
            .toStatus(SponsorNonceSlotStatus.SIGNED)
            .hasRawTx(true)
            .hasTxHash(signed.txHash() != null && !signed.txHash().isBlank())
            .hasSigningEvidence(true)
            .build());
  }

  private void markSlotBroadcasting(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String fromAddress,
      NonceReservation nonceReservation,
      Web3ContractPort.SignedTransaction signed) {
    LocalDateTime now = LocalDateTime.now();
    nonceSlotLifecycleUseCase.transition(
        baseTransition(item, fromAddress, nonceReservation, SponsorNonceSlotStatus.SIGNED)
            .toStatus(SponsorNonceSlotStatus.BROADCASTING)
            .stateChangedAt(now)
            .broadcastRecoveryClaimOwner(workerId)
            .broadcastRecoveryClaimToken(UUID.randomUUID().toString())
            .broadcastRecoveryClaimExpiresAt(now.plusSeconds(claimTtlSeconds()))
            .broadcastRecoveryAttemptCount(1)
            .hasRawTx(true)
            .hasTxHash(signed.txHash() != null && !signed.txHash().isBlank())
            .hasSigningEvidence(true)
            .build());
  }

  private void markSlotBroadcasted(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String fromAddress,
      NonceReservation nonceReservation) {
    nonceSlotLifecycleUseCase.transition(
        baseTransition(item, fromAddress, nonceReservation, SponsorNonceSlotStatus.BROADCASTING)
            .toStatus(SponsorNonceSlotStatus.BROADCASTED)
            .hasRawTx(true)
            .hasTxHash(true)
            .hasSigningEvidence(true)
            .hasBroadcastEvidence(true)
            .build());
  }

  private RecordSponsorNonceSlotTransitionCommand.RecordSponsorNonceSlotTransitionCommandBuilder
      baseTransition(
          LoadTransactionWorkPort.TransactionWorkItem item,
          String fromAddress,
          NonceReservation nonceReservation,
          SponsorNonceSlotStatus fromStatus) {
    return RecordSponsorNonceSlotTransitionCommand.builder()
        .chainId(web3CoreProperties.getChainId())
        .fromAddress(fromAddress)
        .nonce(nonceReservation.nonce())
        .fromStatus(fromStatus)
        .activeAttemptId(nonceReservation.attemptId())
        .activeTxId(item.transactionId())
        .stateChangedAt(LocalDateTime.now());
  }

  private void terminalFailAndDropReservedSlot(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String fromAddress,
      NonceReservation nonceReservation,
      Web3TxFailureReason terminalReason) {
    updateTransactionPort.scheduleRetry(item.transactionId(), terminalReason.code(), null);
    try {
      nonceSlotLifecycleUseCase.transition(
          baseTransition(item, fromAddress, nonceReservation, SponsorNonceSlotStatus.RESERVED)
              .toStatus(SponsorNonceSlotStatus.DROPPED)
              .releasedAttemptId(nonceReservation.attemptId())
              .releasedTxId(item.transactionId())
              .releaseReason(terminalReason.code())
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      log.warn(
          "Failed to drop reserved sponsor nonce slot after terminal signing failure: txId={}, nonce={}",
          item.transactionId(),
          nonceReservation.nonce(),
          e);
    }
  }

  @Override
  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of(Web3InvalidInputException.class, Web3TransactionStateInvalidException.class);
  }

  private record NonceReservation(long nonce, Long attemptId) {}
}
