package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.ExecutionIntentTerminalException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionFailureReason;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionAuditEventType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionType;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.util.KmsClientErrorClassifier;

/**
 * Transactional delegate for {@link ExecuteExecutionIntentService}.
 *
 * <p>Splits stateful DB phases from sponsor KMS signing. Sponsor-wallet preflight
 * (LoadSponsorTreasuryWalletPort + structural fail-fast + KMS DescribeKey verify) runs in the
 * orchestrator before this method is entered, nonce reservation runs in a short transaction, and
 * the KMS signing call runs outside the DB transaction so sponsor nonce locks are not held across
 * AWS latency.
 */
@Slf4j
@RequiredArgsConstructor
public class TransactionalExecuteExecutionIntentDelegate
    implements ExecuteTransactionalExecutionIntentDelegatePort {

  private static final String BROADCAST_FAILED = "BROADCAST_FAILED";
  private static final int SPONSOR_NONCE_OPEN_WINDOW_SIZE = 3;
  private static final String SPONSOR_KMS_SIGN_FAILED_TERMINAL =
      "sponsor kms sign failed (terminal)";
  private static final String SPONSOR_SIGNATURE_INVALID = "sponsor signature invalid";

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  private final ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  private final Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  private final LoadExecutionChainIdPort loadExecutionChainIdPort;
  private final LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;
  private final RunAfterCommitPort runAfterCommitPort;
  private final RunExecutionTransactionPort transactionPort;
  private final Clock appClock;

  /**
   * Executes the target intent.
   *
   * <p>{@code gate} is the pre-validated sponsor wallet handle — required for EIP-7702 intents and
   * {@code null} for EIP-1559 user-signed intents (which never use sponsor material). The EIP-7702
   * path enforces non-null on entry. Repeated calls for already-submitted intents return current
   * state without creating a new transaction.
   */
  @Override
  public ExecuteExecutionIntentResult execute(
      ExecuteExecutionIntentCommand command, SponsorWalletGate gate) {
    ExecutionIntent intent =
        executionIntentPersistencePort
            .findByPublicId(command.executionIntentId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "executionIntentId not found: " + command.executionIntentId()));

    if (!intent.getRequesterUserId().equals(command.requesterUserId())) {
      throw new Web3InvalidInputException("execution intent owner mismatch");
    }

    ExecutionTransactionGatewayPort.TransactionRecord submittedTransaction =
        intent.getSubmittedTxId() == null ? null : loadTransaction(intent.getSubmittedTxId());

    LocalDateTime now = LocalDateTime.now(appClock);
    if (!intent.getExpiresAt().isAfter(now)) {
      expireIntent(intent);
      throw new ExecutionIntentTerminalException(ErrorCode.EXECUTION_INTENT_EXPIRED, false);
    }

    if (submittedTransaction != null && !canResumeEip7702Signing(intent, submittedTransaction)) {
      return toResult(intent, submittedTransaction);
    }

    if (!intent.getStatus().isSignable()) {
      return toResult(intent, submittedTransaction);
    }

    ExecutionActionHandlerPort actionHandler = resolveActionHandler(intent);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);
    actionHandler.beforeExecute(intent, actionPlan);

    return switch (intent.getMode()) {
      case EIP7702 ->
          executeEip7702(command, intent, actionHandler, actionPlan, gate, submittedTransaction);
      case EIP1559 -> executeEip1559(command, intent, actionHandler, actionPlan);
    };
  }

  private ExecuteExecutionIntentResult executeEip7702(
      ExecuteExecutionIntentCommand command,
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      SponsorWalletGate gate,
      ExecutionTransactionGatewayPort.TransactionRecord submittedTransaction) {
    Objects.requireNonNull(gate, "EIP-7702 path requires a sponsor wallet gate");
    //  Validate command, client given signatures.
    if (command.authorizationSignature() == null || command.authorizationSignature().isBlank()) {
      throw new Web3InvalidInputException("authorizationSignature is required");
    }
    if (command.submitSignature() == null || command.submitSignature().isBlank()) {
      throw new Web3InvalidInputException("submitSignature is required");
    }

    // Sponsor wallet snapshot + signer were validated and KMS-verified outside the TX boundary.
    TreasurySigner sponsorSigner = gate.signer();
    String sponsorAddress = gate.walletInfo().walletAddress();

    // Build authorizationTuple using client given authorizationSignature, interacting with eip7702
    // module.
    ExecutionEip7702GatewayPort.AuthorizationTuple authTuple =
        executionEip7702GatewayPort.toAuthorizationTuple(
            loadExecutionChainIdPort.loadChainId(),
            intent.getDelegateTarget(),
            BigInteger.valueOf(intent.getAuthorityNonce()),
            command.authorizationSignature());

    List<ExecutionEip7702GatewayPort.BatchCall> calls =
        actionPlan.calls().stream()
            .map(
                call ->
                    new ExecutionEip7702GatewayPort.BatchCall(
                        call.toAddress(), call.valueWei(), call.data()))
            .toList();
    String callDataHash = executionEip7702GatewayPort.hashCalls(calls);

    // Verify authorizationSignature signer
    if (!executionEip7702GatewayPort.verifyAuthorizationSigner(
        loadExecutionChainIdPort.loadChainId(),
        intent.getDelegateTarget(),
        BigInteger.valueOf(intent.getAuthorityNonce()),
        command.authorizationSignature(),
        intent.getAuthorityAddress())) {
      throw new Web3InvalidInputException("authorizationSignature does not match authority");
    }

    // Resolve nonce
    BigInteger currentAuthorityNonce =
        executionEip7702GatewayPort.loadPendingAccountNonce(intent.getAuthorityAddress());
    if (currentAuthorityNonce.longValueExact() != intent.getAuthorityNonce()) {
      markNonceStale(intent, ErrorCode.AUTH_NONCE_MISMATCH);
      throw new ExecutionIntentTerminalException(ErrorCode.AUTH_NONCE_MISMATCH, false);
    }

    // Verify Execution(Submit) Signature
    BigInteger deadlineEpochSeconds =
        ExecutionDeadlineEpoch.toEpochSeconds(intent.getExpiresAt(), appClock);
    if (!executionEip7702GatewayPort.verifyExecutionSignature(
        intent.getAuthorityAddress(),
        intent.getPublicId(),
        callDataHash,
        deadlineEpochSeconds,
        command.submitSignature())) {
      throw new Web3InvalidInputException("submitSignature does not match authority");
    }

    // Encode Execution(Submit) Signature
    String executeCallData =
        executionEip7702GatewayPort.encodeExecute(
            calls, intent.getPublicId(), deadlineEpochSeconds, command.submitSignature());

    // Estimate gas, load fee plan via eip7702 module
    long chainId = loadExecutionChainIdPort.loadChainId();
    BigInteger estimatedGas =
        executionEip7702GatewayPort.estimateGasWithAuthorization(
            sponsorAddress,
            intent.getAuthorityAddress(),
            executeCallData,
            java.util.List.of(authTuple));
    ExecutionEip7702GatewayPort.FeePlan feePlan = executionEip7702GatewayPort.loadSponsorFeePlan();

    Eip7702Submission submission =
        submittedTransaction == null
            ? reserveEip7702Submission(intent, actionPlan, sponsorAddress, chainId)
            : resumeEip7702Submission(intent, submittedTransaction, sponsorAddress);
    if (submission.existingResult() != null) {
      return submission.existingResult();
    }
    ExecutionTransactionGatewayPort.TransactionRecord created = submission.transaction();
    SponsorNonceContext sponsorNonce = submission.sponsorNonce();

    // Make the signature. KMS sign errors are split into transient (rollback for user retry)
    // vs terminal (cancel + cascade event for QnA escrow refund), mirroring the EIP-1559
    // internal-issuer delegate. SignatureRecoveryException is treated as terminal — a recovered
    // address mismatch indicates corrupted DER / digest / key-pairing, all non-recoverable.
    ExecutionEip7702GatewayPort.SignedPayload signedPayload;
    try {
      signedPayload =
          executionEip7702GatewayPort.signAndEncode(
              new ExecutionEip7702GatewayPort.SignCommand(
                  chainId,
                  BigInteger.valueOf(sponsorNonce.nonce()),
                  feePlan.maxPriorityFeePerGas(),
                  feePlan.maxFeePerGas(),
                  estimatedGas,
                  intent.getAuthorityAddress(),
                  BigInteger.ZERO,
                  executeCallData,
                  java.util.List.of(authTuple),
                  sponsorSigner));
    } catch (KmsSignFailedException e) {
      log.warn(
          "eip7702 sponsor KMS sign failed for intent={}: {}",
          intent.getPublicId(),
          e.getMessage());
      if (KmsClientErrorClassifier.isTerminal(e)) {
        failEip7702SigningAndCancel(
            intent.getPublicId(),
            sponsorNonce,
            ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL.name(),
            ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL,
            ErrorCode.WEB3_KMS_SIGN_FAILED,
            SPONSOR_KMS_SIGN_FAILED_TERMINAL);
        // unreachable; cancelEip7702IntentAndCascade always throws
      }
      // Transient: rethrow original; @Transactional default rollback keeps the intent in
      // AWAITING_SIGNATURE for the next user retry. No cascade event published — QnA escrow
      // refund must not fire on a recoverable AWS hiccup.
      throw e;
    } catch (SignatureRecoveryException e) {
      log.warn(
          "eip7702 sponsor signature recovery failed for intent={}: {}",
          intent.getPublicId(),
          e.getMessage());
      failEip7702SigningAndCancel(
          intent.getPublicId(),
          sponsorNonce,
          ExecutionFailureReason.SIGNATURE_INVALID.name(),
          ExecutionFailureReason.SIGNATURE_INVALID,
          ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED,
          SPONSOR_SIGNATURE_INVALID);
      // unreachable; cancelEip7702IntentAndCascade always throws
      throw e;
    }

    Eip7702SignedSubmission signedSubmission =
        persistEip7702SignedIntent(intent.getPublicId(), sponsorNonce, signedPayload);
    ExecutionIntent signedIntent = signedSubmission.intent();
    if (!signedSubmission.signedNow()) {
      return toResult(signedIntent, loadTransaction(created.transactionId()));
    }
    scheduleBroadcastAfterCommit(
        signedIntent,
        created.transactionId(),
        signedPayload.rawTx(),
        signedPayload.txHash(),
        actionHandler,
        actionPlan,
        true,
        sponsorNonce);
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.transactionId(),
        ExecutionTransactionStatus.SIGNED,
        signedPayload.txHash());
  }

  private ExecuteExecutionIntentResult executeEip1559(
      ExecuteExecutionIntentCommand command,
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan) {
    if (command.signedRawTransaction() == null || command.signedRawTransaction().isBlank()) {
      throw new Web3InvalidInputException("signedRawTransaction is required");
    }

    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        eip1559TransactionCodecPort.decodeAndVerify(
            command.signedRawTransaction(),
            intent.getUnsignedTxSnapshot(),
            intent.getUnsignedTxFingerprint());

    BigInteger currentPendingNonce =
        executionEip7702GatewayPort.loadPendingAccountNonce(decoded.signerAddress());
    if (currentPendingNonce.longValueExact() != intent.getUnsignedTxSnapshot().expectedNonce()) {
      markNonceStale(intent, ErrorCode.NONCE_STALE_RECREATE_REQUIRED);
      throw new ExecutionIntentTerminalException(ErrorCode.NONCE_STALE_RECREATE_REQUIRED, false);
    }

    SignedSubmission signedSubmission =
        persistEip1559SignedIntent(intent.getPublicId(), actionPlan, decoded);
    ExecutionTransactionGatewayPort.TransactionRecord created = signedSubmission.transaction();
    ExecutionIntent signedIntent = signedSubmission.intent();
    if (created == null || signedIntent.getStatus() != ExecutionIntentStatus.SIGNED) {
      return toResult(signedIntent, created);
    }
    scheduleBroadcastAfterCommit(
        signedIntent,
        created.transactionId(),
        decoded.rawTransaction(),
        decoded.txHash(),
        actionHandler,
        actionPlan,
        false,
        null);
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.transactionId(),
        ExecutionTransactionStatus.SIGNED,
        decoded.txHash());
  }

  private void scheduleBroadcastAfterCommit(
      ExecutionIntent signedIntent,
      Long transactionId,
      String rawTx,
      String fallbackTxHash,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      boolean consumeSponsorExposureOnSuccess,
      SponsorNonceContext sponsorNonceContext) {
    runAfterCommitPort.runAfterCommitWithoutTransaction(
        () -> {
          if (!claimSignedForDirectBroadcast(transactionId, "execution-broadcast-")) {
            return;
          }
          if (sponsorNonceContext != null) {
            markSponsorSlotBroadcasting(sponsorNonceContext, fallbackTxHash);
          }
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
                      consumeSponsorExposureOnSuccess,
                      sponsorNonceContext));
        });
  }

  private boolean claimSignedForDirectBroadcast(Long transactionId, String workerPrefix) {
    String workerId = workerPrefix + transactionId;
    LocalDateTime processingUntil =
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds());
    boolean claimed =
        executionTransactionGatewayPort.claimSignedForBroadcast(
            transactionId, workerId, processingUntil);
    if (!claimed) {
      log.info(
          "Skipping direct execution broadcast because signed tx is already claimed or moved: txId={}",
          transactionId);
    }
    return claimed;
  }

  private void persistBroadcastOutcome(
      String executionIntentId,
      Long transactionId,
      String fallbackTxHash,
      ExecutionTransactionGatewayPort.BroadcastResult broadcast,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      boolean consumeSponsorExposureOnSuccess,
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
                consumeSponsorExposureOnSuccess,
                sponsorNonceContext));
  }

  private void persistBroadcastOutcomeInTransaction(
      String executionIntentId,
      Long transactionId,
      String fallbackTxHash,
      ExecutionTransactionGatewayPort.BroadcastResult broadcast,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      boolean consumeSponsorExposureOnSuccess,
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
          "Skipping broadcast outcome for stale execution intent: intentId={}, txId={}",
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
      if (sponsorNonceContext != null) {
        markSponsorSlotBroadcasted(sponsorNonceContext);
      }
      ExecutionIntent pendingIntent =
          executionIntentPersistencePort.update(
              current.markPendingOnchain(transactionId, LocalDateTime.now(appClock)));
      if (consumeSponsorExposureOnSuccess) {
        moveReservedSponsorExposureToConsumed(
            current.getRequesterUserId(),
            current.resolveSponsorUsageDateKst(),
            current.getReservedSponsorCostWei());
      }
      ExecutionActionHookRunner.afterTransactionSubmitted(
          runAfterCommitPort,
          actionHandler,
          pendingIntent,
          actionPlan,
          ExecutionTransactionStatus.PENDING);
      return;
    }

    String reason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? BROADCAST_FAILED
            : broadcast.failureReason();
    executionTransactionGatewayPort.scheduleRetry(
        transactionId,
        reason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    ExecutionActionHookRunner.afterTransactionSubmitted(
        runAfterCommitPort, actionHandler, current, actionPlan, ExecutionTransactionStatus.SIGNED);
  }

  private Eip7702Submission reserveEip7702Submission(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, String sponsorAddress, long chainId) {
    return transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(intent.getPublicId())
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + intent.getPublicId()));
          if (current.getSubmittedTxId() != null) {
            ExecutionTransactionGatewayPort.TransactionRecord transaction =
                loadTransaction(current.getSubmittedTxId());
            if (canResumeEip7702Signing(current, transaction)) {
              return resumeEip7702SubmissionInCurrentTransaction(
                  current, transaction, sponsorAddress);
            }
            return Eip7702Submission.existing(toResult(current, transaction));
          }
          if (!current.getStatus().isSignable()) {
            return Eip7702Submission.existing(toResult(current, null));
          }

          ExecutionTransactionGatewayPort.TransactionRecord created =
              executionTransactionGatewayPort.createAndFlush(
                  new ExecutionTransactionGatewayPort.CreateTransactionCommand(
                      current.getRootIdempotencyKey() + ":" + current.getAttemptNo(),
                      actionPlan.referenceType(),
                      current.getResourceId(),
                      current.getRequesterUserId(),
                      current.getCounterpartyUserId(),
                      sponsorAddress,
                      current.getAuthorityAddress(),
                      actionPlan.amountWei(),
                      chainId,
                      null,
                      ExecutionTransactionStatus.CREATED,
                      ExecutionTransactionType.EIP7702,
                      current.getAuthorityAddress(),
                      current.getAuthorityNonce(),
                      current.getDelegateTarget(),
                      current.getExpiresAt()));
          SponsorNonceContext sponsorNonce =
              reserveSponsorNonce(created.transactionId(), sponsorAddress, chainId);
          executionIntentPersistencePort.update(
              current.bindSubmittedTransaction(
                  created.transactionId(), LocalDateTime.now(appClock)));
          return Eip7702Submission.reserved(created, sponsorNonce);
        });
  }

  private Eip7702Submission resumeEip7702Submission(
      ExecutionIntent intent,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      String sponsorAddress) {
    Optional<SponsorNonceContext> sponsorNonce =
        resolveReusableSponsorNonce(transaction, sponsorAddress);
    if (sponsorNonce.isEmpty()) {
      return Eip7702Submission.existing(
          markSubmittedSponsorNonceStale(
              intent, transaction, ErrorCode.NONCE_STALE_RECREATE_REQUIRED));
    }
    return Eip7702Submission.reserved(transaction, sponsorNonce.get());
  }

  private Eip7702Submission resumeEip7702SubmissionInCurrentTransaction(
      ExecutionIntent current,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      String sponsorAddress) {
    Optional<SponsorNonceContext> sponsorNonce =
        resolveReusableSponsorNonce(transaction, sponsorAddress);
    if (sponsorNonce.isEmpty()) {
      ExecutionIntent staleIntent =
          markSubmittedSponsorNonceStaleInCurrentTransaction(
              current, transaction, ErrorCode.NONCE_STALE_RECREATE_REQUIRED);
      return Eip7702Submission.existing(toResult(staleIntent, transaction));
    }
    return Eip7702Submission.reserved(transaction, sponsorNonce.get());
  }

  private Optional<SponsorNonceContext> resolveReusableSponsorNonce(
      ExecutionTransactionGatewayPort.TransactionRecord transaction, String sponsorAddress) {
    if (transaction.chainId() == null || transaction.nonce() == null) {
      throw new Web3InvalidInputException("submitted sponsor transaction is not nonce-bound");
    }
    if (transaction.fromAddress() != null
        && !transaction.fromAddress().equalsIgnoreCase(sponsorAddress)) {
      throw new Web3InvalidInputException("submitted sponsor transaction sender mismatch");
    }

    ExecutionTransactionGatewayPort.SponsorNonceSnapshot snapshot =
        executionTransactionGatewayPort.loadSponsorNonceSnapshot(
            transaction.chainId(), sponsorAddress);
    ExecutionTransactionGatewayPort.SponsorNonceCoordinationRecord reconciliation =
        executionTransactionGatewayPort.coordinateSponsorNonce(
            new ExecutionTransactionGatewayPort.CoordinateSponsorNonceCommand(
                transaction.chainId(),
                sponsorAddress,
                snapshot.chainPendingNonce(),
                snapshot.chainLatestNonce(),
                snapshot.mainPendingNonce(),
                snapshot.subPendingNonce(),
                snapshot.mainLatestNonce(),
                snapshot.subLatestNonce(),
                SPONSOR_NONCE_OPEN_WINDOW_SIZE,
                null,
                null,
                LocalDateTime.now(appClock)));
    if (isRpcDisagreement(reconciliation)) {
      throwSponsorNonceUnavailable(reconciliation);
    }

    Optional<ExecutionTransactionGatewayPort.SponsorNonceSlotRecord> slot =
        executionTransactionGatewayPort.findSponsorNonceSlot(
            transaction.chainId(), sponsorAddress, transaction.nonce());
    if (snapshot.chainLatestNonce() > transaction.nonce()) {
      log.warn(
          "submitted sponsor nonce was consumed before EIP-7702 resume: txId={}, nonce={}, "
              + "chainLatest={}",
          transaction.transactionId(),
          transaction.nonce(),
          snapshot.chainLatestNonce());
      return Optional.empty();
    }
    if (slot.isPresent() && isReservedSlotOwnedByTransaction(slot.get(), transaction)) {
      return Optional.of(
          new SponsorNonceContext(
              transaction.chainId(),
              sponsorAddress,
              transaction.nonce(),
              slot.get().activeAttemptId(),
              transaction.transactionId()));
    }

    log.warn(
        "submitted sponsor nonce slot is no longer reusable: txId={}, nonce={}, slotStatus={}, "
            + "slotActiveTxId={}",
        transaction.transactionId(),
        transaction.nonce(),
        slot.map(ExecutionTransactionGatewayPort.SponsorNonceSlotRecord::status).orElse(null),
        slot.map(ExecutionTransactionGatewayPort.SponsorNonceSlotRecord::activeTxId).orElse(null));
    return Optional.empty();
  }

  private Eip7702SignedSubmission persistEip7702SignedIntent(
      String executionIntentId,
      SponsorNonceContext sponsorNonce,
      ExecutionEip7702GatewayPort.SignedPayload signedPayload) {
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
            return new Eip7702SignedSubmission(current, false);
          }
          if (current.getStatus() != ExecutionIntentStatus.AWAITING_SIGNATURE
              || !sponsorNonce.transactionId().equals(current.getSubmittedTxId())) {
            throw new Web3InvalidInputException("execution intent signing state changed");
          }
          executionTransactionGatewayPort.markSigned(
              sponsorNonce.transactionId(),
              sponsorNonce.nonce(),
              signedPayload.rawTx(),
              signedPayload.txHash());
          markSponsorSlotSigned(sponsorNonce, signedPayload.txHash());
          audit(
              sponsorNonce.transactionId(),
              ExecutionAuditEventType.AUTHORIZATION,
              null,
              java.util.Map.of("mode", current.getMode().name()));
          ExecutionIntent signedIntent =
              executionIntentPersistencePort.update(
                  current.markSigned(sponsorNonce.transactionId(), LocalDateTime.now(appClock)));
          return new Eip7702SignedSubmission(signedIntent, true);
        });
  }

  private SignedSubmission persistEip1559SignedIntent(
      String executionIntentId,
      ExecutionActionPlan actionPlan,
      Eip1559TransactionCodecPort.DecodedSignedTransaction decoded) {
    return transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(executionIntentId)
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + executionIntentId));
          if (current.getSubmittedTxId() != null) {
            return new SignedSubmission(current, loadTransaction(current.getSubmittedTxId()));
          }
          if (!current.getStatus().isSignable()) {
            return new SignedSubmission(current, null);
          }
          ExecutionTransactionGatewayPort.TransactionRecord created =
              executionTransactionGatewayPort.createAndFlush(
                  new ExecutionTransactionGatewayPort.CreateTransactionCommand(
                      current.getRootIdempotencyKey() + ":" + current.getAttemptNo(),
                      actionPlan.referenceType(),
                      current.getResourceId(),
                      current.getRequesterUserId(),
                      current.getCounterpartyUserId(),
                      decoded.signerAddress(),
                      current.getUnsignedTxSnapshot().toAddress(),
                      actionPlan.amountWei(),
                      current.getUnsignedTxSnapshot().chainId(),
                      current.getUnsignedTxSnapshot().expectedNonce(),
                      ExecutionTransactionStatus.CREATED,
                      ExecutionTransactionType.EIP1559,
                      null,
                      null,
                      null,
                      null));
          executionTransactionGatewayPort.markSigned(
              created.transactionId(),
              current.getUnsignedTxSnapshot().expectedNonce(),
              decoded.rawTransaction(),
              decoded.txHash());
          audit(
              created.transactionId(),
              ExecutionAuditEventType.SIGN,
              null,
              java.util.Map.of("mode", current.getMode().name()));
          ExecutionIntent signedIntent =
              executionIntentPersistencePort.update(
                  current.markSigned(created.transactionId(), LocalDateTime.now(appClock)));
          return new SignedSubmission(signedIntent, created);
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
        "sponsor nonce unavailable: decision="
            + coordination.decisionType()
            + ", reason="
            + coordination.reason(),
        retryable);
  }

  private boolean isRpcDisagreement(
      ExecutionTransactionGatewayPort.SponsorNonceCoordinationRecord coordination) {
    return coordination != null && "RPC_DISAGREEMENT".equals(coordination.decisionType());
  }

  private boolean isReservedSlotOwnedByTransaction(
      ExecutionTransactionGatewayPort.SponsorNonceSlotRecord slot,
      ExecutionTransactionGatewayPort.TransactionRecord transaction) {
    return "RESERVED".equals(slot.status())
        && slot.activeAttemptId() != null
        && transaction.transactionId() != null
        && transaction.transactionId().equals(slot.activeTxId());
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
        "execution-broadcast-" + context.transactionId(),
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

  private void releaseSponsorExposure(
      Long userId, java.time.LocalDate usageDateKst, BigInteger amountWei) {
    if (amountWei == null || amountWei.signum() <= 0) {
      return;
    }
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort.getOrCreateForUpdate(userId, usageDateKst);
    sponsorDailyUsagePersistencePort.update(usage.release(amountWei));
  }

  private void moveReservedSponsorExposureToConsumed(
      Long userId, java.time.LocalDate usageDateKst, BigInteger amountWei) {
    if (amountWei == null || amountWei.signum() <= 0) {
      return;
    }
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort.getOrCreateForUpdate(userId, usageDateKst);
    sponsorDailyUsagePersistencePort.update(usage.release(amountWei).consume(amountWei));
  }

  private ExecutionTransactionGatewayPort.TransactionRecord loadTransaction(Long transactionId) {
    return executionTransactionGatewayPort
        .findById(transactionId)
        .orElseThrow(
            () -> new Web3InvalidInputException("transaction not found: " + transactionId));
  }

  private ExecuteExecutionIntentResult toResult(
      ExecutionIntent intent, ExecutionTransactionGatewayPort.TransactionRecord transaction) {
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        intent.getStatus(),
        transaction == null ? intent.getSubmittedTxId() : transaction.transactionId(),
        transaction == null ? null : transaction.status(),
        transaction == null ? null : transaction.txHash());
  }

  private boolean canResumeEip7702Signing(
      ExecutionIntent intent, ExecutionTransactionGatewayPort.TransactionRecord transaction) {
    return intent.getMode() == ExecutionMode.EIP7702
        && intent.getStatus() == ExecutionIntentStatus.AWAITING_SIGNATURE
        && transaction.status() == ExecutionTransactionStatus.CREATED;
  }

  private void expireIntent(ExecutionIntent intent) {
    transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(intent.getPublicId())
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + intent.getPublicId()));
          if (current.getStatus() != ExecutionIntentStatus.AWAITING_SIGNATURE) {
            return;
          }
          ExecutionReservedTransactionCleanupSupport.cleanupCreatedSubmittedTransaction(
              executionTransactionGatewayPort,
              current.getSubmittedTxId(),
              ErrorCode.EXECUTION_INTENT_EXPIRED.name(),
              appClock);
          ExecutionIntent expired =
              executionIntentPersistencePort.update(
                  current.expire(
                      ErrorCode.EXECUTION_INTENT_EXPIRED.name(),
                      ErrorCode.EXECUTION_INTENT_EXPIRED.getMessage(),
                      LocalDateTime.now(appClock)));
          if (expired.getMode() == ExecutionMode.EIP7702
              && expired.getReservedSponsorCostWei().signum() > 0) {
            releaseSponsorExposure(
                expired.getRequesterUserId(),
                expired.resolveSponsorUsageDateKst(),
                expired.getReservedSponsorCostWei());
          }
          publishTerminated(
              expired, ExecutionIntentStatus.EXPIRED, ErrorCode.EXECUTION_INTENT_EXPIRED.name());
        });
  }

  private void markNonceStale(ExecutionIntent intent, ErrorCode errorCode) {
    transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(intent.getPublicId())
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + intent.getPublicId()));
          if (current.getStatus() != ExecutionIntentStatus.AWAITING_SIGNATURE) {
            return;
          }
          ExecutionReservedTransactionCleanupSupport.cleanupCreatedSubmittedTransaction(
              executionTransactionGatewayPort,
              current.getSubmittedTxId(),
              errorCode.name(),
              appClock);
          ExecutionIntent staleIntent =
              executionIntentPersistencePort.update(
                  current.markNonceStale(
                      errorCode.name(), errorCode.getMessage(), LocalDateTime.now(appClock)));
          if (staleIntent.getMode() == ExecutionMode.EIP7702
              && staleIntent.getReservedSponsorCostWei().signum() > 0) {
            releaseSponsorExposure(
                staleIntent.getRequesterUserId(),
                staleIntent.resolveSponsorUsageDateKst(),
                staleIntent.getReservedSponsorCostWei());
          }
          publishTerminated(staleIntent, ExecutionIntentStatus.NONCE_STALE, errorCode.name());
        });
  }

  private ExecuteExecutionIntentResult markSubmittedSponsorNonceStale(
      ExecutionIntent intent,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      ErrorCode errorCode) {
    return transactionPort.requiresNew(
        () -> {
          ExecutionIntent current =
              executionIntentPersistencePort
                  .findByPublicIdForUpdate(intent.getPublicId())
                  .orElseThrow(
                      () ->
                          new Web3InvalidInputException(
                              "executionIntentId not found: " + intent.getPublicId()));
          if (current.getStatus() != ExecutionIntentStatus.AWAITING_SIGNATURE) {
            return toResult(current, transaction);
          }
          ExecutionIntent staleIntent =
              markSubmittedSponsorNonceStaleInCurrentTransaction(current, transaction, errorCode);
          return toResult(staleIntent, transaction);
        });
  }

  private ExecutionIntent markSubmittedSponsorNonceStaleInCurrentTransaction(
      ExecutionIntent current,
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      ErrorCode errorCode) {
    LocalDateTime now = LocalDateTime.now(appClock);
    ExecutionReservedTransactionCleanupSupport.cleanupCreatedSubmittedTransaction(
        executionTransactionGatewayPort, current.getSubmittedTxId(), errorCode.name(), now);
    ExecutionIntent staleIntent =
        executionIntentPersistencePort.update(
            current.markNonceStale(errorCode.name(), errorCode.getMessage(), now));
    if (staleIntent.getMode() == ExecutionMode.EIP7702
        && staleIntent.getReservedSponsorCostWei().signum() > 0) {
      releaseSponsorExposure(
          staleIntent.getRequesterUserId(),
          staleIntent.resolveSponsorUsageDateKst(),
          staleIntent.getReservedSponsorCostWei());
    }
    publishTerminated(staleIntent, ExecutionIntentStatus.NONCE_STALE, errorCode.name());
    return staleIntent;
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
          "failed to record transaction audit: txId={}, eventType={}", transactionId, eventType, e);
    }
  }

  private java.util.Map<String, Object> broadcastDetail(
      ExecutionTransactionGatewayPort.BroadcastResult broadcast) {
    java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
    detail.put("success", broadcast.success());
    detail.put("txHash", broadcast.txHash());
    detail.put("failureReason", broadcast.failureReason());
    return detail;
  }

  private ExecutionActionHandlerPort resolveActionHandler(ExecutionIntent intent) {
    return ExecutionActionHandlerPort.findMatching(executionActionHandlerPorts, intent)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no execution action handler for actionType=" + intent.getActionType()));
  }

  private void publishTerminated(
      ExecutionIntent intent, ExecutionIntentStatus terminalStatus, String failureReason) {
    publishExecutionIntentTerminatedPort.publish(
        new ExecutionIntentTerminatedEvent(intent.getPublicId(), terminalStatus, failureReason));
  }

  private void failEip7702SigningAndCancel(
      String executionIntentId,
      SponsorNonceContext sponsorNonce,
      String transactionFailureReason,
      ExecutionFailureReason eventReason,
      ErrorCode errorCode,
      String failureReason) {
    transactionPort.requiresNew(
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
            return;
          }
          markCreatedTransactionTerminal(sponsorNonce.transactionId(), transactionFailureReason);
          dropSponsorReservedSlot(sponsorNonce, transactionFailureReason);
          ExecutionIntent canceled =
              executionIntentPersistencePort.update(
                  current.cancel(errorCode.name(), failureReason, LocalDateTime.now(appClock)));
          if (canceled.getReservedSponsorCostWei().signum() > 0) {
            releaseSponsorExposure(
                canceled.getRequesterUserId(),
                canceled.resolveSponsorUsageDateKst(),
                canceled.getReservedSponsorCostWei());
          }
          publishTerminated(canceled, ExecutionIntentStatus.CANCELED, eventReason.name());
        });
    throw new ExecutionIntentTerminalException(errorCode, false);
  }

  private record Eip7702Submission(
      ExecutionTransactionGatewayPort.TransactionRecord transaction,
      SponsorNonceContext sponsorNonce,
      ExecuteExecutionIntentResult existingResult) {

    static Eip7702Submission reserved(
        ExecutionTransactionGatewayPort.TransactionRecord transaction,
        SponsorNonceContext sponsorNonce) {
      return new Eip7702Submission(transaction, sponsorNonce, null);
    }

    static Eip7702Submission existing(ExecuteExecutionIntentResult result) {
      return new Eip7702Submission(null, null, result);
    }
  }

  private record SignedSubmission(
      ExecutionIntent intent, ExecutionTransactionGatewayPort.TransactionRecord transaction) {}

  private record Eip7702SignedSubmission(ExecutionIntent intent, boolean signedNow) {}

  private record SponsorNonceContext(
      long chainId, String fromAddress, long nonce, Long attemptId, Long transactionId) {}
}
