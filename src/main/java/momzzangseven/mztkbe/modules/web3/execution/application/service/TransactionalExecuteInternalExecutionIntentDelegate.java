package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionSignerGates;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalInternalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionFailureReason;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionAuditEventType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionType;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.util.KmsClientErrorClassifier;

/**
 * Transactional inner stage of internal execution intent processing.
 *
 * <p>Owns the FOR UPDATE intent claim, nonce reservation, signed persistence, and broadcast outcome
 * persistence. Sponsor-wallet preflight and the KMS signing call run outside the DB transaction so
 * external latency never pins a JDBC connection while an intent or sponsor nonce row is locked.
 *
 * <p>This class is registered as a plain bean. Each stateful DB phase explicitly enters {@link
 * RunExecutionTransactionPort#requiresNew(java.util.function.Supplier)}.
 */
@Slf4j
@RequiredArgsConstructor
public class TransactionalExecuteInternalExecutionIntentDelegate
    implements ExecuteTransactionalInternalExecutionIntentDelegatePort {

  private static final String BROADCAST_FAILED = "BROADCAST_FAILED";
  private static final String INTERNAL_ISSUER_INVALID_INTENT = "INTERNAL_ISSUER_INVALID_INTENT";
  private static final int SPONSOR_NONCE_OPEN_WINDOW_SIZE = 3;
  private static final String SPONSOR_KMS_SIGN_FAILED_TERMINAL =
      "sponsor kms sign failed (terminal)";
  private static final String SPONSOR_SIGNATURE_INVALID = "sponsor signature invalid";

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  private final ExecutionEip1559SigningPort executionEip1559SigningPort;
  private final Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  private final LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;
  private final RunAfterCommitPort runAfterCommitPort;
  private final RunExecutionTransactionPort transactionPort;
  private final Clock appClock;

  @Override
  public ExecuteInternalExecutionIntentResult execute(
      ExecuteInternalExecutionIntentCommand command, InternalExecutionSignerGates signerGates) {
    InternalSigningPreparation preparation =
        transactionPort.requiresNew(() -> prepareInternalSigning(command, signerGates));
    if (preparation.result() != null) {
      return preparation.result();
    }

    ExecutionIntent signableIntent = preparation.intent();
    ExecutionTransactionGatewayPort.TransactionRecord created = preparation.transaction();
    SponsorNonceContext sponsorNonce = preparation.sponsorNonce();
    ExecutionIntent intent = signableIntent;

    ExecutionEip1559SigningPort.SignedTransaction signedTransaction;
    try {
      signedTransaction =
          executionEip1559SigningPort.sign(
              new ExecutionEip1559SigningPort.SignCommand(
                  signableIntent.getUnsignedTxSnapshot().chainId(),
                  signableIntent.getUnsignedTxSnapshot().expectedNonce(),
                  signableIntent.getUnsignedTxSnapshot().gasLimit(),
                  signableIntent.getUnsignedTxSnapshot().toAddress(),
                  signableIntent.getUnsignedTxSnapshot().valueWei(),
                  signableIntent.getUnsignedTxSnapshot().data(),
                  signableIntent.getUnsignedTxSnapshot().maxPriorityFeePerGas(),
                  signableIntent.getUnsignedTxSnapshot().maxFeePerGas(),
                  preparation.signer()));
    } catch (KmsSignFailedException e) {
      log.warn(
          "internal sponsor KMS sign failed for intent={}: {}",
          intent.getPublicId(),
          e.getMessage());
      if (KmsClientErrorClassifier.isTerminal(e)) {
        return failInternalSigningAndQuarantine(
            intent.getPublicId(),
            preparation.actionHandler(),
            preparation.actionPlan(),
            sponsorNonce,
            ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL.name(),
            ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL,
            ErrorCode.WEB3_KMS_SIGN_FAILED.name(),
            SPONSOR_KMS_SIGN_FAILED_TERMINAL);
      }
      // Transient: leave the intent in AWAITING_SIGNATURE with the reserved transaction bound.
      // The next cron tick re-claims and resumes the same CREATED transaction/nonce.
      return ExecuteInternalExecutionIntentResult.transientRetry(
          intent.getPublicId(), intent.getStatus());
    } catch (SignatureRecoveryException e) {
      log.warn(
          "internal sponsor signature recovery failed for intent={}: {}",
          intent.getPublicId(),
          e.getMessage());
      return failInternalSigningAndQuarantine(
          intent.getPublicId(),
          preparation.actionHandler(),
          preparation.actionPlan(),
          sponsorNonce,
          ExecutionFailureReason.SIGNATURE_INVALID.name(),
          ExecutionFailureReason.SIGNATURE_INVALID,
          ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED.name(),
          SPONSOR_SIGNATURE_INVALID);
    } catch (Web3InvalidInputException e) {
      return failInternalSigningAndQuarantine(
          intent.getPublicId(),
          preparation.actionHandler(),
          preparation.actionPlan(),
          sponsorNonce,
          "PREVALIDATE_INVALID_COMMAND",
          null,
          INTERNAL_ISSUER_INVALID_INTENT,
          e.getMessage());
    }

    InternalSignedSubmission signedSubmission =
        persistInternalSignedIntent(intent.getPublicId(), sponsorNonce, signedTransaction);
    ExecutionIntent signedIntent = signedSubmission.intent();
    if (!signedSubmission.signedNow()) {
      return new ExecuteInternalExecutionIntentResult(
          true,
          false,
          signedIntent.getPublicId(),
          signedIntent.getStatus(),
          signedIntent.getSubmittedTxId(),
          created.status(),
          created.txHash());
    }
    if (signedIntent.getStatus() != ExecutionIntentStatus.SIGNED) {
      return new ExecuteInternalExecutionIntentResult(
          true,
          false,
          signedIntent.getPublicId(),
          signedIntent.getStatus(),
          signedIntent.getSubmittedTxId(),
          created.status(),
          created.txHash());
    }
    scheduleBroadcastAfterCommit(
        signedIntent,
        created.transactionId(),
        signedTransaction.rawTransaction(),
        signedTransaction.txHash(),
        preparation.actionHandler(),
        preparation.actionPlan(),
        sponsorNonce);
    return new ExecuteInternalExecutionIntentResult(
        true,
        false,
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.transactionId(),
        ExecutionTransactionStatus.SIGNED,
        signedTransaction.txHash());
  }

  private InternalSigningPreparation prepareInternalSigning(
      ExecuteInternalExecutionIntentCommand command, InternalExecutionSignerGates signerGates) {
    ExecutionIntent intent =
        executionIntentPersistencePort
            .claimNextInternalExecutableForUpdate(command.actionTypes())
            .orElse(null);
    if (intent == null) {
      return InternalSigningPreparation.completed(ExecuteInternalExecutionIntentResult.notFound());
    }

    ExecutionActionHandlerPort actionHandler = findActionHandler(intent).orElse(null);
    if (actionHandler == null) {
      return InternalSigningPreparation.completed(
          quarantineInvalidIntent(
              intent,
              null,
              null,
              INTERNAL_ISSUER_INVALID_INTENT,
              "no execution action handler for actionType=" + intent.getActionType()));
    }

    ExecutionActionPlan actionPlan;
    try {
      actionPlan = actionHandler.buildActionPlan(intent);
      actionHandler.beforeExecute(intent, actionPlan);
    } catch (IllegalStateException | Web3InvalidInputException e) {
      return InternalSigningPreparation.completed(
          quarantineInvalidIntent(
              intent, actionHandler, null, INTERNAL_ISSUER_INVALID_INTENT, e.getMessage()));
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    if (!intent.getExpiresAt().isAfter(now)) {
      ExecutionIntent expired =
          executionIntentPersistencePort.update(
              intent.expire(
                  ErrorCode.EXECUTION_INTENT_EXPIRED.name(),
                  ErrorCode.EXECUTION_INTENT_EXPIRED.getMessage(),
                  now));
      publishTerminated(
          expired, ExecutionIntentStatus.EXPIRED, ErrorCode.EXECUTION_INTENT_EXPIRED.name());
      return InternalSigningPreparation.completed(
          new ExecuteInternalExecutionIntentResult(
              true, false, expired.getPublicId(), expired.getStatus(), null, null, null));
    }

    if (intent.getMode() != ExecutionMode.EIP1559) {
      return InternalSigningPreparation.completed(
          quarantineInvalidIntent(
              intent,
              actionHandler,
              actionPlan,
              INTERNAL_ISSUER_INVALID_INTENT,
              "internal issuer supports only EIP1559"));
    }
    if (!intent.getStatus().isSignable()) {
      return InternalSigningPreparation.completed(
          new ExecuteInternalExecutionIntentResult(
              true,
              false,
              intent.getPublicId(),
              intent.getStatus(),
              intent.getSubmittedTxId(),
              null,
              null));
    }
    if (intent.getUnsignedTxSnapshot() == null || intent.getUnsignedTxFingerprint() == null) {
      return InternalSigningPreparation.completed(
          quarantineInvalidIntent(
              intent,
              actionHandler,
              actionPlan,
              INTERNAL_ISSUER_INVALID_INTENT,
              "internal executable intent requires unsigned tx snapshot"));
    }

    // The signer wallet was preflighted outside the TX boundary; signer + walletInfo are
    // pre-validated for the claimed action type.
    SponsorWalletGate gate = signerGates.gateFor(intent.getActionType());
    TreasuryWalletInfo walletInfo = gate.walletInfo();
    String expectedSigner = walletInfo.walletAddress();
    TreasurySigner signer = gate.signer();

    if (!expectedSigner.equalsIgnoreCase(intent.getUnsignedTxSnapshot().fromAddress())) {
      return InternalSigningPreparation.completed(
          quarantineInvalidIntent(
              intent,
              actionHandler,
              actionPlan,
              INTERNAL_ISSUER_INVALID_INTENT,
              "internal intent signer does not match expected action signer"));
    }

    if (intent.getSubmittedTxId() != null) {
      ExecutionTransactionGatewayPort.TransactionRecord submittedTransaction =
          loadTransaction(intent.getSubmittedTxId());
      if (!canResumeInternalSigning(intent, submittedTransaction, expectedSigner)) {
        return InternalSigningPreparation.completed(
            new ExecuteInternalExecutionIntentResult(
                true,
                false,
                intent.getPublicId(),
                intent.getStatus(),
                submittedTransaction.transactionId(),
                submittedTransaction.status(),
                submittedTransaction.txHash()));
      }
      SponsorNonceContext sponsorNonce = resumeInternalSubmission(submittedTransaction);
      ExecutionIntent signableIntent = rebindReservedNonce(intent, sponsorNonce.nonce());
      if (signableIntent.getUnsignedTxSnapshot().expectedNonce()
              != intent.getUnsignedTxSnapshot().expectedNonce()
          || !signableIntent.getUnsignedTxFingerprint().equals(intent.getUnsignedTxFingerprint())) {
        signableIntent =
            executionIntentPersistencePort.update(
                intent.bindSubmittedTransactionAndUnsignedSnapshot(
                    submittedTransaction.transactionId(),
                    signableIntent.getUnsignedTxSnapshot(),
                    signableIntent.getUnsignedTxFingerprint(),
                    LocalDateTime.now(appClock)));
      }
      return InternalSigningPreparation.ready(
          signableIntent, submittedTransaction, sponsorNonce, actionHandler, actionPlan, signer);
    }

    ExecutionTransactionGatewayPort.TransactionRecord created =
        executionTransactionGatewayPort.createAndFlush(
            new ExecutionTransactionGatewayPort.CreateTransactionCommand(
                intent.getRootIdempotencyKey() + ":" + intent.getAttemptNo(),
                actionPlan.referenceType(),
                intent.getResourceId(),
                intent.getRequesterUserId(),
                intent.getCounterpartyUserId(),
                expectedSigner,
                intent.getUnsignedTxSnapshot().toAddress(),
                actionPlan.amountWei(),
                intent.getUnsignedTxSnapshot().chainId(),
                null,
                ExecutionTransactionStatus.CREATED,
                ExecutionTransactionType.EIP1559,
                null,
                null,
                null,
                null));
    SponsorNonceContext sponsorNonce =
        reserveSponsorNonce(
            created.transactionId(), expectedSigner, intent.getUnsignedTxSnapshot().chainId());
    ExecutionIntent signableIntent = rebindReservedNonce(intent, sponsorNonce.nonce());
    ExecutionIntent boundIntent =
        executionIntentPersistencePort.update(
            intent.bindSubmittedTransactionAndUnsignedSnapshot(
                created.transactionId(),
                signableIntent.getUnsignedTxSnapshot(),
                signableIntent.getUnsignedTxFingerprint(),
                LocalDateTime.now(appClock)));
    return InternalSigningPreparation.ready(
        boundIntent, created, sponsorNonce, actionHandler, actionPlan, signer);
  }

  private void scheduleBroadcastAfterCommit(
      ExecutionIntent signedIntent,
      Long transactionId,
      String rawTx,
      String fallbackTxHash,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      SponsorNonceContext sponsorNonceContext) {
    runAfterCommitPort.runAfterCommitWithoutTransaction(
        () -> {
          markSponsorSlotBroadcasting(sponsorNonceContext, fallbackTxHash);
          ExecutionTransactionGatewayPort.BroadcastResult broadcast =
              executionTransactionGatewayPort.broadcast(rawTx);
          runAfterCommitPort.runAfterCommit(
              () ->
                  persistBroadcastOutcome(
                      signedIntent.getPublicId(),
                      transactionId,
                      fallbackTxHash,
                      broadcast,
                      actionHandler,
                      actionPlan,
                      sponsorNonceContext));
        });
  }

  private void persistBroadcastOutcome(
      String executionIntentId,
      Long transactionId,
      String fallbackTxHash,
      ExecutionTransactionGatewayPort.BroadcastResult broadcast,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      SponsorNonceContext sponsorNonceContext) {
    transactionPort.requiresNew(
        () ->
            persistBroadcastOutcomeInTransaction(
                executionIntentId,
                transactionId,
                fallbackTxHash,
                broadcast,
                actionHandler,
                actionPlan,
                sponsorNonceContext));
  }

  private void persistBroadcastOutcomeInTransaction(
      String executionIntentId,
      Long transactionId,
      String fallbackTxHash,
      ExecutionTransactionGatewayPort.BroadcastResult broadcast,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      SponsorNonceContext sponsorNonceContext) {
    audit(
        transactionId,
        ExecutionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail(broadcast));
    ExecutionIntent current =
        executionIntentPersistencePort.findByPublicIdForUpdate(executionIntentId).orElse(null);
    if (current == null
        || current.getSubmittedTxId() == null
        || !current.getSubmittedTxId().equals(transactionId)
        || current.getStatus() != ExecutionIntentStatus.SIGNED) {
      log.warn(
          "Skipping internal broadcast outcome for stale execution intent: intentId={}, txId={}",
          executionIntentId,
          transactionId);
      return;
    }

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? fallbackTxHash
              : broadcast.txHash();
      executionTransactionGatewayPort.markPending(transactionId, txHash);
      markSponsorSlotBroadcasted(sponsorNonceContext);
      ExecutionIntent pendingIntent =
          executionIntentPersistencePort.update(
              current.markPendingOnchain(transactionId, LocalDateTime.now(appClock)));
      ExecutionActionHookRunner.afterTransactionSubmitted(
          runAfterCommitPort,
          actionHandler,
          pendingIntent,
          actionPlan,
          ExecutionTransactionStatus.PENDING);
      return;
    }

    String failureReason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? BROADCAST_FAILED
            : broadcast.failureReason();
    executionTransactionGatewayPort.scheduleRetry(
        transactionId,
        failureReason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    ExecutionActionHookRunner.afterTransactionSubmitted(
        runAfterCommitPort, actionHandler, current, actionPlan, ExecutionTransactionStatus.SIGNED);
  }

  private ExecutionTransactionGatewayPort.TransactionRecord loadTransaction(Long transactionId) {
    return executionTransactionGatewayPort
        .findById(transactionId)
        .orElseThrow(
            () -> new Web3InvalidInputException("transaction not found: " + transactionId));
  }

  private boolean canResumeInternalSigning(
      ExecutionIntent intent,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      String expectedSigner) {
    return intent.getMode() == ExecutionMode.EIP1559
        && intent.getStatus() == ExecutionIntentStatus.AWAITING_SIGNATURE
        && transaction.status() == ExecutionTransactionStatus.CREATED
        && transaction.chainId() != null
        && transaction.nonce() != null
        && transaction.fromAddress() != null
        && transaction.fromAddress().equalsIgnoreCase(expectedSigner);
  }

  private SponsorNonceContext resumeInternalSubmission(
      ExecutionTransactionGatewayPort.TransactionRecord transaction) {
    return new SponsorNonceContext(
        transaction.chainId(),
        transaction.fromAddress(),
        transaction.nonce(),
        null,
        transaction.transactionId());
  }

  private InternalSignedSubmission persistInternalSignedIntent(
      String executionIntentId,
      SponsorNonceContext sponsorNonce,
      ExecutionEip1559SigningPort.SignedTransaction signedTransaction) {
    return transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(executionIntentId)
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + executionIntentId));
          if (current.getStatus() == ExecutionIntentStatus.SIGNED) {
            return new InternalSignedSubmission(current, false);
          }
          if (current.getStatus() != ExecutionIntentStatus.AWAITING_SIGNATURE
              || !sponsorNonce.transactionId().equals(current.getSubmittedTxId())) {
            throw new Web3InvalidInputException("internal execution intent signing state changed");
          }
          executionTransactionGatewayPort.markSigned(
              sponsorNonce.transactionId(),
              sponsorNonce.nonce(),
              signedTransaction.rawTransaction(),
              signedTransaction.txHash());
          markSponsorSlotSigned(sponsorNonce, signedTransaction.txHash());
          audit(
              sponsorNonce.transactionId(),
              ExecutionAuditEventType.SIGN,
              null,
              java.util.Map.of("mode", current.getMode().name(), "internal", true));
          ExecutionIntent signedIntent =
              executionIntentPersistencePort.update(
                  current.markSigned(sponsorNonce.transactionId(), LocalDateTime.now(appClock)));
          return new InternalSignedSubmission(signedIntent, true);
        });
  }

  private ExecuteInternalExecutionIntentResult failInternalSigningAndQuarantine(
      String executionIntentId,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      SponsorNonceContext sponsorNonce,
      String transactionFailureReason,
      ExecutionFailureReason eventReason,
      String errorCode,
      String failureReason) {
    return transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(executionIntentId)
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + executionIntentId));
          if (current.getStatus() != ExecutionIntentStatus.AWAITING_SIGNATURE
              || !sponsorNonce.transactionId().equals(current.getSubmittedTxId())) {
            return new ExecuteInternalExecutionIntentResult(
                true,
                false,
                current.getPublicId(),
                current.getStatus(),
                current.getSubmittedTxId(),
                null,
                null);
          }
          markCreatedTransactionTerminal(sponsorNonce.transactionId(), transactionFailureReason);
          dropSponsorReservedSlot(sponsorNonce, transactionFailureReason);
          return quarantineInvalidIntent(
              current, actionHandler, actionPlan, errorCode, failureReason, eventReason);
        });
  }

  private SponsorNonceContext reserveSponsorNonce(
      Long transactionId, String sponsorAddress, long chainId) {
    ExecutionTransactionGatewayPort.SponsorNonceSnapshot snapshot =
        executionTransactionGatewayPort.loadSponsorNonceSnapshot(chainId, sponsorAddress);
    ExecutionTransactionGatewayPort.SponsorNonceCoordinationRecord coordination =
        executionTransactionGatewayPort.coordinateSponsorNonce(
            new ExecutionTransactionGatewayPort.CoordinateSponsorNonceCommand(
                chainId,
                sponsorAddress,
                snapshot.chainPendingNonce(),
                snapshot.chainLatestNonce(),
                snapshot.mainPendingNonce(),
                snapshot.subPendingNonce(),
                snapshot.mainLatestNonce(),
                snapshot.subLatestNonce(),
                SPONSOR_NONCE_OPEN_WINDOW_SIZE,
                transactionId,
                null,
                LocalDateTime.now(appClock)));
    if (!coordination.reserved() || coordination.nonce() == null) {
      throwSponsorNonceUnavailable(coordination);
    }
    return new SponsorNonceContext(
        chainId, sponsorAddress, coordination.nonce(), coordination.attemptId(), transactionId);
  }

  private void throwSponsorNonceUnavailable(
      ExecutionTransactionGatewayPort.SponsorNonceCoordinationRecord coordination) {
    boolean retryable =
        "WAIT_FOR_OPEN_WINDOW".equals(coordination.decisionType())
            || "WAIT_FOR_IN_FLIGHT_SLOT".equals(coordination.decisionType())
            || "WAIT_FOR_IN_FLIGHT_REPLACEMENT".equals(coordination.decisionType())
            || "RPC_DISAGREEMENT".equals(coordination.decisionType());
    throw new Web3TransferException(
        ErrorCode.WEB3_SPONSOR_NONCE_UNAVAILABLE,
        "internal sponsor nonce unavailable: decision="
            + coordination.decisionType()
            + ", reason="
            + coordination.reason(),
        retryable);
  }

  private void markSponsorSlotSigned(SponsorNonceContext context, String txHash) {
    transitionSponsorSlot(
        context,
        "RESERVED",
        "SIGNED",
        null,
        null,
        null,
        null,
        0,
        true,
        txHash != null && !txHash.isBlank(),
        true,
        false);
  }

  private void markSponsorSlotBroadcasting(SponsorNonceContext context, String txHash) {
    transitionSponsorSlot(
        context,
        "SIGNED",
        "BROADCASTING",
        null,
        "internal-broadcast-" + context.transactionId(),
        UUID.randomUUID().toString(),
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()),
        1,
        true,
        txHash != null && !txHash.isBlank(),
        true,
        false);
  }

  private void markSponsorSlotBroadcasted(SponsorNonceContext context) {
    transitionSponsorSlot(
        context, "BROADCASTING", "BROADCASTED", null, null, null, null, 0, true, true, true, true);
  }

  private void dropSponsorReservedSlot(SponsorNonceContext context, String releaseReason) {
    transitionSponsorSlot(
        context,
        "RESERVED",
        "DROPPED",
        releaseReason,
        null,
        null,
        null,
        0,
        false,
        false,
        false,
        false);
  }

  private void markCreatedTransactionTerminal(Long transactionId, String failureReason) {
    executionTransactionGatewayPort.scheduleRetry(transactionId, failureReason, null);
  }

  private void transitionSponsorSlot(
      SponsorNonceContext context,
      String fromStatus,
      String toStatus,
      String releaseReason,
      String broadcastClaimOwner,
      String broadcastClaimToken,
      LocalDateTime broadcastClaimExpiresAt,
      int broadcastAttemptCount,
      boolean hasRawTx,
      boolean hasTxHash,
      boolean hasSigningEvidence,
      boolean hasBroadcastEvidence) {
    executionTransactionGatewayPort.transitionSponsorNonceSlot(
        new ExecutionTransactionGatewayPort.SponsorNonceSlotTransitionCommand(
            context.chainId(),
            context.fromAddress(),
            context.nonce(),
            fromStatus,
            toStatus,
            context.attemptId(),
            context.transactionId(),
            "DROPPED".equals(toStatus) ? context.attemptId() : null,
            "DROPPED".equals(toStatus) ? context.transactionId() : null,
            LocalDateTime.now(appClock),
            releaseReason,
            null,
            broadcastClaimOwner,
            broadcastClaimToken,
            broadcastClaimExpiresAt,
            broadcastAttemptCount,
            hasRawTx,
            hasTxHash,
            hasSigningEvidence,
            hasBroadcastEvidence,
            false));
  }

  private ExecutionIntent rebindReservedNonce(ExecutionIntent intent, long reservedNonce) {
    if (intent.getUnsignedTxSnapshot().expectedNonce() == reservedNonce) {
      return intent;
    }
    var reboundSnapshot =
        new momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot(
            intent.getUnsignedTxSnapshot().chainId(),
            intent.getUnsignedTxSnapshot().fromAddress(),
            intent.getUnsignedTxSnapshot().toAddress(),
            intent.getUnsignedTxSnapshot().valueWei(),
            intent.getUnsignedTxSnapshot().data(),
            reservedNonce,
            intent.getUnsignedTxSnapshot().gasLimit(),
            intent.getUnsignedTxSnapshot().maxPriorityFeePerGas(),
            intent.getUnsignedTxSnapshot().maxFeePerGas());
    return intent.rebindUnsignedTxSnapshot(
        reboundSnapshot, eip1559TransactionCodecPort.computeFingerprint(reboundSnapshot));
  }

  private void audit(
      Long transactionId,
      ExecutionAuditEventType eventType,
      String rpcAlias,
      java.util.Map<String, Object> detail) {
    try {
      executionTransactionGatewayPort.recordAudit(
          new ExecutionTransactionGatewayPort.AuditCommand(
              transactionId, eventType, rpcAlias, detail));
    } catch (Exception e) {
      log.warn(
          "failed to record internal execution audit: txId={}, eventType={}",
          transactionId,
          eventType,
          e);
    }
  }

  private java.util.Optional<ExecutionActionHandlerPort> findActionHandler(ExecutionIntent intent) {
    return ExecutionActionHandlerPort.findMatching(executionActionHandlerPorts, intent);
  }

  private ExecuteInternalExecutionIntentResult quarantineInvalidIntent(
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      String errorCode,
      String failureReason) {
    return quarantineInvalidIntent(
        intent, actionHandler, actionPlan, errorCode, failureReason, null);
  }

  private ExecuteInternalExecutionIntentResult quarantineInvalidIntent(
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      String errorCode,
      String failureReason,
      ExecutionFailureReason eventReason) {
    LocalDateTime now = LocalDateTime.now(appClock);
    ExecutionIntent canceled =
        executionIntentPersistencePort.update(
            intent.cancel(
                errorCode,
                failureReason == null || failureReason.isBlank() ? errorCode : failureReason,
                now));
    if (actionHandler != null && actionPlan != null) {
      String publishedReason = eventReason != null ? eventReason.name() : errorCode;
      publishTerminated(canceled, ExecutionIntentStatus.CANCELED, publishedReason);
    }
    log.error(
        "internal execution issuer quarantined invalid intent: "
            + "executionIntentId={}, actionType={}, errorCode={}, reason={}",
        canceled.getPublicId(),
        canceled.getActionType(),
        errorCode,
        canceled.getLastErrorReason());
    return ExecuteInternalExecutionIntentResult.quarantined(
        canceled.getPublicId(), canceled.getStatus());
  }

  private java.util.Map<String, Object> broadcastDetail(
      ExecutionTransactionGatewayPort.BroadcastResult broadcast) {
    java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
    detail.put("success", broadcast.success());
    detail.put("txHash", broadcast.txHash());
    detail.put("failureReason", broadcast.failureReason());
    detail.put("internal", true);
    return detail;
  }

  private void publishTerminated(
      ExecutionIntent intent, ExecutionIntentStatus terminalStatus, String failureReason) {
    publishExecutionIntentTerminatedPort.publish(
        new ExecutionIntentTerminatedEvent(intent.getPublicId(), terminalStatus, failureReason));
  }

  private record InternalSigningPreparation(
      ExecutionIntent intent,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      SponsorNonceContext sponsorNonce,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      TreasurySigner signer,
      ExecuteInternalExecutionIntentResult result) {

    static InternalSigningPreparation ready(
        ExecutionIntent intent,
        ExecutionTransactionGatewayPort.TransactionRecord transaction,
        SponsorNonceContext sponsorNonce,
        ExecutionActionHandlerPort actionHandler,
        ExecutionActionPlan actionPlan,
        TreasurySigner signer) {
      return new InternalSigningPreparation(
          intent, transaction, sponsorNonce, actionHandler, actionPlan, signer, null);
    }

    static InternalSigningPreparation completed(ExecuteInternalExecutionIntentResult result) {
      return new InternalSigningPreparation(null, null, null, null, null, null, result);
    }
  }

  private record InternalSignedSubmission(ExecutionIntent intent, boolean signedNow) {}

  private record SponsorNonceContext(
      long chainId, String fromAddress, long nonce, Long attemptId, Long transactionId) {}
}
