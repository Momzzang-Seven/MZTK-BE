package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteInternalExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionAuditEventType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionType;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

@Slf4j
@RequiredArgsConstructor
public class ExecuteInternalExecutionIntentService
    implements ExecuteInternalExecutionIntentUseCase {

  private static final String BROADCAST_FAILED = "BROADCAST_FAILED";
  private static final String INTERNAL_ISSUER_INVALID_INTENT = "INTERNAL_ISSUER_INVALID_INTENT";

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  private final LoadInternalExecutionSignerConfigPort loadInternalExecutionSignerConfigPort;
  private final LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;
  private final ExecutionEip1559SigningPort executionEip1559SigningPort;
  private final Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  private final LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final Clock appClock;

  @Override
  public ExecuteInternalExecutionIntentResult execute(
      ExecuteInternalExecutionIntentCommand command) {
    ExecutionIntent intent =
        executionIntentPersistencePort
            .claimNextInternalExecutableForUpdate(command.actionTypes())
            .orElse(null);
    if (intent == null) {
      return ExecuteInternalExecutionIntentResult.notFound();
    }

    ExecutionActionHandlerPort actionHandler = findActionHandler(intent).orElse(null);
    if (actionHandler == null) {
      return quarantineInvalidIntent(
          intent,
          null,
          null,
          "no execution action handler for actionType=" + intent.getActionType());
    }

    ExecutionActionPlan actionPlan;
    try {
      actionPlan = actionHandler.buildActionPlan(intent);
      actionHandler.beforeExecute(intent, actionPlan);
    } catch (IllegalStateException | Web3InvalidInputException e) {
      return quarantineInvalidIntent(intent, actionHandler, null, e.getMessage());
    }

    LocalDateTime now = LocalDateTime.now(appClock);
    if (intent.getExpiresAt().isBefore(now)) {
      ExecutionIntent expired =
          executionIntentPersistencePort.update(
              intent.expire(
                  ErrorCode.EXECUTION_INTENT_EXPIRED.name(),
                  ErrorCode.EXECUTION_INTENT_EXPIRED.getMessage(),
                  now));
      safeAfterExecutionTerminated(
          actionHandler,
          expired,
          actionPlan,
          ExecutionIntentStatus.EXPIRED,
          ErrorCode.EXECUTION_INTENT_EXPIRED.name());
      return new ExecuteInternalExecutionIntentResult(
          true, false, expired.getPublicId(), expired.getStatus(), null, null, null);
    }

    if (intent.getMode() != ExecutionMode.EIP1559) {
      return quarantineInvalidIntent(
          intent, actionHandler, actionPlan, "internal issuer supports only EIP1559");
    }
    if (!intent.getStatus().isSignable()) {
      return new ExecuteInternalExecutionIntentResult(
          true,
          false,
          intent.getPublicId(),
          intent.getStatus(),
          intent.getSubmittedTxId(),
          null,
          null);
    }
    if (intent.getUnsignedTxSnapshot() == null || intent.getUnsignedTxFingerprint() == null) {
      return quarantineInvalidIntent(
          intent,
          actionHandler,
          actionPlan,
          "internal executable intent requires unsigned tx snapshot");
    }

    ExecutionSponsorWalletConfig sponsorWalletConfig =
        loadInternalExecutionSignerConfigPort.loadSignerConfig();
    LoadExecutionSponsorKeyPort.ExecutionSponsorKey sponsorKey =
        loadExecutionSponsorKeyPort
            .loadByAlias(
                sponsorWalletConfig.walletAlias(), sponsorWalletConfig.keyEncryptionKeyB64())
            .orElse(null);
    if (sponsorKey == null) {
      return quarantineInvalidIntent(
          intent, actionHandler, actionPlan, "sponsor signer key is missing");
    }

    String expectedSigner;
    try {
      expectedSigner = EvmAddress.of(sponsorKey.address()).value();
    } catch (Web3InvalidInputException e) {
      return quarantineInvalidIntent(intent, actionHandler, actionPlan, e.getMessage());
    }
    if (!expectedSigner.equalsIgnoreCase(intent.getUnsignedTxSnapshot().fromAddress())) {
      return quarantineInvalidIntent(
          intent,
          actionHandler,
          actionPlan,
          "internal intent signer does not match sponsor signer");
    }

    long reservedNonce = executionTransactionGatewayPort.reserveNextNonce(expectedSigner);
    ExecutionIntent signableIntent = rebindReservedNonce(intent, reservedNonce);

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
                  sponsorKey.privateKeyHex()));
    } catch (Web3InvalidInputException e) {
      return quarantineInvalidIntent(intent, actionHandler, actionPlan, e.getMessage());
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
                signableIntent.getUnsignedTxSnapshot().toAddress(),
                actionPlan.amountWei(),
                signableIntent.getUnsignedTxSnapshot().expectedNonce(),
                ExecutionTransactionStatus.CREATED,
                ExecutionTransactionType.EIP1559,
                null,
                null,
                null,
                null));

    executionTransactionGatewayPort.markSigned(
        created.transactionId(),
        signableIntent.getUnsignedTxSnapshot().expectedNonce(),
        signedTransaction.rawTransaction(),
        signedTransaction.txHash());
    executionIntentPersistencePort.update(signableIntent.markSigned(created.transactionId(), now));
    audit(
        created.transactionId(),
        ExecutionAuditEventType.SIGN,
        null,
        java.util.Map.of("mode", intent.getMode().name(), "internal", true));

    ExecutionTransactionGatewayPort.BroadcastResult broadcast =
        executionTransactionGatewayPort.broadcast(signedTransaction.rawTransaction());
    audit(
        created.transactionId(),
        ExecutionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail(broadcast));

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? signedTransaction.txHash()
              : broadcast.txHash();
      executionTransactionGatewayPort.markPending(created.transactionId(), txHash);
      executionIntentPersistencePort.update(
          signableIntent.markPendingOnchain(created.transactionId(), LocalDateTime.now(appClock)));
      actionHandler.afterTransactionSubmitted(
          signableIntent, actionPlan, ExecutionTransactionStatus.PENDING);
      return new ExecuteInternalExecutionIntentResult(
          true,
          false,
          intent.getPublicId(),
          ExecutionIntentStatus.PENDING_ONCHAIN,
          created.transactionId(),
          ExecutionTransactionStatus.PENDING,
          txHash);
    }

    String failureReason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? BROADCAST_FAILED
            : broadcast.failureReason();
    executionTransactionGatewayPort.scheduleRetry(
        created.transactionId(),
        failureReason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    executionIntentPersistencePort.update(
        signableIntent.markSigned(created.transactionId(), LocalDateTime.now(appClock)));
    actionHandler.afterTransactionSubmitted(
        signableIntent, actionPlan, ExecutionTransactionStatus.SIGNED);
    return new ExecuteInternalExecutionIntentResult(
        true,
        false,
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.transactionId(),
        ExecutionTransactionStatus.SIGNED,
        signedTransaction.txHash());
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
    return executionActionHandlerPorts.stream()
        .filter(handler -> handler.supports(intent.getActionType()))
        .findFirst();
  }

  private ExecuteInternalExecutionIntentResult quarantineInvalidIntent(
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      String failureReason) {
    LocalDateTime now = LocalDateTime.now(appClock);
    ExecutionIntent canceled =
        executionIntentPersistencePort.update(
            intent.cancel(
                INTERNAL_ISSUER_INVALID_INTENT,
                failureReason == null || failureReason.isBlank()
                    ? INTERNAL_ISSUER_INVALID_INTENT
                    : failureReason,
                now));
    if (actionHandler != null && actionPlan != null) {
      safeAfterExecutionTerminated(
          actionHandler,
          canceled,
          actionPlan,
          ExecutionIntentStatus.CANCELED,
          INTERNAL_ISSUER_INVALID_INTENT);
    }
    log.error(
        "internal execution issuer quarantined invalid intent: executionIntentId={}, actionType={}, reason={}",
        canceled.getPublicId(),
        canceled.getActionType(),
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

  private void safeAfterExecutionTerminated(
      ExecutionActionHandlerPort actionHandler,
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    try {
      actionHandler.afterExecutionTerminated(intent, actionPlan, terminalStatus, failureReason);
    } catch (RuntimeException e) {
      log.error(
          "internal execution issuer termination hook failed: executionIntentId={}, actionType={}, terminalStatus={}, failureReason={}",
          intent.getPublicId(),
          intent.getActionType(),
          terminalStatus,
          failureReason,
          e);
    }
  }
}
