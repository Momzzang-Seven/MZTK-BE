package momzzangseven.mztkbe.modules.answer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerOwnPostException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAnswerOnSolvedPostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Answer domain model")
class AnswerTest {

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("create() initializes default fields for a valid answer")
    void create_initializesDefaults() {
      Answer answer = Answer.create(10L, 30L, false, 20L, "answer content");

      assertThat(answer.getPostId()).isEqualTo(10L);
      assertThat(answer.getUserId()).isEqualTo(20L);
      assertThat(answer.getContent()).isEqualTo("answer content");
      assertThat(answer.getIsAccepted()).isFalse();
    }

    @Test
    @DisplayName("update() replaces content for the owner")
    void update_replacesContent() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false);

      Answer updated = answer.update("after", 20L, false);

      assertThat(updated).isNotSameAs(answer);
      assertThat(updated.getContent()).isEqualTo("after");
    }

    @Test
    @DisplayName("update() returns the same instance when no fields are changed")
    void update_returnsSameInstance_whenNothingChanges() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false);

      Answer updated = answer.update(null, 20L, false);

      assertThat(updated).isSameAs(answer);
    }

    @Test
    @DisplayName("validateDeletable() passes for the owner when the parent question is unsolved")
    void validateDeletable_passes_whenOwnedAndParentQuestionIsUnsolved() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", false);

      answer.validateDeletable(20L, false);

      assertThat(answer.getUserId()).isEqualTo(20L);
    }
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("create() throws when content is blank")
    void create_throws_whenContentIsBlank() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, false, 20L, " "))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("create() throws when the question post is already solved")
    void create_throws_whenPostIsSolved() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, "answer content"))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("create() prioritizes solved-post validation over blank content")
    void create_prioritizesSolvedPostValidation() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, " "))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("create() throws when the post writer answers his or her own post")
    void create_throws_whenWriterAnswersOwnPost() {
      assertThatThrownBy(() -> Answer.create(10L, 20L, false, 20L, "answer content"))
          .isInstanceOf(CannotAnswerOwnPostException.class);
    }

    @Test
    @DisplayName("create() throws when postWriterId is null")
    void create_throws_whenPostWriterIdIsNull() {
      assertThatThrownBy(() -> Answer.create(10L, null, false, 20L, "answer content"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update() throws when the requester is not the owner")
    void update_throws_whenRequesterIsNotOwner() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false);

      assertThatThrownBy(() -> answer.update("after", 99L, false))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("update() throws when content is blank")
    void update_throws_whenContentIsBlank() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false);

      assertThatThrownBy(() -> answer.update(" ", 20L, false))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("update() throws when parent question is solved")
    void update_throws_whenParentQuestionIsSolved() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false);

      assertThatThrownBy(() -> answer.update("after", 20L, true))
          .isInstanceOf(CannotUpdateAnswerOnSolvedPostException.class);
    }

    @Test
    @DisplayName("update() uses parent solved state as the single mutability guard")
    void update_throws_whenAcceptedAnswerBelongsToSolvedQuestion() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", true);

      assertThatThrownBy(() -> answer.update("after", 20L, true))
          .isInstanceOf(CannotUpdateAnswerOnSolvedPostException.class);
    }

    @Test
    @DisplayName("validateDeletable() throws when the requester is not the owner")
    void validateDeletable_throws_whenRequesterIsNotOwner() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", false);

      assertThatThrownBy(() -> answer.validateDeletable(99L, false))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("validateDeletable() throws when parent question is solved")
    void validateDeletable_throws_whenParentQuestionIsSolved() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", false);

      assertThatThrownBy(() -> answer.validateDeletable(20L, true))
          .isInstanceOf(CannotDeleteAnswerOnSolvedPostException.class);
    }

    @Test
    @DisplayName("validateDeletable() uses parent solved state as the single mutability guard")
    void validateDeletable_throws_whenAcceptedAnswerBelongsToSolvedQuestion() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", true);

      assertThatThrownBy(() -> answer.validateDeletable(20L, true))
          .isInstanceOf(CannotDeleteAnswerOnSolvedPostException.class);
    }
  }

  private Answer buildAnswer(
      Long id, Long postId, Long userId, String content, boolean isAccepted) {
    return Answer.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }
}
