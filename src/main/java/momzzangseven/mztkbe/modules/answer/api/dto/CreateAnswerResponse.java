package momzzangseven.mztkbe.modules.answer.api.dto;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;

public record CreateAnswerResponse(
    Long postId,
    Long answerId,
    AnswerPublicationStatus publicationStatus,
    AnswerWeb3WriteResponse web3) {

  public static CreateAnswerResponse from(CreateAnswerResult result) {
    return new CreateAnswerResponse(
        result.postId(),
        result.answerId(),
        result.publicationStatus(),
        AnswerWeb3WriteResponse.from(result.web3()));
  }
}
