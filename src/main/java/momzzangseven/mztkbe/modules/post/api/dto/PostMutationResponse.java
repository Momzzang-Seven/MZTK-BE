package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;

/**
 * API response for shared post mutation endpoints.
 *
 * <p>Free-board mutations return {@code web3 = null}; question mutations populate it only when a
 * new escrow intent was prepared.
 */
public record PostMutationResponse(Long postId, QuestionWeb3WriteResponse web3) {

  /** Maps application mutation result into the public post mutation response. */
  public static PostMutationResponse from(PostMutationResult result) {
    return new PostMutationResponse(result.postId(), QuestionWeb3WriteResponse.from(result.web3()));
  }
}
