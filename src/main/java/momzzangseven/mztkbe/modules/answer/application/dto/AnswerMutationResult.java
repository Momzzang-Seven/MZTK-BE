package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;

/**
 * Shared mutation result for answer update/delete endpoints.
 *
 * <p>{@code web3} is populated only when answer content changes required a new escrow lifecycle
 * intent. Local-only mutations such as image-only updates return {@code null}.
 */
public record AnswerMutationResult(
    Long postId,
    Long answerId,
    AnswerPublicationStatus publicationStatus,
    AnswerUpdateStatus pendingUpdateStatus,
    Long pendingUpdateVersion,
    AnswerExecutionWriteView web3) {

  public AnswerMutationResult(
      Long postId,
      Long answerId,
      AnswerPublicationStatus publicationStatus,
      AnswerExecutionWriteView web3) {
    this(postId, answerId, publicationStatus, null, null, web3);
  }

  public AnswerMutationResult(Long postId, Long answerId, AnswerExecutionWriteView web3) {
    this(postId, answerId, AnswerPublicationStatus.VISIBLE, null, null, web3);
  }
}
