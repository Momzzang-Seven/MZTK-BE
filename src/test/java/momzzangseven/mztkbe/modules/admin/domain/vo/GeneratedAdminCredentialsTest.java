package momzzangseven.mztkbe.modules.admin.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GeneratedAdminCredentials VO 단위 테스트")
class GeneratedAdminCredentialsTest {

  @Nested
  @DisplayName("toString() 마스킹")
  class ToStringMasking {

    @Test
    @DisplayName("[M-67] toString masks plaintext password")
    void toString_masksPlaintextPassword() {
      // given
      GeneratedAdminCredentials credentials =
          new GeneratedAdminCredentials("admin001", "SuperSecret123!");

      // when
      String result = credentials.toString();

      // then
      assertThat(result).isEqualTo("GeneratedAdminCredentials[loginId=admin001, plaintext=***]");
      assertThat(result).doesNotContain("SuperSecret123!");
    }

    @Test
    @DisplayName("[M-68] toString includes loginId in output")
    void toString_includesLoginId() {
      // given
      GeneratedAdminCredentials credentials = new GeneratedAdminCredentials("admin999", "password");

      // when
      String result = credentials.toString();

      // then
      assertThat(result).contains("loginId=admin999");
    }

    @Test
    @DisplayName("[M-70] toString with null loginId does not throw")
    void toString_nullLoginId_doesNotThrow() {
      // given
      GeneratedAdminCredentials credentials = new GeneratedAdminCredentials(null, "password");

      // when
      String result = credentials.toString();

      // then
      assertThat(result).isEqualTo("GeneratedAdminCredentials[loginId=null, plaintext=***]");
    }

    @Test
    @DisplayName("[M-71] toString with null plaintext still masks")
    void toString_nullPlaintext_stillMasks() {
      // given
      GeneratedAdminCredentials credentials = new GeneratedAdminCredentials("admin001", null);

      // when
      String result = credentials.toString();

      // then
      assertThat(result).isEqualTo("GeneratedAdminCredentials[loginId=admin001, plaintext=***]");
    }
  }

  @Nested
  @DisplayName("레코드 접근자")
  class RecordAccessors {

    @Test
    @DisplayName("[M-69] record accessor methods return correct values")
    void accessors_returnCorrectValues() {
      // given
      GeneratedAdminCredentials credentials =
          new GeneratedAdminCredentials("admin001", "SuperSecret123!");

      // then
      assertThat(credentials.loginId()).isEqualTo("admin001");
      assertThat(credentials.plaintext()).isEqualTo("SuperSecret123!");
    }
  }
}
