package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;

public interface QnaProjectionPersistencePort {

  Optional<QnaQuestionProjection> findQuestionByPostIdForUpdate(Long postId);

  QnaQuestionProjection saveQuestion(QnaQuestionProjection questionProjection);

  Optional<QnaAnswerProjection> findAnswerByAnswerIdForUpdate(Long answerId);

  QnaAnswerProjection saveAnswer(QnaAnswerProjection answerProjection);

  void deleteAnswerByAnswerId(Long answerId);
}
