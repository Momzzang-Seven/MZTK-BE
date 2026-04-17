package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAutoAcceptCandidate;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaAnswerProjectionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class QnaAutoAcceptCandidatePersistenceAdapterTest {

  @Mock private QnaAnswerProjectionJpaRepository qnaAnswerProjectionJpaRepository;

  private QnaAutoAcceptCandidatePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new QnaAutoAcceptCandidatePersistenceAdapter(qnaAnswerProjectionJpaRepository);
  }

  @Test
  void claimNextCandidate_mapsRepositoryRowToDto() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 4, 17, 9, 30);
    LocalDateTime answerCreatedAt = LocalDateTime.of(2026, 4, 10, 10, 0);
    when(qnaAnswerProjectionJpaRepository.claimNextAutoAcceptCandidate(
            cutoff, QnaQuestionState.ANSWERED.code()))
        .thenReturn(
            Optional.of(
                new QnaAutoAcceptCandidateRowFixture(101L, 201L, 7L, 22L, answerCreatedAt)));

    Optional<QnaAutoAcceptCandidate> result = adapter.claimNextCandidate(cutoff);

    verify(qnaAnswerProjectionJpaRepository)
        .claimNextAutoAcceptCandidate(cutoff, QnaQuestionState.ANSWERED.code());
    assertThat(result).isPresent();
    assertThat(result.orElseThrow())
        .isEqualTo(new QnaAutoAcceptCandidate(101L, 201L, 7L, 22L, answerCreatedAt));
  }

  @Test
  void claimNextCandidate_requiresExistingTransaction() throws NoSuchMethodException {
    Method method =
        QnaAutoAcceptCandidatePersistenceAdapter.class.getMethod(
            "claimNextCandidate", LocalDateTime.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
  }

  private static final class QnaAutoAcceptCandidateRowFixture
      implements QnaAnswerProjectionJpaRepository.QnaAutoAcceptCandidateRow {

    private final Long postId;
    private final Long answerId;
    private final Long askerUserId;
    private final Long responderUserId;
    private final LocalDateTime answerCreatedAt;

    private QnaAutoAcceptCandidateRowFixture(
        Long postId,
        Long answerId,
        Long askerUserId,
        Long responderUserId,
        LocalDateTime answerCreatedAt) {
      this.postId = postId;
      this.answerId = answerId;
      this.askerUserId = askerUserId;
      this.responderUserId = responderUserId;
      this.answerCreatedAt = answerCreatedAt;
    }

    @Override
    public Long getPostId() {
      return postId;
    }

    @Override
    public Long getAnswerId() {
      return answerId;
    }

    @Override
    public Long getAskerUserId() {
      return askerUserId;
    }

    @Override
    public Long getResponderUserId() {
      return responderUserId;
    }

    @Override
    public LocalDateTime getAnswerCreatedAt() {
      return answerCreatedAt;
    }
  }
}
