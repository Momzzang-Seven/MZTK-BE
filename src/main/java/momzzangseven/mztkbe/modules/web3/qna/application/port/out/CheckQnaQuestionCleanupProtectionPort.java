package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface CheckQnaQuestionCleanupProtectionPort {

  boolean hasFailedQuestionCreate(Long postId, Long requesterUserId);
}
