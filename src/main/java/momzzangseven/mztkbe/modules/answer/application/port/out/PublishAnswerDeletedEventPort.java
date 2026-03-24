package momzzangseven.mztkbe.modules.answer.application.port.out;

import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;

public interface PublishAnswerDeletedEventPort {
  void publish(AnswerDeletedEvent event);
}
