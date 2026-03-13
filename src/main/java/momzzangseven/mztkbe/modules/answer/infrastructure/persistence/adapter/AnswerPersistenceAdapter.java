package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPersistenceAdapter implements SaveAnswerPort, LoadAnswerPort, DeleteAnswerPort {

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
  public void deleteAnswer(Long answerId) {
    answerJpaRepository.deleteById(answerId);
  }

  @Override
  public void deleteAnswersByPostId(Long postId) {
    answerJpaRepository.deleteAllByPostId(postId);
  }

  private AnswerEntity toEntity(Answer answer) {
    return AnswerEntity.builder()
        .id(answer.getId())
        .postId(answer.getPostId())
        .userId(answer.getUserId())
        .content(answer.getContent())
        .isAccepted(answer.getIsAccepted())
        .imageUrls(answer.getImageUrls())
        .build();
  }

  private Answer toDomain(AnswerEntity entity) {
    return Answer.builder()
        .id(entity.getId())
        .postId(entity.getPostId())
        .userId(entity.getUserId())
        .content(entity.getContent())
        .isAccepted(entity.getIsAccepted())
        .imageUrls(entity.getImageUrls())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
