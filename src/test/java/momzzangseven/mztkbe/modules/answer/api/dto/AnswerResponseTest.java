package momzzangseven.mztkbe.modules.answer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerResponse unit test")
class AnswerResponseTest {

  @Nested
  @DisplayName("from(AnswerResult)")
  class From {

    @Test
    @DisplayName("converts all fields from AnswerResult")
    void from_convertsAllFields() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 3, 3, 9, 15);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 4, 10, 45);
      AnswerResult result =
          new AnswerResult(
              10L,
              20L,
              "writer",
              "https://cdn.example.com/profile.webp",
              "answer content",
              true,
              5L,
              true,
              List.of("https://cdn.example.com/answer.webp"),
              null,
              createdAt,
              updatedAt);

      AnswerResponse response = AnswerResponse.from(result);

      assertThat(response.answerId()).isEqualTo(10L);
      assertThat(response.userId()).isEqualTo(20L);
      assertThat(response.nickname()).isEqualTo("writer");
      assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/profile.webp");
      assertThat(response.content()).isEqualTo("answer content");
      assertThat(response.isAccepted()).isTrue();
      assertThat(response.likeCount()).isEqualTo(5L);
      assertThat(response.isLiked()).isTrue();
      assertThat(response.imageUrls()).containsExactly("https://cdn.example.com/answer.webp");
      assertThat(response.createdAt()).isEqualTo(createdAt);
      assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("converts null imageUrls to empty list")
    void from_convertsNullImageUrlsToEmptyList() {
      AnswerResult result =
          new AnswerResult(
              10L,
              20L,
              "writer",
              null,
              "answer content",
              false,
              0L,
              false,
              null,
              null,
              LocalDateTime.of(2026, 3, 3, 9, 15),
              LocalDateTime.of(2026, 3, 4, 10, 45));

      AnswerResponse response = AnswerResponse.from(result);

      assertThat(response.likeCount()).isZero();
      assertThat(response.isLiked()).isFalse();
      assertThat(response.imageUrls()).isEmpty();
    }
  }
}
