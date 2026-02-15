package momzzangseven.mztkbe.modules.tag.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Tag {
  private final Long id;
  private final String name;

  @Builder
  public Tag(Long id, String name) {
    this.id = id;
    this.name = name;
  }

  public static Tag create(String name) {
    return Tag.builder().name(name).build();
  }
}
