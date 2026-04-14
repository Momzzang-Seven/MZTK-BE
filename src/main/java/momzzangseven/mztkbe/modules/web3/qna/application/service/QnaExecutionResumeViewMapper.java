package momzzangseven.mztkbe.modules.web3.qna.application.service;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

final class QnaExecutionResumeViewMapper {

  private QnaExecutionResumeViewMapper() {}

  static QnaExecutionResumeViewResult toResult(
      QnaExecutionResourceType resourceType, GetLatestExecutionIntentSummaryResult summary) {
    return new QnaExecutionResumeViewResult(
        new QnaExecutionResumeViewResult.Resource(
            resourceType, summary.resourceId(), toResourceStatus(summary.resourceStatus())),
        new QnaExecutionResumeViewResult.ExecutionIntent(
            summary.executionIntentId(),
            summary.executionIntentStatus().name(),
            summary.expiresAt()),
        new QnaExecutionResumeViewResult.Execution(summary.mode().name(), summary.signCount()),
        summary.transactionId() == null
            ? null
            : new QnaExecutionResumeViewResult.Transaction(
                summary.transactionId(), summary.transactionStatus().name(), summary.txHash()));
  }

  private static QnaExecutionResourceStatus toResourceStatus(ExecutionResourceStatus status) {
    return switch (status) {
      case PENDING_EXECUTION -> QnaExecutionResourceStatus.PENDING_EXECUTION;
      case COMPLETED -> QnaExecutionResourceStatus.COMPLETED;
      case FAILED -> QnaExecutionResourceStatus.FAILED;
    };
  }
}
