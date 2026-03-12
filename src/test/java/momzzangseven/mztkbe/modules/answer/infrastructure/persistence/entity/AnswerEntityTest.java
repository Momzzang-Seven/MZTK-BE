// src/test/java/momzzangseven/mztkbe/modules/answer/infrastructure/persistence/entity/AnswerEntityTest.java
package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerEntity 단위 테스트")
class AnswerEntityTest {

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("도메인에서 엔티티로 변환할 때 주요 필드를 복사한다")
    void fromDomain_copiesFields() {
      // given
      Answer answer =
          Answer.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("답변 내용")
              .isAccepted(true)
              .imageUrls(List.of("https://image"))
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      // when
      AnswerEntity entity = AnswerEntity.fromDomain(answer);

      // then
      assertThat(entity.getId()).isEqualTo(1L);
      assertThat(entity.getPostId()).isEqualTo(10L);
      assertThat(entity.getUserId()).isEqualTo(20L);
      assertThat(entity.getContent()).isEqualTo("답변 내용");
      assertThat(entity.getIsAccepted()).isTrue();
      assertThat(entity.getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("엔티티에서 도메인으로 변환할 때 주요 필드를 복사한다")
    void toDomain_copiesFields() {
      // given
      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("답변 내용")
              .isAccepted(true)
              .imageUrls(List.of("https://image"))
              .build();

      // when
      Answer answer = entity.toDomain();

      // then
      assertThat(answer.getId()).isEqualTo(1L);
      assertThat(answer.getPostId()).isEqualTo(10L);
      assertThat(answer.getUserId()).isEqualTo(20L);
      assertThat(answer.getContent()).isEqualTo("답변 내용");
      assertThat(answer.getIsAccepted()).isTrue();
      assertThat(answer.getImageUrls()).containsExactly("https://image");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("이미지 목록이 null이면 빈 리스트로 초기화한다")
    void constructor_initializesEmptyList_whenImageUrlsIsNull() {
      // when
      AnswerEntity entity =
          AnswerEntity.builder()
              .id(1L)
              .postId(10L)
              .userId(20L)
              .content("답변 내용")
              .isAccepted(null)
              .imageUrls(null)
              .build();

      // then
      assertThat(entity.getIsAccepted()).isFalse();
      assertThat(entity.getImageUrls()).isEmpty();
    }
  }
}
