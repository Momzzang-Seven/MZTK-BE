package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

final class ExecutionIntentViewMapper {

  private ExecutionIntentViewMapper() {}

  static GetLatestExecutionIntentSummaryResult toLatestSummary(
      ExecutionIntent intent, Optional<ExecutionTransactionSummary> transaction) {
    ExecutionTransactionView transactionView = toTransactionView(transaction);
    return new GetLatestExecutionIntentSummaryResult(
        intent.getResourceType(),
        intent.getResourceId(),
        toResourceStatus(intent.getStatus()),
        intent.getActionType(),
        intent.getPublicId(),
        intent.getStatus(),
        intent.getExpiresAt(),
        intent.getMode(),
        intent.getMode().requiredSignCount(),
        transactionView.transactionId(),
        transactionView.transactionStatus(),
        transactionView.txHash());
  }

  static ExecutionResourceStatus toResourceStatus(ExecutionIntentStatus status) {
    return switch (status) {
      case AWAITING_SIGNATURE, SIGNED, PENDING_ONCHAIN -> ExecutionResourceStatus.PENDING_EXECUTION;
      case CONFIRMED -> ExecutionResourceStatus.COMPLETED;
      case FAILED_ONCHAIN, EXPIRED, NONCE_STALE, CANCELED -> ExecutionResourceStatus.FAILED;
    };
  }

  static ExecutionTransactionView toTransactionView(
      Optional<ExecutionTransactionSummary> transaction) {
    return new ExecutionTransactionView(
        transaction.map(ExecutionTransactionSummary::transactionId).orElse(null),
        transaction.map(ExecutionTransactionSummary::status).orElse(null),
        transaction.map(ExecutionTransactionSummary::txHash).orElse(null));
  }

  record ExecutionTransactionView(
      Long transactionId, ExecutionTransactionStatus transactionStatus, String txHash) {}
}
