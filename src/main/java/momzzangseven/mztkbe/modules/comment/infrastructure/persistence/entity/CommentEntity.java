package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;

@Entity
@Table(name = "comments")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자
public class CommentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private CommentTargetType targetType = CommentTargetType.POST;

  @Column private Long postId;

  @Column private Long answerId;

  @Column(nullable = false)
  private Long writerId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false)
  private boolean isDeleted;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private CommentEntity parent;

  @Builder.Default
  @OneToMany(mappedBy = "parent", orphanRemoval = false)
  private List<CommentEntity> children = new ArrayList<>();

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  // [Factory Method] 도메인 모델 -> 엔티티 변환
  public static CommentEntity from(Comment comment, CommentEntity parentEntity) {
    return CommentEntity.builder()
        .id(comment.getId())
        .targetType(comment.getTargetType())
        .postId(comment.getPostId())
        .answerId(comment.getAnswerId())
        .writerId(comment.getWriterId())
        .content(comment.getContent())
        .isDeleted(comment.isDeleted())
        .parent(parentEntity)
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .build();
  }

  // [Method] 엔티티 -> 도메인 모델 변환
  public Comment toDomain() {
    return Comment.builder()
        .id(this.id)
        .targetType(this.targetType == null ? CommentTargetType.POST : this.targetType)
        .postId(this.postId)
        .answerId(this.answerId)
        .writerId(this.writerId)
        .parentId(this.parent != null ? this.parent.getId() : null)
        .content(this.content)
        .isDeleted(this.isDeleted)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }
}
