package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for the {@code class_tags} join table.
 *
 * <p>Maps a marketplace class to a tag using the same pattern as {@code post_tags}. The tag
 * module's global {@code tags} table is the canonical store for tag names; this entity only holds
 * the (classId, tagId) association.
 *
 * <p>Indexed on {@code classId} for efficient lookup of tags by class.
 */
@Entity
@Getter
@Table(
    name = "class_tags",
    indexes = {@Index(name = "idx_class_tag_class_id", columnList = "class_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassTagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long classId;

  @Column(nullable = false)
  private Long tagId;

  public ClassTagEntity(Long classId, Long tagId) {
    this.classId = classId;
    this.tagId = tagId;
  }
}
