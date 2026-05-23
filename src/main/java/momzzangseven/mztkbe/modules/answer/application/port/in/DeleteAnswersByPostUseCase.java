package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;

public interface DeleteAnswersByPostUseCase {

  void deleteByPostId(Long postId);

  void deleteByPostId(Long postId, List<Long> answerIds);
}
