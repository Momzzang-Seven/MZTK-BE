package momzzangseven.mztkbe.modules.web3.token.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class ProvisionTreasuryKeyResultTest {

  @Test
  void of_returnsRecord_whenAllFieldsProvided() {
    ProvisionTreasuryKeyResult result =
        ProvisionTreasuryKeyResult.of("0x" + "a".repeat(40), "encrypted", "kek");

    assertThat(result.treasuryAddress()).isEqualTo("0x" + "a".repeat(40));
    assertThat(result.treasuryPrivateKeyEncrypted()).isEqualTo("encrypted");
    assertThat(result.treasuryKeyEncryptionKeyB64()).isEqualTo("kek");
  }

  @Test
  void of_throws_whenEncryptedKeyBlank() {
    assertThatThrownBy(() -> ProvisionTreasuryKeyResult.of("0x" + "a".repeat(40), " ", "kek"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("treasuryPrivateKeyEncrypted is required");
  }

  @Test
  void of_throws_whenTreasuryAddressBlank() {
    assertThatThrownBy(() -> ProvisionTreasuryKeyResult.of(" ", "encrypted", "kek"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("treasuryAddress is required");
  }

  @Test
  void of_throws_whenEncryptionKeyBlank() {
    assertThatThrownBy(() -> ProvisionTreasuryKeyResult.of("0x" + "a".repeat(40), "encrypted", " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("treasuryKeyEncryptionKeyB64 is required");
  }
}
