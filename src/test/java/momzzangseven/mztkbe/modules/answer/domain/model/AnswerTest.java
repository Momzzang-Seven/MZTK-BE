package momzzangseven.mztkbe.modules.answer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerOwnPostException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAcceptedAnswerException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAcceptedAnswerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Answer")
class AnswerTest {

  @Nested
  class SuccessCases {

    @Test
    void create_returnsDefaultState() {
      Answer answer =
          Answer.create(10L, 30L, false, 20L, "answer content", List.of("https://image"));

      assertThat(answer.getPostId()).isEqualTo(10L);
      assertThat(answer.getUserId()).isEqualTo(20L);
      assertThat(answer.getContent()).isEqualTo("answer content");
      assertThat(answer.getIsAccepted()).isFalse();
      assertThat(answer.getImageUrls()).containsExactly("https://image");
    }

    @Test
    void update_returnsUpdatedAnswer() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of("https://old"));

      Answer updated = answer.update("after", List.of("https://updated"), 20L);

      assertThat(updated).isNotSameAs(answer);
      assertThat(updated.getContent()).isEqualTo("after");
      assertThat(updated.getImageUrls()).containsExactly("https://updated");
    }

    @Test
    void validateDeletable_passes_whenOwnedAndNotAccepted() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", false, List.of());

      answer.validateDeletable(20L);

      assertThat(answer.getUserId()).isEqualTo(20L);
    }

    @Test
    void update_returnsSameInstance_whenNothingChanges() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of("https://old"));

      Answer updated = answer.update(null, null, 20L);

      assertThat(updated).isSameAs(answer);
    }
  }

  @Nested
  class FailureCases {

    @Test
    void create_throws_whenContentIsBlank() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, false, 20L, " ", List.of()))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    void create_throws_whenPostIsSolved() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, "answer content", List.of()))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    void create_prioritizesBusinessRuleOverBlankContent_whenPostIsSolved() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, " ", List.of()))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    void create_throws_whenWriterAnswersOwnPost() {
      assertThatThrownBy(() -> Answer.create(10L, 20L, false, 20L, "answer content", List.of()))
          .isInstanceOf(CannotAnswerOwnPostException.class);
    }

    @Test
    void update_throws_whenRequesterIsNotOwner() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of());

      assertThatThrownBy(() -> answer.update("after", List.of(), 99L))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    void update_throws_whenAnswerIsAccepted() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", true, List.of());

      assertThatThrownBy(() -> answer.update("after", List.of(), 20L))
          .isInstanceOf(CannotUpdateAcceptedAnswerException.class);
    }

    @Test
    void validateDeletable_throws_whenAnswerIsAccepted() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", true, List.of());

      assertThatThrownBy(() -> answer.validateDeletable(20L))
          .isInstanceOf(CannotDeleteAcceptedAnswerException.class);
    }
  }

  private Answer buildAnswer(
      Long id,
      Long postId,
      Long userId,
      String content,
      boolean isAccepted,
      List<String> imageUrls) {
    return Answer.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .imageUrls(imageUrls)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }
}
