package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;

public interface GetAnswerIdsByPostUseCase {

  List<Long> getAnswerIdsByPostId(Long postId);
}
