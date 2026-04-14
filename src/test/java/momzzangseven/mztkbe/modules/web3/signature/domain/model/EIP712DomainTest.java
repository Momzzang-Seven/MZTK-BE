package momzzangseven.mztkbe.modules.web3.signature.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EIP712Domain
 *
 * <p>Tests domain separator creation and validation logic.
 */
@DisplayName("EIP712Domain Test")
class EIP712DomainTest {

  private static final String VALID_NAME = "MomzzangSeven";
  private static final String VALID_VERSION = "1";
  private static final Long VALID_CHAIN_ID = 11155111L;
  private static final String VALID_CONTRACT = "0xCcCCccccCCCCccccCCCCccccCCCCccccCCCCcccc";

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("Valid domain creates successfully")
    void build_ValidInputs_CreatesDomain() {
      // Given & When
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // Then
      assertThat(domain).isNotNull();
      assertThat(domain.getName()).isEqualTo(VALID_NAME);
      assertThat(domain.getVersion()).isEqualTo(VALID_VERSION);
      assertThat(domain.getChainId()).isEqualTo(VALID_CHAIN_ID);
      assertThat(domain.getVerifyingContract()).isEqualTo(VALID_CONTRACT);
    }

    @Test
    @DisplayName("validate() succeeds with valid inputs")
    void validate_ValidInputs_NoException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatCode(() -> domain.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Lowercase contract address is valid")
    void validate_LowercaseContract_NoException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract("0xcccccccccccccccccccccccccccccccccccccccc")
              .build();

      // When & Then
      assertThatCode(() -> domain.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mixed case contract address is valid")
    void validate_MixedCaseContract_NoException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
              .build();

      // When & Then
      assertThatCode(() -> domain.validate()).doesNotThrowAnyException();
    }
  }

  // ========================================
  // Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Validation Failure Cases")
  class ValidationFailureCases {

    @Test
    @DisplayName("Null name throws exception")
    void validate_NullName_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(null)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain name must not be blank");
    }

    @Test
    @DisplayName("Empty name throws exception")
    void validate_EmptyName_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name("")
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain name must not be blank");
    }

    @Test
    @DisplayName("Blank name throws exception")
    void validate_BlankName_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name("   ")
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain name must not be blank");
    }

    @Test
    @DisplayName("Null version throws exception")
    void validate_NullVersion_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(null)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain version must not be blank");
    }

    @Test
    @DisplayName("Empty version throws exception")
    void validate_EmptyVersion_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version("")
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Domain version must not be blank");
    }

    @Test
    @DisplayName("Null chainId throws exception")
    void validate_NullChainId_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(null)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Chain ID must be positive");
    }

    @Test
    @DisplayName("Zero chainId throws exception")
    void validate_ZeroChainId_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(0L)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Chain ID must be positive");
    }

    @Test
    @DisplayName("Negative chainId throws exception")
    void validate_NegativeChainId_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(-1L)
              .verifyingContract(VALID_CONTRACT)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Chain ID must be positive");
    }

    @Test
    @DisplayName("Null verifying contract throws exception")
    void validate_NullContract_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract(null)
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid verifying contract address");
    }

    @Test
    @DisplayName("Invalid contract address format throws exception")
    void validate_InvalidContractFormat_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract("invalid-address")
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid verifying contract address");
    }

    @Test
    @DisplayName("Contract address without 0x prefix throws exception")
    void validate_ContractWithout0xPrefix_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract("CcCCccccCCCCccccCCCCccccCCCCccccCCCCcccc")
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid verifying contract address");
    }

    @Test
    @DisplayName("Contract address too short throws exception")
    void validate_ContractTooShort_ThrowsException() {
      // Given
      EIP712Domain domain =
          EIP712Domain.builder()
              .name(VALID_NAME)
              .version(VALID_VERSION)
              .chainId(VALID_CHAIN_ID)
              .verifyingContract("0xCcCC")
              .build();

      // When & Then
      assertThatThrownBy(() -> domain.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid verifying contract address");
    }
  }
}
