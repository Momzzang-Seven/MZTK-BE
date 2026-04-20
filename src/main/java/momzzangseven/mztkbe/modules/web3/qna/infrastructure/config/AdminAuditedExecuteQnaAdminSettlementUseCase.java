package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.service.ExecuteQnaAdminSettlementService;
import org.springframework.transaction.support.TransactionTemplate;

public class AdminAuditedExecuteQnaAdminSettlementUseCase
    implements ExecuteQnaAdminSettlementUseCase {

  private final ExecuteQnaAdminSettlementService delegate;
  private final TransactionTemplate transactionTemplate;

  public AdminAuditedExecuteQnaAdminSettlementUseCase(
      ExecuteQnaAdminSettlementService delegate, TransactionTemplate transactionTemplate) {
    this.delegate = delegate;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  @AdminOnly(
      actionType = "QNA_ADMIN_SETTLE",
      targetType = AuditTargetType.QNA_ESCROW_QUESTION,
      operatorId = "#command.operatorId()",
      targetId = "'post:' + #command.postId()")
  public QnaExecutionIntentResult execute(ExecuteQnaAdminSettlementCommand command) {
    return transactionTemplate.execute(status -> delegate.execute(command));
  }
}
