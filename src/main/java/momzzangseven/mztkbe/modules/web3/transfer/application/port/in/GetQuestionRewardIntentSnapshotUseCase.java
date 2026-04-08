package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetQuestionRewardIntentSnapshotQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.QuestionRewardIntentSnapshotResult;

public interface GetQuestionRewardIntentSnapshotUseCase {

  QuestionRewardIntentSnapshotResult execute(GetQuestionRewardIntentSnapshotQuery query);
}
