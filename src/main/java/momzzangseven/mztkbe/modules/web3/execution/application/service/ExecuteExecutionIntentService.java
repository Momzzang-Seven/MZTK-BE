package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecuteExecutionIntentService implements ExecuteExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final TransferTransactionPersistencePort transferTransactionPersistencePort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final LoadTreasuryKeyPort loadTreasuryKeyPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  private final VerifyExecutionSignaturePort verifyExecutionSignaturePort;
  private final Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  private final LoadExecutionChainIdPort loadExecutionChainIdPort;
  private final LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort;
  private final LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final Clock appClock;

  @Override
  public ExecuteExecutionIntentResult execute(ExecuteExecutionIntentCommand command) {
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
      throw new Web3TransferException(ErrorCode.EXECUTION_INTENT_EXPIRED, false);
    }

    if (!intent.getStatus().isSignable()) {
      return toResult(intent, null);
    }

    ExecutionActionHandlerPort actionHandler = resolveActionHandler(intent);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);
    actionHandler.beforeExecute(intent, actionPlan);

    return switch (intent.getMode()) {
      case EIP7702 -> executeEip7702(command, intent, actionHandler, actionPlan);
      case EIP1559 -> executeEip1559(command, intent, actionHandler, actionPlan);
    };
  }

  private ExecuteExecutionIntentResult executeEip7702(
      ExecuteExecutionIntentCommand command,
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan) {
    if (command.authorizationSignature() == null || command.authorizationSignature().isBlank()) {
      throw new Web3InvalidInputException("authorizationSignature is required");
    }
    if (command.submitSignature() == null || command.submitSignature().isBlank()) {
      throw new Web3InvalidInputException("submitSignature is required");
    }

    var sponsorWalletConfig = loadExecutionSponsorWalletConfigPort.loadSponsorWalletConfig();
    LoadTreasuryKeyPort.TreasuryKeyMaterial sponsorKey =
        loadTreasuryKeyPort
            .loadByAlias(
                sponsorWalletConfig.walletAlias(), sponsorWalletConfig.keyEncryptionKeyB64())
            .orElseThrow(() -> new Web3InvalidInputException("sponsor signer key is missing"));

    String sponsorAddress = EvmAddress.of(sponsorKey.treasuryAddress()).value();
    Eip7702ChainPort.AuthorizationTuple authTuple =
        eip7702AuthorizationPort.toAuthorizationTuple(
            loadExecutionChainIdPort.loadChainId(),
            intent.getDelegateTarget(),
            BigInteger.valueOf(intent.getAuthorityNonce()),
            command.authorizationSignature());

    List<Eip7702TransactionCodecPort.BatchCall> calls =
        actionPlan.calls().stream()
            .map(
                call ->
                    new Eip7702TransactionCodecPort.BatchCall(
                        call.toAddress(),
                        call.valueWei(),
                        Numeric.hexStringToByteArray(call.data())))
            .toList();
    String callDataHash = eip7702TransactionCodecPort.hashCalls(calls);

    if (!eip7702AuthorizationPort.verifySigner(
        loadExecutionChainIdPort.loadChainId(),
        intent.getDelegateTarget(),
        BigInteger.valueOf(intent.getAuthorityNonce()),
        command.authorizationSignature(),
        intent.getAuthorityAddress())) {
      throw new Web3InvalidInputException("authorizationSignature does not match authority");
    }

    BigInteger deadlineEpochSeconds =
        BigInteger.valueOf(intent.getExpiresAt().toEpochSecond(ZoneOffset.UTC));
    if (!verifyExecutionSignaturePort.verify(
        intent.getAuthorityAddress(),
        intent.getPublicId(),
        callDataHash,
        deadlineEpochSeconds,
        command.submitSignature())) {
      throw new Web3InvalidInputException("submitSignature does not match authority");
    }

    String executeCalldata =
        eip7702TransactionCodecPort.encodeExecute(
            calls, Numeric.hexStringToByteArray(command.submitSignature()));

    BigInteger estimatedGas =
        eip7702ChainPort.estimateGasWithAuthorization(
            sponsorAddress,
            intent.getAuthorityAddress(),
            executeCalldata,
            java.util.List.of(authTuple));
    Eip7702ChainPort.FeePlan feePlan = eip7702ChainPort.loadSponsorFeePlan();
    long sponsorNonce = reserveNoncePort.reserveNextNonce(sponsorAddress);

    Eip7702TransactionCodecPort.SignedPayload signedPayload =
        eip7702TransactionCodecPort.signAndEncode(
            new Eip7702TransactionCodecPort.SignCommand(
                loadExecutionChainIdPort.loadChainId(),
                BigInteger.valueOf(sponsorNonce),
                feePlan.maxPriorityFeePerGas(),
                feePlan.maxFeePerGas(),
                estimatedGas,
                intent.getAuthorityAddress(),
                BigInteger.ZERO,
                executeCalldata,
                java.util.List.of(authTuple),
                sponsorKey.privateKeyHex()));

    TransferTransaction created =
        transferTransactionPersistencePort.createAndFlush(
            TransferTransaction.builder()
                .idempotencyKey(intent.getRootIdempotencyKey() + ":" + intent.getAttemptNo())
                .referenceType(actionPlan.referenceType())
                .referenceId(intent.getResourceId())
                .fromUserId(intent.getRequesterUserId())
                .toUserId(intent.getCounterpartyUserId())
                .fromAddress(sponsorAddress)
                .toAddress(intent.getAuthorityAddress())
                .amountWei(actionPlan.amountWei())
                .status(Web3TxStatus.CREATED)
                .txType(Web3TxType.EIP7702)
                .authorityAddress(intent.getAuthorityAddress())
                .authorizationNonce(intent.getAuthorityNonce())
                .delegateTarget(intent.getDelegateTarget())
                .authorizationExpiresAt(intent.getExpiresAt())
                .build());

    updateTransactionPort.markSigned(
        created.getId(), sponsorNonce, signedPayload.rawTx(), signedPayload.txHash());
    executionIntentPersistencePort.update(
        intent.markSigned(created.getId(), LocalDateTime.now(appClock)));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.AUTHORIZATION,
        null,
        java.util.Map.of("mode", intent.getMode().name()));

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(signedPayload.rawTx()));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        java.util.Map.of(
            "success", broadcast.success(),
            "txHash", broadcast.txHash(),
            "failureReason", broadcast.failureReason()));

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? signedPayload.txHash()
              : broadcast.txHash();
      updateTransactionPort.markPending(created.getId(), txHash);
      executionIntentPersistencePort.update(
          intent.markPendingOnchain(created.getId(), LocalDateTime.now(appClock)));
      moveReservedSponsorExposureToConsumed(
          intent.getRequesterUserId(),
          intent.resolveSponsorUsageDateKst(),
          intent.getReservedSponsorCostWei());
      actionHandler.afterTransactionSubmitted(intent, actionPlan, Web3TxStatus.PENDING);
      return new ExecuteExecutionIntentResult(
          intent.getPublicId(),
          ExecutionIntentStatus.PENDING_ONCHAIN,
          created.getId(),
          Web3TxStatus.PENDING,
          txHash);
    }

    String reason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? Web3TxFailureReason.BROADCAST_FAILED.code()
            : broadcast.failureReason();
    updateTransactionPort.scheduleRetry(
        created.getId(),
        reason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    executionIntentPersistencePort.update(
        intent.markSigned(created.getId(), LocalDateTime.now(appClock)));
    actionHandler.afterTransactionSubmitted(intent, actionPlan, Web3TxStatus.SIGNED);
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.getId(),
        Web3TxStatus.SIGNED,
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
        eip7702ChainPort.loadPendingAccountNonce(decoded.signerAddress());
    if (currentPendingNonce.longValueExact() != intent.getUnsignedTxSnapshot().expectedNonce()) {
      executionIntentPersistencePort.update(
          intent.markNonceStale(
              ErrorCode.NONCE_STALE_RECREATE_REQUIRED.name(),
              ErrorCode.NONCE_STALE_RECREATE_REQUIRED.getMessage(),
              LocalDateTime.now(appClock)));
      throw new Web3TransferException(ErrorCode.NONCE_STALE_RECREATE_REQUIRED, false);
    }

    TransferTransaction created =
        transferTransactionPersistencePort.createAndFlush(
            TransferTransaction.builder()
                .idempotencyKey(intent.getRootIdempotencyKey() + ":" + intent.getAttemptNo())
                .referenceType(actionPlan.referenceType())
                .referenceId(intent.getResourceId())
                .fromUserId(intent.getRequesterUserId())
                .toUserId(intent.getCounterpartyUserId())
                .fromAddress(decoded.signerAddress())
                .toAddress(intent.getUnsignedTxSnapshot().toAddress())
                .amountWei(actionPlan.amountWei())
                .nonce(intent.getUnsignedTxSnapshot().expectedNonce())
                .status(Web3TxStatus.CREATED)
                .txType(Web3TxType.EIP1559)
                .build());

    updateTransactionPort.markSigned(
        created.getId(),
        intent.getUnsignedTxSnapshot().expectedNonce(),
        decoded.rawTransaction(),
        decoded.txHash());
    executionIntentPersistencePort.update(
        intent.markSigned(created.getId(), LocalDateTime.now(appClock)));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.SIGN,
        null,
        java.util.Map.of("mode", intent.getMode().name()));

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(decoded.rawTransaction()));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        java.util.Map.of(
            "success", broadcast.success(),
            "txHash", broadcast.txHash(),
            "failureReason", broadcast.failureReason()));

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? decoded.txHash()
              : broadcast.txHash();
      updateTransactionPort.markPending(created.getId(), txHash);
      executionIntentPersistencePort.update(
          intent.markPendingOnchain(created.getId(), LocalDateTime.now(appClock)));
      actionHandler.afterTransactionSubmitted(intent, actionPlan, Web3TxStatus.PENDING);
      return new ExecuteExecutionIntentResult(
          intent.getPublicId(),
          ExecutionIntentStatus.PENDING_ONCHAIN,
          created.getId(),
          Web3TxStatus.PENDING,
          txHash);
    }

    String reason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? Web3TxFailureReason.BROADCAST_FAILED.code()
            : broadcast.failureReason();
    updateTransactionPort.scheduleRetry(
        created.getId(),
        reason,
        LocalDateTime.now(appClock)
            .plusSeconds(loadExecutionRetryPolicyPort.loadRetryPolicy().retryBackoffSeconds()));
    executionIntentPersistencePort.update(
        intent.markSigned(created.getId(), LocalDateTime.now(appClock)));
    actionHandler.afterTransactionSubmitted(intent, actionPlan, Web3TxStatus.SIGNED);
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        ExecutionIntentStatus.SIGNED,
        created.getId(),
        Web3TxStatus.SIGNED,
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

  private TransferTransaction loadTransaction(Long transactionId) {
    return transferTransactionPersistencePort
        .findById(transactionId)
        .orElseThrow(
            () -> new Web3InvalidInputException("transaction not found: " + transactionId));
  }

  private ExecuteExecutionIntentResult toResult(
      ExecutionIntent intent, TransferTransaction transaction) {
    return new ExecuteExecutionIntentResult(
        intent.getPublicId(),
        intent.getStatus(),
        transaction == null ? intent.getSubmittedTxId() : transaction.getId(),
        transaction == null ? null : transaction.getStatus(),
        transaction == null ? null : transaction.getTxHash());
  }

  private void audit(
      Long transactionId,
      Web3TransactionAuditEventType eventType,
      String rpcAlias,
      java.util.Map<String, Object> detail) {
    try {
      recordTransactionAuditPort.record(
          new RecordTransactionAuditPort.AuditCommand(transactionId, eventType, rpcAlias, detail));
    } catch (Exception e) {
      log.warn(
          "failed to record transaction audit: txId={}, eventType={}", transactionId, eventType, e);
    }
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
}
