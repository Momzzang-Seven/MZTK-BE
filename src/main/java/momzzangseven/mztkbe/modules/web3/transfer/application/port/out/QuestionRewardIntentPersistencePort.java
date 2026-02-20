package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Collection;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;

/** Persistence port for QUESTION_REWARD intent SSOT rows. */
public interface QuestionRewardIntentPersistencePort {

  Optional<QuestionRewardIntentEntity> findByPostId(Long postId);

  Optional<QuestionRewardIntentEntity> findForUpdateByPostId(Long postId);

  QuestionRewardIntentEntity save(QuestionRewardIntentEntity entity);

  int updateStatusIfCurrentIn(
      Long postId,
      QuestionRewardIntentStatus toStatus,
      Collection<QuestionRewardIntentStatus> fromStatuses);
}
