package momzzangseven.mztkbe.modules.tag.domain.model;

import java.util.Locale;
import lombok.Builder;
import lombok.Getter;

@Getter
public class Tag {
  private final Long id;
  private final String name;

  @Builder
  public Tag(Long id, String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("태그 이름은 필수입니다.");
    }
    this.id = id;
    this.name = name.trim().toLowerCase(Locale.ROOT);
  }

  public static Tag create(String name) {
    return Tag.builder().name(name).build();
  }
}
