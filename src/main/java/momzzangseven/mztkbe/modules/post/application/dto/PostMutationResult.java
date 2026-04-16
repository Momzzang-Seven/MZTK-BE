package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;

/**
 * Shared mutation result for post update/delete endpoints.
 *
 * <p>{@code web3} is populated only when the mutation produced a new on-chain question lifecycle
 * intent. Free posts and question metadata-only updates return {@code null}.
 */
public record PostMutationResult(Long postId, QuestionExecutionWriteView web3) {}
