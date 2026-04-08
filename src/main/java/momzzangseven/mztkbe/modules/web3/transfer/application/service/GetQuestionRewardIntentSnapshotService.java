package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetQuestionRewardIntentSnapshotQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.QuestionRewardIntentSnapshotResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetQuestionRewardIntentSnapshotUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetQuestionRewardIntentSnapshotService
    implements GetQuestionRewardIntentSnapshotUseCase {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  public QuestionRewardIntentSnapshotResult execute(GetQuestionRewardIntentSnapshotQuery query) {
    return questionRewardIntentPersistencePort
        .findByPostId(query.postId())
        .map(intent -> new QuestionRewardIntentSnapshotResult(true, intent.getStatus().name()))
        .orElseGet(() -> new QuestionRewardIntentSnapshotResult(false, null));
  }
}
