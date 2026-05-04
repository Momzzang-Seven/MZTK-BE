package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.ExecutionIntentTerminalException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
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
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionAuditEventType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionType;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional delegate for {@link ExecuteExecutionIntentService}.
 *
 * <p>Holds the FOR UPDATE intent lock + atomic write semantics. Sponsor-wallet preflight
 * (LoadSponsorTreasuryWalletPort + structural fail-fast + KMS DescribeKey verify) is performed by
 * the orchestrator before this method is entered, so external KMS latency never pins a JDBC
 * connection. The pre-validated sponsor signer enters via {@link SponsorWalletGate}.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(noRollbackFor = ExecutionIntentTerminalException.class)
public class TransactionalExecuteExecutionIntentDelegate
    implements ExecuteTransactionalExecutionIntentDelegatePort {

  private static final String BROADCAST_FAILED = "BROADCAST_FAILED";

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  private final ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  private final Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  private final LoadExecutionChainIdPort loadExecutionChainIdPort;
  private final LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;
  private final Clock appClock;

  /**
   * Executes the target intent within a single transaction.
   *
   * <p>Caller must supply a pre-validated {@link SponsorWalletGate}; the gate is consumed only
   * inside the EIP-7702 path. Repeated calls for already-submitted intents return current state
   * without creating a new transaction.
   */
  @Override
  public ExecuteExecutionIntentResult execute(
      ExecuteExecutionIntentCommand command, SponsorWalletGate gate) {
    ExecutionIntent intent =
        executionIntentPersistencePort
            .findByPublicIdForUpdate(command.executionIntentId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "executionIntentId not found: " + command.executionIntentId()));

    if (!intent.getRequesterUserId().equals(command.requesterUserId())) {
      throw new Web3InvalidInputException("execution intent owner mismatch");
    }

    if (intent.getSubmittedTxId() != null) {
      return toResult(intent, loadTransaction(intent.getSubmittedTxId()));
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    if (intent.getExpiresAt().isBefore(now)) {
      ExecutionIntent expired =
          executionIntentPersistencePort.update(
              intent.expire(
                  ErrorCode.EXECUTION_INTENT_EXPIRED.name(),
                  ErrorCode.EXECUTION_INTENT_EXPIRED.getMessage(),
                  now));
      if (expired.getMode() == ExecutionMode.EIP7702
          && expired.getReservedSponsorCostWei().signum() > 0) {
        releaseSponsorExposure(
            expired.getRequesterUserId(),
            expired.resolveSponsorUsageDateKst(),
            expired.getReservedSponsorCostWei());
      }
      publishTerminated(
          expired, ExecutionIntentStatus.EXPIRED, ErrorCode.EXECUTION_INTENT_EXPIRED.name());
      throw new ExecutionIntentTerminalException(ErrorCode.EXECUTION_INTENT_EXPIRED, false);
    }

    if (!intent.getStatus().isSignable()) {
      return toResult(intent, null);
    }

    ExecutionActionHandlerPort actionHandler = resolveActionHandler(intent);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);
    actionHandler.beforeExecute(intent, actionPlan);

    return switch (intent.getMode()) {
      case EIP7702 -> executeEip7702(command, intent, actionHandler, actionPlan, gate);
      case EIP1559 -> executeEip1559(command, intent, actionHandler, actionPlan);
    };
  }

  private ExecuteExecutionIntentResult executeEip7702(
      ExecuteExecutionIntentCommand command,
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      SponsorWalletGate gate) {
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
      ExecutionIntent staleIntent =
          executionIntentPersistencePort.update(
              intent.markNonceStale(
                  ErrorCode.AUTH_NONCE_MISMATCH.name(),
                  ErrorCode.AUTH_NONCE_MISMATCH.getMessage(),
                  LocalDateTime.now(appClock)));
      if (staleIntent.getReservedSponsorCostWei().signum() > 0) {
        releaseSponsorExposure(
            staleIntent.getRequesterUserId(),
            staleIntent.resolveSponsorUsageDateKst(),
            staleIntent.getReservedSponsorCostWei());
      }
      publishTerminated(
          staleIntent, ExecutionIntentStatus.NONCE_STALE, ErrorCode.AUTH_NONCE_MISMATCH.name());
      throw new ExecutionIntentTerminalException(ErrorCode.AUTH_NONCE_MISMATCH, false);
    }

    // Verify Execution(Submit) Signature
    BigInteger deadlineEpochSeconds =
        BigInteger.valueOf(intent.getExpiresAt().toEpochSecond(ZoneOffset.UTC));
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
        executionEip7702GatewayPort.encodeExecute(calls, command.submitSignature());

    // Estimate gas, load fee plan via eip7702 module
    BigInteger estimatedGas =
        executionEip7702GatewayPort.estimateGasWithAuthorization(
            sponsorAddress,
            intent.getAuthorityAddress(),
            executeCallData,
            java.util.List.of(authTuple));
    ExecutionEip7702GatewayPort.FeePlan feePlan = executionEip7702GatewayPort.loadSponsorFeePlan();

    // Reserve next nonce(persistence + JSON-RPC)
    long sponsorNonce = executionTransactionGatewayPort.reserveNextNonce(sponsorAddress);

    // Make the signature
    ExecutionEip7702GatewayPort.SignedPayload signedPayload =
        executionEip7702GatewayPort.signAndEncode(
            new ExecutionEip7702GatewayPort.SignCommand(
                loadExecutionChainIdPort.loadChainId(),
                BigInteger.valueOf(sponsorNonce),
                feePlan.maxPriorityFeePerGas(),
                feePlan.maxFeePerGas(),
                estimatedGas,
                intent.getAuthorityAddress(),
                BigInteger.ZERO,
                executeCallData,
                java.util.List.of(authTuple),
                sponsorSigner));

    ExecutionTransactionGatewayPort.TransactionRecord created =
        executionTransactionGatewayPort.createAndFlush(
            new ExecutionTransactionGatewayPort.CreateTransactionCommand(
                intent.getRootIdempotencyKey() + ":" + intent.getAttemptNo(),
                actionPlan.referenceType(),
                intent.getResourceId(),
                intent.getRequesterUserId(),
                intent.getCounterpartyUserId(),
                sponsorAddress,
                intent.getAuthorityAddress(),
                actionPlan.amountWei(),
                null,
                ExecutionTransactionStatus.CREATED,
                ExecutionTransactionType.EIP7702,
                intent.getAuthorityAddress(),
                intent.getAuthorityNonce(),
                intent.getDelegateTarget(),
                intent.getExpiresAt()));

    executionTransactionGatewayPort.markSigned(
        created.transactionId(), sponsorNonce, signedPayload.rawTx(), signedPayload.txHash());
    executionIntentPersistencePort.update(
        intent.markSigned(created.transactionId(), LocalDateTime.now(appClock)));
    audit(
        created.transactionId(),
        ExecutionAuditEventType.AUTHORIZATION,
        null,
        java.util.Map.of("mode", intent.getMode().name()));

    ExecutionTransactionGatewayPort.BroadcastResult broadcast =
        executionTransactionGatewayPort.broadcast(signedPayload.rawTx());
    audit(
        created.transactionId(),
        ExecutionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail(broadcast));

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? signedPayload.txHash()
              : broadcast.txHash();
      executionTransactionGatewayPort.markPending(created.transactionId(), txHash);
      executionIntentPersistencePort.update(
          intent.markPendingOnchain(created.transactionId(), LocalDateTime.now(appClock)));
      moveReservedSponsorExposureToConsumed(
          intent.getRequesterUserId(),
          intent.resolveSponsorUsageDateKst(),
          intent.getReservedSponsorCostWei());
      actionHandler.afterTransactionSubmitted(
          intent, actionPlan, ExecutionTransactionStatus.PENDING);
      return new ExecuteExecutionIntentResult(
          intent.getPublicId(),
          ExecutionIntentStatus.PENDING_ONCHAIN,
          created.transactionId(),
          ExecutionTransactionStatus.PENDING,
          txHash);
    }

    String reason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? BROADCAST_FAILED
            : broadcast.failureReason();
    executionTransactionGatewayPort.scheduleRetry(
        created.transactionId(),
        reason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    executionIntentPersistencePort.update(
        intent.markSigned(created.transactionId(), LocalDateTime.now(appClock)));
    actionHandler.afterTransactionSubmitted(intent, actionPlan, ExecutionTransactionStatus.SIGNED);
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
      ExecutionIntent staleIntent =
          executionIntentPersistencePort.update(
              intent.markNonceStale(
                  ErrorCode.NONCE_STALE_RECREATE_REQUIRED.name(),
                  ErrorCode.NONCE_STALE_RECREATE_REQUIRED.getMessage(),
                  LocalDateTime.now(appClock)));
      publishTerminated(
          staleIntent,
          ExecutionIntentStatus.NONCE_STALE,
          ErrorCode.NONCE_STALE_RECREATE_REQUIRED.name());
      throw new ExecutionIntentTerminalException(ErrorCode.NONCE_STALE_RECREATE_REQUIRED, false);
    }

    ExecutionTransactionGatewayPort.TransactionRecord created =
        executionTransactionGatewayPort.createAndFlush(
            new ExecutionTransactionGatewayPort.CreateTransactionCommand(
                intent.getRootIdempotencyKey() + ":" + intent.getAttemptNo(),
                actionPlan.referenceType(),
                intent.getResourceId(),
                intent.getRequesterUserId(),
                intent.getCounterpartyUserId(),
                decoded.signerAddress(),
                intent.getUnsignedTxSnapshot().toAddress(),
                actionPlan.amountWei(),
                intent.getUnsignedTxSnapshot().expectedNonce(),
                ExecutionTransactionStatus.CREATED,
                ExecutionTransactionType.EIP1559,
                null,
                null,
                null,
                null));

    executionTransactionGatewayPort.markSigned(
        created.transactionId(),
        intent.getUnsignedTxSnapshot().expectedNonce(),
        decoded.rawTransaction(),
        decoded.txHash());
    executionIntentPersistencePort.update(
        intent.markSigned(created.transactionId(), LocalDateTime.now(appClock)));
    audit(
        created.transactionId(),
        ExecutionAuditEventType.SIGN,
        null,
        java.util.Map.of("mode", intent.getMode().name()));

    ExecutionTransactionGatewayPort.BroadcastResult broadcast =
        executionTransactionGatewayPort.broadcast(decoded.rawTransaction());
    audit(
        created.transactionId(),
        ExecutionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail(broadcast));

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? decoded.txHash()
              : broadcast.txHash();
      executionTransactionGatewayPort.markPending(created.transactionId(), txHash);
      executionIntentPersistencePort.update(
          intent.markPendingOnchain(created.transactionId(), LocalDateTime.now(appClock)));
      actionHandler.afterTransactionSubmitted(
          intent, actionPlan, ExecutionTransactionStatus.PENDING);
      return new ExecuteExecutionIntentResult(
          intent.getPublicId(),
          ExecutionIntentStatus.PENDING_ONCHAIN,
          created.transactionId(),
          ExecutionTransactionStatus.PENDING,
          txHash);
    }

    String reason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? BROADCAST_FAILED
            : broadcast.failureReason();
    executionTransactionGatewayPort.scheduleRetry(
        created.transactionId(),
        reason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    executionIntentPersistencePort.update(
        intent.markSigned(created.transactionId(), LocalDateTime.now(appClock)));
    actionHandler.afterTransactionSubmitted(intent, actionPlan, ExecutionTransactionStatus.SIGNED);
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.transactionId(),
        ExecutionTransactionStatus.SIGNED,
        decoded.txHash());
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
    return executionActionHandlerPorts.stream()
        .filter(handler -> handler.supports(intent.getActionType()))
        .findFirst()
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
}
