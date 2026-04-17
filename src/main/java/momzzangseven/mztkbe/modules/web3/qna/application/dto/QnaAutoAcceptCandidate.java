package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaAutoAcceptCandidate(
    Long postId,
    Long answerId,
    Long requesterUserId,
    Long answerWriterUserId,
    LocalDateTime answerCreatedAt) {

  public QnaAutoAcceptCandidate {
    validatePositive(postId, "postId");
    validatePositive(answerId, "answerId");
    validatePositive(requesterUserId, "requesterUserId");
    validatePositive(answerWriterUserId, "answerWriterUserId");
    if (answerCreatedAt == null) {
      throw new Web3InvalidInputException("answerCreatedAt is required");
    }
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }
}
