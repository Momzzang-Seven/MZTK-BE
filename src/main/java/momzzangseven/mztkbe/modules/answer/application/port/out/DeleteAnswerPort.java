package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;

public interface DeleteAnswerPort {
  void deleteAnswer(Long answerId);

  void deleteAnswersByPostId(Long postId);

  void deleteAnswersByIds(List<Long> answerIds);
}
