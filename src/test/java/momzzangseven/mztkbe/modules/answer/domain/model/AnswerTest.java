// src/test/java/momzzangseven/mztkbe/modules/answer/domain/model/AnswerTest.java
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

@DisplayName("Answer 도메인 단위 테스트")
class AnswerTest {

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("답변 생성 시 기본값을 채워 반환한다")
    void create_returnsAnswerWithDefaults() {
      // given
      List<String> imageUrls = List.of("https://image");

      // when
      Answer answer = Answer.create(10L, 30L, false, 20L, "답변 내용", imageUrls);

      // then
      assertThat(answer.getPostId()).isEqualTo(10L);
      assertThat(answer.getUserId()).isEqualTo(20L);
      assertThat(answer.getContent()).isEqualTo("답변 내용");
      assertThat(answer.getIsAccepted()).isFalse();
      assertThat(answer.getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("답변 수정 시 내용과 이미지를 변경한 새 객체를 반환한다")
    void update_returnsUpdatedAnswer() {
      // given
      Answer answer = buildAnswer(1L, 10L, 20L, "이전 내용", false, List.of("https://old"));

      // when
      Answer updated = answer.update("수정된 내용", List.of("https://updated"), 20L);

      // then
      assertThat(updated).isNotSameAs(answer);
      assertThat(updated.getContent()).isEqualTo("수정된 내용");
      assertThat(updated.getImageUrls()).containsExactly("https://updated");
    }

    @Test
    @DisplayName("삭제 가능 검증은 작성자이면서 미채택 답변이면 통과한다")
    void validateDeletable_passes_whenOwnedAndNotAccepted() {
      // given
      Answer answer = buildAnswer(1L, 10L, 20L, "답변 내용", false, List.of());

      // when
      answer.validateDeletable(20L);

      // then
      assertThat(answer.getUserId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("수정 값이 모두 없으면 기존 객체를 그대로 반환한다")
    void update_returnsSameInstance_whenNothingChanges() {
      // given
      Answer answer = buildAnswer(1L, 10L, 20L, "이전 내용", false, List.of("https://old"));

      // when
      Answer updated = answer.update(null, null, 20L);

      // then
      assertThat(updated).isSameAs(answer);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("답변 생성 시 내용이 비어 있으면 예외를 던진다")
    void create_throwsException_whenContentIsBlank() {
      // when & then
      assertThatThrownBy(() -> Answer.create(10L, 30L, false, 20L, " ", List.of()))
          .isInstanceOf(AnswerInvalidInputException.class);
    }

    @Test
    @DisplayName("답변 생성 시 해결된 게시글이면 예외를 던진다")
    void create_throwsException_whenPostIsSolved() {
      // when & then
      assertThatThrownBy(() -> Answer.create(10L, 30L, true, 20L, "답변 내용", List.of()))
          .isInstanceOf(CannotAnswerSolvedPostException.class);
    }

    @Test
    @DisplayName("답변 생성 시 게시글 작성자는 답변할 수 없다")
    void create_throwsException_whenWriterAnswersOwnPost() {
      // when & then
      assertThatThrownBy(() -> Answer.create(10L, 20L, false, 20L, "답변 내용", List.of()))
          .isInstanceOf(CannotAnswerOwnPostException.class);
    }

    @Test
    @DisplayName("답변 수정 시 작성자가 아니면 예외를 던진다")
    void update_throwsException_whenRequesterIsNotOwner() {
      // given
      Answer answer = buildAnswer(1L, 10L, 20L, "이전 내용", false, List.of());

      // when & then
      assertThatThrownBy(() -> answer.update("수정된 내용", List.of(), 99L))
          .isInstanceOf(AnswerUnauthorizedException.class);
    }

    @Test
    @DisplayName("답변 수정 시 채택된 답변이면 예외를 던진다")
    void update_throwsException_whenAnswerIsAccepted() {
      // given
      Answer answer = buildAnswer(1L, 10L, 20L, "이전 내용", true, List.of());

      // when & then
      assertThatThrownBy(() -> answer.update("수정된 내용", List.of(), 20L))
          .isInstanceOf(CannotUpdateAcceptedAnswerException.class);
    }

    @Test
    @DisplayName("답변 삭제 가능 검증 시 채택된 답변이면 예외를 던진다")
    void validateDeletable_throwsException_whenAnswerIsAccepted() {
      // given
      Answer answer = buildAnswer(1L, 10L, 20L, "답변 내용", true, List.of());

      // when & then
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
