package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaAnswerProjectionEntity;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionProjectionEntity;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaAnswerProjectionJpaRepository;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaQuestionProjectionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaProjectionPersistenceAdapterTest {

  @Mock private QnaQuestionProjectionJpaRepository qnaQuestionProjectionJpaRepository;
  @Mock private QnaAnswerProjectionJpaRepository qnaAnswerProjectionJpaRepository;

  @InjectMocks private QnaProjectionPersistenceAdapter adapter;

  private QnaQuestionProjection questionProjection;

  @BeforeEach
  void setUp() {
    questionProjection =
        QnaQuestionProjection.create(
                101L,
                7L,
                QnaEscrowIdCodec.questionId(101L),
                "0x1111111111111111111111111111111111111111",
                new BigInteger("50000000000000000000"),
                QnaContentHashFactory.hash("질문 본문"))
            .markAccepted(QnaEscrowIdCodec.answerId(201L));
  }

  @Test
  void saveQuestion_mapsAcceptedAnswerAndPaidOutState() {
    when(qnaQuestionProjectionJpaRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    QnaQuestionProjection saved = adapter.saveQuestion(questionProjection);

    ArgumentCaptor<QnaQuestionProjectionEntity> captor =
        ArgumentCaptor.forClass(QnaQuestionProjectionEntity.class);
    verify(qnaQuestionProjectionJpaRepository).save(captor.capture());
    assertThat(captor.getValue().getAcceptedAnswerId()).isEqualTo(QnaEscrowIdCodec.answerId(201L));
    assertThat(captor.getValue().getState()).isEqualTo(QnaQuestionState.PAID_OUT.code());
    assertThat(saved.getAcceptedAnswerId()).isEqualTo(QnaEscrowIdCodec.answerId(201L));
    assertThat(saved.getState()).isEqualTo(QnaQuestionState.PAID_OUT);
  }

  @Test
  void findAnswerByAnswerIdForUpdate_mapsEntityToDomain() {
    QnaAnswerProjectionEntity entity =
        QnaAnswerProjectionEntity.builder()
            .answerId(201L)
            .postId(101L)
            .questionId(QnaEscrowIdCodec.questionId(101L))
            .answerKey(QnaEscrowIdCodec.answerId(201L))
            .responderUserId(22L)
            .contentHash(QnaContentHashFactory.hash("답변 본문"))
            .build();
    when(qnaAnswerProjectionJpaRepository.findByAnswerIdForUpdate(201L))
        .thenReturn(Optional.of(entity));

    Optional<QnaAnswerProjection> result = adapter.findAnswerByAnswerIdForUpdate(201L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getAnswerId()).isEqualTo(201L);
    assertThat(result.orElseThrow().getAnswerKey()).isEqualTo(QnaEscrowIdCodec.answerId(201L));
    assertThat(result.orElseThrow().getContentHash())
        .isEqualTo(QnaContentHashFactory.hash("답변 본문"));
  }
}
