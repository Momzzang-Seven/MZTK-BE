package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

public interface CheckQnaAnswerCreateIntentConflictUseCase {

  boolean hasActiveAnswerCreateIntent(Long postId);
}
