package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateResult;

public interface GetExecutionIntentStateUseCase {

  GetExecutionIntentStateResult execute(GetExecutionIntentStateQuery query);
}
