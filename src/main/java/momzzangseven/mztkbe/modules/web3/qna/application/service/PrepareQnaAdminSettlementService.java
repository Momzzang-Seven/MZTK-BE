package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class PrepareQnaAdminSettlementService {

  private final QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService;

  public QnaExecutionIntentResult execute(PrepareAdminSettleCommand command) {
    return questionEscrowAdminExecutionService.prepareAdminSettle(command);
  }
}
