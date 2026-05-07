package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerDeleteStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPersistenceAdapter
    implements SaveAnswerPort, LoadAnswerPort, DeleteAnswerPort, CountAnswersPort {

  private final AnswerJpaRepository answerJpaRepository;

  @Override
  public Answer saveAnswer(Answer answer) {
    AnswerEntity entity = toEntity(answer);
    AnswerEntity savedEntity = answerJpaRepository.save(entity);
    return toDomain(savedEntity);
  }

  @Override
  public List<Answer> loadAnswersByPostId(Long postId) {
    return answerJpaRepository.findByPostIdOrderByIsAcceptedDescCreatedAtAsc(postId).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Answer> loadPublicVisibleAnswersByPostId(Long postId) {
    return answerJpaRepository
        .findByPostIdAndPublicationStatusAndPendingDeleteStatusIsNullOrderByIsAcceptedDescCreatedAtAsc(
            postId, AnswerPublicationStatus.VISIBLE)
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Answer> loadPublicAndOwnerVisibleAnswersByPostId(Long postId, Long ownerUserId) {
    return answerJpaRepository
        .findPublicAndOwnerVisibleByPostId(
            postId,
            ownerUserId,
            AnswerPublicationStatus.VISIBLE,
            List.of(
                AnswerPublicationStatus.PENDING,
                AnswerPublicationStatus.FAILED,
                AnswerPublicationStatus.RECONCILIATION_REQUIRED))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<Answer> loadAnswer(Long answerId) {
    return answerJpaRepository.findById(answerId).map(this::toDomain);
  }

  @Override
  public Optional<Answer> loadAnswerForUpdate(Long answerId) {
    return answerJpaRepository.findByIdForUpdate(answerId).map(this::toDomain);
  }

  @Override
  public long countAnswers(Long postId) {
    return answerJpaRepository.countByPostId(postId);
  }

  @Override
  public long countPublicVisibleAnswers(Long postId) {
    return answerJpaRepository.countByPostIdAndPublicationStatusAndPendingDeleteStatusIsNull(
        postId, AnswerPublicationStatus.VISIBLE);
  }

  @Override
  public List<Long> loadAnswerIdsByPostId(Long postId) {
    return answerJpaRepository.findIdsByPostId(postId);
  }

  @Override
  public List<Long> loadOrphanAnswerIds(int batchSize) {
    return answerJpaRepository.findOrphanAnswerIds(batchSize);
  }

  @Override
  public boolean existsPreparingOrPendingCreateByPostId(Long postId) {
    return answerJpaRepository.existsPreparingOrPendingCreateByPostId(
        postId, AnswerPublicationStatus.PENDING);
  }

  @Override
  public void deleteAnswer(Long answerId) {
    answerJpaRepository.deleteById(answerId);
  }

  @Override
  public void deleteAnswersByPostId(Long postId) {
    answerJpaRepository.deleteAllByPostId(postId);
  }

  @Override
  public void deleteAnswersByIds(List<Long> answerIds) {
    if (answerIds.isEmpty()) {
      return;
    }
    answerJpaRepository.deleteAllByIdInBatch(answerIds);
  }

  @Override
  public int bindCreateIntentIfCurrent(
      Long answerId, String preparationToken, String executionIntentId) {
    return answerJpaRepository.bindCreateIntentIfCurrent(
        answerId, preparationToken, executionIntentId, AnswerPublicationStatus.PENDING);
  }

  @Override
  public int confirmCreateIfCurrent(Long answerId, String executionIntentId) {
    return answerJpaRepository.confirmCreateIfCurrent(
        answerId,
        executionIntentId,
        AnswerPublicationStatus.PENDING,
        AnswerPublicationStatus.VISIBLE);
  }

  @Override
  public int markCreateFailedIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason) {
    return answerJpaRepository.markCreateFailedIfCurrent(
        answerId,
        executionIntentId,
        terminalStatus,
        failureReason,
        AnswerPublicationStatus.PENDING,
        AnswerPublicationStatus.FAILED);
  }

  @Override
  public int bindDeleteIntentIfCurrent(
      Long answerId, String preparationToken, String executionIntentId) {
    return answerJpaRepository.bindDeleteIntentIfCurrent(
        answerId,
        preparationToken,
        executionIntentId,
        AnswerPublicationStatus.VISIBLE,
        AnswerDeleteStatus.PREPARING,
        AnswerDeleteStatus.PENDING);
  }

  @Override
  public int rollbackDeleteIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason) {
    return answerJpaRepository.rollbackDeleteIfCurrent(
        answerId, executionIntentId, terminalStatus, failureReason);
  }

  @Override
  public int markDeleteSyncConflictIfMismatched(
      Long answerId, String executionIntentId, String conflictReason) {
    return answerJpaRepository.markDeleteSyncConflictIfMismatched(
        answerId,
        executionIntentId,
        conflictReason,
        AnswerPublicationStatus.RECONCILIATION_REQUIRED);
  }

  private AnswerEntity toEntity(Answer answer) {
    return AnswerEntity.builder()
        .id(answer.getId())
        .postId(answer.getPostId())
        .userId(answer.getUserId())
        .content(answer.getContent())
        .isAccepted(answer.getIsAccepted())
        .publicationStatus(answer.getPublicationStatus())
        .currentCreateExecutionIntentId(answer.getCurrentCreateExecutionIntentId())
        .createPreparationToken(answer.getCreatePreparationToken())
        .publicationFailureTerminalStatus(answer.getPublicationFailureTerminalStatus())
        .publicationFailureReason(answer.getPublicationFailureReason())
        .createPreparationExpiresAt(answer.getCreatePreparationExpiresAt())
        .pendingDeleteStatus(answer.getPendingDeleteStatus())
        .currentDeleteExecutionIntentId(answer.getCurrentDeleteExecutionIntentId())
        .deletePreparationToken(answer.getDeletePreparationToken())
        .deletePreparationExpiresAt(answer.getDeletePreparationExpiresAt())
        .deleteFailureTerminalStatus(answer.getDeleteFailureTerminalStatus())
        .deleteFailureReason(answer.getDeleteFailureReason())
        .reconciliationRequiredReason(answer.getReconciliationRequiredReason())
        .reconciliationRequiredIntentId(answer.getReconciliationRequiredIntentId())
        .build();
  }

  private Answer toDomain(AnswerEntity entity) {
    return Answer.builder()
        .id(entity.getId())
        .postId(entity.getPostId())
        .userId(entity.getUserId())
        .content(entity.getContent())
        .isAccepted(entity.getIsAccepted())
        .publicationStatus(entity.getPublicationStatus())
        .currentCreateExecutionIntentId(entity.getCurrentCreateExecutionIntentId())
        .createPreparationToken(entity.getCreatePreparationToken())
        .publicationFailureTerminalStatus(entity.getPublicationFailureTerminalStatus())
        .publicationFailureReason(entity.getPublicationFailureReason())
        .createPreparationExpiresAt(entity.getCreatePreparationExpiresAt())
        .pendingDeleteStatus(entity.getPendingDeleteStatus())
        .currentDeleteExecutionIntentId(entity.getCurrentDeleteExecutionIntentId())
        .deletePreparationToken(entity.getDeletePreparationToken())
        .deletePreparationExpiresAt(entity.getDeletePreparationExpiresAt())
        .deleteFailureTerminalStatus(entity.getDeleteFailureTerminalStatus())
        .deleteFailureReason(entity.getDeleteFailureReason())
        .reconciliationRequiredReason(entity.getReconciliationRequiredReason())
        .reconciliationRequiredIntentId(entity.getReconciliationRequiredIntentId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
