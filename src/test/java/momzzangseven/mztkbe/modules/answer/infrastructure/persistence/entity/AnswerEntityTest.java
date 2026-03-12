// src/test/java/momzzangseven/mztkbe/modules/answer/infrastructure/persistence/entity/AnswerEntityTest.java
package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerEntity 단위 테스트")
class AnswerEntityTest {

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("빌더는 전달된 필드를 그대로 보관한다")
    void builder_storesFields() {
      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("답변 내용")
              .isAccepted(true)
              .imageUrls(List.of("https://image"))
              .build();

      assertThat(entity.getId()).isEqualTo(1L);
      assertThat(entity.getPostId()).isEqualTo(10L);
      assertThat(entity.getUserId()).isEqualTo(20L);
      assertThat(entity.getContent()).isEqualTo("답변 내용");
      assertThat(entity.getIsAccepted()).isTrue();
      assertThat(entity.getImageUrls()).containsExactly("https://image");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("이미지 목록이 null이면 빈 리스트로 초기화한다")
    void builder_initializesEmptyList_whenImageUrlsIsNull() {
      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("답변 내용")
              .isAccepted(null)
              .imageUrls(null)
              .build();

      assertThat(entity.getIsAccepted()).isFalse();
      assertThat(entity.getImageUrls()).isEmpty();
    }
  }
}
