package momzzangseven.mztkbe.modules.tag.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tag unit test")
class TagTest {

  @Test
  @DisplayName("create trims and lowercases name")
  void createNormalizesName() {
    Tag tag = Tag.create("  JaVa  ");

    assertThat(tag.getName()).isEqualTo("java");
    assertThat(tag.getId()).isNull();
  }

  @Test
  @DisplayName("builder keeps id and normalized name")
  void builderSetsId() {
    Tag tag = Tag.builder().id(5L).name(" Spring ").build();

    assertThat(tag.getId()).isEqualTo(5L);
    assertThat(tag.getName()).isEqualTo("spring");
  }

  @Test
  @DisplayName("blank or null name is rejected")
  void rejectsInvalidName() {
    assertThatThrownBy(() -> Tag.create(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("태그 이름은 필수입니다.");

    assertThatThrownBy(() -> Tag.create("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("태그 이름은 필수입니다.");
  }

  @Test
  @DisplayName("builder rejects blank name")
  void builderRejectsBlankName() {
    assertThatThrownBy(() -> Tag.builder().id(9L).name(" ").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("태그 이름은 필수입니다.");
  }
}
