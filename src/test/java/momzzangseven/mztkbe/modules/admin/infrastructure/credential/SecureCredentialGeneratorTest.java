package momzzangseven.mztkbe.modules.admin.infrastructure.credential;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SecureCredentialGenerator 단위 테스트")
class SecureCredentialGeneratorTest {

  private static final String ALLOWED_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";

  private SecureCredentialGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new SecureCredentialGenerator();
  }

  // ---------------------------------------------------------------------------
  // generate() — full credentials
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("generate() 메서드")
  class Generate {

    @Test
    @DisplayName("[M-78] generate returns GeneratedAdminCredentials with 8-digit loginId")
    void generate_returnsCredentialsWithEightDigitLoginId() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      assertThat(credentials).isNotNull();
      assertThat(credentials.loginId()).hasSize(8);
      assertThat(credentials.loginId()).matches("^\\d{8}$");
    }

    @Test
    @DisplayName("[M-79] generate returns password with exactly 20 characters")
    void generate_returnsPasswordWithTwentyCharacters() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      assertThat(credentials.plaintext()).hasSize(20);
    }

    @Test
    @DisplayName("[M-80] generate returns password containing at least one uppercase letter")
    void generate_passwordContainsUppercase() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      assertThat(credentials.plaintext()).matches(".*[A-Z].*");
    }

    @Test
    @DisplayName("[M-81] generate returns password containing at least one lowercase letter")
    void generate_passwordContainsLowercase() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      assertThat(credentials.plaintext()).matches(".*[a-z].*");
    }

    @Test
    @DisplayName("[M-82] generate returns password containing at least one digit")
    void generate_passwordContainsDigit() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      assertThat(credentials.plaintext()).matches(".*\\d.*");
    }

    @Test
    @DisplayName("[M-83] generate returns password containing at least one special character")
    void generate_passwordContainsSpecialCharacter() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      assertThat(credentials.plaintext()).matches(".*[!@#$%^&*()\\-_=+].*");
    }

    @Test
    @DisplayName("[M-84] generate returns unique credentials on successive calls")
    void generate_returnsUniqueCredentialsOnSuccessiveCalls() {
      // when
      GeneratedAdminCredentials first = generator.generate();
      GeneratedAdminCredentials second = generator.generate();

      // then
      assertThat(first.loginId()).isNotEqualTo(second.loginId());
      assertThat(first.plaintext()).isNotEqualTo(second.plaintext());
    }

    @Test
    @DisplayName("[M-85] generate password contains only characters from the allowed set")
    void generate_passwordContainsOnlyAllowedCharacters() {
      // when
      GeneratedAdminCredentials credentials = generator.generate();

      // then
      for (char c : credentials.plaintext().toCharArray()) {
        assertThat(ALLOWED_CHARS).contains(String.valueOf(c));
      }
    }
  }

  // ---------------------------------------------------------------------------
  // generatePasswordOnly()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("generatePasswordOnly() 메서드")
  class GeneratePasswordOnly {

    @Test
    @DisplayName("[M-86] generatePasswordOnly returns 20-character password")
    void generatePasswordOnly_returnsTwentyCharacterPassword() {
      // when
      String password = generator.generatePasswordOnly();

      // then
      assertThat(password).hasSize(20);
    }

    @Test
    @DisplayName(
        "[M-87] generatePasswordOnly returns password satisfying all complexity requirements")
    void generatePasswordOnly_satisfiesAllComplexityRequirements() {
      // when
      String password = generator.generatePasswordOnly();

      // then
      assertThat(password).matches(".*[A-Z].*");
      assertThat(password).matches(".*[a-z].*");
      assertThat(password).matches(".*\\d.*");
      assertThat(password).matches(".*[!@#$%^&*()\\-_=+].*");
    }

    @Test
    @DisplayName("[M-88] generatePasswordOnly returns unique values on successive calls")
    void generatePasswordOnly_returnsUniqueValuesOnSuccessiveCalls() {
      // when
      String first = generator.generatePasswordOnly();
      String second = generator.generatePasswordOnly();

      // then
      assertThat(first).isNotEqualTo(second);
    }
  }
}
