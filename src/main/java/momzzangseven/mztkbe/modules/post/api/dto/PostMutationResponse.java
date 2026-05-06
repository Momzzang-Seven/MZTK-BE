package momzzangseven.mztkbe.modules.post.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;

/**
 * API response for shared post mutation endpoints.
 *
 * <p>Free-board mutations return {@code web3 = null}; question mutations populate it only when a
 * new escrow intent was prepared.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostMutationResponse(
    Long postId, QuestionWeb3WriteResponse web3, QuestionUpdateStatusResponse questionUpdate) {

  public PostMutationResponse(Long postId, QuestionWeb3WriteResponse web3) {
    this(postId, web3, null);
  }

  /** Maps application mutation result into the public post mutation response. */
  public static PostMutationResponse from(PostMutationResult result) {
    return new PostMutationResponse(
        result.postId(),
        QuestionWeb3WriteResponse.from(result.web3()),
        QuestionUpdateStatusResponse.from(result.questionUpdate()));
  }

  public record QuestionUpdateStatusResponse(String status, boolean retryable, String errorCode) {

    static QuestionUpdateStatusResponse from(PostMutationResult.QuestionUpdateStatus status) {
      if (status == null) {
        return null;
      }
      return new QuestionUpdateStatusResponse(
          status.status(), status.retryable(), status.errorCode());
    }
  }
}
