package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record GetQnaQuestionPublicationEvidenceQuery(Long postId, Long requesterUserId) {

  public GetQnaQuestionPublicationEvidenceQuery {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive.");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive.");
    }
  }
}
