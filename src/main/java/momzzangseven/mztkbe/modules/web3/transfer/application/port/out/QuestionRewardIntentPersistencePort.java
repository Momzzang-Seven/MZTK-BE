package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Collection;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.QuestionRewardIntentRecord;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;

/** Persistence port for QUESTION_REWARD intent SSOT rows. */
public interface QuestionRewardIntentPersistencePort {

  Optional<QuestionRewardIntentRecord> findByPostId(Long postId);

  Optional<QuestionRewardIntentRecord> findForUpdateByPostId(Long postId);

  QuestionRewardIntentRecord save(QuestionRewardIntentRecord record);

  int updateStatusIfCurrentIn(
      Long postId,
      QuestionRewardIntentStatus toStatus,
      Collection<QuestionRewardIntentStatus> fromStatuses);
}
