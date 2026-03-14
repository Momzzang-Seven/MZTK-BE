package momzzangseven.mztkbe.modules.answer.application.port.out;

import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public interface SaveAnswerPort {
  Answer saveAnswer(Answer answer);
}
