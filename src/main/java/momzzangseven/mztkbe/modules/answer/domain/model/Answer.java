package momzzangseven.mztkbe.modules.answer.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerOwnPostException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAcceptedAnswerException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAcceptedAnswerException;

@Getter
@Builder(toBuilder = true)
public class Answer {

  private final Long id;
  private final Long postId;
  private final Long userId;
  private final String content;
  private final Boolean isAccepted;
  private final List<String> imageUrls;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  @Builder
  public Answer(
      Long id,
      Long postId,
      Long userId,
      String content,
      Boolean isAccepted,
      List<String> imageUrls,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.isAccepted = isAccepted != null ? isAccepted : false;
    this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Answer create(
      Long postId,
      Long postWriterId,
      boolean isPostSolved,
      Long answererId,
      String content,
      List<String> imageUrls) {

    if (content == null || content.isBlank()) {
      throw new AnswerInvalidInputException("Answer content must not be blank.");
    }

    if (isPostSolved) {
      throw new CannotAnswerSolvedPostException();
    }

    if (postWriterId.equals(answererId)) {
      throw new CannotAnswerOwnPostException();
    }

    return Answer.builder()
        .postId(postId)
        .userId(answererId)
        .content(content)
        .isAccepted(false)
        .imageUrls(imageUrls != null ? imageUrls : new ArrayList<>())
        .build();
  }

  public void validateOwnership(Long currentUserId) {
    if (!this.userId.equals(currentUserId)) {
      throw new AnswerUnauthorizedException();
    }
  }

  public void validateDeletable(Long requesterId) {
    validateOwnership(requesterId);
    if (this.isAccepted) {
      throw new CannotDeleteAcceptedAnswerException();
    }
  }

  public Answer update(String content, List<String> imageUrls, Long requesterId) {
    validateOwnership(requesterId);

    if (this.isAccepted) {
      throw new CannotUpdateAcceptedAnswerException();
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

    if (imageUrls != null) {
      builder.imageUrls(imageUrls);
      isUpdated = true;
    }

    return isUpdated ? builder.build() : this;
  }
}
