package momzzangseven.mztkbe.modules.answer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

@DisplayName("Answer domain model")
class AnswerTest {

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("create() initializes default fields for a valid answer")
    void create_initializesDefaults() {
      Answer answer =
          Answer.create(10L, 30L, false, 20L, "answer content", List.of("https://image"));

      assertThat(answer.getPostId()).isEqualTo(10L);
      assertThat(answer.getUserId()).isEqualTo(20L);
      assertThat(answer.getContent()).isEqualTo("answer content");
      assertThat(answer.getIsAccepted()).isFalse();
      assertThat(answer.getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("create() initializes an empty image list when imageUrls is null")
    void create_initializesEmptyImageList_whenImageUrlsIsNull() {
      Answer answer = Answer.create(10L, 30L, false, 20L, "answer content", null);

      assertThat(answer.getImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("create() makes a defensive copy of imageUrls")
    void create_makesDefensiveCopyOfImageUrls() {
      List<String> imageUrls = new ArrayList<>(List.of("https://image"));

      Answer answer = Answer.create(10L, 30L, false, 20L, "answer content", imageUrls);
      imageUrls.add("https://mutated");

      assertThat(answer.getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("update() replaces content and imageUrls for the owner")
    void update_replacesFields() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of("https://old"));

      Answer updated = answer.update("after", List.of("https://updated"), 20L);

      assertThat(updated).isNotSameAs(answer);
      assertThat(updated.getContent()).isEqualTo("after");
      assertThat(updated.getImageUrls()).containsExactly("https://updated");
    }

    @Test
    @DisplayName("update() returns the same instance when no fields are changed")
    void update_returnsSameInstance_whenNothingChanges() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of("https://old"));

      Answer updated = answer.update(null, null, 20L);

      assertThat(updated).isSameAs(answer);
    }

    @Test
    @DisplayName("validateDeletable() passes for the owner of a non-accepted answer")
    void validateDeletable_passes_whenOwnedAndNotAccepted() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", false, List.of());

      answer.validateDeletable(20L);

      assertThat(answer.getUserId()).isEqualTo(20L);
    }
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("create() throws when content is blank")
    void create_throws_whenContentIsBlank() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, false, 20L, " ", List.of()))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("create() throws when the question post is already solved")
    void create_throws_whenPostIsSolved() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, "answer content", List.of()))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("create() prioritizes solved-post validation over blank content")
    void create_prioritizesSolvedPostValidation() {
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, " ", List.of()))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("create() throws when the post writer answers his or her own post")
    void create_throws_whenWriterAnswersOwnPost() {
      assertThatThrownBy(() -> Answer.create(10L, 20L, false, 20L, "answer content", List.of()))
          .isInstanceOf(CannotAnswerOwnPostException.class);
    }

    @Test
    @DisplayName("create() throws when postWriterId is null")
    void create_throws_whenPostWriterIdIsNull() {
      assertThatThrownBy(() -> Answer.create(10L, null, false, 20L, "answer content", List.of()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update() throws when the requester is not the owner")
    void update_throws_whenRequesterIsNotOwner() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of());

      assertThatThrownBy(() -> answer.update("after", List.of(), 99L))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("update() throws when the answer is accepted")
    void update_throws_whenAnswerIsAccepted() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", true, List.of());

      assertThatThrownBy(() -> answer.update("after", List.of(), 20L))
          .isInstanceOf(CannotUpdateAcceptedAnswerException.class);
    }

    @Test
    @DisplayName("update() throws when content is blank")
    void update_throws_whenContentIsBlank() {
      Answer answer = buildAnswer(1L, 10L, 20L, "before", false, List.of());

      assertThatThrownBy(() -> answer.update(" ", null, 20L))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("validateDeletable() throws when the requester is not the owner")
    void validateDeletable_throws_whenRequesterIsNotOwner() {
      Answer answer = buildAnswer(1L, 10L, 20L, "answer content", false, List.of());

      assertThatThrownBy(() -> answer.validateDeletable(99L))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("validateDeletable() throws when the answer is accepted")
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
