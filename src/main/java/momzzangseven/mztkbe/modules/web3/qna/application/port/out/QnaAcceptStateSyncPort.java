package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface QnaAcceptStateSyncPort {

  void beginPendingAccept(Long postId, Long answerId);

  void confirmAccepted(Long postId, Long answerId);

  void rollbackPendingAccept(Long postId, Long answerId);
}
