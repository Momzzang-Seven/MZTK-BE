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
    // 1. Domain -> Entity 변환
    AnswerEntity entity = AnswerEntity.fromDomain(answer);

    // 2. 답변 저장
    AnswerEntity savedEntity = answerJpaRepository.save(entity);

    // 3. Entity -> Domain 변환 후 반환
    return savedEntity.toDomain();
  }

  @Override
  public List<Answer> loadAnswersByPostId(Long postId) {
    return answerJpaRepository.findByPostIdOrderByIsAcceptedDescCreatedAtAsc(postId).stream()
        .map(AnswerEntity::toDomain) // Entity -> Domain 변환
        .toList();
  }

  @Override
  public Optional<Answer> loadAnswer(Long answerId) {
    return answerJpaRepository.findById(answerId).map(AnswerEntity::toDomain);
  }

  @Override
  public void deleteAnswer(Long answerId) {
    answerJpaRepository.deleteById(answerId);
  }
}
