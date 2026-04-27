package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyCommentedPostsCursorCommand unit test")
class GetMyCommentedPostsCursorCommandTest {

  @Test
  @DisplayName("applies default size when size is null")
  void pageRequest_appliesDefaultSize() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.FREE, null, null, null);

    CursorPageRequest pageRequest = command.pageRequest();

    assertThat(pageRequest.size()).isEqualTo(10);
    assertThat(pageRequest.hasCursor()).isFalse();
  }

  @Test
  @DisplayName("allows max size boundary")
  void pageRequest_allowsMaxSize() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.QUESTION, null, null, 50);

    CursorPageRequest pageRequest = command.pageRequest();

    assertThat(pageRequest.size()).isEqualTo(50);
    assertThat(pageRequest.limitWithProbe()).isEqualTo(51);
  }

  @Test
  @DisplayName("rejects size over max")
  void pageRequest_rejectsSizeOverMax() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.FREE, null, null, 51);

    assertThatThrownBy(command::pageRequest).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("rejects null requester id")
  void validate_rejectsNullRequesterId() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(null, PostType.FREE, null, null, 10);

    assertThatThrownBy(command::validate).isInstanceOf(PostInvalidInputException.class);
  }

  @Test
  @DisplayName("rejects non-positive requester id")
  void validate_rejectsNonPositiveRequesterId() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(0L, PostType.FREE, null, null, 10);

    assertThatThrownBy(command::validate).isInstanceOf(PostInvalidInputException.class);
  }

  @Test
  @DisplayName("rejects null type")
  void validate_rejectsNullType() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, null, null, null, 10);

    assertThatThrownBy(command::validate).isInstanceOf(PostInvalidInputException.class);
  }

  @Test
  @DisplayName("rejects cursor from different requester scope")
  void pageRequest_rejectsCursorScopeMismatch() {
    String otherScope = CursorScope.commentedPosts(2L, PostType.FREE.name());
    String cursor =
        CursorCodec.encode(new KeysetCursor(LocalDateTime.of(2026, 4, 26, 12, 0), 10L, otherScope));
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.FREE, null, cursor, 10);

    assertThatThrownBy(command::pageRequest).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("rejects cursor from different post type scope")
  void pageRequest_rejectsCursorPostTypeScopeMismatch() {
    String otherScope = CursorScope.commentedPosts(1L, PostType.QUESTION.name());
    String cursor =
        CursorCodec.encode(new KeysetCursor(LocalDateTime.of(2026, 4, 26, 12, 0), 10L, otherScope));
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.FREE, null, cursor, 10);

    assertThatThrownBy(command::pageRequest).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("normalizes search for QUESTION and ignores search for FREE")
  void effectiveSearch_normalizesQuestionOnly() {
    GetMyCommentedPostsCursorCommand questionCommand =
        new GetMyCommentedPostsCursorCommand(1L, PostType.QUESTION, "  FoRm  ", null, 10);
    GetMyCommentedPostsCursorCommand freeCommand =
        new GetMyCommentedPostsCursorCommand(1L, PostType.FREE, "  FoRm  ", null, 10);

    assertThat(questionCommand.effectiveSearch()).isEqualTo("form");
    assertThat(freeCommand.effectiveSearch()).isNull();
  }

  @Test
  @DisplayName("rejects cursor from different search scope")
  void pageRequest_rejectsCursorSearchScopeMismatch() {
    String originalScope = CursorScope.commentedPosts(1L, PostType.QUESTION.name(), "form");
    String cursor =
        CursorCodec.encode(
            new KeysetCursor(LocalDateTime.of(2026, 4, 26, 12, 0), 10L, originalScope));
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.QUESTION, "other", cursor, 10);

    assertThatThrownBy(command::pageRequest).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("validate rejects malformed cursor")
  void validate_rejectsMalformedCursor() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(1L, PostType.FREE, null, "%%%", 10);

    assertThatThrownBy(command::validate).isInstanceOf(InvalidCursorException.class);
  }
}
