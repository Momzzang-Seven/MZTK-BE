package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ResolveExecutionIntentRecoveryTargetUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

@RequiredArgsConstructor
public class ResolveExecutionIntentRecoveryTargetService
    implements ResolveExecutionIntentRecoveryTargetUseCase {

  private static final int RESOURCE_REPLAY_CANDIDATE_LIMIT = 100;
  private static final String RESOLUTION_RESOLVED = "RESOLVED";
  private static final String RESOLUTION_AMBIGUOUS = "TARGET_AMBIGUOUS";

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final LoadExecutionTransactionPort loadExecutionTransactionPort;

  @Override
  public Optional<ResolveExecutionIntentRecoveryTargetResult> execute(
      ResolveExecutionIntentRecoveryTargetQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }

    return resolve(query);
  }

  private Optional<ResolveExecutionIntentRecoveryTargetResult> resolve(
      ResolveExecutionIntentRecoveryTargetQuery query) {
    if (query.executionIntentId() != null && !query.executionIntentId().isBlank()) {
      return executionIntentPersistencePort
          .findByPublicId(query.executionIntentId())
          .map(this::toResult);
    }
    if (query.transactionId() != null) {
      return executionIntentPersistencePort
          .findBySubmittedTxId(query.transactionId())
          .map(this::toResult);
    }
    if (query.resourceType() != null) {
      ExecutionResourceType resourceType =
          ExecutionResourceType.valueOf(query.resourceType().name());
      if (resourceType == ExecutionResourceType.WALLET_REGISTRATION) {
        return resolveWalletRegistrationResource(resourceType, query.resourceId());
      }
      return executionIntentPersistencePort
          .findLatestByResource(resourceType, query.resourceId())
          .map(this::toResult);
    }
    throw new Web3InvalidInputException(
        "executionIntentId, transactionId, or resource target is required");
  }

  private Optional<ResolveExecutionIntentRecoveryTargetResult> resolveWalletRegistrationResource(
      ExecutionResourceType resourceType, String registrationId) {
    List<ExecutionIntent> intents =
        executionIntentPersistencePort.findByResource(
            resourceType, registrationId, RESOURCE_REPLAY_CANDIDATE_LIMIT);
    if (intents.isEmpty()) {
      return Optional.empty();
    }
    List<ExecutionIntent> replayableIntents =
        intents.stream().filter(this::canReplayConfirmedIntent).toList();
    if (replayableIntents.size() == 1) {
      return Optional.of(toResult(replayableIntents.get(0)));
    }
    if (intents.size() == 1) {
      return Optional.of(toResult(intents.get(0)));
    }
    return Optional.of(ambiguousWalletRegistrationResult(registrationId));
  }

  private boolean canReplayConfirmedIntent(ExecutionIntent intent) {
    if (intent.getStatus() == ExecutionIntentStatus.CONFIRMED) {
      return true;
    }
    if (intent.getSubmittedTxId() == null
        || (intent.getStatus() != ExecutionIntentStatus.SIGNED
            && intent.getStatus() != ExecutionIntentStatus.PENDING_ONCHAIN)) {
      return false;
    }
    return loadExecutionTransactionPort
        .findById(intent.getSubmittedTxId())
        .filter(transaction -> transaction.status() == ExecutionTransactionStatus.SUCCEEDED)
        .isPresent();
  }

  private ResolveExecutionIntentRecoveryTargetResult ambiguousWalletRegistrationResult(
      String registrationId) {
    return new ResolveExecutionIntentRecoveryTargetResult(
        RESOLUTION_AMBIGUOUS,
        null,
        ExecutionResourceType.WALLET_REGISTRATION.name(),
        registrationId,
        null,
        null,
        null,
        null,
        null);
  }

  private ResolveExecutionIntentRecoveryTargetResult toResult(ExecutionIntent intent) {
    Optional<ExecutionTransactionSummary> transaction =
        intent.getSubmittedTxId() == null
            ? Optional.empty()
            : loadExecutionTransactionPort.findById(intent.getSubmittedTxId());
    return new ResolveExecutionIntentRecoveryTargetResult(
        RESOLUTION_RESOLVED,
        intent.getPublicId(),
        intent.getResourceType().name(),
        intent.getResourceId(),
        intent.getActionType().name(),
        intent.getStatus().name(),
        transaction.map(ExecutionTransactionSummary::transactionId).orElse(null),
        transaction.map(ExecutionTransactionSummary::status).orElse(null),
        transaction.map(ExecutionTransactionSummary::txHash).orElse(null));
  }
}
