package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.QuestionRewardIntentRecord;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionRewardIntentPersistenceAdapter implements QuestionRewardIntentPersistencePort {

  private final QuestionRewardIntentJpaRepository repository;

  @Override
  public Optional<QuestionRewardIntentRecord> findByPostId(Long postId) {
    return repository.findByPostId(postId).map(this::toRecord);
  }

  @Override
  public Optional<QuestionRewardIntentRecord> findForUpdateByPostId(Long postId) {
    return repository.findForUpdateByPostId(postId).map(this::toRecord);
  }

  @Override
  public QuestionRewardIntentRecord save(QuestionRewardIntentRecord record) {
    QuestionRewardIntentEntity entity =
        record.getId() == null
            ? repository.findByPostId(record.getPostId()).orElseGet(QuestionRewardIntentEntity.builder()::build)
            : repository.findById(record.getId()).orElseGet(QuestionRewardIntentEntity.builder()::build);

    entity.setPostId(record.getPostId());
    entity.setAcceptedCommentId(record.getAcceptedCommentId());
    entity.setFromUserId(record.getFromUserId());
    entity.setToUserId(record.getToUserId());
    entity.setAmountWei(record.getAmountWei());
    entity.setStatus(record.getStatus());

    return toRecord(repository.save(entity));
  }

  @Override
  public int updateStatusIfCurrentIn(
      Long postId,
      QuestionRewardIntentStatus toStatus,
      Collection<QuestionRewardIntentStatus> fromStatuses) {
    return repository.updateStatusIfCurrentIn(postId, toStatus, fromStatuses);
  }

  private QuestionRewardIntentRecord toRecord(QuestionRewardIntentEntity entity) {
    return QuestionRewardIntentRecord.builder()
        .id(entity.getId())
        .postId(entity.getPostId())
        .acceptedCommentId(entity.getAcceptedCommentId())
        .fromUserId(entity.getFromUserId())
        .toUserId(entity.getToUserId())
        .amountWei(entity.getAmountWei())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
