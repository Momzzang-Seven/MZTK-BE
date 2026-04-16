package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;

/**
 * API response for question-post creation.
 *
 * <p>Unlike free-post creation, this response may include nullable Web3 escrow write payload while
 * preserving the legacy XP grant fields expected by clients.
 */
public record CreateQuestionPostResponse(
    Long postId,
    boolean isXpGranted,
    Long grantedXp,
    String message,
    QuestionWeb3WriteResponse web3) {

  /** Maps application-layer question create result into the public response contract. */
  public static CreateQuestionPostResponse from(CreateQuestionPostResult result) {
    return new CreateQuestionPostResponse(
        result.postId(),
        result.isXpGranted(),
        result.grantedXp(),
        result.message(),
        QuestionWeb3WriteResponse.from(result.web3()));
  }
}
