package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class ProvisionTreasuryKeyResultTest {

  @Test
  void constructor_storesValue_whenValid() {
    ProvisionTreasuryKeyResult result = new ProvisionTreasuryKeyResult("base64-key");

    assertThat(result.treasuryKeyEncryptionKeyB64()).isEqualTo("base64-key");
  }

  @Test
  void constructor_throws_whenBlank() {
    assertThatThrownBy(() -> new ProvisionTreasuryKeyResult("  "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("treasuryKeyEncryptionKeyB64 is required");
  }
}
