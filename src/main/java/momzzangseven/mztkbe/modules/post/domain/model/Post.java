package momzzangseven.mztkbe.modules.post.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.post.PostAlreadySolvedException;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostUnauthorizedException;

@Getter
@Builder(toBuilder = true)
public class Post {
  private final Long id;
  private final Long userId;
  private final PostType type;
  private final String title;
  private final String content;
  private final Long reward;
  private final Long acceptedAnswerId;
  private final PostStatus status;
  private final List<String> tags;

  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  @Builder
  public Post(
      Long id,
      Long userId,
      PostType type,
      String title,
      String content,
      Long reward,
      Long acceptedAnswerId,
      PostStatus status,
      List<String> tags,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.reward = reward;
    this.acceptedAnswerId = acceptedAnswerId;
    this.status = validateAndResolveStatus(type, status, acceptedAnswerId);
    this.tags = tags != null ? tags : new ArrayList<>();
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Post create(
      Long userId, PostType type, String title, String content, Long reward, List<String> tags) {

    if (userId == null) throw new IllegalArgumentException("Author ID is required.");
    if (type == null) throw new IllegalArgumentException("Post type is required.");
    if (content == null || content.isBlank())
      throw new IllegalArgumentException("Content must not be blank.");

    if (type == PostType.QUESTION) {
      if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("Title is required for question posts.");
      }
      if (reward == null || reward <= 0) {
        throw new IllegalArgumentException("Reward must be positive for question posts.");
      }
    } else if (type == PostType.FREE) {
      reward = 0L;
    }

    return Post.builder()
        .userId(userId)
        .type(type)
        .title(title)
        .content(content)
        .reward(reward)
        .acceptedAnswerId(null)
        .status(PostStatus.OPEN)
        .tags(tags != null ? tags : new ArrayList<>())
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  /**
   * Compatibility getter for legacy consumers.
   *
   * <p>`status` is the source of truth; this boolean is derived only.
   */
  public Boolean getIsSolved() {
    return isResolved();
  }

  public void validateOwnership(Long currentUserId) {
    if (!this.userId.equals(currentUserId)) {
      throw new PostUnauthorizedException();
    }
  }

  public void validateDeletable() {
    if (PostType.QUESTION.equals(this.type) && isResolved()) {
      throw new PostInvalidInputException("A solved question post cannot be deleted.");
    }
  }

  public Post update(String title, String content, List<String> tags) {
    if (PostType.QUESTION.equals(this.type) && isResolved()) {
      throw new PostInvalidInputException("A solved question post cannot be edited.");
    }

    var builder = this.toBuilder();
    boolean isUpdated = false;

    if (title != null) {
      if (title.isBlank()) throw new IllegalArgumentException("Title cannot be blank.");
      builder.title(title);
      isUpdated = true;
    }

    if (content != null) {
      if (content.isBlank()) throw new IllegalArgumentException("Content cannot be blank.");
      builder.content(content);
      isUpdated = true;
    }

    if (tags != null) {
      builder.tags(tags);
      isUpdated = true;
    }

    if (isUpdated) {
      builder.updatedAt(LocalDateTime.now());
      return builder.build();
    }

    return this;
  }

  public Post withTags(List<String> tags) {
    return this.toBuilder().tags(tags != null ? tags : new ArrayList<>()).build();
  }

  public boolean isResolved() {
    return this.status == PostStatus.RESOLVED;
  }

  public Post accept(Long answerId) {
    if (type != PostType.QUESTION) {
      throw new PostInvalidInputException("Only question posts can accept an answer.");
    }
    if (answerId == null || answerId <= 0) {
      throw new PostInvalidInputException("answerId must be positive.");
    }
    if (isResolved()) {
      throw new PostAlreadySolvedException();
    }

    return this.toBuilder()
        .acceptedAnswerId(answerId)
        .status(PostStatus.RESOLVED)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private static PostStatus validateAndResolveStatus(
      PostType type, PostStatus status, Long acceptedAnswerId) {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(status, "status must not be null");

    if (type == PostType.FREE) {
      if (status != PostStatus.OPEN) {
        throw new IllegalArgumentException("Free posts must remain OPEN.");
      }
      if (acceptedAnswerId != null) {
        throw new IllegalArgumentException("Free posts cannot have acceptedAnswerId.");
      }
      return status;
    }

    if (status == PostStatus.OPEN && acceptedAnswerId != null) {
      throw new IllegalArgumentException("Open question posts cannot have acceptedAnswerId.");
    }
    if (status == PostStatus.RESOLVED && acceptedAnswerId == null) {
      throw new IllegalArgumentException("Resolved question posts require acceptedAnswerId.");
    }
    return status;
  }
}
