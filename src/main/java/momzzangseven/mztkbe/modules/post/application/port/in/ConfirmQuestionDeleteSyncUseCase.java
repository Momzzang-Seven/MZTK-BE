package momzzangseven.mztkbe.modules.post.application.port.in;

/**
 * Confirms a previously prepared question delete by applying the local hard-delete side effects.
 */
public interface ConfirmQuestionDeleteSyncUseCase {

  void confirmDeleted(Long postId);
}
