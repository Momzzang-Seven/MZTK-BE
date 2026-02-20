package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "post_tags",
    indexes = {@Index(name = "idx_post_tag_post_id", columnList = "postId")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long postId;

  @Column(nullable = false)
  private Long tagId;

  public PostTagEntity(Long postId, Long tagId) {
    this.postId = postId;
    this.tagId = tagId;
  }
}
