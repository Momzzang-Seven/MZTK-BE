package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidCommentedPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyCommentedPostsCursorCommand unit test")
class GetMyCommentedPostsCursorCommandTest {

  @Test
  @DisplayName("missing type throws dedicated commented-posts query exception")
  void validate_missingType_throwsDedicatedException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(7L, null, null, null, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidCommentedPostsQueryException.class)
        .hasMessage("type is required.");
  }

  @Test
  @DisplayName("invalid requester id throws dedicated commented-posts query exception")
  void validate_invalidRequesterId_throwsDedicatedException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(0L, PostType.FREE, null, null, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidCommentedPostsQueryException.class)
        .hasMessage("requesterId must be positive.");
  }

  @Test
  @DisplayName("QUESTION search is normalized and FREE search is ignored")
  void effectiveSearch_normalizesQuestionAndIgnoresFree() {
    GetMyCommentedPostsCursorCommand questionCommand =
        new GetMyCommentedPostsCursorCommand(7L, PostType.QUESTION, " FoRm ", null, 10);
    GetMyCommentedPostsCursorCommand freeCommand =
        new GetMyCommentedPostsCursorCommand(7L, PostType.FREE, " FoRm ", null, 10);

    assertThat(questionCommand.effectiveSearch()).isEqualTo("form");
    assertThat(freeCommand.effectiveSearch()).isNull();
  }

  @Test
  @DisplayName("pageRequest validates cursor size rules")
  void pageRequest_invalidSize_throwsInvalidCursorException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(7L, PostType.FREE, null, null, 0);

    assertThatThrownBy(command::pageRequest)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("size");
  }

  @Test
  @DisplayName("pageRequest uses default size when size is null")
  void pageRequest_nullSize_usesDefaultSize() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(7L, PostType.FREE, null, null, null);

    assertThat(command.pageRequest().size()).isEqualTo(10);
  }

  @Test
  @DisplayName("pageRequest accepts max size")
  void pageRequest_maxSize_isAccepted() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(7L, PostType.FREE, null, null, 50);

    assertThat(command.pageRequest().size()).isEqualTo(50);
  }

  @Test
  @DisplayName("malformed cursor is rejected")
  void pageRequest_malformedCursor_throwsInvalidCursorException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(7L, PostType.FREE, null, "%%%", 10);

    assertThatThrownBy(command::pageRequest)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("cursor");
  }

  @Test
  @DisplayName("cursor from different requester scope is rejected")
  void pageRequest_differentRequesterScope_throwsInvalidCursorException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(
            7L, PostType.QUESTION, "Form", cursorFor(8L, PostType.QUESTION, "form"), 10);

    assertThatThrownBy(command::pageRequest)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }

  @Test
  @DisplayName("cursor from different type scope is rejected")
  void pageRequest_differentTypeScope_throwsInvalidCursorException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(
            7L, PostType.QUESTION, null, cursorFor(7L, PostType.FREE, null), 10);

    assertThatThrownBy(command::pageRequest)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }

  @Test
  @DisplayName("cursor from different search scope is rejected")
  void pageRequest_differentSearchScope_throwsInvalidCursorException() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(
            7L, PostType.QUESTION, "Form", cursorFor(7L, PostType.QUESTION, "other"), 10);

    assertThatThrownBy(command::pageRequest)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }

  private String cursorFor(Long requesterId, PostType type, String search) {
    String scope = CursorScope.commentedPosts(requesterId, type.name(), search);
    return CursorCodec.encode(new KeysetCursor(LocalDateTime.of(2026, 4, 27, 12, 0), 15L, scope));
  }
}
