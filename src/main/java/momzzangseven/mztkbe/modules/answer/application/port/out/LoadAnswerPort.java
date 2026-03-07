package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public interface LoadAnswerPort {
  // 특정 게시글의 답변 목록 조회
  List<Answer> loadAnswersByPostId(Long postId);

  // 답변 수정을 위한 특정 답변 단건 조회
  Optional<Answer> loadAnswer(Long answerId);
}
