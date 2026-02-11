package momzzangseven.mztkbe.modules.comment.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
public class Comment {
  private final Long id;
  private final Long postId;
  private final Long writerId;
  private final Long parentId;

  private String content;
  private boolean isDeleted;

  private final LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @Builder
  public Comment(
      Long id,
      Long postId,
      Long writerId,
      Long parentId,
      String content,
      boolean isDeleted,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.postId = postId;
    this.writerId = writerId;
    this.parentId = parentId;
    this.content = content;
    this.isDeleted = isDeleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  // [Factory Method] 댓글 생성
  public static Comment create(Long postId, Long writerId, Long parentId, String content) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("댓글 내용은 필수입니다.");
    }

    return Comment.builder()
        .postId(postId)
        .writerId(writerId)
        .parentId(parentId)
        .content(content)
        .isDeleted(false)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  // [Business Logic] 내용 수정
  public void updateContent(String newContent) {
    if (this.isDeleted) {
      throw new IllegalStateException("삭제된 댓글은 수정할 수 없습니다.");
    }
    if (newContent == null || newContent.isBlank()) {
      throw new IllegalArgumentException("수정할 내용은 비워둘 수 없습니다.");
    }
    this.content = newContent;
    this.updatedAt = LocalDateTime.now();
  }

  // [Business Logic] 댓글 삭제
  public void delete() {
    this.isDeleted = true;
    this.content = "삭제된 댓글입니다.";
    this.updatedAt = LocalDateTime.now();
  }
}
