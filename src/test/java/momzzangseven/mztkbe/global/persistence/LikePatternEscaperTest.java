package momzzangseven.mztkbe.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LikePatternEscaper unit test")
class LikePatternEscaperTest {

  @Test
  @DisplayName("null or blank returns null")
  void escape_nullOrBlank_returnsNull() {
    assertThat(LikePatternEscaper.escape(null)).isNull();
    assertThat(LikePatternEscaper.escape(" ")).isNull();
  }

  @Test
  @DisplayName("wildcard characters are escaped for literal LIKE search")
  void escape_wildcards_returnsEscapedPattern() {
    assertThat(LikePatternEscaper.escape("100%_!")).isEqualTo("100!%!_!!");
  }

  @Test
  @DisplayName("plain text remains unchanged")
  void escape_plainText_returnsSameValue() {
    assertThat(LikePatternEscaper.escape("form")).isEqualTo("form");
  }
}
