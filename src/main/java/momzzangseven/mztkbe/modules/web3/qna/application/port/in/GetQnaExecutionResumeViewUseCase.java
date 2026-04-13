package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;

public interface GetQnaExecutionResumeViewUseCase {

  Optional<QnaExecutionResumeViewResult> execute(GetQnaExecutionResumeViewQuery query);
}
