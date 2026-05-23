package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidatesQuery;

public interface GetExecutionIntentCandidatesUseCase {

  List<GetExecutionIntentCandidateResult> execute(GetExecutionIntentCandidatesQuery query);
}
