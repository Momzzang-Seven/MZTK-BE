package momzzangseven.mztkbe.modules.answer.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerOwnPostException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAnswerOnSolvedPostException;

@Getter
public class Answer {

  private final Long id;
  private final Long postId;
  private final Long userId;
  private final String content;
  private final Boolean isAccepted;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  @Builder(toBuilder = true)
  private Answer(
      Long id,
      Long postId,
      Long userId,
      String content,
      Boolean isAccepted,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.isAccepted = isAccepted != null ? isAccepted : false;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Answer create(
      Long postId, Long postWriterId, boolean isPostSolved, Long answererId, String content) {

    Objects.requireNonNull(postWriterId, "postWriterId must not be null");
    Objects.requireNonNull(answererId, "answererId must not be null");

    if (isPostSolved) {
      throw new CannotAnswerSolvedPostException();
    }
    if (postWriterId.equals(answererId)) {
      throw new CannotAnswerOwnPostException();
    }
    if (content == null || content.isBlank()) {
      throw new AnswerInvalidInputException("Answer content must not be blank.");
    }

    return Answer.builder()
        .postId(postId)
        .userId(answererId)
        .content(content)
        .isAccepted(false)
        .build();
  }

  public void validateOwnership(Long currentUserId) {
    Objects.requireNonNull(currentUserId, "currentUserId must not be null");
    if (!this.userId.equals(currentUserId)) {
      throw new AnswerUnauthorizedException();
    }
  }

  public void validateDeletable(Long requesterId, boolean parentQuestionSolved) {
    validateOwnership(requesterId);
    if (parentQuestionSolved) {
      throw new CannotDeleteAnswerOnSolvedPostException();
    }
  }

  public Answer update(String content, Long requesterId, boolean parentQuestionSolved) {
    validateOwnership(requesterId);
    if (parentQuestionSolved) {
      throw new CannotUpdateAnswerOnSolvedPostException();
    }

    var builder = this.toBuilder();
    boolean isUpdated = false;

    if (content != null) {
      if (content.isBlank()) {
        throw new AnswerInvalidInputException("Updated content must not be blank.");
      }
      builder.content(content);
      isUpdated = true;
    }

    return isUpdated ? builder.build() : this;
  }

  public Answer accept() {
    if (this.isAccepted) {
      return this;
    }
    return this.toBuilder().isAccepted(true).updatedAt(LocalDateTime.now()).build();
  }
}
