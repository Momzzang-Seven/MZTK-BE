package momzzangseven.mztkbe.modules.answer.application.port.in;

public interface DeleteAnswerUseCase {

  void deleteAnswer(DeleteAnswerCommand command);

  record DeleteAnswerCommand(Long answerId, Long userId) {}
}
