package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.tag.domain.model.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
  @DisplayName("toDomain maps id and normalizes name via domain builder")
  void toDomainMapsEntityToDomain() {
    TagEntity entity = new TagEntity(" Spring ");
    ReflectionTestUtils.setField(entity, "id", 22L);

    Tag tag = entity.toDomain();

    assertThat(tag.getId()).isEqualTo(22L);
    assertThat(tag.getName()).isEqualTo("spring");
  }
}
