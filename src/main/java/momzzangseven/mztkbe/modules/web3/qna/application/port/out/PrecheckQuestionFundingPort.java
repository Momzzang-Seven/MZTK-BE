package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;

public interface PrecheckQuestionFundingPort {

  void precheck(PrecheckQuestionCreateCommand command);
}
