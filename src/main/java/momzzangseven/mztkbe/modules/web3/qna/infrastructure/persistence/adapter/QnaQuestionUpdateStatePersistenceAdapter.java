package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionUpdateStateEntity;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaQuestionUpdateStateJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class QnaQuestionUpdateStatePersistenceAdapter
    implements QnaQuestionUpdateStatePersistencePort {

  private final QnaQuestionUpdateStateJpaRepository repository;
  private final Clock appClock;

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> findLatestByPostIdForUpdate(Long postId) {
    return repository.findLatestByPostIdForUpdate(postId, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<QnaQuestionUpdateState> findLatestByPostId(Long postId) {
    return repository.findLatestByPostId(postId, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> findByExecutionIntentPublicIdForUpdate(String publicId) {
    return repository.findByExecutionIntentPublicIdForUpdate(publicId).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<QnaQuestionUpdateState> findConfirmedIntentBoundForReconciliation(int limit) {
    return repository
        .findConfirmedIntentBoundForReconciliation(
            QnaQuestionUpdateStateStatus.INTENT_BOUND,
            ExecutionIntentStatus.CONFIRMED,
            ExecutionActionType.QNA_QUESTION_UPDATE,
            PageRequest.of(0, limit))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public QnaQuestionUpdateState save(QnaQuestionUpdateState state) {
    return toDomain(repository.save(toEntity(state)));
  }

  @Override
  @Transactional
  public int markSupersedableStaleByPostId(Long postId) {
    return repository.markNonTerminalStaleByPostId(
        postId,
        QnaQuestionUpdateStateStatus.supersedableByNewPreparationStatuses(),
        QnaQuestionUpdateStateStatus.STALE,
        LocalDateTime.now(appClock));
  }

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> bindExecutionIntent(
      Long postId, Long updateVersion, String updateToken, String executionIntentPublicId) {
    int updated =
        repository.bindExecutionIntent(
            postId,
            updateVersion,
            updateToken,
            executionIntentPublicId,
            QnaQuestionUpdateStateStatus.bindableStatuses(),
            QnaQuestionUpdateStateStatus.PREPARATION_FAILED,
            QnaQuestionUpdateStateStatus.INTENT_BOUND,
            LocalDateTime.now(appClock));
    if (updated == 0) {
      return Optional.empty();
    }
    return findByExecutionIntentPublicIdForUpdate(executionIntentPublicId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<QnaQuestionUpdateState> markPreparationFailed(
      Long postId,
      Long updateVersion,
      String updateToken,
      String errorCode,
      String errorReason,
      boolean retryable) {
    int updated =
        repository.markPreparationFailed(
            postId,
            updateVersion,
            updateToken,
            errorCode,
            errorReason,
            retryable,
            QnaQuestionUpdateStateStatus.bindableStatuses(),
            QnaQuestionUpdateStateStatus.PREPARATION_FAILED,
            LocalDateTime.now(appClock));
    if (updated == 0) {
      return Optional.empty();
    }
    return findLatestByPostIdForUpdate(postId);
  }

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> markPreparationFailedByExecutionIntentPublicId(
      String executionIntentPublicId, String errorCode, String errorReason, boolean retryable) {
    int updated =
        repository.markPreparationFailedByExecutionIntentPublicId(
            executionIntentPublicId,
            errorCode,
            errorReason,
            retryable,
            QnaQuestionUpdateStateStatus.nonTerminalStatuses(),
            QnaQuestionUpdateStateStatus.PREPARATION_FAILED,
            LocalDateTime.now(appClock));
    if (updated == 0) {
      return Optional.empty();
    }
    return findByExecutionIntentPublicIdForUpdate(executionIntentPublicId);
  }

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> recordSyncFailure(
      String executionIntentPublicId, String errorCode, String errorReason) {
    int updated =
        repository.recordSyncFailure(
            executionIntentPublicId, errorCode, errorReason, LocalDateTime.now(appClock));
    if (updated == 0) {
      return Optional.empty();
    }
    return findByExecutionIntentPublicIdForUpdate(executionIntentPublicId);
  }

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> markConfirmed(String executionIntentPublicId) {
    int updated =
        repository.markConfirmed(
            executionIntentPublicId,
            QnaQuestionUpdateStateStatus.INTENT_BOUND,
            QnaQuestionUpdateStateStatus.CONFIRMED,
            LocalDateTime.now(appClock));
    if (updated == 0) {
      return Optional.empty();
    }
    return findByExecutionIntentPublicIdForUpdate(executionIntentPublicId);
  }

  @Override
  @Transactional
  public Optional<QnaQuestionUpdateState> markStaleByExecutionIntentPublicId(
      String executionIntentPublicId) {
    int updated =
        repository.markStaleByExecutionIntentPublicId(
            executionIntentPublicId,
            QnaQuestionUpdateStateStatus.nonTerminalStatuses(),
            QnaQuestionUpdateStateStatus.STALE,
            LocalDateTime.now(appClock));
    if (updated == 0) {
      return Optional.empty();
    }
    return findByExecutionIntentPublicIdForUpdate(executionIntentPublicId);
  }

  private QnaQuestionUpdateStateEntity toEntity(QnaQuestionUpdateState state) {
    return QnaQuestionUpdateStateEntity.builder()
        .id(state.getId())
        .postId(state.getPostId())
        .requesterUserId(state.getRequesterUserId())
        .updateVersion(state.getUpdateVersion())
        .updateToken(state.getUpdateToken())
        .expectedQuestionHash(state.getExpectedQuestionHash())
        .executionIntentPublicId(state.getExecutionIntentPublicId())
        .status(state.getStatus())
        .preparationRetryable(state.isPreparationRetryable())
        .lastErrorCode(state.getLastErrorCode())
        .lastErrorReason(state.getLastErrorReason())
        .createdAt(state.getCreatedAt())
        .updatedAt(state.getUpdatedAt())
        .build();
  }

  private QnaQuestionUpdateState toDomain(QnaQuestionUpdateStateEntity entity) {
    return QnaQuestionUpdateState.builder()
        .id(entity.getId())
        .postId(entity.getPostId())
        .requesterUserId(entity.getRequesterUserId())
        .updateVersion(entity.getUpdateVersion())
        .updateToken(entity.getUpdateToken())
        .expectedQuestionHash(entity.getExpectedQuestionHash())
        .executionIntentPublicId(entity.getExecutionIntentPublicId())
        .status(entity.getStatus())
        .preparationRetryable(entity.isPreparationRetryable())
        .lastErrorCode(entity.getLastErrorCode())
        .lastErrorReason(entity.getLastErrorReason())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
