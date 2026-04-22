package momzzangseven.mztkbe.modules.post.application.port.in;

public interface SyncQuestionAdminRefundStateUseCase {

  void beginPendingRefund(Long postId);

  void rollbackPendingRefund(Long postId);
}
