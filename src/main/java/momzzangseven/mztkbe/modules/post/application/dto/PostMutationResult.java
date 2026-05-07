package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;

/**
 * Shared mutation result for post update/delete endpoints.
 *
 * <p>{@code web3} is populated only when the mutation produced a new on-chain question lifecycle
 * intent. Free posts and question metadata-only updates return {@code null}.
 */
public record PostMutationResult(
    Long postId, QuestionExecutionWriteView web3, QuestionUpdateStatus questionUpdate) {

  public PostMutationResult(Long postId, QuestionExecutionWriteView web3) {
    this(postId, web3, null);
  }

  public static PostMutationResult questionUpdatePreparationFailed(Long postId) {
    return questionUpdatePreparationFailed(postId, null, true);
  }

  public static PostMutationResult questionUpdatePreparationFailed(
      Long postId, String errorCode, boolean retryable) {
    return new PostMutationResult(
        postId, null, new QuestionUpdateStatus("PREPARATION_FAILED", retryable, errorCode));
  }

  public record QuestionUpdateStatus(String status, boolean retryable, String errorCode) {}
}
