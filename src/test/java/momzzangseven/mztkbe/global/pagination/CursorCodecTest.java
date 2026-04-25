package momzzangseven.mztkbe.global.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CursorCodec unit test")
class CursorCodecTest {

  @Test
  @DisplayName("encodes and decodes cursor")
  void encodeDecodeRoundTrip() {
    String scope = CursorScope.posts("QUESTION", "squat", "form");
    KeysetCursor cursor = new KeysetCursor(LocalDateTime.of(2026, 4, 24, 12, 0), 10L, scope);

    KeysetCursor decoded = CursorCodec.decode(CursorCodec.encode(cursor), scope);

    assertThat(decoded).isEqualTo(cursor);
  }

  @Test
  @DisplayName("rejects malformed cursor")
  void rejectsMalformedCursor() {
    String scope = CursorScope.posts(null, null, null);

    assertThatThrownBy(() -> CursorCodec.decode("not-a-cursor", scope))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("rejects scope mismatch")
  void rejectsScopeMismatch() {
    String originalScope = CursorScope.rootComments(1L);
    String otherScope = CursorScope.rootComments(2L);
    String encoded =
        CursorCodec.encode(
            new KeysetCursor(LocalDateTime.of(2026, 4, 24, 12, 0), 1L, originalScope));

    assertThatThrownBy(() -> CursorCodec.decode(encoded, otherScope))
        .isInstanceOf(InvalidCursorException.class)
        .hasMessageContaining("scope");
  }
}
