package momzzangseven.mztkbe.modules.answer.application.port.in;

public interface CountAnswersUseCase {

  long countAnswers(Long postId);

  long countPublicVisibleAnswers(Long postId);

  boolean existsPreparingOrPendingCreateByPostId(Long postId);
}
