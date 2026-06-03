package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.RequeueWeb3TransactionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class RequeueWeb3TransactionService implements RequeueWeb3TransactionUseCase {

  private final Web3TransactionRequeueProcessor processor;

  @Override
  @AdminOnly(
      actionType = "TRANSACTION_REQUEUE",
      targetType = AuditTargetType.WEB3_TRANSACTION,
      operatorId = "#command.operatorId()",
      targetId = "#command.transactionId()",
      detail = {
        "reason=#command.reason()",
        "evidence=#command.evidence()",
        "previousStatus=#result != null ? #result.previousStatus().name() : null",
        "status=#result != null ? #result.status().name() : null",
        "originalFailureReason=#result != null ? #result.originalFailureReason() : null"
      })
  public RequeueWeb3TransactionResult execute(RequeueWeb3TransactionCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return processor.requeueOrThrow(command);
  }
}
