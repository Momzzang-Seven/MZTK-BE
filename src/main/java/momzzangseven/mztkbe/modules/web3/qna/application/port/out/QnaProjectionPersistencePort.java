package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;

public interface QnaProjectionPersistencePort {

  Optional<QnaQuestionProjection> findQuestionByPostId(Long postId);

  Optional<QnaQuestionProjection> findQuestionByPostIdForUpdate(Long postId);

  QnaQuestionProjection saveQuestion(QnaQuestionProjection questionProjection);

  Optional<QnaAnswerProjection> findAnswerByAnswerId(Long answerId);

  Optional<QnaAnswerProjection> findAnswerByAnswerIdForUpdate(Long answerId);

  List<QnaAnswerProjection> findAnswersByPostId(Long postId);

  List<QnaAnswerProjection> findAnswersByPostIdForUpdate(Long postId);

  QnaAnswerProjection saveAnswer(QnaAnswerProjection answerProjection);

  void deleteAnswerByAnswerId(Long answerId);
}
