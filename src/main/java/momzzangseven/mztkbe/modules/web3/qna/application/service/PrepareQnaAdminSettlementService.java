package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminSettlementUseCase;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class PrepareQnaAdminSettlementService implements PrepareQnaAdminSettlementUseCase {

  private final QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService;

  @Override
  public QnaExecutionIntentResult execute(PrepareAdminSettleCommand command) {
    return questionEscrowAdminExecutionService.prepareAdminSettle(command);
  }
}
