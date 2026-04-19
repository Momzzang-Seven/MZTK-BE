package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ClaimNextQnaAutoAcceptCandidatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAutoAcceptCandidate;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository.QnaAnswerProjectionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class QnaAutoAcceptCandidatePersistenceAdapter
    implements ClaimNextQnaAutoAcceptCandidatePort {

  private final QnaAnswerProjectionJpaRepository qnaAnswerProjectionJpaRepository;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<QnaAutoAcceptCandidate> claimNextCandidate(LocalDateTime cutoff) {
    return qnaAnswerProjectionJpaRepository
        .claimNextAutoAcceptCandidate(cutoff, QnaQuestionState.ANSWERED.code())
        .map(this::toDto);
  }

  private QnaAutoAcceptCandidate toDto(
      QnaAnswerProjectionJpaRepository.QnaAutoAcceptCandidateRow row) {
    return new QnaAutoAcceptCandidate(
        row.getPostId(),
        row.getAnswerId(),
        row.getAskerUserId(),
        row.getResponderUserId(),
        row.getAnswerCreatedAt());
  }
}
