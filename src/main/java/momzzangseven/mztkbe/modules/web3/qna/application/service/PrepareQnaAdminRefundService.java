package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class PrepareQnaAdminRefundService {

  private final QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService;

  public QnaExecutionIntentResult execute(PrepareAdminRefundCommand command) {
    return questionEscrowAdminExecutionService.prepareAdminRefund(command);
  }
}
