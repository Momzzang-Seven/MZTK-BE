package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.List;

public interface UpdateAnswerUseCase {

  void updateAnswer(UpdateAnswerCommand command);

  record UpdateAnswerCommand(Long answerId, Long userId, String content, List<String> imageUrls) {}
}
