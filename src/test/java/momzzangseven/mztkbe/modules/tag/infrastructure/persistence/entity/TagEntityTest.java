package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TagEntity unit test")
class TagEntityTest {

  @Test
  @DisplayName("from converts domain tag to entity")
  void fromMapsDomainToEntity() {
    Tag tag = Tag.builder().id(1L).name(" Java ").build();

    TagEntity entity = TagEntity.from(tag);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getName()).isEqualTo("java");
  }

  @Test
  @DisplayName("toDomain normalizes name via domain builder")
  void toDomainNormalizesName() {
    TagEntity entity = new TagEntity(" Spring ");

    Tag tag = entity.toDomain();

    assertThat(tag.getId()).isNull();
    assertThat(tag.getName()).isEqualTo("spring");
  }
}
