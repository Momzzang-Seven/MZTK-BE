package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaAdminRefundUseCase;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class PrepareQnaAdminRefundService implements PrepareQnaAdminRefundUseCase {

  private final QuestionEscrowAdminExecutionService questionEscrowAdminExecutionService;

  @Override
  public QnaExecutionIntentResult execute(PrepareAdminRefundCommand command) {
    return questionEscrowAdminExecutionService.prepareAdminRefund(command);
  }
}
