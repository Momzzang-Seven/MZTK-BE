package momzzangseven.mztkbe.modules.answer.api.dto;

import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;

/**
 * API response for answer update/delete endpoints.
 *
 * <p>{@code web3} is nullable so image-only or feature-off flows stay compatible without
 * fabricating escrow work that never happened.
 */
public record AnswerMutationResponse(
    Long postId,
    Long answerId,
    AnswerPublicationStatus publicationStatus,
    AnswerUpdateStatus pendingUpdateStatus,
    Long pendingUpdateVersion,
    AnswerWeb3WriteResponse web3) {

  /** Maps application mutation result into the public answer mutation response. */
  public static AnswerMutationResponse from(AnswerMutationResult result) {
    return new AnswerMutationResponse(
        result.postId(),
        result.answerId(),
        result.publicationStatus(),
        result.pendingUpdateStatus(),
        result.pendingUpdateVersion(),
        AnswerWeb3WriteResponse.from(result.web3()));
  }
}
