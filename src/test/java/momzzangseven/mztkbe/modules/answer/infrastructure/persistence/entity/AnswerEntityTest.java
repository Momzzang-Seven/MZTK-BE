package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerEntity")
class AnswerEntityTest {

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("builder stores all given fields")
    void builder_storesFields() {
      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("answer content")
              .isAccepted(true)
              .build();

      assertThat(entity.getId()).isEqualTo(1L);
      assertThat(entity.getPostId()).isEqualTo(10L);
      assertThat(entity.getUserId()).isEqualTo(20L);
      assertThat(entity.getContent()).isEqualTo("answer content");
      assertThat(entity.getIsAccepted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("builder initializes default values when nullable fields are omitted")
    void builder_initializesDefaults_whenNullableFieldsAreNull() {
      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("answer content")
              .isAccepted(null)
              .build();

      assertThat(entity.getIsAccepted()).isFalse();
    }
  }
}
