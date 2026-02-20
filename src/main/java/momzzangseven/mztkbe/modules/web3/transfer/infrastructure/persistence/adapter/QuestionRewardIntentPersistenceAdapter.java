package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionRewardIntentPersistenceAdapter implements QuestionRewardIntentPersistencePort {

  private final QuestionRewardIntentJpaRepository repository;

  @Override
  public Optional<QuestionRewardIntentEntity> findByPostId(Long postId) {
    return repository.findByPostId(postId);
  }

  @Override
  public Optional<QuestionRewardIntentEntity> findForUpdateByPostId(Long postId) {
    return repository.findForUpdateByPostId(postId);
  }

  @Override
  public QuestionRewardIntentEntity save(QuestionRewardIntentEntity entity) {
    return repository.save(entity);
  }

  @Override
  public int updateStatusIfCurrentIn(
      Long postId,
      QuestionRewardIntentStatus toStatus,
      Collection<QuestionRewardIntentStatus> fromStatuses) {
    return repository.updateStatusIfCurrentIn(postId, toStatus, fromStatuses);
  }
}
