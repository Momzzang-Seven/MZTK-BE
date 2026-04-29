package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidMyPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyPostsCursorCommand unit test")
class GetMyPostsCursorCommandTest {

  @Test
  @DisplayName("validate rejects malformed cursor")
  void validate_rejectsMalformedCursor() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.FREE, null, null, "%%%", null);

    assertThatThrownBy(command::validate).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("pageRequest resolves default size and my-posts scope")
  void pageRequest_defaultSize() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.FREE, null, "ignored", null, null);

    CursorPageRequest pageRequest = command.pageRequest();

    assertThat(pageRequest.size()).isEqualTo(10);
    assertThat(pageRequest.hasCursor()).isFalse();
    assertThat(command.search()).isEqualTo("ignored");
    assertThat(command.effectiveSearch()).isNull();
  }

  @Test
  @DisplayName("normalizes tag and QUESTION search")
  void normalizesTagAndQuestionSearch() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.QUESTION, " Squat ", " Form ", null, 10);

    assertThat(command.tagName()).isEqualTo("squat");
    assertThat(command.search()).isEqualTo(" Form ");
    assertThat(command.effectiveSearch()).isEqualTo("form");
  }

  @Test
  @DisplayName("FREE search is ignored")
  void freeSearchIgnored() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.FREE, " Squat ", " Form ", null, 10);

    assertThat(command.tagName()).isEqualTo("squat");
    assertThat(command.search()).isEqualTo(" Form ");
    assertThat(command.effectiveSearch()).isNull();
  }

  @Test
  @DisplayName("rejects missing type with dedicated business exception")
  void validate_rejectsMissingType() {
    GetMyPostsCursorCommand command = new GetMyPostsCursorCommand(1L, null, null, null, null, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidMyPostsQueryException.class)
        .hasMessageContaining("type");
  }

  @Test
  @DisplayName("rejects invalid requester id with dedicated business exception")
  void validate_rejectsInvalidRequesterId() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(0L, PostType.FREE, null, null, null, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidMyPostsQueryException.class)
        .hasMessageContaining("requesterId");
  }

  @Test
  @DisplayName("pageRequest rejects invalid size")
  void pageRequest_rejectsInvalidSize() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.FREE, null, null, null, 51);

    assertThatThrownBy(command::pageRequest).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("pageRequest rejects malformed cursor")
  void pageRequest_rejectsMalformedCursor() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.FREE, null, null, "%%%", 10);

    assertThatThrownBy(command::pageRequest).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("pageRequest rejects cursor scope mismatch across filters")
  void pageRequest_rejectsScopeMismatch() {
    String originalScope = CursorScope.myPosts(1L, PostType.QUESTION.name(), "squat", "form");
    String cursor =
        CursorCodec.encode(
            new KeysetCursor(LocalDateTime.of(2026, 4, 27, 12, 0), 10L, originalScope));
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.QUESTION, "bench", "form", cursor, 10);

    assertThatThrownBy(command::pageRequest)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }

  @Test
  @DisplayName("pageRequest decodes matching cursor")
  void pageRequest_decodesMatchingCursor() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 4, 27, 12, 0);
    String scope = CursorScope.myPosts(1L, PostType.QUESTION.name(), "squat", "form");
    String cursor = CursorCodec.encode(new KeysetCursor(createdAt, 10L, scope));
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(1L, PostType.QUESTION, " Squat ", " FORM ", cursor, 10);

    CursorPageRequest pageRequest = command.pageRequest();

    assertThat(pageRequest.cursor().createdAt()).isEqualTo(createdAt);
    assertThat(pageRequest.cursor().id()).isEqualTo(10L);
  }
}
