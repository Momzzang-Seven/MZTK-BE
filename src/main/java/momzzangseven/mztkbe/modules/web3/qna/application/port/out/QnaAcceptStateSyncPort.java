package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface QnaAcceptStateSyncPort {

  void confirmAccepted(Long postId, Long answerId);

  void rollbackPendingAccept(Long postId, Long answerId);
}
