package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerResult unit test")
class AnswerResultTest {

  @Nested
  @DisplayName("from()")
  class From {

    @Test
    @DisplayName("maps Answer and metadata into AnswerResult")
    void from_mapsAnswerAndMetadata() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 10, 0);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 2, 11, 30);
      Answer answer =
          mockedAnswer(
              Map.of(
                  "getId",
                  100L,
                  "getUserId",
                  200L,
                  "getContent",
                  "answer content",
                  "getIsAccepted",
                  true,
                  "getCreatedAt",
                  createdAt,
                  "getUpdatedAt",
                  updatedAt));

      AnswerResult result =
          AnswerResult.from(
              answer,
              "writer",
              "https://cdn.example.com/profile.webp",
              3L,
              true,
              List.of("https://cdn.example.com/answer-1.webp"));

      assertThat(result.answerId()).isEqualTo(100L);
      assertThat(result.userId()).isEqualTo(200L);
      assertThat(result.nickname()).isEqualTo("writer");
      assertThat(result.profileImageUrl()).isEqualTo("https://cdn.example.com/profile.webp");
      assertThat(result.content()).isEqualTo("answer content");
      assertThat(result.accepted()).isTrue();
      assertThat(result.likeCount()).isEqualTo(3L);
      assertThat(result.liked()).isTrue();
      assertThat(result.imageUrls()).containsExactly("https://cdn.example.com/answer-1.webp");
      assertThat(result.createdAt()).isEqualTo(createdAt);
      assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("converts null imageUrls to empty list")
    void from_convertsNullImageUrlsToEmptyList() {
      Answer answer =
          mockedAnswer(
              Map.of(
                  "getId",
                  100L,
                  "getUserId",
                  200L,
                  "getContent",
                  "answer content",
                  "getIsAccepted",
                  false,
                  "getCreatedAt",
                  LocalDateTime.of(2026, 3, 1, 10, 0),
                  "getUpdatedAt",
                  LocalDateTime.of(2026, 3, 2, 11, 30)));

      AnswerResult result = AnswerResult.from(answer, "writer", null, 0L, false, null);

      assertThat(result.likeCount()).isZero();
      assertThat(result.liked()).isFalse();
      assertThat(result.imageUrls()).isEmpty();
    }
  }

  private Answer mockedAnswer(Map<String, Object> valuesByMethodName) {
    return mock(
        Answer.class,
        invocation -> {
          Object value = valuesByMethodName.get(invocation.getMethod().getName());
          if (value != null || valuesByMethodName.containsKey(invocation.getMethod().getName())) {
            return value;
          }
          return invocation.callRealMethod();
        });
  }
}
