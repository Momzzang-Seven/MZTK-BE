package momzzangseven.mztkbe.modules.answer.api.dto;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;

public record CreateAnswerResponse(Long postId, Long answerId, AnswerWeb3WriteResponse web3) {

  public static CreateAnswerResponse from(CreateAnswerResult result) {
    return new CreateAnswerResponse(
        result.postId(), result.answerId(), AnswerWeb3WriteResponse.from(result.web3()));
  }
}
