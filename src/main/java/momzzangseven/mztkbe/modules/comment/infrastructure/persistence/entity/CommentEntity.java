package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long postId;

  @Column(nullable = false)
  private Long writerId;

  @Column(nullable = false, length = 1000)
  private String content;

  @Column(nullable = false)
  private boolean isDeleted;

  // [Self-Referencing] 대댓글 구조
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private CommentEntity parent;

  @OneToMany(mappedBy = "parent", orphanRemoval = false)
  private List<CommentEntity> children = new ArrayList<>();

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  // [Factory Method] 도메인 모델 -> 엔티티 변환 생성자
  public static CommentEntity from(Comment comment, CommentEntity parentEntity) {
    CommentEntity entity = new CommentEntity();
    entity.id = comment.getId();
    entity.postId = comment.getPostId();
    entity.writerId = comment.getWriterId();
    entity.content = comment.getContent();
    entity.isDeleted = comment.isDeleted();
    entity.parent = parentEntity;
    entity.createdAt = comment.getCreatedAt();
    entity.updatedAt = comment.getUpdatedAt();
    return entity;
  }

  // [Method] 엔티티 -> 도메인 모델 변환
  public Comment toDomain() {
    return Comment.builder()
        .id(this.id)
        .postId(this.postId)
        .writerId(this.writerId)
        .parentId(this.parent != null ? this.parent.getId() : null)
        .content(this.content)
        .isDeleted(this.isDeleted)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }
}
