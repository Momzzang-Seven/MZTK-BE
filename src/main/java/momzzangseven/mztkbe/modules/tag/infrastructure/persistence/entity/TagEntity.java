package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.tag.domain.model.Tag;

@Entity
@Getter
@Table(name = "tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String name;

  public TagEntity(String name) {
    this.name = name;
  }

  public static TagEntity from(Tag tag) {
    return new TagEntity(tag.getName());
  }

  public Tag toDomain() {
    return Tag.builder().id(this.id).name(this.name).build();
  }
}
