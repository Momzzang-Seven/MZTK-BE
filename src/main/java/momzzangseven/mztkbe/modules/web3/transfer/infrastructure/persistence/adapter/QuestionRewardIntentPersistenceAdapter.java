package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionRewardIntentPersistenceAdapter implements QuestionRewardIntentPersistencePort {

  private final QuestionRewardIntentJpaRepository repository;

  @Override
  public Optional<QuestionRewardIntent> findByPostId(Long postId) {
    return repository.findByPostId(postId).map(this::toDomain);
  }

  @Override
  public Optional<QuestionRewardIntent> findForUpdateByPostId(Long postId) {
    return repository.findForUpdateByPostId(postId).map(this::toDomain);
  }

  @Override
  public QuestionRewardIntent create(QuestionRewardIntent intent) {
    if (intent.getId() != null) {
      throw new Web3InvalidInputException("create requires id to be null");
    }
    return toDomain(repository.save(toEntity(intent)));
  }

  @Override
  public QuestionRewardIntent update(QuestionRewardIntent intent) {
    if (intent.getId() == null) {
      throw new Web3InvalidInputException("update requires id");
    }
    QuestionRewardIntentEntity entity =
        repository
            .findById(intent.getId())
            .orElseThrow(() -> new Web3InvalidInputException("question reward intent not found"));
    merge(intent, entity);
    return toDomain(repository.save(entity));
  }

  @Override
  public int updateStatusIfCurrentIn(
      Long postId,
      QuestionRewardIntentStatus toStatus,
      Collection<QuestionRewardIntentStatus> fromStatuses) {
    return repository.updateStatusIfCurrentIn(postId, toStatus, fromStatuses);
  }

  private QuestionRewardIntentEntity toEntity(QuestionRewardIntent domain) {
    QuestionRewardIntentEntity entity = QuestionRewardIntentEntity.builder().build();
    merge(domain, entity);
    return entity;
  }

  private void merge(QuestionRewardIntent domain, QuestionRewardIntentEntity entity) {
    entity.setPostId(domain.getPostId());
    entity.setAcceptedCommentId(domain.getAcceptedCommentId());
    entity.setFromUserId(domain.getFromUserId());
    entity.setToUserId(domain.getToUserId());
    entity.setAmountWei(domain.getAmountWei());
    entity.setStatus(domain.getStatus());
    entity.setLastExecutionIntentErrorCode(domain.getLastExecutionIntentErrorCode());
    entity.setLastExecutionIntentErrorReason(domain.getLastExecutionIntentErrorReason());
  }

  private QuestionRewardIntent toDomain(QuestionRewardIntentEntity entity) {
    return QuestionRewardIntent.builder()
        .id(entity.getId())
        .postId(entity.getPostId())
        .acceptedCommentId(entity.getAcceptedCommentId())
        .fromUserId(entity.getFromUserId())
        .toUserId(entity.getToUserId())
        .amountWei(entity.getAmountWei())
        .status(entity.getStatus())
        .lastExecutionIntentErrorCode(entity.getLastExecutionIntentErrorCode())
        .lastExecutionIntentErrorReason(entity.getLastExecutionIntentErrorReason())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
