package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidCommentedPostsQueryException;
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
}
