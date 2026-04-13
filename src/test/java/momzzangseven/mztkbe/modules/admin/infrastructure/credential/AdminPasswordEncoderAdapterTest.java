package momzzangseven.mztkbe.modules.admin.infrastructure.credential;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AdminPasswordEncoderAdapter 단위 테스트")
class AdminPasswordEncoderAdapterTest {

  private AdminPasswordEncoderAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AdminPasswordEncoderAdapter();
  }

  // ---------------------------------------------------------------------------
  // encode()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("encode() 메서드")
  class Encode {

    @Test
    @DisplayName("[M-89] encode returns a BCrypt hash starting with $2a$")
    void encode_returnsBcryptHash() {
      // when
      String hash = adapter.encode("MyP@ssw0rd!");

      // then
      assertThat(hash).startsWith("$2a$");
      assertThat(hash).isNotEqualTo("MyP@ssw0rd!");
    }

    @Test
    @DisplayName("[M-90] encode returns different hashes for the same input (salt randomness)")
    void encode_returnsDifferentHashesForSameInput() {
      // when
      String hash1 = adapter.encode("SamePassword");
      String hash2 = adapter.encode("SamePassword");

      // then
      assertThat(hash1).isNotEqualTo(hash2);
    }
  }

  // ---------------------------------------------------------------------------
  // matches()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("matches() 메서드")
  class Matches {

    @Test
    @DisplayName("[M-91] matches returns true for correct raw password vs its hash")
    void matches_correctPassword_returnsTrue() {
      // given
      String hash = adapter.encode("CorrectPassword");

      // when
      boolean result = adapter.matches("CorrectPassword", hash);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[M-92] matches returns false for incorrect raw password vs hash")
    void matches_incorrectPassword_returnsFalse() {
      // given
      String hash = adapter.encode("CorrectPassword");

      // when
      boolean result = adapter.matches("WrongPassword", hash);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[M-93] matches returns false for empty string vs hash")
    void matches_emptyString_returnsFalse() {
      // given
      String hash = adapter.encode("CorrectPassword");

      // when
      boolean result = adapter.matches("", hash);

      // then
      assertThat(result).isFalse();
    }
  }
}
