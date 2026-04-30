package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidLikedPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyLikedPostsCursorCommand unit test")
class GetMyLikedPostsCursorCommandTest {

  @Test
  @DisplayName("validates default size and liked posts cursor scope")
  void validate_defaultSize() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.FREE, null, null, null);

    assertThatCode(command::validate).doesNotThrowAnyException();
    assertThat(command.pageRequest().size()).isEqualTo(10);
    assertThat(command.pageRequest().hasCursor()).isFalse();
  }

  @Test
  @DisplayName("accepts max size")
  void validate_acceptsMaxSize() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.QUESTION, null, null, 50);

    assertThat(command.pageRequest().size()).isEqualTo(50);
  }

  @Test
  @DisplayName("rejects missing type with dedicated business exception")
  void validate_rejectsMissingType() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, null, null, null, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidLikedPostsQueryException.class)
        .hasMessageContaining("type");
  }

  @Test
  @DisplayName("rejects invalid requester id with dedicated business exception")
  void validate_rejectsInvalidRequesterId() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(0L, PostType.FREE, null, null, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidLikedPostsQueryException.class)
        .hasMessageContaining("requesterId");
  }

  @Test
  @DisplayName("rejects invalid size using existing cursor exception")
  void validate_rejectsInvalidSize() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.FREE, null, null, 51);

    assertThatThrownBy(command::validate).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("rejects malformed cursor using existing cursor exception")
  void validate_rejectsMalformedCursor() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.FREE, null, "%%%", 10);

    assertThatThrownBy(command::validate).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("rejects cursor scope mismatch using existing cursor exception")
  void validate_rejectsScopeMismatch() {
    String originalScope = CursorScope.likedPosts(1L, PostType.FREE.name());
    String cursor =
        CursorCodec.encode(
            new KeysetCursor(LocalDateTime.of(2026, 4, 26, 12, 0), 10L, originalScope));
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.QUESTION, null, cursor, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }

  @Test
  @DisplayName("decodes matching cursor")
  void validate_decodesMatchingCursor() {
    LocalDateTime likedAt = LocalDateTime.of(2026, 4, 26, 12, 0);
    String scope = CursorScope.likedPosts(1L, PostType.FREE.name());
    String cursor = CursorCodec.encode(new KeysetCursor(likedAt, 10L, scope));
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.FREE, null, cursor, 10);

    CursorPageRequest pageRequest = command.pageRequest();

    assertThat(pageRequest.cursor().createdAt()).isEqualTo(likedAt);
    assertThat(pageRequest.cursor().id()).isEqualTo(10L);
  }

  @Test
  @DisplayName("normalizes QUESTION search and ignores FREE search")
  void effectiveSearch() {
    GetMyLikedPostsCursorCommand question =
        new GetMyLikedPostsCursorCommand(1L, PostType.QUESTION, " FoRm ", null, 10);
    GetMyLikedPostsCursorCommand free =
        new GetMyLikedPostsCursorCommand(1L, PostType.FREE, " FoRm ", null, 10);

    assertThat(question.effectiveSearch()).isEqualTo("form");
    assertThat(free.effectiveSearch()).isNull();
  }

  @Test
  @DisplayName("rejects cursor scope mismatch when search changes")
  void validate_rejectsSearchScopeMismatch() {
    String originalScope = CursorScope.likedPosts(1L, PostType.QUESTION.name(), "form");
    String cursor =
        CursorCodec.encode(
            new KeysetCursor(LocalDateTime.of(2026, 4, 26, 12, 0), 10L, originalScope));
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(1L, PostType.QUESTION, "bench", cursor, 10);

    assertThatThrownBy(command::validate)
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }
}
