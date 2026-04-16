package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;

/**
 * Application result for question-post creation.
 *
 * <p>Preserves legacy XP fields while attaching nullable Web3 escrow write material reserved for
 * question-board posts.
 */
public record CreateQuestionPostResult(
    Long postId,
    boolean isXpGranted,
    Long grantedXp,
    String message,
    QuestionExecutionWriteView web3) {}
