package momzzangseven.mztkbe.modules.answer.api.dto;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;

public record CreateAnswerResponse(Long answerId) {

  public static CreateAnswerResponse from(CreateAnswerResult result) {
    return new CreateAnswerResponse(result.answerId());
  }
}
