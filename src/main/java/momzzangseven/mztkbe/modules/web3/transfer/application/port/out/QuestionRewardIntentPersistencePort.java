package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Collection;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;

/** Persistence port for QUESTION_REWARD intent SSOT rows. */
public interface QuestionRewardIntentPersistencePort {

  Optional<QuestionRewardIntent> findByPostId(Long postId);

  Optional<QuestionRewardIntent> findForUpdateByPostId(Long postId);

  QuestionRewardIntent create(QuestionRewardIntent intent);

  QuestionRewardIntent update(QuestionRewardIntent intent);

  int updateStatusIfCurrentIn(
      Long postId,
      QuestionRewardIntentStatus toStatus,
      Collection<QuestionRewardIntentStatus> fromStatuses);
}
