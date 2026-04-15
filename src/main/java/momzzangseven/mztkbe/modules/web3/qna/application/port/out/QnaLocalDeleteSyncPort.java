package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

/**
 * Applies local hard-delete side effects only after the corresponding on-chain delete confirmed.
 *
 * <p>This keeps local question/answer rows available while a delete intent is still awaiting
 * signature or on-chain settlement, which avoids irreversible local data loss on terminal intent
 * failure.
 */
public interface QnaLocalDeleteSyncPort {

  void confirmQuestionDeleted(Long postId);

  void confirmAnswerDeleted(Long answerId);
}
