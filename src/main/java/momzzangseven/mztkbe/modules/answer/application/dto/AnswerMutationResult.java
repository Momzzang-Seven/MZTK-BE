package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;

/**
 * Shared mutation result for answer update/delete endpoints.
 *
 * <p>{@code web3} is populated only when answer content changes required a new escrow lifecycle
 * intent. Local-only mutations such as image-only updates return {@code null}.
 */
public record AnswerMutationResult(Long postId, Long answerId, AnswerExecutionWriteView web3) {}
