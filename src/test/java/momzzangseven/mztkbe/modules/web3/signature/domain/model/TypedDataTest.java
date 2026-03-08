package momzzangseven.mztkbe.modules.web3.signature.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TypedData
 *
 * <p>Tests TypedData factory method and validation logic.
 */
@DisplayName("TypedData Test")
class TypedDataTest {

  private EIP712Domain validDomain;
  private EIP712Message validMessage;

  @BeforeEach
  void setUp() {
    validDomain =
        EIP712Domain.builder()
            .name("MomzzangSeven")
            .version("1")
            .chainId(11155111L)
            .verifyingContract("0xCcCCccccCCCCccccCCCCccccCCCCccccCCCCcccc")
            .build();

    validMessage =
        EIP712Message.builder()
            .content(
                "MomzzangSeven wants you to register your wallet with your Ethereum account:\n"
                    + "0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
            .nonce("550e8400-e29b-41d4-a716-446655440000")
            .build();
  }

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("forWalletRegistration creates valid TypedData")
    void forWalletRegistration_ValidInputs_CreatesTypedData() {
      // Given & When
      TypedData typedData = TypedData.forWalletRegistration(validDomain, validMessage);

      // Then
      assertThat(typedData).isNotNull();
      assertThat(typedData.getDomain()).isEqualTo(validDomain);
      assertThat(typedData.getMessage()).isEqualTo(validMessage);
      assertThat(typedData.getPrimaryType()).isEqualTo("AuthRequest");
    }

    @Test
    @DisplayName("Primary type is always AuthRequest")
    void forWalletRegistration_AlwaysSetsAuthRequest() {
      // Given & When
      TypedData typedData = TypedData.forWalletRegistration(validDomain, validMessage);

      // Then
      assertThat(typedData.getPrimaryType()).isEqualTo("AuthRequest");
    }

    @Test
    @DisplayName("Domain and message are preserved")
    void forWalletRegistration_PreservesDomainAndMessage() {
      // Given & When
      TypedData typedData = TypedData.forWalletRegistration(validDomain, validMessage);

      // Then
      assertThat(typedData.getDomain()).isSameAs(validDomain);
      assertThat(typedData.getMessage()).isSameAs(validMessage);
    }
  }

  // ========================================
  // Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Validation Failure Cases")
  class ValidationFailureCases {

    @Test
    @DisplayName("Null domain throws exception")
    void forWalletRegistration_NullDomain_ThrowsException() {
      // Given
      EIP712Domain nullDomain = null;

      // When & Then
      assertThatThrownBy(() -> TypedData.forWalletRegistration(nullDomain, validMessage))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain must not be null");
    }

    @Test
    @DisplayName("Null message throws exception")
    void forWalletRegistration_NullMessage_ThrowsException() {
      // Given
      EIP712Message nullMessage = null;

      // When & Then
      assertThatThrownBy(() -> TypedData.forWalletRegistration(validDomain, nullMessage))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Message must not be null");
    }

    @Test
    @DisplayName("Invalid domain throws exception")
    void forWalletRegistration_InvalidDomain_ThrowsException() {
      // Given
      EIP712Domain invalidDomain =
          EIP712Domain.builder()
              .name("") // Invalid: empty name
              .version("1")
              .chainId(11155111L)
              .verifyingContract("0xCcCCccccCCCCccccCCCCccccCCCCccccCCCCcccc")
              .build();

      // When & Then
      assertThatThrownBy(() -> TypedData.forWalletRegistration(invalidDomain, validMessage))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain name must not be blank");
    }

    @Test
    @DisplayName("Invalid message throws exception")
    void forWalletRegistration_InvalidMessage_ThrowsException() {
      // Given
      EIP712Message invalidMessage =
          EIP712Message.builder()
              .content("Valid content")
              .nonce("") // Invalid: empty nonce
              .build();

      // When & Then
      assertThatThrownBy(() -> TypedData.forWalletRegistration(validDomain, invalidMessage))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nonce must not be blank");
    }
  }
}
