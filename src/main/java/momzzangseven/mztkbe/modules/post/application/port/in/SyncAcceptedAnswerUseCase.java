package momzzangseven.mztkbe.modules.post.application.port.in;

public interface SyncAcceptedAnswerUseCase {

  void confirmAccepted(Long postId, Long answerId);

  void rollbackPendingAccept(Long postId, Long answerId);
}
