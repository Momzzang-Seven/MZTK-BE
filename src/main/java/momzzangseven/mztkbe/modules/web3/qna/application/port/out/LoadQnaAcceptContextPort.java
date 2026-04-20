package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadQnaAcceptContextPort {

  /**
   * Loads the local question/answer accept context under the caller's active transaction while
   * holding row-level locks in a stable order.
   */
  Optional<QnaAcceptContext> loadForUpdate(Long postId, Long answerId);

  record QnaAcceptContext(
      Long postId,
      Long requesterUserId,
      Long answerId,
      Long answerWriterUserId,
      String questionContent,
      String answerContent) {

    public QnaAcceptContext {
      validatePositive(postId, "postId");
      validatePositive(requesterUserId, "requesterUserId");
      validatePositive(answerId, "answerId");
      validatePositive(answerWriterUserId, "answerWriterUserId");
      validateContent(questionContent, "questionContent");
      validateContent(answerContent, "answerContent");
    }

    private static void validatePositive(Long value, String fieldName) {
      if (value == null || value <= 0) {
        throw new Web3InvalidInputException(fieldName + " must be positive");
      }
    }

    private static void validateContent(String value, String fieldName) {
      if (value == null || value.isBlank()) {
        throw new Web3InvalidInputException(fieldName + " must not be blank");
      }
    }
  }
}
