package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;

public interface CreateAnswerUseCase {

  Long createAnswer(CreateAnswerCommand command);

  record CreateAnswerCommand(Long postId, Long userId, String content, List<String> imageUrls) {}
}
