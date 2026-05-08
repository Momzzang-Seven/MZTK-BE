package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
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
  public Map<Long, Long> countAnswersByPostIds(List<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Map.of();
    }
    return answerJpaRepository.countAnswersByPostIds(postIds).stream()
        .collect(
            Collectors.toMap(
                AnswerJpaRepository.PostAnswerCount::getPostId,
                AnswerJpaRepository.PostAnswerCount::getAnswerCount));
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

  private AnswerEntity toEntity(Answer answer) {
    return AnswerEntity.builder()
        .id(answer.getId())
        .postId(answer.getPostId())
        .userId(answer.getUserId())
        .content(answer.getContent())
        .isAccepted(answer.getIsAccepted())
        .build();
  }

  private Answer toDomain(AnswerEntity entity) {
    return Answer.builder()
        .id(entity.getId())
        .postId(entity.getPostId())
        .userId(entity.getUserId())
        .content(entity.getContent())
        .isAccepted(entity.getIsAccepted())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
