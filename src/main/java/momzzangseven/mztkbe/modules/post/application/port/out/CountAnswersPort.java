package momzzangseven.mztkbe.modules.post.application.port.out;

public interface CountAnswersPort {

  long countAnswers(Long postId);

  long countPublicVisibleAnswers(Long postId);

  long countOnchainBlockingAnswers(Long postId);

  boolean existsPreparingOrPendingCreateByPostId(Long postId);
}
