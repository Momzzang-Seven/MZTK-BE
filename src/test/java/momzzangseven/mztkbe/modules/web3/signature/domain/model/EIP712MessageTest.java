package momzzangseven.mztkbe.modules.web3.signature.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EIP712Message
 *
 * <p>Tests message creation and validation logic.
 */
@DisplayName("EIP712Message Test")
class EIP712MessageTest {

  private static final String VALID_CONTENT =
      "MomzzangSeven wants you to register your wallet with your Ethereum account:\n"
          + "0x71C7656EC7ab88b098defB751B7401B5f6d8976F\n\n"
          + "URI: https://mztk.com/login\n"
          + "Version: 1\n"
          + "Chain ID: 11155111\n"
          + "Nonce: 550e8400-e29b-41d4-a716-446655440000\n"
          + "Issued At: 2026-01-09T12:00:00Z";
  private static final String VALID_NONCE = "550e8400-e29b-41d4-a716-446655440000";

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("Valid message creates successfully")
    void build_ValidInputs_CreatesMessage() {
      // Given & When
      EIP712Message message =
          EIP712Message.builder().content(VALID_CONTENT).nonce(VALID_NONCE).build();

      // Then
      assertThat(message).isNotNull();
      assertThat(message.getContent()).isEqualTo(VALID_CONTENT);
      assertThat(message.getNonce()).isEqualTo(VALID_NONCE);
    }

    @Test
    @DisplayName("validate() succeeds with valid inputs")
    void validate_ValidInputs_NoException() {
      // Given
      EIP712Message message =
          EIP712Message.builder().content(VALID_CONTENT).nonce(VALID_NONCE).build();

      // When & Then
      assertThatCode(() -> message.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Content with special characters is valid")
    void validate_ContentWithSpecialChars_NoException() {
      // Given
      String specialContent = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?";
      EIP712Message message =
          EIP712Message.builder().content(specialContent).nonce(VALID_NONCE).build();

      // When & Then
      assertThatCode(() -> message.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Content with newlines is valid")
    void validate_ContentWithNewlines_NoException() {
      // Given
      String multilineContent = "Line 1\nLine 2\nLine 3";
      EIP712Message message =
          EIP712Message.builder().content(multilineContent).nonce(VALID_NONCE).build();

      // When & Then
      assertThatCode(() -> message.validate()).doesNotThrowAnyException();
    }
  }

  // ========================================
  // Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Validation Failure Cases")
  class ValidationFailureCases {

    @Test
    @DisplayName("Null content throws exception")
    void validate_NullContent_ThrowsException() {
      // Given
      EIP712Message message = EIP712Message.builder().content(null).nonce(VALID_NONCE).build();

      // When & Then
      assertThatThrownBy(() -> message.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Message content must not be blank");
    }

    @Test
    @DisplayName("Empty content throws exception")
    void validate_EmptyContent_ThrowsException() {
      // Given
      EIP712Message message = EIP712Message.builder().content("").nonce(VALID_NONCE).build();

      // When & Then
      assertThatThrownBy(() -> message.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Message content must not be blank");
    }

    @Test
    @DisplayName("Blank content throws exception")
    void validate_BlankContent_ThrowsException() {
      // Given
      EIP712Message message = EIP712Message.builder().content("   ").nonce(VALID_NONCE).build();

      // When & Then
      assertThatThrownBy(() -> message.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Message content must not be blank");
    }

    @Test
    @DisplayName("Null nonce throws exception")
    void validate_NullNonce_ThrowsException() {
      // Given
      EIP712Message message = EIP712Message.builder().content(VALID_CONTENT).nonce(null).build();

      // When & Then
      assertThatThrownBy(() -> message.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nonce must not be blank");
    }

    @Test
    @DisplayName("Empty nonce throws exception")
    void validate_EmptyNonce_ThrowsException() {
      // Given
      EIP712Message message = EIP712Message.builder().content(VALID_CONTENT).nonce("").build();

      // When & Then
      assertThatThrownBy(() -> message.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nonce must not be blank");
    }

    @Test
    @DisplayName("Blank nonce throws exception")
    void validate_BlankNonce_ThrowsException() {
      // Given
      EIP712Message message = EIP712Message.builder().content(VALID_CONTENT).nonce("   ").build();

      // When & Then
      assertThatThrownBy(() -> message.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nonce must not be blank");
    }
  }
}
