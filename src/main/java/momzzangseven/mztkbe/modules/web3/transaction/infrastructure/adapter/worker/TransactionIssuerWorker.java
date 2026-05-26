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
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceDecisionType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.BroadcastAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.PrevalidateAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.SignAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.SponsorNonceProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionIssuerWorker extends AbstractWeb3Worker {

  private final LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  private final LoadSponsorChainNoncePort loadSponsorChainNoncePort;
  private final CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final PersistSponsorNonceTransactionStateUseCase
      persistSponsorNonceTransactionStateUseCase;
  private final Web3ContractPort web3ContractPort;
  private final int sponsorNonceOpenWindowSize;

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
      PersistSponsorNonceTransactionStateUseCase persistSponsorNonceTransactionStateUseCase,
      Web3ContractPort web3ContractPort,
      SponsorNonceProperties sponsorNonceProperties,
      TransactionRewardTokenProperties rewardTokenProperties,
      RetryStrategy retryStrategy) {
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
    this.persistSponsorNonceTransactionStateUseCase = persistSponsorNonceTransactionStateUseCase;
    this.web3ContractPort = web3ContractPort;
    this.sponsorNonceOpenWindowSize = sponsorNonceProperties.getOpenWindowSize();
  }

  @Scheduled(fixedDelayString = "${web3.transaction.issuer.fixed-delay:1000}")
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

    NonceReservation nonceReservation = resolveNonce(item, signer.walletAddress());
    if (nonceReservation == null) {
      return;
    }

    Web3ContractPort.PrevalidateResult prevalidateResult;
    try {
      prevalidateResult =
          web3ContractPort.prevalidate(
              new Web3ContractPort.PrevalidateCommand(
                  signer.walletAddress(), item.toAddress(), item.amountWei()));
    } catch (RuntimeException e) {
      if (e instanceof Web3InvalidInputException) {
        dropReservedSlot(
            item, signer.walletAddress(), nonceReservation, prevalidateExceptionReason(e));
      }
      throw e;
    }

    Map<String, Object> prevalidateDetail =
        new PrevalidateAuditDetail(
                prevalidateResult.detail(),
                prevalidateResult.ok(),
                prevalidateResult.failureReason())
            .toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.PREVALIDATE, null, prevalidateDetail);

    if (!prevalidateResult.ok()) {
      failPrevalidateAfterReservation(
          item,
          signer.walletAddress(),
          nonceReservation,
          prevalidateResult.failureReason(),
          prevalidateResult.retryable());
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
                  item.chainId(),
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

    persistSponsorNonceTransactionStateUseCase.markSigned(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNonceSignedCommand(
            item.transactionId(),
            item.chainId(),
            signer.walletAddress(),
            nonce,
            nonceReservation.attemptId(),
            signed.rawTx(),
            signed.txHash(),
            LocalDateTime.now()));
    Map<String, Object> signDetail = new SignAuditDetail(nonce, signed.txHash()).toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.SIGN, null, signDetail);
    auditStateChange(item.transactionId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    if (!claimSignedForDirectBroadcast(item.transactionId())) {
      return;
    }

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

    if (broadcast.success() || isBroadcastAlreadyKnown(broadcast)) {
      String txHash =
          (broadcast.txHash() == null || broadcast.txHash().isBlank())
              ? signed.txHash()
              : broadcast.txHash();
      persistSponsorNonceTransactionStateUseCase.markPending(
          new PersistSponsorNonceTransactionStateUseCase.SponsorNoncePendingCommand(
              item.transactionId(),
              item.chainId(),
              signer.walletAddress(),
              nonceReservation.nonce(),
              nonceReservation.attemptId(),
              txHash,
              LocalDateTime.now()));
      auditStateChange(item.transactionId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      return;
    }
    if (isBroadcastNonceTooLow(broadcast)) {
      persistSponsorNonceTransactionStateUseCase.markBroadcastingOperatorReview(
          new PersistSponsorNonceTransactionStateUseCase
              .SponsorNonceBroadcastingOperatorReviewCommand(
              item.transactionId(),
              item.chainId(),
              signer.walletAddress(),
              nonceReservation.nonce(),
              nonceReservation.attemptId(),
              Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code(),
              Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code(),
              true,
              true,
              true,
              true,
              LocalDateTime.now()));
      return;
    }

    retry(
        item.transactionId(),
        broadcast.failureReason() != null
            ? broadcast.failureReason()
            : Web3TxFailureReason.BROADCAST_FAILED.code());
  }

  private boolean isBroadcastAlreadyKnown(Web3ContractPort.BroadcastResult broadcast) {
    return broadcast != null
        && Web3TxFailureReason.BROADCAST_ALREADY_KNOWN.code().equals(broadcast.failureReason());
  }

  private boolean isBroadcastNonceTooLow(Web3ContractPort.BroadcastResult broadcast) {
    return broadcast != null
        && Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code().equals(broadcast.failureReason());
  }

  private boolean isSlotNotFound(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("nonce slot not found");
  }

  private boolean isStaleActual(
      Web3TransactionStateInvalidException e, SponsorNonceSlotStatus actualStatus) {
    return e.getMessage() != null
        && e.getMessage().contains("stale nonce slot transition")
        && e.getMessage().contains("actual=" + actualStatus);
  }

  private boolean claimSignedForDirectBroadcast(Long transactionId) {
    boolean claimed =
        updateTransactionPort.claimForProcessing(
            transactionId,
            Web3TxStatus.SIGNED,
            workerId,
            LocalDateTime.now().plusSeconds(claimTtlSeconds()));
    if (!claimed) {
      log.info(
          "Skipping direct reward broadcast because signed tx is already claimed or moved: txId={}",
          transactionId);
    }
    return claimed;
  }

  private void failPrevalidate(Long transactionId, String failureReason, boolean retryable) {
    if (retryable) {
      retry(transactionId, failureReason);
      return;
    }
    updateTransactionPort.scheduleRetry(transactionId, failureReason, null);
  }

  private void failPrevalidateAfterReservation(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String fromAddress,
      NonceReservation nonceReservation,
      String failureReason,
      boolean retryable) {
    String reason =
        failureReason == null || failureReason.isBlank()
            ? Web3TxFailureReason.PREVALIDATE_REVERT.code()
            : failureReason;
    if (!retryable) {
      dropReservedSlot(item, fromAddress, nonceReservation, reason);
    }
    failPrevalidate(item.transactionId(), reason, retryable);
  }

  private String prevalidateExceptionReason(RuntimeException e) {
    if (e instanceof Web3InvalidInputException) {
      return Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code();
    }
    return Web3TxFailureReason.RPC_UNAVAILABLE.code();
  }

  private void dropReservedSlot(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String fromAddress,
      NonceReservation nonceReservation,
      String terminalReason) {
    try {
      nonceSlotLifecycleUseCase.transition(
          baseTransition(item, fromAddress, nonceReservation, SponsorNonceSlotStatus.RESERVED)
              .toStatus(SponsorNonceSlotStatus.DROPPED)
              .stateChangedAt(LocalDateTime.now())
              .releasedAttemptId(nonceReservation.attemptId())
              .releaseReason(terminalReason)
              .terminalReason(terminalReason)
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e)
          || isStaleActual(e, SponsorNonceSlotStatus.DROPPED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED_UNKNOWN)
          || isStaleActual(e, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)) {
        log.debug(
            "Skipping reserved slot drop after prevalidate failure for txId={}: {}",
            item.transactionId(),
            e.getMessage());
        return;
      }
      throw e;
    }
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

    long chainId = item.chainId();
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
                sponsorNonceOpenWindowSize,
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
            .loadSlotForReview(item.chainId(), treasuryAddress, item.nonce())
            .orElse(null);

    if (isActiveReservationOwnedByTransaction(slot, item.transactionId())) {
      LoadSponsorChainNoncePort.SponsorChainNonceSnapshot snapshot =
          loadSponsorChainNoncePort.loadSnapshot(item.chainId(), treasuryAddress);
      SponsorNonceCoordinationResult reconciliation =
          coordinateSponsorNonceUseCase.execute(
              new SponsorNonceCoordinationCommand(
                  item.chainId(),
                  treasuryAddress,
                  snapshot.chainPendingNonce(),
                  snapshot.chainLatestNonce(),
                  snapshot.mainPendingNonce(),
                  snapshot.subPendingNonce(),
                  snapshot.mainLatestNonce(),
                  snapshot.subLatestNonce(),
                  sponsorNonceOpenWindowSize,
                  null,
                  null,
                  LocalDateTime.now()));
      if (isRpcDisagreement(reconciliation)) {
        retry(
            item.transactionId(), Web3TxFailureReason.SPONSOR_NONCE_RPC_DISAGREEMENT.code(), item);
        return null;
      }
      if (snapshot.chainLatestNonce() > item.nonce()) {
        log.warn(
            "sponsor nonce reservation consumed before signing: txId={}, nonce={}, chainLatest={}",
            item.transactionId(),
            item.nonce(),
            snapshot.chainLatestNonce());
        failPrevalidate(
            item.transactionId(),
            Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(),
            false);
        return null;
      }
      SponsorNonceSlotView refreshedSlot =
          nonceSlotLifecycleUseCase
              .loadSlotForReview(item.chainId(), treasuryAddress, item.nonce())
              .orElse(null);
      if (isActiveReservationOwnedByTransaction(refreshedSlot, item.transactionId())) {
        return new NonceReservation(item.nonce(), refreshedSlot.activeAttemptId());
      }
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

  private boolean isRpcDisagreement(SponsorNonceCoordinationResult result) {
    return result != null
        && result.decision() != null
        && result.decision().type() == SponsorNonceDecisionType.RPC_DISAGREEMENT;
  }

  private boolean isReservationStillOwnedByTransaction(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String treasuryAddress,
      NonceReservation nonceReservation) {
    SponsorNonceSlotView slot =
        nonceSlotLifecycleUseCase
            .loadSlotForReview(item.chainId(), treasuryAddress, nonceReservation.nonce())
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

  private RecordSponsorNonceSlotTransitionCommand.RecordSponsorNonceSlotTransitionCommandBuilder
      baseTransition(
          LoadTransactionWorkPort.TransactionWorkItem item,
          String fromAddress,
          NonceReservation nonceReservation,
          SponsorNonceSlotStatus fromStatus) {
    return RecordSponsorNonceSlotTransitionCommand.builder()
        .chainId(item.chainId())
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
    persistSponsorNonceTransactionStateUseCase.failTerminalAndDropReservedSlot(
        new SponsorNonceTerminalReservedSlotFailureCommand(
            item.transactionId(),
            item.chainId(),
            fromAddress,
            nonceReservation.nonce(),
            nonceReservation.attemptId(),
            terminalReason.code(),
            LocalDateTime.now()));
  }

  @Override
  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of(Web3InvalidInputException.class, Web3TransactionStateInvalidException.class);
  }

  @Override
  protected String permanentFailureReason(Throwable throwable, String defaultFailureReason) {
    if (throwable instanceof Web3TransactionStateInvalidException) {
      return Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code();
    }
    if (throwable instanceof Web3InvalidInputException) {
      return Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code();
    }
    return super.permanentFailureReason(throwable, defaultFailureReason);
  }

  private record NonceReservation(long nonce, Long attemptId) {}
}
