package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
              .imageUrls(List.of("https://image"))
              .build();

      assertThat(entity.getId()).isEqualTo(1L);
      assertThat(entity.getPostId()).isEqualTo(10L);
      assertThat(entity.getUserId()).isEqualTo(20L);
      assertThat(entity.getContent()).isEqualTo("answer content");
      assertThat(entity.getIsAccepted()).isTrue();
      assertThat(entity.getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("builder makes a mutable defensive copy of imageUrls")
    void builder_makesMutableDefensiveCopyOfImageUrls() {
      List<String> imageUrls = new ArrayList<>(List.of("https://image"));

      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("answer content")
              .imageUrls(imageUrls)
              .build();

      imageUrls.add("https://mutated");

      assertThat(entity.getImageUrls()).containsExactly("https://image");
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
              .imageUrls(null)
              .build();

      assertThat(entity.getIsAccepted()).isFalse();
      assertThat(entity.getImageUrls()).isEmpty();
    }
  }
}
