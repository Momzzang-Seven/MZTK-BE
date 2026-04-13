package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaAnswerProjectionEntity;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionProjectionEntity;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaAnswerProjectionJpaRepository;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaQuestionProjectionJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaProjectionPersistenceAdapter implements QnaProjectionPersistencePort {

  private final QnaQuestionProjectionJpaRepository qnaQuestionProjectionJpaRepository;
  private final QnaAnswerProjectionJpaRepository qnaAnswerProjectionJpaRepository;

  @Override
  public Optional<QnaQuestionProjection> findQuestionByPostIdForUpdate(Long postId) {
    return qnaQuestionProjectionJpaRepository.findByPostIdForUpdate(postId).map(this::toDomain);
  }

  @Override
  public QnaQuestionProjection saveQuestion(QnaQuestionProjection questionProjection) {
    return toDomain(qnaQuestionProjectionJpaRepository.save(toEntity(questionProjection)));
  }

  @Override
  public Optional<QnaAnswerProjection> findAnswerByAnswerIdForUpdate(Long answerId) {
    return qnaAnswerProjectionJpaRepository.findByAnswerIdForUpdate(answerId).map(this::toDomain);
  }

  @Override
  public QnaAnswerProjection saveAnswer(QnaAnswerProjection answerProjection) {
    return toDomain(qnaAnswerProjectionJpaRepository.save(toEntity(answerProjection)));
  }

  @Override
  public void deleteAnswerByAnswerId(Long answerId) {
    qnaAnswerProjectionJpaRepository.deleteById(answerId);
  }

  private QnaQuestionProjectionEntity toEntity(QnaQuestionProjection questionProjection) {
    return QnaQuestionProjectionEntity.builder()
        .postId(questionProjection.getPostId())
        .questionId(questionProjection.getQuestionId())
        .askerUserId(questionProjection.getAskerUserId())
        .tokenAddress(questionProjection.getTokenAddress())
        .rewardAmountWei(questionProjection.getRewardAmountWei())
        .questionHash(questionProjection.getQuestionHash())
        .acceptedAnswerId(questionProjection.getAcceptedAnswerId())
        .answerCount(questionProjection.getAnswerCount())
        .state(questionProjection.getState().code())
        .build();
  }

  private QnaQuestionProjection toDomain(QnaQuestionProjectionEntity entity) {
    return QnaQuestionProjection.builder()
        .postId(entity.getPostId())
        .questionId(entity.getQuestionId())
        .askerUserId(entity.getAskerUserId())
        .tokenAddress(entity.getTokenAddress())
        .rewardAmountWei(entity.getRewardAmountWei())
        .questionHash(entity.getQuestionHash())
        .acceptedAnswerId(entity.getAcceptedAnswerId())
        .answerCount(entity.getAnswerCount())
        .state(QnaQuestionState.fromCode(entity.getState()))
        .build();
  }

  private QnaAnswerProjectionEntity toEntity(QnaAnswerProjection answerProjection) {
    return QnaAnswerProjectionEntity.builder()
        .answerId(answerProjection.getAnswerId())
        .postId(answerProjection.getPostId())
        .questionId(answerProjection.getQuestionId())
        .answerKey(answerProjection.getAnswerKey())
        .responderUserId(answerProjection.getResponderUserId())
        .contentHash(answerProjection.getContentHash())
        .accepted(answerProjection.isAccepted())
        .build();
  }

  private QnaAnswerProjection toDomain(QnaAnswerProjectionEntity entity) {
    return QnaAnswerProjection.builder()
        .answerId(entity.getAnswerId())
        .postId(entity.getPostId())
        .questionId(entity.getQuestionId())
        .answerKey(entity.getAnswerKey())
        .responderUserId(entity.getResponderUserId())
        .contentHash(entity.getContentHash())
        .accepted(entity.isAccepted())
        .build();
  }
}
