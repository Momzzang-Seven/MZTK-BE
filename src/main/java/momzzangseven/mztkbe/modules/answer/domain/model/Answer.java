package momzzangseven.mztkbe.modules.answer.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

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

  // [Create] 도메인 생성
  public static Answer create(
      Long postId,
      Long postWriterId,
      boolean isPostSolved,
      Long answererId,
      String content,
      List<String> imageUrls) {

    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Answer content must not be blank.");
    }

    if (isPostSolved) {
      throw new IllegalArgumentException("Cannot add an answer to an already solved post.");
    }

    if (postWriterId.equals(answererId)) {
      throw new IllegalArgumentException("Cannot answer your own post.");
    }

    return Answer.builder()
        .postId(postId)
        .userId(answererId)
        .content(content)
        .isAccepted(false)
        .imageUrls(imageUrls != null ? imageUrls : new ArrayList<>())
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  // [Validate] 소유권 검증
  public void validateOwnership(Long currentUserId) {
    if (!this.userId.equals(currentUserId)) {
      throw new IllegalArgumentException("Unauthorized access to this answer.");
    }
  }

  // [Validate] 삭제 가능 여부 검증
  public void validateDeletable(Long requesterId) {
    validateOwnership(requesterId);
    if (this.isAccepted) {
      throw new IllegalArgumentException("Cannot delete an accepted answer.");
    }
  }

  // [Update] 도메인 수정
  public Answer update(String content, List<String> imageUrls, Long requesterId) {
    validateOwnership(requesterId);

    if (this.isAccepted) {
      throw new IllegalArgumentException("Cannot update an accepted answer.");
    }

    var builder = this.toBuilder();
    boolean isUpdated = false;

    if (content != null) {
      if (content.isBlank())
        throw new IllegalArgumentException("Updated content must not be blank.");
      builder.content(content);
      isUpdated = true;
    }

    if (imageUrls != null) {
      builder.imageUrls(imageUrls);
      isUpdated = true;
    }

    if (isUpdated) {
      builder.updatedAt(LocalDateTime.now());
      return builder.build();
    }

    return this;
  }
}
