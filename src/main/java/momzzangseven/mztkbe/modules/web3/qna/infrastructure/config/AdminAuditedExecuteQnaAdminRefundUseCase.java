package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ExecuteQnaAdminRefundService;
import org.springframework.transaction.support.TransactionTemplate;

public class AdminAuditedExecuteQnaAdminRefundUseCase implements ExecuteQnaAdminRefundUseCase {

  private final ExecuteQnaAdminRefundService delegate;
  private final TransactionTemplate transactionTemplate;

  public AdminAuditedExecuteQnaAdminRefundUseCase(
      ExecuteQnaAdminRefundService delegate, TransactionTemplate transactionTemplate) {
    this.delegate = delegate;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  @AdminOnly(
      actionType = "QNA_ADMIN_REFUND",
      targetType = AuditTargetType.QNA_ESCROW_QUESTION,
      operatorId = "#command.operatorId()",
      targetId = "'post:' + #command.postId()")
  public QnaExecutionIntentResult execute(ExecuteQnaAdminRefundCommand command) {
    return transactionTemplate.execute(status -> delegate.execute(command));
  }
}
