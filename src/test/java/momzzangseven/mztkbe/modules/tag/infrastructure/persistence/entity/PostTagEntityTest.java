package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostTagEntity unit test")
class PostTagEntityTest {

  @Test
  @DisplayName("constructor stores post and tag ids")
  void constructorStoresIds() {
    PostTagEntity entity = new PostTagEntity(5L, 9L);

    assertThat(entity.getPostId()).isEqualTo(5L);
    assertThat(entity.getTagId()).isEqualTo(9L);
    assertThat(entity.getId()).isNull();
  }
}
